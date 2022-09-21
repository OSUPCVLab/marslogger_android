package edu.osu.pcv.marslogger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;

import android.util.Log;

import android.util.Size;
import android.util.SizeF;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;

public class Camera2Proxy {

    private static final String TAG = "Camera2Proxy";

    private Activity mActivity;
    private static SharedPreferences mSharedPreferences;
    private String mCameraIdStr = "";
    private Size mPreviewSize;
    private Size mVideoSize;
    private CameraManager mCameraManager;
    private CameraCharacteristics mCameraCharacteristics;
    private Set<String> mPhysicalCameraIds;
    private List<Integer> mZoomRatios;
    private int mMaxZoomIndex;
    private Rect mScalarCropRegion;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Rect sensorArraySize;
    private Integer mTimeSourceValue;

    private CaptureRequest mPreviewRequest;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private ImageReader mImageReader;
    private Surface mPreviewSurface;
    private SurfaceTexture mPreviewSurfaceTexture = null;
    private OrientationEventListener mOrientationEventListener;

    private int mDisplayRotate = 0;
    private int mDeviceOrientation = 0;
    private int mZoom = 1;


    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Wait until the CONTROL_AF_MODE is in auto.
     */
    private static final int STATE_WAITING_AUTO = 1;

    /**
     * Trigger auto focus algorithm.
     */
    private static final int STATE_TRIGGER_AUTO = 2;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 3;

    /**
     * Camera state: Focus distance is locked.
     */
    private static final int STATE_FOCUS_LOCKED = 4;
    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mFocusCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    private BufferedWriter mFrameMetadataWriter = null;

    // https://stackoverflow.com/questions/3786825/volatile-boolean-vs-atomicboolean
    private volatile boolean mRecordingMetadata = false;

    private FocalLengthHelper mFocalLengthHelper = new FocalLengthHelper();

    public boolean mSupportSnapshot = false; // Previewing both video frames and image frames slows down video frame rate.

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Timber.d("onOpened");
            mCameraDevice = camera;
            initPreviewRequest(mSupportSnapshot);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Timber.d("onDisconnected");
            releaseCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Timber.w("Camera Open failed with error %d", error);
            releaseCamera();
        }
    };

    public Integer getmTimeSourceValue() {
        return mTimeSourceValue;
    }

    public Size getmVideoSize() {
        return mVideoSize;
    }

    public void startRecordingCaptureResult(String captureResultFile) {
        try {
            if (mFrameMetadataWriter != null) {
                try {
                    mFrameMetadataWriter.flush();
                    mFrameMetadataWriter.close();
                    Log.d(TAG, "Flushing results!");
                } catch (IOException err) {
                    Timber.e(err, "IOException in closing an earlier frameMetadataWriter.");
                }
            }
            mFrameMetadataWriter = new BufferedWriter(
                    new FileWriter(captureResultFile, true));
            String header = "Timestamp[nanosec],fx[px],fy[px],Frame No.," +
                    "Exposure time[nanosec],Sensor frame duration[nanosec]," +
                    "Frame readout time[nanosec]," +
                    "ISO,Focal length,Focus distance,AF mode,Unix time[nanosec]";

            mFrameMetadataWriter.write(header + "\n");
            mRecordingMetadata = true;
        } catch (IOException err) {
            Timber.e(err, "IOException in opening frameMetadataWriter at %s",
                    captureResultFile);
        }
    }

    public void resumeRecordingCaptureResult() {
        mRecordingMetadata = true;
    }

    public void pauseRecordingCaptureResult() {
        mRecordingMetadata = false;
    }

    public void stopRecordingCaptureResult() {
        if (mRecordingMetadata) {
            mRecordingMetadata = false;
        }
        if (mFrameMetadataWriter != null) {
            try {
                mFrameMetadataWriter.flush();
                mFrameMetadataWriter.close();
            } catch (IOException err) {
                Timber.e(err, "IOException in closing frameMetadataWriter.");
            }
            mFrameMetadataWriter = null;
        }
    }

    public Camera2Proxy(Activity activity) {
        mActivity = activity;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        mOrientationEventListener = new OrientationEventListener(mActivity) {
            @Override
            public void onOrientationChanged(int orientation) {
                mDeviceOrientation = orientation;
            }
        };
    }

    /**
     * This function requires mCameraCharacteristics.
     * Refer to setZoom() in OpenCamera,
     * https://github.com/AntumDeluge/Open_Camera/blob/master/app/src/main/java/
     * net/sourceforge/opencamera/cameracontroller/CameraController2.java
     * @param value
     */
    public void computeZoomRegion(int value) {
        if (mZoomRatios == null) {
            Timber.d("zoom not supported");
            return;
        }
        if (value < 0 || value > mZoomRatios.size()) {
            Timber.e("invalid zoom value %d", value);
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        float zoom = mZoomRatios.get(value) / 100.0f;
        Rect sensor_rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int left = sensor_rect.width() / 2;
        int right = left;
        int top = sensor_rect.height() / 2;
        int bottom = top;
        int hwidth = (int) (sensor_rect.width() / (2.0 * zoom));
        int hheight = (int) (sensor_rect.height() / (2.0 * zoom));
        left -= hwidth;
        right += hwidth;
        top -= hheight;
        bottom += hheight;

        Timber.d("zoom: %f\nhwidth: %d\nhheight: %d\n" +
                        "sensor_rect left: %d\nsensor_rect top: %d\n" +
                        "sensor_rect right: %d\nsensor_rect bottom: %d\n" +
                        "left: %d\ntop: %d\nright: %d\nbottom: %d",
                zoom, hwidth, hheight,
                sensor_rect.left, sensor_rect.top, sensor_rect.right, sensor_rect.bottom,
                left, top, right, bottom);
        mScalarCropRegion = new Rect(left, top, right, bottom);
    }

    /**
     * This function requires mCameraCharacteristics.
     * @param value
     */
    private void computeZoomRatios() {
        float max_zoom = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        boolean is_zoom_supported = max_zoom > 0.0f;
        Timber.d("max_zoom: " + max_zoom);
        if( is_zoom_supported ) {
            // set 20 steps per 2x factor
            final int steps_per_2x_factor = 20;
            //final double scale_factor = Math.pow(2.0, 1.0/(double)steps_per_2x_factor);
            int n_steps =(int)( (steps_per_2x_factor * Math.log(max_zoom + 1.0e-11)) / Math.log(2.0));
            final double scale_factor = Math.pow(max_zoom, 1.0/(double)n_steps);
            Timber.d("n_steps: %d\nscale_factor: %f", n_steps, scale_factor);

            mZoomRatios = new ArrayList<>();
            mZoomRatios.add(100);
            double zoom = 1.0;
            for(int i=0;i<n_steps-1;i++) {
                zoom *= scale_factor;
                mZoomRatios.add((int)(zoom*100));
            }
            mZoomRatios.add((int)(max_zoom*100));
            mMaxZoomIndex = mZoomRatios.size()-1;
        }
        else {
            mZoomRatios = null;
        }
    }

    public Size configureCamera() {
        try {
            mCameraIdStr = mSharedPreferences.getString("prefCamera", "0");
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraIdStr);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mPhysicalCameraIds = mCameraCharacteristics.getPhysicalCameraIds();
            }

            computeZoomRatios();
            String imageSize = mSharedPreferences.getString("prefSizeRaw",
                    DesiredCameraSetting.mDesiredFrameSize);
            int width = Integer.parseInt(imageSize.substring(0, imageSize.lastIndexOf("x")));
            int height = Integer.parseInt(imageSize.substring(imageSize.lastIndexOf("x") + 1));

            sensorArraySize = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            mTimeSourceValue = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);

            StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics
                    .SCALER_STREAM_CONFIGURATION_MAP);

            Size[] videoSizeChoices = map.getOutputSizes(MediaRecorder.class);
            mVideoSize = CameraUtils.chooseVideoSize(videoSizeChoices, width, height, width);

            mFocalLengthHelper.setLensParams(mCameraCharacteristics);
            mFocalLengthHelper.setmImageSize(mVideoSize);

            mPreviewSize = CameraUtils.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);
            Timber.d("Video size %s preview size %s.",
                    mVideoSize.toString(), mPreviewSize.toString());

        } catch (CameraAccessException e) {
            Timber.e(e);
        }
        return mPreviewSize;
    }

    @SuppressLint("MissingPermission")
    public void openCamera(boolean supportSnapshot) {
        Timber.v("openCamera");
        startBackgroundThread();
        mOrientationEventListener.enable();
        if (mCameraIdStr.isEmpty()) {
            configureCamera();
        }
        if (supportSnapshot)
            initImageReader();
        mSupportSnapshot = supportSnapshot;
        try {
            mCameraManager.openCamera(mCameraIdStr, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Timber.e(e);
        }
    }

    public void releaseCamera() {
        Timber.v("releaseCamera");
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        mOrientationEventListener.disable();
        mPreviewSurfaceTexture = null;
        mCameraIdStr = "";
        stopRecordingCaptureResult();
        stopBackgroundThread();
    }

    public void setPreviewSurface(SurfaceHolder holder) {
        mPreviewSurface = holder.getSurface();
    }

    public void setPreviewSurfaceTexture(SurfaceTexture surfaceTexture) {
        mPreviewSurfaceTexture = surfaceTexture;
    }

    /**
     * assume mVideoSize has been initialized say by configureCamera.
     */
    private void initImageReader() {
        mImageReader = ImageReader.newInstance(mVideoSize.getWidth(), mVideoSize.getHeight(),
                ImageFormat.JPEG, 3);
        // Because saving images is done on the main UI thread, the handler is set null.
        // If the handler is not null say mBackgroundHandler, when onPause() is called,
        // the handler will be torn down, The IllegalStateException:
        // sending message to a Handler on a dead thread, will be thrown out.
        mImageReader.setOnImageAvailableListener(
                ((PhotoCaptureActivity) mActivity).mImageAvailableListener, null);

        Timber.d("Image reader size w: %d, h: %d",mImageReader.getWidth(),
                mImageReader.getHeight());
    }

    private class NumExpoIso {
        public Long mNumber;
        public Long mExposureNanos;
        public Integer mIso;

        public NumExpoIso(Long number, Long expoNanos, Integer iso) {
            mNumber = number;
            mExposureNanos = expoNanos;
            mIso = iso;
        }
    }

    private final int kMaxExpoSamples = 10;
    private ArrayList<NumExpoIso> expoStats = new ArrayList<>(kMaxExpoSamples);

    private void setExposureAndIso() {
        Long exposureNanos = DesiredCameraSetting.mDesiredExposureTime;
        Long desiredIsoL = 30L * 30000000L / exposureNanos;
        Integer desiredIso = desiredIsoL.intValue();
        if (!expoStats.isEmpty()) {
            int index = expoStats.size() / 2;
            Long actualExpo = expoStats.get(index).mExposureNanos;
            Integer actualIso = expoStats.get(index).mIso;
            if (actualExpo != null && actualIso != null) {
                if (actualExpo <= exposureNanos) {
                    exposureNanos = actualExpo;
                    desiredIso = actualIso;
                } else {
                    desiredIsoL = actualIso * actualExpo / exposureNanos;
                    desiredIso = desiredIsoL.intValue();
                }
            } // else may occur on an emulated device.
        }

        boolean manualControl = mSharedPreferences.getBoolean("switchManualControl", false);
        if (manualControl) {
            float exposureTimeMs = (float) exposureNanos / 1e6f;
            String exposureTimeMsStr = mSharedPreferences.getString(
                    "prefExposureTime", String.valueOf(exposureTimeMs));
            exposureNanos = (long) (Float.parseFloat(exposureTimeMsStr) * 1e6f);
            String desiredIsoStr = mSharedPreferences.getString("prefISO", String.valueOf(desiredIso));
            desiredIso = Integer.parseInt(desiredIsoStr);
        }

        // fix exposure
        mPreviewRequestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);

        mPreviewRequestBuilder.set(
                CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNanos);
        Timber.d("Exposure time set to %d", exposureNanos);

        // fix ISO
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, desiredIso);
        Timber.d("ISO set to %d", desiredIso);
    }

    private void initPreviewRequest(boolean previewForSnapshot) {
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            // Set control elements, we want auto white balance
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);

//            computeZoomRegion(0);
//            if( mScalarCropRegion != null ) {
//                Rect current_rect = mPreviewRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION);
//                Timber.d("current_rect left: %d, current_rect top: %d, " +
//                                "current_rect right: %d, current_rect bottom: %d",
//                        current_rect.left, current_rect.top,
//                        current_rect.right, current_rect.bottom);
//                mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, mScalarCropRegion);
//            }

            // We disable customizing focus distance by user input because
            // it is less flexible than tap to focus.
//            boolean manualControl = mSharedPreferences.getBoolean("switchManualControl", false);
//            if (manualControl) {
//                String focus = mSharedPreferences.getString("prefFocusDistance", "5.0");
//                Float focusDistance = Float.parseFloat(focus);
//                mPreviewRequestBuilder.set(
//                        CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
//                mPreviewRequestBuilder.set(
//                        CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance);
//                Timber.d("Focus distance set to %f", focusDistance);
//            }

            List<Surface> surfaces = new ArrayList<>();
            if (previewForSnapshot) {
                Surface readerSurface = mImageReader.getSurface();
                surfaces.add(readerSurface);
                mPreviewRequestBuilder.addTarget(readerSurface);
            }

            if (mPreviewSurfaceTexture != null && mPreviewSurface == null) { // use texture view
                mPreviewSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),
                        mPreviewSize.getHeight());
                mPreviewSurface = new Surface(mPreviewSurfaceTexture);
            }
            surfaces.add(mPreviewSurface);
            mPreviewRequestBuilder.addTarget(mPreviewSurface);

            CameraCaptureSession.StateCallback captureStateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCaptureSession = session;
                    mPreviewRequest = mPreviewRequestBuilder.build();
                    startPreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Timber.w("ConfigureFailed. session: mCaptureSession");
                }
            };

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                for (Surface surface : surfaces) {
                    OutputConfiguration config = new OutputConfiguration(surface);
                    if (!mPhysicalCameraIds.isEmpty()) {
                        // As a rule of thumb, we assume that normal camera at index 0, wide angle lens at index 1.
                        // TODO(jhuai): choose wide angles lens by checking the camera characteristics as in Basicbokeh.
                        String physicalCameraId = (String) mPhysicalCameraIds.toArray()[1];
                        config.setPhysicalCameraId(physicalCameraId);
                    }
                    outputConfigs.add(config);
                }
                ExecutorService executor = Executors.newCachedThreadPool();
                SessionConfiguration sessionConfig = new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputConfigs,
                        executor,
                        captureStateCallback);
                mCameraDevice.createCaptureSession(sessionConfig);
            } else {
                mCameraDevice.createCaptureSession(surfaces,
                        captureStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            Timber.e(e);
        }
    }

    public void startPreview() {
        Timber.v("startPreview");
        if (mCaptureSession == null || mPreviewRequestBuilder == null) {
            Timber.w("startPreview: mCaptureSession or mPreviewRequestBuilder is null");
            return;
        }
        try {
            mCaptureSession.setRepeatingRequest(
                    mPreviewRequest, mFocusCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Timber.e(e);
        }
    }

    public void stopPreview() {
        Timber.v("stopPreview");
        if (mCaptureSession == null || mPreviewRequestBuilder == null) {
            Timber.w("stopPreview: mCaptureSession or mPreviewRequestBuilder is null");
            return;
        }
        try {
            mCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            Timber.e(e);
        }
    }

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to tap to focus.
     * https://stackoverflow.com/questions/42127464/how-to-lock-focus-in-camera2-api-android
     */
    private CameraCaptureSession.CaptureCallback mFocusCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_AUTO: {
                    Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);
                    if (afMode != null && afMode == CaptureResult.CONTROL_AF_MODE_AUTO) {
                        mState = STATE_TRIGGER_AUTO;

                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_AUTO);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CameraMetadata.CONTROL_AF_TRIGGER_START);
                        try {
                            mCaptureSession.capture(
                                    mPreviewRequestBuilder.build(),
                                    mFocusCaptureCallback, mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            Timber.e(e);
                        }
                    }
                    break;
                }
                case STATE_TRIGGER_AUTO: {
                    mState = STATE_WAITING_LOCK;

                    setExposureAndIso();

                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_AUTO);
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                    try {
                        mCaptureSession.setRepeatingRequest(
                                mPreviewRequestBuilder.build(),
                                mFocusCaptureCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        Timber.e(e);
                    }
                    Timber.d("Focus trigger auto");
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        mState = STATE_FOCUS_LOCKED;
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        mState = STATE_FOCUS_LOCKED;
                        Timber.d("Focus locked after waiting lock");
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            long unixTime = System.currentTimeMillis();
            process(result);

            Long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
            Long number = result.getFrameNumber();
            Long exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);

            Long frmDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION);
            Long frmReadoutNs = result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW);
            Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (expoStats.size() > kMaxExpoSamples) {
                expoStats.subList(0, kMaxExpoSamples / 2).clear();
            }
            expoStats.add(new NumExpoIso(number, exposureTimeNs, iso));

            Float fl = result.get(CaptureResult.LENS_FOCAL_LENGTH);

            Float fd = result.get(CaptureResult.LENS_FOCUS_DISTANCE);

            Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);

            Rect rect = result.get(CaptureResult.SCALER_CROP_REGION);
            mFocalLengthHelper.setmFocalLength(fl);
            mFocalLengthHelper.setmFocusDistance(fd);
            mFocalLengthHelper.setmCropRegion(rect);
            SizeF sz_focal_length = mFocalLengthHelper.getFocalLengthPixel();
            String delimiter = ",";
            StringBuilder sb = new StringBuilder();
            sb.append(timestamp);
            sb.append(delimiter + sz_focal_length.getWidth());
            sb.append(delimiter + sz_focal_length.getHeight());
            sb.append(delimiter + number);
            sb.append(delimiter + exposureTimeNs);
            sb.append(delimiter + frmDurationNs);
            sb.append(delimiter + frmReadoutNs);
            sb.append(delimiter + iso);
            sb.append(delimiter + fl);
            sb.append(delimiter + fd);
            sb.append(delimiter + afMode);
            sb.append(delimiter + unixTime + "000000");
            String frame_info = sb.toString();
            if (mRecordingMetadata) {
                try {
                    mFrameMetadataWriter.write(frame_info + "\n");
                } catch (IOException err) {
                    Timber.e(err, "Error writing captureResult");
                }
            }
            ((CameraCaptureActivityBase) mActivity).updateCaptureResultPanel(
                    sz_focal_length.getWidth(), exposureTimeNs, afMode);
        }

    };


    void changeManualFocusPoint(ManualFocusConfig focusConfig) {
        float eventX = focusConfig.mEventX;
        float eventY = focusConfig.mEventY;
        int viewWidth = focusConfig.mViewWidth;
        int viewHeight = focusConfig.mViewHeight;

        final int y = (int) ((eventX / (float) viewWidth) * (float) sensorArraySize.height());
        final int x = (int) ((eventY / (float) viewHeight) * (float) sensorArraySize.width());
        final int halfTouchWidth = 400;
        final int halfTouchHeight = 400;
        MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(x - halfTouchWidth, 0),
                Math.max(y - halfTouchHeight, 0),
                halfTouchWidth * 2,
                halfTouchHeight * 2,
                MeteringRectangle.METERING_WEIGHT_MAX - 1);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS,
                new MeteringRectangle[]{focusAreaTouch});
        try {
            mState = STATE_WAITING_AUTO;
            mCaptureSession.setRepeatingRequest(
                    mPreviewRequestBuilder.build(), mFocusCaptureCallback, null);
        } catch (CameraAccessException e) {
            Timber.e(e);
        }
    }

    private void startBackgroundThread() {
        if (mBackgroundThread == null || mBackgroundHandler == null) {
            Timber.v("startBackgroundThread");
            mBackgroundThread = new HandlerThread("CameraBackground");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        Timber.v("stopBackgroundThread");
        try {
            if (mBackgroundThread != null) {
                mBackgroundThread.quitSafely();
                mBackgroundThread.join();
            }
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Timber.e(e);
        }
    }
}
