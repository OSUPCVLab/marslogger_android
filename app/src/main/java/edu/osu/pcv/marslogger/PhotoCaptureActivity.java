package edu.osu.pcv.marslogger;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;

import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;

import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;

import timber.log.Timber;


public class PhotoCaptureActivity extends Activity
        implements AdapterView.OnItemSelectedListener {
    private CameraCapture mCameraCapture;
    private TextView mOutputDirText;

    private String mSnapshotOutputDir = null;
    private boolean mSnap = false;
    private int mSnapNumber = 0;

    private CameraHandler mCameraHandler;

    private TextureMovieEncoder sVideoEncoder = new TextureMovieEncoder();
    private IMUManager mImuManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Occasionally some device show landscape views despite the portrait in manifest. See
        // https://stackoverflow.com/questions/47228194/android-8-1-screen-orientation-issue-flipping-to-landscape-a-portrait-screen
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        setContentView(R.layout.activity_photo_capture);
        mCameraCapture = new CameraCapture(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mCameraCapture.initCamera2Proxy();

        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mCameraHandler = new CameraHandler(mCameraCapture);

        // Configure the GLSurfaceView.  This will start the Renderer thread, with an
        // appropriate EGL context.
        SampleGLView glView = (SampleGLView) findViewById(R.id.cameraPreview_surfaceView);
        mCameraCapture.initRenderer(mCameraHandler, glView);

        glView.setTouchListener((event) -> {
            ManualFocusConfig focusConfig =
                    new ManualFocusConfig(event.getX(), event.getY(), glView.getWidth(), glView.getHeight());
            Timber.d(focusConfig.toString());
            mCameraHandler.sendMessage(
                    mCameraHandler.obtainMessage(CameraHandler.MSG_MANUAL_FOCUS, focusConfig));
        });
        if (mImuManager == null) {
            mImuManager = new IMUManager(this);
        }
        mCameraCapture.mKeyCameraParamsText = (TextView) findViewById(R.id.cameraParams_text);
        mCameraCapture.mCaptureResultText = (TextView) findViewById(R.id.captureResult_text);
        mOutputDirText = (TextView) findViewById(R.id.cameraOutputDir_text);
    }

    @Override
    protected void onResume() {
        Timber.d("onResume -- acquiring camera");
        super.onResume();
        Timber.d("Keeping screen on for previewing recording.");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mCameraCapture.initCamera2Proxy();
        mCameraCapture.resume();

        mImuManager.register();
    }

    @Override
    protected void onPause() {
        Timber.d("onPause -- releasing camera");
        super.onPause();

        mSnapshotOutputDir = null;
        mSnap = false;
        mSnapNumber = 0;

        mCameraCapture.pause();
        mImuManager.unregister();
        Timber.d("onPause complete");
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy");
        mCameraHandler.invalidateHandler();     // paranoia
        super.onDestroy();
    }

    // spinner selected
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Spinner spinner = (Spinner) parent;
        final int filterNum = spinner.getSelectedItemPosition();

        Timber.d("onItemSelected: %d", filterNum);
        mCameraCapture.changeFilter(filterNum);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    //    https://github.com/almalence/OpenCamera/blob/master/src/com/almalence/opencam/cameracontroller/Camera2Controller.java#L3455
//    https://stackoverflow.com/questions/34664131/camera2-imagereader-freezes-repeating-capture-request
    public final ImageReader.OnImageAvailableListener mImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader ir) {
                    if (mSnap) {
                        Image image = ir.acquireNextImage();
                        Long timestamp = image.getTimestamp();
                        String outputFile = mSnapshotOutputDir + File.separator + timestamp.toString() + ".jpg";
                        File dest = new File(outputFile);
                        Timber.d("Saving image to %s", outputFile);
                        new ImageSaver(image, dest).run();
                        mSnap = false;
                        ++mSnapNumber;
                        mCameraCapture.mCamera2Proxy.pauseRecordingCaptureResult();
                    } else {
                        Image image = ir.acquireLatestImage();
                        image.close();
                    }
                }
            };

    public void clickSnapshot(@SuppressWarnings("unused") View unused) {
        if (mSnapshotOutputDir != null) {
            mCameraCapture.mCamera2Proxy.resumeRecordingCaptureResult();
            mSnap = true;
        } else {
            mSnapshotOutputDir = mCameraCapture.renewOutputDir();
            String basename = mSnapshotOutputDir.substring(mSnapshotOutputDir.lastIndexOf("/") + 1);
            mOutputDirText.setText(basename);
            mSnapNumber = 0;
            mCameraCapture.mCamera2Proxy.startRecordingCaptureResult(
                    mSnapshotOutputDir + File.separator + "movie_metadata.csv");
            mSnap = true;
        }
        Timber.d("Number of snapshots: %d", mSnapNumber + 1);
    }
}
