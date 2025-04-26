/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.osu.pcv.marslogger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.SurfaceTexture;

import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import edu.osu.pcv.marslogger.gles.FullFrameRect;
import edu.osu.pcv.marslogger.gles.Texture2dProgram;
import timber.log.Timber;

/**
 *  Activity
 *  └── CameraCaptureActivity
 *      └── has CameraCapture implements SurfaceTexture.OnFrameAvailableListener
 *          └── has Camera2Proxy which refs CameraCapture
 */

/**
 * Shows the camera preview on screen while simultaneously recording it to a .mp4 file.
 * <p>
 * Every time we receive a frame from the camera, we need to:
 * <ul>
 * <li>Render the frame to the SurfaceView, on GLSurfaceView's renderer thread.
 * <li>Render the frame to the mediacodec's input surface, on the encoder thread, if
 * recording is enabled.
 * </ul>
 * <p>
 * At any given time there are four things in motion:
 * <ol>
 * <li>The UI thread, embodied by this Activity.  We must respect -- or work around -- the
 * app lifecycle changes.  In particular, we need to release and reacquire the Camera
 * so that, if the user switches away from us, we're not preventing another app from
 * using the camera.
 * <li>The Camera, which will busily generate preview frames once we hand it a
 * SurfaceTexture.  We'll get notifications on the main UI thread unless we define a
 * Looper on the thread where the SurfaceTexture is created (the GLSurfaceView renderer
 * thread).
 * <li>The video encoder thread, embodied by TextureMovieEncoder.  This needs to share
 * the Camera preview external texture with the GLSurfaceView renderer, which means the
 * EGLContext in this thread must be created with a reference to the renderer thread's
 * context in hand.
 * <li>The GLSurfaceView renderer thread, embodied by CameraSurfaceRenderer.  The thread
 * is created for us by GLSurfaceView.  We don't get callbacks for pause/resume or
 * thread startup/shutdown, though we could generate messages from the Activity for most
 * of these things.  The EGLContext created on this thread must be shared with the
 * video encoder, and must be used to create a SurfaceTexture that is used by the
 * Camera.  As the creator of the SurfaceTexture, it must also be the one to call
 * updateTexImage().  The renderer thread is thus at the center of a multi-thread nexus,
 * which is a bit awkward since it's the thread we have the least control over.
 * </ol>
 * <p>
 * GLSurfaceView is fairly painful here.  Ideally we'd create the video encoder, create
 * an EGLContext for it, and pass that into GLSurfaceView to share.  The API doesn't allow
 * this, so we have to do it the other way around.  When GLSurfaceView gets torn down
 * (say, because we rotated the device), the EGLContext gets tossed, which means that when
 * it comes back we have to re-create the EGLContext used by the video encoder.  (And, no,
 * the "preserve EGLContext on pause" feature doesn't help.)
 * <p>
 * We could simplify this quite a bit by using TextureView instead of GLSurfaceView, but that
 * comes with a performance hit.  We could also have the renderer thread drive the video
 * encoder directly, allowing them to work from a single EGLContext, but it's useful to
 * decouple the operations, and it's generally unwise to perform disk I/O on the thread that
 * renders your UI.
 * <p>
 * We want to access Camera from the UI thread (setup, teardown) and the renderer thread
 * (configure SurfaceTexture, start preview), but the API says you can only access the object
 * from a single thread.  So we need to pick one thread to own it, and the other thread has to
 * access it remotely.  Some things are simpler if we let the renderer thread manage it,
 * but we'd really like to be sure that Camera is released before we leave onPause(), which
 * means we need to make a synchronous call from the UI thread into the renderer thread, which
 * we don't really have full control over.  It's less scary to have the UI thread own Camera
 * and have the renderer call back into the UI thread through the standard Handler mechanism.
 * <p>
 * (The <a href="http://developer.android.com/training/camera/cameradirect.html#TaskOpenCamera">
 * camera docs</a> recommend accessing the camera from a non-UI thread to avoid bogging the
 * UI thread down.  Since the GLSurfaceView-managed renderer thread isn't a great choice,
 * we might want to create a dedicated camera thread.  Not doing that here.)
 * <p>
 * With three threads working simultaneously (plus Camera causing periodic events as frames
 * arrive) we have to be very careful when communicating state changes.  In general we want
 * to send a message to the thread, rather than directly accessing state in the object.
 * <p>
 * &nbsp;
 * <p>
 * To exercise the API a bit, the video encoder is required to survive Activity restarts.  In the
 * current implementation it stops recording but doesn't stop time from advancing, so you'll
 * see a pause in the video.  (We could adjust the timer to make it seamless, or output a
 * "paused" message and hold on that in the recording, or leave the Camera running so it
 * continues to generate preview frames while the Activity is paused.)  The video encoder object
 * is managed as a static property of the Activity.
 */

class DesiredCameraSetting {
    static final int mDesiredFrameWidth = 1280;
    static final int mDesiredFrameHeight = 720;
    static final Long mDesiredExposureTime = 5000000L; // nanoseconds
    static final String mDesiredFrameSize = mDesiredFrameWidth +
            "x" + mDesiredFrameHeight;
}


/**
 * Dependency relations between the key components:
 * CameraSurfaceRenderer onSurfaceCreated depends on mCameraHandler, and eventually mCamera2Proxy
 * mCamera2Proxy initialization depends on onRequestPermissionsResult
 *
 * The order of calls in requesting permission inside onCreate()
 * activity.onCreate() -> requestCameraPermission()
 * activity.onResume()
 * activity.onPause()
 * activity.onRequestPermissionsResult()
 * activity.onResume()
*/
public class CameraCaptureActivity extends Activity
        implements OnItemSelectedListener {
    private CameraCapture mCameraCapture = null;

    private static SharedPreferences mSharedPreferences;
    private boolean mTapFocus;
    private TextView mOutputDirText;

    private CameraHandler mCameraHandler;
    private boolean mRecordingEnabled;      // controls button state

    private IMUManager mImuManager;
    private GPSManager mGpsManager;
    private TimeBaseManager mTimeBaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Occasionally some device show landscape views despite the portrait in manifest. See
        // https://stackoverflow.com/questions/47228194/android-8-1-screen-orientation-issue-flipping-to-landscape-a-portrait-screen
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        setContentView(R.layout.activity_camera_capture);

        mCameraCapture = new CameraCapture(this);
        Spinner spinner = (Spinner) findViewById(R.id.cameraFilter_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.cameraFilterNames, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner.
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mCameraCapture.initCamera2Proxy();

        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mCameraHandler = new CameraHandler(mCameraCapture);

        mRecordingEnabled = mCameraCapture.sVideoEncoder.isRecording();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mCameraCapture.activity());
        mTapFocus = mSharedPreferences.getBoolean("switchTapFocus", false);

        // Configure the GLSurfaceView.  This will start the Renderer thread, with an
        // appropriate EGL context.
        SampleGLView glView = (SampleGLView) findViewById(R.id.cameraPreview_surfaceView);
        mCameraCapture.initRenderer(mCameraHandler, glView);
        glView.setTouchListener((event) -> {
            if (mTapFocus) {
                ManualFocusConfig focusConfig =
                        new ManualFocusConfig(event.getX(), event.getY(), glView.getWidth(), glView.getHeight());
                Timber.d(focusConfig.toString());
                Toast.makeText(getApplicationContext(), "Changing focus point...", Toast.LENGTH_SHORT).show();
                mCameraHandler.sendMessage(
                        mCameraHandler.obtainMessage(CameraHandler.MSG_MANUAL_FOCUS, focusConfig));
            }
        });
        if (mGpsManager == null) {
            mGpsManager = new GPSManager(this);
        }
        if (mImuManager == null) {
            mImuManager = new IMUManager(this);
            mTimeBaseManager = new TimeBaseManager();
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
        updateControls();

        mCameraCapture.initCamera2Proxy();
        mCameraCapture.resume();
        mImuManager.register();
    }

    @Override
    protected void onPause() {
        Timber.d("onPause -- releasing camera");
        super.onPause();

        mCameraCapture.pause();
        mImuManager.unregister();
        Timber.d("onPause complete");
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy");
        super.onDestroy();
        mCameraHandler.invalidateHandler();     // paranoia
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

    /**
     * onClick handler for "record" button.
     */
    public void clickToggleRecording(@SuppressWarnings("unused") View unused) {
        mRecordingEnabled = !mRecordingEnabled;
        if (mRecordingEnabled) {
            String outputDir = mCameraCapture.renewOutputDir();
            String outputFile = outputDir + File.separator + "movie.mp4";
            String metaFile = outputDir + File.separator + "frame_timestamps.txt";
            String basename = outputDir.substring(outputDir.lastIndexOf("/")+1);
            mOutputDirText.setText(basename);
            mCameraCapture.mRenderer.resetOutputFiles(outputFile, metaFile); // this will not cause sync issues
            String inertialFile = outputDir + File.separator + "gyro_accel.csv";
            String gpsFile = outputDir + File.separator + "gps.csv";
            String allGpsFile = outputDir + File.separator + "all_gps.csv";
            String edgeEpochFile = outputDir + File.separator + "edge_epochs.txt";
            mTimeBaseManager.startRecording(edgeEpochFile, mCameraCapture.mCamera2Proxy.getmTimeSourceValue());
            mGpsManager.startRecording(gpsFile, allGpsFile);
            mImuManager.startRecording(inertialFile);
            mCameraCapture.mCamera2Proxy.startRecordingCaptureResult(
                    outputDir + File.separator + "movie_metadata.csv");
        } else {
            mCameraCapture.mCamera2Proxy.stopRecordingCaptureResult();
            mImuManager.stopRecording();
            mGpsManager.stopRecording();
            mTimeBaseManager.stopRecording();
        }
        mCameraCapture.toggleRecording(mRecordingEnabled);
        updateControls();
    }

    /**
     * Updates the on-screen controls to reflect the current state of the app.
     */
    private void updateControls() {
        Button toggleRelease = (Button) findViewById(R.id.toggleRecording_button);
        int id = mRecordingEnabled ?
                R.string.toggleRecordingOff : R.string.toggleRecordingOn;
        toggleRelease.setText(id);
    }
}
