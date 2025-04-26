package edu.osu.pcv.marslogger;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.os.Environment;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import timber.log.Timber;

public class CameraCapture implements SurfaceTexture.OnFrameAvailableListener {
    public static final String TAG = "MarsLogger";
    protected static final boolean VERBOSE = false;

    // Camera filters; must match up with cameraFilterNames in strings.xml
    static final int FILTER_NONE = 0;
    static final int FILTER_BLACK_WHITE = 1;
    static final int FILTER_BLUR = 2;
    static final int FILTER_SHARPEN = 3;
    static final int FILTER_EDGE_DETECT = 4;
    static final int FILTER_EMBOSS = 5;

    private final Activity mActivity;

    protected CameraSurfaceRenderer mRenderer = null;
    protected TextView mKeyCameraParamsText;
    protected TextView mCaptureResultText;

    protected int mCameraPreviewWidth, mCameraPreviewHeight;
    protected int mVideoFrameWidth, mVideoFrameHeight;
    static boolean mSnapshotMode = false;
    protected Camera2Proxy mCamera2Proxy = null;

    protected SampleGLView mGLView;
    protected TextureMovieEncoder sVideoEncoder = new TextureMovieEncoder();

    public CameraCapture(Activity activity) {
        mActivity = activity;
    }

    protected Activity activity() {
        return mActivity;
    }

    void initCamera2Proxy() {
        if (mCamera2Proxy == null) {
            mCamera2Proxy = new Camera2Proxy(this);
            Size previewSize = mCamera2Proxy.configureCamera();
            setLayoutAspectRatio(previewSize);  // updates mCameraPreviewWidth/Height
            Size videoSize = mCamera2Proxy.getmVideoSize();
            mVideoFrameWidth = videoSize.getWidth();
            mVideoFrameHeight = videoSize.getHeight();
        }
    }

    void initRenderer(CameraHandler cameraHandler, SampleGLView glView) {
        mGLView = glView;
        if (mRenderer == null) {
            mRenderer = new CameraSurfaceRenderer(
                    cameraHandler, sVideoEncoder);
            mGLView.setEGLContextClientVersion(2);     // select GLES 2.0
            mGLView.setRenderer(mRenderer);
            mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
    }

    void resume() {
        mGLView.onResume();
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);
                mRenderer.setVideoFrameSize(mVideoFrameWidth, mVideoFrameHeight);
            }
        });
    }


    void pause() {
        // no more frame metadata will be saved during pause
        if (mCamera2Proxy != null) {
            mCamera2Proxy.releaseCamera();
            mCamera2Proxy = null;
        }

        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // Tell the renderer that it's about to be paused so it can clean up.
                mRenderer.notifyPausing();
            }
        });
        mGLView.onPause();
    }

    void changeFilter(int filterNum) {
        mGLView.queueEvent(new Runnable() {
        @Override
        public void run () {
                // notify the renderer that we want to change the encoder's state
                mRenderer.changeFilterMode(filterNum);
            }
        });
    }

    void toggleRecording(boolean recordingEnabled) {
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.changeRecordingState(recordingEnabled);
            }
        });
    }

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    public void handleSetSurfaceTexture(SurfaceTexture st) {
        st.setOnFrameAvailableListener(this);

        if (mCamera2Proxy != null) {
            mCamera2Proxy.setPreviewSurfaceTexture(st);
            mCamera2Proxy.openCamera(mSnapshotMode);
        } else {
            throw new RuntimeException(
                    "Try to set surface texture while camera2proxy is null");
        }
    }
    public Camera2Proxy getmCamera2Proxy() {
        if (mCamera2Proxy == null) {
            throw new RuntimeException(
                    "Get a null Camera2Proxy");
        }
        return mCamera2Proxy;
    }

    protected String renewOutputDir() {
        SimpleDateFormat dateFormat =
                new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String folderName = dateFormat.format(new Date());

        String dir1 = mActivity.getFilesDir().getAbsolutePath();
        String dir2 = Environment.getExternalStorageDirectory().
                getAbsolutePath() + File.separator + "mars_logger";

        String dir3 = mActivity.getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
        Timber.d("dir 1 %s\ndir 2 %s\ndir 3 %s", dir1, dir2, dir3);
        // dir1 and dir3 are always available for the app even the
        // write external storage permission is not granted.
        // "Apparently in Marshmallow when you install with Android studio it
        // never asks you if you should give it permission it just quietly
        // fails, like you denied it. You must go into Settings, apps, select
        // your application and flip the permission switch on."
        // ref: https://stackoverflow.com/questions/40087355/android-mkdirs-not-working
        String outputDir = dir3 + File.separator + folderName;
        (new File(outputDir)).mkdirs();
        return outputDir;
    }

    // updates mCameraPreviewWidth/Height
    protected void setLayoutAspectRatio(Size cameraPreviewSize) {
        AspectFrameLayout layout =  mActivity.findViewById(R.id.cameraPreview_afl);
        Display display = ((WindowManager)mActivity.getSystemService(mActivity.WINDOW_SERVICE)).getDefaultDisplay();
        mCameraPreviewWidth = cameraPreviewSize.getWidth();
        mCameraPreviewHeight = cameraPreviewSize.getHeight();
        if (display.getRotation() == Surface.ROTATION_0) {
            layout.setAspectRatio((double) mCameraPreviewHeight / mCameraPreviewWidth);
        } else if (display.getRotation() == Surface.ROTATION_180) {
            layout.setAspectRatio((double) mCameraPreviewHeight / mCameraPreviewWidth);
        } else {
            layout.setAspectRatio((double) mCameraPreviewWidth / mCameraPreviewHeight);
        }
    }

    public void updateCaptureResultPanel(
            final Float fl,
            final Long exposureTimeNs, final Integer afMode, final Float fd) {
        final String sfl = String.format(Locale.getDefault(), "%.3f", fl);
        final String sexpotime =
                exposureTimeNs == null ?
                        "null ms" :
                        String.format(Locale.getDefault(), "%.2f ms",
                                exposureTimeNs / 1000000.0);

        final String saf = "AF Mode: " + afMode.toString();
        String sfd = "Focus dist: " + String.format("%.3f", fd);

        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCaptureResultText.setText(sfl + " " + sexpotime + " " + saf + "\n" + sfd);
                }
            });
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        // The SurfaceTexture uses this to signal the availability of a new frame.  The
        // thread that "owns" the external texture associated with the SurfaceTexture (which,
        // by virtue of the context being shared, *should* be either one) needs to call
        // updateTexImage() to latch the buffer.
        //
        // Once the buffer is latched, the GLSurfaceView thread can signal the encoder thread.
        // This feels backward -- we want recording to be prioritized over rendering -- but
        // since recording is only enabled some of the time it's easier to do it this way.
        //
        // Since GLSurfaceView doesn't establish a Looper, this will *probably* execute on
        // the main UI thread.  Fortunately, requestRender() can be called from any thread,
        // so it doesn't really matter.
        if (VERBOSE) Timber.d("ST onFrameAvailable");
        mGLView.requestRender();

        final String sfps = String.format(Locale.getDefault(), "%.1f FPS",
                sVideoEncoder.mFrameRate);
        String previewFacts = mCameraPreviewWidth + "x" + mCameraPreviewHeight + "@" + sfps;

        mKeyCameraParamsText.setText(previewFacts);
    }
}
