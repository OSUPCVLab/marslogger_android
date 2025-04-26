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

import static java.lang.Thread.sleep;

import static edu.osu.pcv.marslogger.WoncanUtils.convertStatusToString;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.LocationManager;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
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

import com.woncan.device.Device;
import com.woncan.device.NMEA;
import com.woncan.device.RTCM;
import com.woncan.device.RTCMInterval;
import com.woncan.device.ScanManager;
import com.woncan.device.bean.DeviceInfo;
import com.woncan.device.bean.DeviceNtripAccount;
import com.woncan.device.bean.SatelliteInfo;
import com.woncan.device.bean.WLocation;
import com.woncan.device.device.DeviceInterval;
import com.woncan.device.listener.DeviceStatesListener;
import com.woncan.device.listener.RTCMListener;
import com.woncan.device.listener.SatelliteListener;
import com.woncan.device.listener.WLocationListener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import edu.osu.pcv.marslogger.gles.FullFrameRect;
import edu.osu.pcv.marslogger.gles.Texture2dProgram;
import sensor_msgs.PointCloud2;
import sg.edu.nus.comp.android3dvisualisationtool.app.openGLES20Support.GLES20SurfaceView;
import sg.edu.nus.comp.android3dvisualisationtool.app.points.Point;
import timber.log.Timber;

import org.ollide.rosandroid.FileManager;
import org.ollide.rosandroid.ImuPublisherNode;
import org.ollide.rosandroid.LocationPublisherNode;
import org.ollide.rosandroid.RecordSignalNode;
import org.ollide.rosandroid.LocationUpdateListener;
import org.ollide.rosandroid.FrameNumberListener;
import org.ollide.rosandroid.PCConverter;
import org.ollide.rosandroid.WorldPCListener;
import org.ollide.rosandroid.RosListenerNode;
import org.ros.address.InetAddressFactory;
import org.ros.android.IPTool;
import org.ros.android.RosActivity;
import org.ros.helpers.ParameterLoaderNode;
import org.ros.node.DefaultNodeListener;
import org.ros.node.Node;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;
import org.ros.node.NodeListener;

import org.ros.rosjava_tutorial_native_node.FastLioNativeNode;
import org.ros.rosjava_tutorial_native_node.FasterLioNativeNode;
import org.ros.rosjava_tutorial_native_node.LivoxRosDriver2NativeNode;
import org.ros.rosjava_tutorial_native_node.LaserLoggerNativeNode;
import org.ros.rosjava_tutorial_native_node.PandarGeneralNativeNode;

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


class CameraCaptureActivityBase extends RosActivity implements SurfaceTexture.OnFrameAvailableListener {
    public static final String TAG = "MarsLogger";
    protected static final boolean VERBOSE = false;

    // Camera filters; must match up with cameraFilterNames in strings.xml
    static final int FILTER_NONE = 0;
    static final int FILTER_BLACK_WHITE = 1;
    static final int FILTER_BLUR = 2;
    static final int FILTER_SHARPEN = 3;
    static final int FILTER_EDGE_DETECT = 4;
    static final int FILTER_EMBOSS = 5;

    protected TextView mKeyCameraParamsText;
    protected TextView mCaptureResultText;

    protected int mCameraPreviewWidth, mCameraPreviewHeight;
    protected int mVideoFrameWidth, mVideoFrameHeight;
    static boolean mSnapshotMode = false;
    protected Camera2Proxy mCamera2Proxy = null;

    protected SampleGLView mGLView;
    protected TextureMovieEncoder sVideoEncoder = new TextureMovieEncoder();

    public CameraCaptureActivityBase() {
        super("RosAndroidExample", "RosAndroidExample");
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
        String dir1 = getFilesDir().getAbsolutePath();
        String dir2 = Environment.getExternalStorageDirectory().
                getAbsolutePath() + File.separator + "mars_logger";

        String dir3 = getExternalFilesDir(
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
        AspectFrameLayout layout = findViewById(R.id.cameraPreview_afl);
        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
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
            final Long exposureTimeNs, final Integer afMode) {
        final String sfl = String.format(Locale.getDefault(), "%.3f", fl);
        final String sexpotime =
                exposureTimeNs == null ?
                        "null ms" :
                        String.format(Locale.getDefault(), "%.2f ms",
                                exposureTimeNs / 1000000.0);

        final String saf = "AF Mode: " + afMode.toString();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCaptureResultText.setText(sfl + " " + sexpotime + " " + saf);
            }
        });
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

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {

    }
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
public class CameraCaptureActivity extends CameraCaptureActivityBase
        implements OnItemSelectedListener {
    private GLES20SurfaceView mPCGLView;
    private CameraSurfaceRenderer mRenderer = null;
    private TextView mOutputDirText;

    private CameraHandler mCameraHandler;
    private boolean mRecordingEnabled;      // controls button state

    private IMUManager mImuManager;
    private GPSManager mGpsManager;
    private TimeBaseManager mTimeBaseManager;

    ///@{ // ros stuff
    static {
        System.loadLibrary("laser_logger_jni");
        System.loadLibrary("fastlio_jni");
        System.loadLibrary("fasterlio_jni");
        System.loadLibrary("livox_ros_driver2_jni");
        System.loadLibrary("pandar_general_jni");
    }

    private ArrayList<ParameterLoaderNode.Resource> mOpenedResources = new ArrayList<>();
    private NodeMainExecutor nodeMainExecutor = null;
    private URI masterUri;
    private String hostName;

    private FastLioNativeNode fastlioNativeNode;
    private FasterLioNativeNode fasterLioNativeNode;
    private LivoxRosDriver2NativeNode livoxNativeNode = null;

    private PandarGeneralNativeNode pandarNativeNode = null;

    public enum LidarType {
        Mid360(0),
        PandarXT32(1);

        private final int value;

        LidarType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static LidarType fromValue(int value) {
            for (LidarType type : LidarType.values()) {
                if (type.getValue() == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown LidarType value: " + value);
        }

        public static LidarType parseString(String val) {
            if (val == null) {
                Timber.e("Lidar type string is null");
                return Mid360;
            }
            val = val.toLowerCase();
            if (val.contains("mid360") || val.contains("livox")) {
                return Mid360;
            } else if (val.contains("pandar") || val.contains("xt32")) {
                return PandarXT32;
            } else {
                Timber.e("Unknown lidar type: %s", val);
                return Mid360;
            }
        }
    }

    private LaserLoggerNativeNode laserLoggerNativeNode;
    private RecordSignalNode recordSignalNode;
    private RosListenerNode rosListenerNode = null;
    private ParameterLoaderNode mParameterLoaderNode;

    private LocationPublisherNode locationPublisherNode = null;

    private ImuPublisherNode imuPublisherNode = null;
    private SensorManager mSensorManager;

    private static SharedPreferences mSharedPreferences;

    private LidarType lidarType;
    private int lidarId;

    private TextView masterUriTextView;
    private TextView msgCountTextView;
    private TextView positionTextView;
    private TextView lidarIdTextView;
    ///@} // end of ros stuff

    ///@{ // woncan gnss stick
    private Device mDevice = null;
    private TextView gnssRtkLog;
    private ArrayAdapter<Device> gnssAdapter;
    private BufferedWriter mGnssRtkWriter = null;
    private boolean mRecordingGnssRtkData = false;
    ///@} // woncan gnss stick

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Occasionally some device show landscape views despite the portrait in manifest. See
        // https://stackoverflow.com/questions/47228194/android-8-1-screen-orientation-issue-flipping-to-landscape-a-portrait-screen
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        setContentView(R.layout.activity_camera_capture);
        mSnapshotMode = false;

        ///@{ // ros stuff
        masterUriTextView = findViewById(R.id.masterUriText);
        msgCountTextView = findViewById(R.id.numLidarMsgText);
        positionTextView = findViewById(R.id.currentPositionText);
        lidarIdTextView = findViewById(R.id.lidarIdText);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String lidarTypeStr = mSharedPreferences.getString("prefLidarType", "mid360");
        lidarType = LidarType.parseString(lidarTypeStr);
        String lidarIdStr = mSharedPreferences.getString("prefLidarId", "0");
        lidarId = Integer.parseInt(lidarIdStr);
        lidarIdTextView.setText(lidarTypeStr + " " + lidarIdStr);

        List<Pair<String, String>> resourcesToLoad = new ArrayList<>();
        if (lidarType == LidarType.Mid360) {
            resourcesToLoad.add(new Pair<>("movebase_params/fasterlio_mid360.yaml", "/"));
            resourcesToLoad.add(new Pair<>("movebase_params/livox_ros_driver2_params.yaml", "/"));
            // We use the global namespace for Livox ROS Driver2.
        } else {
            resourcesToLoad.add(new Pair<>("movebase_params/fasterlio_pandarxt32.yaml", "/"));
            resourcesToLoad.add(new Pair<>("movebase_params/pandar_general_ros_params.yaml", PandarGeneralNativeNode.nodeName));
        }
        // Load raw resources
        for (Pair<String, String> ip : resourcesToLoad) {
            InputStream assetInStream=null;
            try { // https://stackoverflow.com/questions/1933015/opening-a-file-from-assets-folder-in-android
                assetInStream=getAssets().open(ip.first);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mOpenedResources.add(new ParameterLoaderNode.Resource(assetInStream, ip.second));
        }
        ///@} // end of ros stuff

        ///@{ woncan gnss rtk
        gnssRtkLog = (TextView)findViewById(R.id.gnssRtkText);
        gnssRtkLog.setMovementMethod(ScrollingMovementMethod.getInstance());
        gnssRtkLog.setText("");

        gnssAdapter = new MyDeviceAdapter(this, new ArrayList<>());

        Spinner spinner = (Spinner) findViewById(R.id.cameraFilter_spinner);
        spinner.setAdapter(gnssAdapter);
//        spinner.setSelection(gnssAdapter.NO_SELECTION, false);
        spinner.setOnItemSelectedListener(this);
        Button scanBLEButton = (Button) findViewById(R.id.scanBluetooth_button);
        scanBLEButton.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ScanManager.scanDevice(CameraCaptureActivity.this, device -> {
                        Log.i(TAG, "Found device: " + device.getName());
                        // Check if the device is already in the adapter's list
                        if (gnssAdapter.getPosition(device) == -1) {
                            Log.i(TAG, "Adding device " + device.getName() + " to the adapter");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    gnssAdapter.add(device);
                                }
                            });
                        }
                    });
                }
            }
        );
        ///@} woncan gnss rtk
    }

    ///@{ woncan gnss rtk
    // bluetooth device selected
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Timber.d("Connecting device: %d", pos);
        ScanManager.stopScan(this);
        mDevice = gnssAdapter.getItem(pos);
        connect(mDevice);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    private void connect(Device device) {
        device.registerSatesListener(new DeviceStatesListener() {
            @Override
            public void onConnectionStateChange(boolean isConnect) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gnssRtkLog.append(isConnect ? "设备已连接\n" : "断开连接\n");
                    }
                });
            }

            @Override
            public void onDeviceAccountChange(@NonNull DeviceNtripAccount account) {
                super.onDeviceAccountChange(account);

            }

            @Override
            public void onDeviceInfoChange(@NonNull DeviceInfo deviceInfo) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gnssRtkLog.setText(String.format(Locale.CHINA, "型号：%s\n设备ID：%s\n产品名：%s",
                                deviceInfo.getModel(), deviceInfo.getDeviceID(), deviceInfo.getProductNameZH()));
                    }
                });
            }

            @Override
            public void onLaserStateChange(boolean isOpen) {

            }
        });

        device.registerLocationListener(new WLocationListener() {
            @Override
            public void onReceiveLocation(@NonNull WLocation wLocation) {
                // refer to https://developer.android.com/reference/android/os/SystemClock
                long unixTimeMillis = wLocation.getTime(); // unix time
                long bootTimeNanos = wLocation.getElapsedRealtimeNanos(); // Warn: This value may be 0.
                long upTimeNanos = System.nanoTime();
                final long kSecToNano = 1000000000L;
                if (mRecordingGnssRtkData) {
                    try {
                        mGnssRtkWriter.write(String.format("%d.%09d %.9f %.9f %.6f %d %d.%03d\n",
                                upTimeNanos / kSecToNano, upTimeNanos % kSecToNano,
                                wLocation.getLatitude(), wLocation.getLongitude(),
                                wLocation.getAltitude(), wLocation.getFixStatus(),
                                unixTimeMillis / 1000, unixTimeMillis % 1000));
                    } catch (IOException ioe) {
                        Timber.e(ioe);
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gnssRtkLog.setText(String.format(Locale.CHINA,
                                "纬度：%.8f\n经度：%.8f\n椭球高：%.3f\n解状态：%s",
                                wLocation.getLatitude(), wLocation.getLongitude(),
                                wLocation.getAltitude(), convertStatusToString(wLocation.getFixStatus())));
                    }
                });
            }

            @Override
            public void onError(int i, @NonNull String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gnssRtkLog.append(String.format(Locale.CHINA, "onError:%d  %s\n", i, s));
                    }
                });
            }
        });

        device.openRTCM(new RTCM[]{RTCM.RTCM1074}, RTCMInterval.SECOND_3);
        device.registerRTCMAListener(new RTCMListener() {
            @Override
            public void onSFRReceiver(byte[] bytes) {

            }

            @Override
            public void onRTCMReceiver(int[] ints, byte[] bytes) {

            }
        });

        device.registerSatelliteListener(new SatelliteListener() {
            @Override
            public void onReceiveSatellite(List<SatelliteInfo> list) {

            }
        });

        device.setNMEAEnable(NMEA.GGA , true);
        device.setNMEAEnable(NMEA.GSV , true);
        device.setNMEAEnable(NMEA.GSA , true);
        device.setNMEAEnable(NMEA.GLL , true);
        device.setNMEAEnable(NMEA.GMC , true);
        device.setNMEAEnable(NMEA.VTG , true);
//        device.setNMEAListener(s -> Log.i(TAG, "onReceiveNMEA: "+s));
        device.setNMEAListener(s -> {});

        device.connect(this);
        new Thread(() -> {
            try {
                sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            device.setInterval(DeviceInterval.HZ_5);
        }).start();

//        device.setAccount("",8001,"","","AUTO");
//        device.setLaserState(true);
    }
    
    public void startGnssRtkRecording(String captureResultFile) {
        try {
            final String GnssRtkHeader = "sensor uptime[sec],lat[deg],lon[deg],ellipsoid height[m],fix status[1],host unix time[sec]\n";
            mGnssRtkWriter = new BufferedWriter(
                    new FileWriter(captureResultFile, false));
            if (mDevice == null) {
                String warning = "The woncan GNSS RTK device is not connected!\n" +
                        "No GNSS RTK data will be logged.\n";
                mGnssRtkWriter.write(warning);
            } else {
                mGnssRtkWriter.write(GnssRtkHeader);
            }
            mRecordingGnssRtkData = true;
        } catch (IOException err) {
            Timber.e(err,"IOException in opening inertial data writer at %s",
                    captureResultFile);
        }
    }

    public void stopGnssRtkRecording() {
        if (mRecordingGnssRtkData) {
            mRecordingGnssRtkData = false;
            try {
                mGnssRtkWriter.flush();
                mGnssRtkWriter.close();
            } catch (IOException err) {
                Timber.e(err, "IOException in closing GNSS RTK data writer");
            }
            mGnssRtkWriter = null;
        }
    }
    ///@} woncan gnss rtk

    @Override
    protected void onStart() {
        super.onStart();
        mCamera2Proxy = new Camera2Proxy(this);
        Size previewSize = mCamera2Proxy.configureCamera();
        setLayoutAspectRatio(previewSize);  // updates mCameraPreviewWidth/Height
        Size videoSize = mCamera2Proxy.getmVideoSize();
        mVideoFrameWidth = videoSize.getWidth();
        mVideoFrameHeight = videoSize.getHeight();
        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mCameraHandler = new CameraHandler(this, false);

        mRecordingEnabled = sVideoEncoder.isRecording();

        // Configure the GLSurfaceView.  This will start the Renderer thread, with an
        // appropriate EGL context.
        mGLView = (SampleGLView) findViewById(R.id.cameraPreview_surfaceView);
        if (mRenderer == null) {
            mRenderer = new CameraSurfaceRenderer(
                    mCameraHandler, sVideoEncoder);
            mGLView.setEGLContextClientVersion(2);     // select GLES 2.0
            mGLView.setRenderer(mRenderer);
            mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
        mGLView.setTouchListener((event) -> {
            ManualFocusConfig focusConfig =
                    new ManualFocusConfig(event.getX(), event.getY(), mGLView.getWidth(), mGLView.getHeight());
            Timber.d(focusConfig.toString());
            mCameraHandler.sendMessage(
                    mCameraHandler.obtainMessage(CameraHandler.MSG_MANUAL_FOCUS, focusConfig));
        });
        // Configure the GLSurfaceView for point clouds
        // https://www.dre.vanderbilt.edu/~schmidt/android/android-4.0/out/target/common/docs/doc-comment-check/resources/articles/glsurfaceview.html
        mPCGLView = (GLES20SurfaceView) findViewById(R.id.gl_surface_view);

        if (mGpsManager == null) {
            mGpsManager = new GPSManager(this);
        }
        if (mImuManager == null) {
            mImuManager = new IMUManager(this);
            mTimeBaseManager = new TimeBaseManager();
        }
        mKeyCameraParamsText = (TextView) findViewById(R.id.cameraParams_text);
        mCaptureResultText = (TextView) findViewById(R.id.captureResult_text);
        mOutputDirText = (TextView) findViewById(R.id.cameraOutputDir_text);
    }

    @Override
    protected void onResume() {
        Timber.d("onResume -- acquiring camera");
        super.onResume();
        Timber.d("Keeping screen on for previewing recording.");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateControls();

        if (mCamera2Proxy == null) {
            mCamera2Proxy = new Camera2Proxy(this);
            Size previewSize = mCamera2Proxy.configureCamera();
            setLayoutAspectRatio(previewSize);
            Size videoSize = mCamera2Proxy.getmVideoSize();
            mVideoFrameWidth = videoSize.getWidth();
            mVideoFrameHeight = videoSize.getHeight();
        }

        mGLView.onResume();
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);
                mRenderer.setVideoFrameSize(mVideoFrameWidth, mVideoFrameHeight);
            }
        });
        if (mPCGLView != null)
            mPCGLView.onResume();
        mImuManager.register();
        if (nodeMainExecutor != null && livoxNativeNode == null && pandarNativeNode == null) {
            init(nodeMainExecutor);
        }
    }

    @Override
    protected void onPause() {
        Timber.d("onPause -- releasing camera");
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
        if (mPCGLView != null)
            mPCGLView.onPause();
        mImuManager.unregister();
        stopRosListener();
        stopRecordSignalPublisher();
        if (lidarType == LidarType.Mid360) {
            stopLivoxRosDriver2();
        } else {
            stopPandarRosDriver();
        }
        nodeMainExecutor.shutdownNodeMain(mParameterLoaderNode);
        mSensorManager.unregisterListener(imuPublisherNode.getAccelerometerListener());
        mSensorManager.unregisterListener(imuPublisherNode.getGyroscopeListener());
        mSensorManager.unregisterListener(imuPublisherNode.getOrientationListener());
        mParameterLoaderNode = null;
        if (mDevice != null) {
            mDevice.closeRTCM();
            mDevice.disconnect();
            mDevice = null;
        }
        super.onPause();
        Timber.d("onPause complete");
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy");
        nodeMainExecutor.shutdown();
        mCameraHandler.invalidateHandler();     // paranoia
        super.onDestroy();
    }

    /**
     * onClick handler for "record" button.
     */
    public void clickToggleRecording(@SuppressWarnings("unused") View unused) {
        mRecordingEnabled = !mRecordingEnabled;
        if (mRecordingEnabled) {
            String outputDir = renewOutputDir();
            String outputFile = outputDir + File.separator + "movie.mp4";
            String timeFile = outputDir + File.separator + "frame_timestamps.txt";
            String basename = outputDir.substring(outputDir.lastIndexOf("/")+1);
            mOutputDirText.setText(basename);
            mRenderer.resetOutputFiles(outputFile, timeFile); // this will not cause sync issues
            String inertialFile = outputDir + File.separator + "gyro_accel.csv";
            String gpsFile = outputDir + File.separator + "gps.csv";
            String allGpsFile = outputDir + File.separator + "all_gps.csv";
            String gnssRtkFile = outputDir + File.separator + "woncan_gnss.csv";
            String edgeEpochFile = outputDir + File.separator + "edge_epochs.txt";
            mTimeBaseManager.startRecording(edgeEpochFile, mCamera2Proxy.getmTimeSourceValue());
            mGpsManager.startRecording(gpsFile, allGpsFile);
            mImuManager.startRecording(inertialFile);
            startGnssRtkRecording(gnssRtkFile);
            mCamera2Proxy.startRecordingCaptureResult(
                    outputDir + File.separator + "movie_metadata.csv");
            startFasterLio(outputDir);
            startLaserLogging2(outputDir + File.separator + "lidar.bag");
            rosListenerNode.setRecording(true);
        } else {
            mCamera2Proxy.stopRecordingCaptureResult();
            stopGnssRtkRecording();
            mImuManager.stopRecording();
            mGpsManager.stopRecording();
            mTimeBaseManager.stopRecording();
            rosListenerNode.setRecording(false);
            stopLaserLogging2();
            stopFasterLio();
        }
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.changeRecordingState(mRecordingEnabled);
            }
        });
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

    ///@{ // ros stuff
    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        // Store a reference to the NodeMainExecutor and unblock any processes that were waiting
        // for this to start ROS Nodes
        this.nodeMainExecutor = nodeMainExecutor;
        masterUri = getMasterUri();
        hostName = getRosHostname();

        configureParameterServer();

        locationPublisherNode = new LocationPublisherNode();
        imuPublisherNode = new ImuPublisherNode();

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(false);
        criteria.setCostAllowed(true);
        final String provider = LocationManager.GPS_PROVIDER;
        String svcName = Context.LOCATION_SERVICE;
        final LocationManager locationManager = (LocationManager) getSystemService(svcName);
        final int t = 500;
        final float distance = 0.1f;

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    boolean permissionFineLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                    boolean permissionCoarseLocation = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                    Log.d(TAG, "PERMISSION 1: " + String.valueOf(permissionFineLocation));
                    Log.d(TAG, "PERMISSION 2: " + String.valueOf(permissionCoarseLocation));
                    if (permissionFineLocation && permissionCoarseLocation) {
                        if (locationManager != null) {
                            Log.d(TAG, "Requesting location");
                            locationManager.requestLocationUpdates(provider, t, distance,
                                    locationPublisherNode.getLocationListener());
                        }
                    } else {
                        // Request permissions
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PackageManager.GET_PERMISSIONS);
                    }
                } else {
                    locationManager.requestLocationUpdates(provider, t, distance, locationPublisherNode.getLocationListener());
                }
                masterUriTextView.setText(masterUri.toString());
            }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        try {
            Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(imuPublisherNode.getAccelerometerListener(), accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        } catch (NullPointerException e) {
            Log.e(TAG, e.toString());
            return;
        }

        try {
            Sensor gyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mSensorManager.registerListener(imuPublisherNode.getGyroscopeListener(), gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        } catch (NullPointerException e) {
            Log.e(TAG, e.toString());
            return;
        }

        try {
            Sensor orientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
            mSensorManager.registerListener(imuPublisherNode.getOrientationListener(), orientation, SensorManager.SENSOR_DELAY_FASTEST);
        } catch (NullPointerException e) {
            Log.e(TAG, e.toString());
            return;
        }

        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(hostName);
        nodeConfiguration.setMasterUri(masterUri);
        nodeConfiguration.setNodeName(imuPublisherNode.getDefaultNodeName());
        nodeMainExecutor.execute(imuPublisherNode, nodeConfiguration);

        NodeConfiguration nodeConfiguration2 = NodeConfiguration.newPublic(hostName);
        nodeConfiguration2.setMasterUri(masterUri);
        nodeConfiguration2.setNodeName(locationPublisherNode.getDefaultNodeName());
        nodeMainExecutor.execute(locationPublisherNode, nodeConfiguration2);

        SharedPreferences sp = getSharedPreferences("SharedPreferences", MODE_PRIVATE);
        SharedPreferences.Editor spe = sp.edit();
        if (lidarId != 0) {
            spe.putInt("LidarId", lidarId);
        }
        spe.apply();
        if (lidarType == LidarType.Mid360) {
            startLivoxRosDriver2();
        } else {
            startPandarRosDriver();
        }
        startRosListener();
        startRecordSignalPublisher();
    }

    /**
     * Helper method to block the calling thread until the latch is zeroed by some other task.
     * @param latch Latch to wait for.
     * @param latchName Name to be used in log messages for the given latch.
     */
    private void waitForLatchUnlock(CountDownLatch latch, String latchName) {
        try {
            Log.i(TAG, "Waiting for " + latchName + " latch release...");
            latch.await();
            Log.i(TAG,latchName + " latch released!");
        } catch (InterruptedException ie) {
            Log.w(TAG, "Warning: continuing before " + latchName + " latch was released");
        }
    }

    /**
     * Starts {@link ParameterLoaderNode} and waits for it to finish setting parameters.
     */
    private void configureParameterServer() {
        CountDownLatch latch = new CountDownLatch(1);
        startParameterLoaderNode(latch);
        waitForLatchUnlock(latch, "parameter");
    }

    private void startParameterLoaderNode(final CountDownLatch latch) {
        // Create node to load configuration to Parameter Server
        Log.i(TAG, "Setting parameters in Parameter Server");
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(hostName);
        nodeConfiguration.setMasterUri(masterUri);
        nodeConfiguration.setNodeName(ParameterLoaderNode.NODE_NAME);
        mParameterLoaderNode = new ParameterLoaderNode(mOpenedResources);
        nodeMainExecutor.execute(mParameterLoaderNode, nodeConfiguration,
                new ArrayList<NodeListener>() {{
                    add(new DefaultNodeListener() {
                        @Override
                        public void onShutdown(Node node) {
                            latch.countDown();
                        }

                        @Override
                        public void onError(Node node, Throwable throwable) {
                            Log.e(TAG, "Error loading parameters to ROS parameter server: " + throwable.getMessage(), throwable);
                        }
                    });
                }});
    }

    private String checkPointCloudPcdMap() {
        String extdir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
        File mapFile = new File(extdir, "maps/map_0.1.pcd");
        // we do not copy the pgm here because the pgm from the apk is corrupt and unable to be loaded by the map_server.
        if (!mapFile.exists()) {
            Log.e(TAG, "Error cannot find point cloud map: " + mapFile.getAbsolutePath() +
                    "\n.Push them into the folder with adb push.");
        }
        return mapFile.getAbsolutePath();
    }

    private String checkOccupancyGridMap() {
        // Create the sample map in the app data dir
        String extdir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
        File mapFile = new File(extdir, "maps/map.yaml");
        // we do not copy the pgm here because the pgm from the apk is corrupt and unable to be loaded by the map_server.
        // The pgm file should be put into the /sdcard/Android/data/org.ollide.rosandroid/files/data folder by adb push.
        if (!mapFile.exists()) {
            Log.e(TAG, "Error cannot find occupancy grid map: " + mapFile.getAbsolutePath() +
                    "\n.Push them into the folder with adb push.");
        }
        return mapFile.getAbsolutePath();
    }

    private String createLidarUserConfig(String hostip, String lidarip) {
        String extdir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
        File configFile = new File(extdir, "MID360_config.json");
        String config = LivoxRosDriver2NativeNode.createLidarConfig(hostip, lidarip);
        FileManager.writeToFile(config, configFile);
        return configFile.getAbsolutePath();
    }

    private void startFastLio() {
        Log.i(TAG, "Starting native fastlio node wrapper...");
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(hostName);

        nodeConfiguration.setMasterUri(masterUri);
        nodeConfiguration.setNodeName(FastLioNativeNode.nodeName);
        String pcdmappath = checkPointCloudPcdMap();
        String[] extraArgs = new String[1];
        extraArgs[0] = pcdmappath;
        fastlioNativeNode = new FastLioNativeNode(extraArgs);
        nodeMainExecutor.execute(fastlioNativeNode, nodeConfiguration);
    }

    private void stopFastLio() {
        fastlioNativeNode.shutdown();
    }

    private void startFasterLio(String outputDir) {
        Log.i(TAG, "Starting native fasterlio node wrapper...");
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(hostName);
        nodeConfiguration.setMasterUri(masterUri);
        nodeConfiguration.setNodeName(FasterLioNativeNode.nodeName);
        String[] extraArgs = new String[1];
        extraArgs[0] = outputDir;
        fasterLioNativeNode = new FasterLioNativeNode(extraArgs);
        nodeMainExecutor.execute(fasterLioNativeNode, nodeConfiguration);
    }

    private void stopFasterLio() {
        fasterLioNativeNode.shutdown();
        nodeMainExecutor.shutdownNodeMain(fasterLioNativeNode);
        fasterLioNativeNode = null;
    }

    private void startLivoxRosDriver2() {
        Log.i(TAG, "Starting native livox ros driver2 node wrapper...");
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(hostName);

        nodeConfiguration.setMasterUri(masterUri);
        nodeConfiguration.setNodeName(LivoxRosDriver2NativeNode.nodeName);
        // The IP address of eth0 of the android phone.
        String hostip = IPTool.getHostEthernetIp();
        // the subnet of the IP address of eth0 of the android phone + .1xx
        // where xx are the last two digits of the mid360 serial number.

        String lidarid = String.valueOf(lidarId);
        String lidarip = IPTool.composeLidarIp(hostip, lidarid);
        String userconfigpath = createLidarUserConfig(hostip, lidarip);
        Log.i(TAG, "host ip:" + hostip + ", lidar ip:" + lidarip);
        String[] extraArgs = new String[1];
        extraArgs[0] = userconfigpath;
        livoxNativeNode = new LivoxRosDriver2NativeNode(extraArgs);
        nodeMainExecutor.execute(livoxNativeNode, nodeConfiguration);
    }

    private void stopLivoxRosDriver2() {
        livoxNativeNode.shutdown();
        nodeMainExecutor.shutdownNodeMain(livoxNativeNode);
        livoxNativeNode = null;
    }

    private void startPandarRosDriver() {
        Log.i(TAG, "Starting native pandar ros driver node wrapper...");
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(hostName);

        nodeConfiguration.setMasterUri(masterUri);
        nodeConfiguration.setNodeName(PandarGeneralNativeNode.nodeName);
        // The IP address of eth0 of the android phone.
        String hostip = IPTool.getHostEthernetIp();
        // the subnet of the IP address of eth0 of the android phone + .1xx
        // where xx are the last two digits of the mid360 serial number.

        String lidarid = "201"; // String.valueOf(lidarId);
        String lidarip = IPTool.composeLidarIp(hostip, lidarid);
        String lidarCorrectionFile = copyLidarCorrectionFile();
        Log.i(TAG, "host ip:" + hostip + ", lidar ip:" + lidarip + ", correction file:" + lidarCorrectionFile);
        String[] extraArgs = new String[2];
        extraArgs[0] = lidarip;
        extraArgs[1] = lidarCorrectionFile;
        pandarNativeNode = new PandarGeneralNativeNode(extraArgs);
        nodeMainExecutor.execute(pandarNativeNode, nodeConfiguration);
    }

    private void stopPandarRosDriver() {
        pandarNativeNode.shutdown();
        nodeMainExecutor.shutdownNodeMain(pandarNativeNode);
        pandarNativeNode = null;
    }

    private String copyLidarCorrectionFile() {
        // Create the sample map in the app data dir
        String extdir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath() + "/movebase_params";
        if (!new File(extdir).exists()) {
            new File(extdir).mkdir();
        }
        String correctionFilename = extdir + "/PandarXT-32.csv";
        File correctionFile = new File(correctionFilename);
//        if (!correctionFile.exists()) {
        FileManager.copyAssetFile(getAssets(), "movebase_params/PandarXT-32.csv", correctionFilename);
//        }
        return correctionFilename;
    }

    void startRecordSignalPublisher() {
        recordSignalNode = new RecordSignalNode();
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(hostName);
        nodeConfiguration.setMasterUri(masterUri);
        nodeConfiguration.setNodeName(RecordSignalNode.nodeName);
        nodeMainExecutor.execute(recordSignalNode, nodeConfiguration);
    }

    void stopRecordSignalPublisher() {
        nodeMainExecutor.shutdownNodeMain(recordSignalNode);
        recordSignalNode = null;
    }

    private void startRosListener() {
        Log.i(TAG, "Starting ros listener node...");
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(hostName);

        nodeConfiguration.setMasterUri(masterUri);
        nodeConfiguration.setNodeName(RosListenerNode.nodeName);

        rosListenerNode = new RosListenerNode();
        rosListenerNode.setOnLocationUpdateListener(
                new LocationUpdateListener() {
                    @Override
                    public void onLocationUpdate(double x, double y, double z) {
                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        String s = "Odom: " + String.format("%.2f", x) + "," +
                                                String.format("%.2f", y) + "," + String.format("%.2f", z);
                                        positionTextView.setText(s);
                                    }
                                }
                        );
                    }});
        rosListenerNode.setOnFrameNumberListener(new FrameNumberListener() {
            @Override
            public void onFrameNumber(int numFrames) {
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                msgCountTextView.setText(String.valueOf(numFrames));
                            }
                        }
                );
            }
        });

        rosListenerNode.setOnWorldPCListener(new WorldPCListener() {
            @Override
            public void onWorldPC(PointCloud2 msg) {
                List<Point> points = PCConverter.toPointList(msg);
                mPCGLView.appendPoints(points);
                mPCGLView.requestRender();
            }
        });

        nodeMainExecutor.execute(rosListenerNode, nodeConfiguration);
    }

    private void stopRosListener() {
        rosListenerNode.shutdown();
        nodeMainExecutor.shutdownNodeMain(rosListenerNode);
        rosListenerNode = null;
    }

    private void startLaserLogging(String bagname) {
        Log.i(TAG, "Starting native laser logging node wrapper...");
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(hostName);
        nodeConfiguration.setMasterUri(masterUri);
        nodeConfiguration.setNodeName(LaserLoggerNativeNode.nodeName);

        String[] extraArgs = new String[1];
        extraArgs[0] = bagname;
        laserLoggerNativeNode = new LaserLoggerNativeNode(extraArgs);
        nodeMainExecutor.execute(laserLoggerNativeNode, nodeConfiguration);
    }

    private void stopLaserLogging() {
        int pcmsgCount = laserLoggerNativeNode.shutdown();
        nodeMainExecutor.shutdownNodeMain(laserLoggerNativeNode);
        msgCountTextView.setText(String.valueOf(pcmsgCount));
    }

    private void startLaserLogging2(String bagname) {
        recordSignalNode.startRecording(bagname);
    }

    private void stopLaserLogging2() {
        recordSignalNode.stopRecording();
    }
    ///@} // end of ros stuff
}


/**
 * Handles camera operation requests from other threads.  Necessary because the Camera
 * must only be accessed from one thread.
 * <p>
 * The object is created on the UI thread, and all handlers run there.  Messages are
 * sent from other threads, using sendMessage().
 */
class CameraHandler extends Handler {
    public static final int MSG_SET_SURFACE_TEXTURE = 0;
    public static final int MSG_MANUAL_FOCUS = 1;

    // Weak reference to the Activity; only access this from the UI thread.
    private WeakReference<Activity> mWeakActivity;
    private boolean mSnapshot;

    public CameraHandler(Activity activity, boolean snapshot) {
        mWeakActivity = new WeakReference<Activity>(activity);
        mSnapshot = snapshot;
    }

    /**
     * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
     * attempts to access a stale Activity through a handler are caught.
     */
    public void invalidateHandler() {
        mWeakActivity.clear();
    }


    @Override  // runs on UI thread
    public void handleMessage(Message inputMessage) {
        int what = inputMessage.what;
        Object obj = inputMessage.obj;

        Timber.d("CameraHandler [%s]: what=%d", this.toString(), what);

        Activity activity = mWeakActivity.get();
        if (activity == null) {
            Timber.w("CameraHandler.handleMessage: activity is null");
            return;
        }

        switch (what) {
            case MSG_SET_SURFACE_TEXTURE:
                ((CameraCaptureActivityBase) activity).handleSetSurfaceTexture(
                        (SurfaceTexture) inputMessage.obj);
                break;
            case MSG_MANUAL_FOCUS:
                Camera2Proxy camera2proxy = ((CameraCaptureActivityBase) activity).getmCamera2Proxy();
                camera2proxy.changeManualFocusPoint((ManualFocusConfig) obj);
                break;
            default:
                throw new RuntimeException("unknown msg " + what);
        }
    }
}
/**
 * Renderer object for our GLSurfaceView.
 * <p>
 * Do not call any methods here directly from another thread -- use the
 * GLSurfaceView#queueEvent() call.
 */
class CameraSurfaceRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = CameraCaptureActivity.TAG;
    private static final boolean VERBOSE = false;

    private static final int RECORDING_OFF = 0;
    private static final int RECORDING_ON = 1;
    private static final int RECORDING_RESUMED = 2;

    private CameraHandler mCameraHandler;
    private TextureMovieEncoder mVideoEncoder;
    private String mOutputFile;
    private String mTimeFile;

    private FullFrameRect mFullScreen;

    private final float[] mSTMatrix = new float[16];
    private int mTextureId;

    private SurfaceTexture mSurfaceTexture;
    private boolean mRecordingEnabled;
    private int mRecordingStatus;
    private int mFrameCount;

    // width/height of the incoming camera preview frames
    private boolean mIncomingSizeUpdated;
    private int mIncomingWidth;
    private int mIncomingHeight;

    private int mVideoFrameWidth;
    private int mVideoFrameHeight;

    private int mCurrentFilter;
    private int mNewFilter;


    /**
     * Constructs CameraSurfaceRenderer.
     * <p>
     *
     * @param cameraHandler Handler for communicating with UI thread
     * @param movieEncoder  video encoder object
     */
    public CameraSurfaceRenderer(CameraHandler cameraHandler,
                                 TextureMovieEncoder movieEncoder) {
        mCameraHandler = cameraHandler;
        mVideoEncoder = movieEncoder;
        mTextureId = -1;

        mRecordingStatus = -1;
        mRecordingEnabled = false;
        mFrameCount = -1;

        mIncomingSizeUpdated = false;
        mIncomingWidth = mIncomingHeight = -1;
        mVideoFrameWidth = mVideoFrameHeight = -1;

        // We could preserve the old filter mode, but currently not bothering.
        mCurrentFilter = -1;
        mNewFilter = CameraCaptureActivity.FILTER_NONE;
    }

    public void resetOutputFiles(String outputFile, String timeFile) {
        mOutputFile = outputFile;
        mTimeFile = timeFile;
    }

    /**
     * Notifies the renderer thread that the activity is pausing.
     * <p>
     * For best results, call this *after* disabling Camera preview.
     */
    public void notifyPausing() {
        if (mSurfaceTexture != null) {
            Timber.d("renderer pausing -- releasing SurfaceTexture");
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release(false);     // assume the GLSurfaceView EGL context is about
            mFullScreen = null;             //  to be destroyed
        }
        mIncomingWidth = mIncomingHeight = -1;
        mVideoFrameWidth = mVideoFrameHeight = -1;
    }

    /**
     * Notifies the renderer that we want to stop or start recording.
     */
    public void changeRecordingState(boolean isRecording) {
        Timber.d("changeRecordingState: was %b now %b", mRecordingEnabled, isRecording);
        mRecordingEnabled = isRecording;
    }

    /**
     * Changes the filter that we're applying to the camera preview.
     */
    public void changeFilterMode(int filter) {
        mNewFilter = filter;
    }

    /**
     * Updates the filter program.
     */
    public void updateFilter() {
        Texture2dProgram.ProgramType programType;
        float[] kernel = null;
        float colorAdj = 0.0f;

        Timber.d("Updating filter to %d", mNewFilter);
        switch (mNewFilter) {
            case CameraCaptureActivity.FILTER_NONE:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT;
                break;
            case CameraCaptureActivity.FILTER_BLACK_WHITE:
                // (In a previous version the TEXTURE_EXT_BW variant was enabled by a flag called
                // ROSE_COLORED_GLASSES, because the shader set the red channel to the B&W color
                // and green/blue to zero.)
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_BW;
                break;
            case CameraCaptureActivity.FILTER_BLUR:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        1f / 16f, 2f / 16f, 1f / 16f,
                        2f / 16f, 4f / 16f, 2f / 16f,
                        1f / 16f, 2f / 16f, 1f / 16f};
                break;
            case CameraCaptureActivity.FILTER_SHARPEN:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        0f, -1f, 0f,
                        -1f, 5f, -1f,
                        0f, -1f, 0f};
                break;
            case CameraCaptureActivity.FILTER_EDGE_DETECT:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        -1f, -1f, -1f,
                        -1f, 8f, -1f,
                        -1f, -1f, -1f};
                break;
            case CameraCaptureActivity.FILTER_EMBOSS:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        2f, 0f, 0f,
                        0f, -1f, 0f,
                        0f, 0f, -1f};
                colorAdj = 0.5f;
                break;
            default:
                throw new RuntimeException("Unknown filter mode " + mNewFilter);
        }

        // Do we need a whole new program?  (We want to avoid doing this if we don't have
        // too -- compiling a program could be expensive.)
        if (programType != mFullScreen.getProgram().getProgramType()) {
            mFullScreen.changeProgram(new Texture2dProgram(programType));
            // If we created a new program, we need to initialize the texture width/height.
            mIncomingSizeUpdated = true;
        }

        // Update the filter kernel (if any).
        if (kernel != null) {
            mFullScreen.getProgram().setKernel(kernel, colorAdj);
        }

        mCurrentFilter = mNewFilter;
    }

    /**
     * Records the size of the incoming camera preview frames.
     * <p>
     * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
     * so we assume it could go either way.  (Fortunately they both run on the same thread,
     * so we at least know that they won't execute concurrently.)
     */
    public void setCameraPreviewSize(int width, int height) {
        Timber.d("setCameraPreviewSize");
        mIncomingWidth = width;
        mIncomingHeight = height;
        mIncomingSizeUpdated = true;
    }

    public void setVideoFrameSize(int width, int height) {
        mVideoFrameWidth = width;
        mVideoFrameHeight = height;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        Timber.d("onSurfaceCreated");

        // We're starting up or coming back.  Either way we've got a new EGLContext that will
        // need to be shared with the video encoder, so figure out if a recording is already
        // in progress.
        mRecordingEnabled = mVideoEncoder.isRecording();
        if (mRecordingEnabled) {
            mRecordingStatus = RECORDING_RESUMED;
        } else {
            mRecordingStatus = RECORDING_OFF;
        }

        // Set up the texture blitter that will be used for on-screen display.  This
        // is *not* applied to the recording, because that uses a separate shader.
        mFullScreen = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

        mTextureId = mFullScreen.createTextureObject();

        // Create a SurfaceTexture, with an external texture, in this EGL context.  We don't
        // have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
        // available messages will arrive on the main thread.
        mSurfaceTexture = new SurfaceTexture(mTextureId);

        // Tell the UI thread to enable the camera preview.
        mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        Timber.d("onSurfaceChanged %dx%d", width, height);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onDrawFrame(GL10 unused) {
        if (VERBOSE) Timber.d("onDrawFrame tex=%d", mTextureId);
        boolean showBox = false;

        // Latch the latest frame.  If there isn't anything new, we'll just re-use whatever
        // was there before.
        mSurfaceTexture.updateTexImage();

        // If the recording state is changing, take care of it here.  Ideally we wouldn't
        // be doing all this in onDrawFrame(), but the EGLContext sharing with GLSurfaceView
        // makes it hard to do elsewhere.
        if (mRecordingEnabled) {
            switch (mRecordingStatus) {
                case RECORDING_OFF:
                    if (mVideoFrameWidth <= 0 || mVideoFrameHeight <= 0) {
                        Timber.i("Start recording before setting video frame size; skipping");
                        break;
                    }
                    Timber.d("START recording");
                    // TODO(jhuai): why does the height and width have to be swapped here?
                    // The output video has a size e.g., 720x1280. Video of the same size is recorded in
                    // the portrait mode of the complex CameraRecorder-android at
                    // https://github.com/MasayukiSuda/CameraRecorder-android.
                    mVideoEncoder.startRecording(
                            new TextureMovieEncoder.EncoderConfig(
                                    mOutputFile,
                                    mVideoFrameHeight, mVideoFrameWidth,
                                    CameraUtils.calcBitRate(mVideoFrameWidth, mVideoFrameHeight,
                                            VideoEncoderCore.FRAME_RATE),
                                    EGL14.eglGetCurrentContext(),
                                    mTimeFile));
                    mRecordingStatus = RECORDING_ON;
                    break;
                case RECORDING_RESUMED:
                    Timber.d("RESUME recording");
                    mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                    mRecordingStatus = RECORDING_ON;
                    break;
                case RECORDING_ON:
                    // yay
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        } else {
            switch (mRecordingStatus) {
                case RECORDING_ON:
                case RECORDING_RESUMED:
                    // stop recording
                    Timber.d("STOP recording");
                    mVideoEncoder.stopRecording();
                    mRecordingStatus = RECORDING_OFF;
                    break;
                case RECORDING_OFF:
                    // yay
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        }

        // Set the video encoder's texture name.  We only need to do this once, but in the
        // current implementation it has to happen after the video encoder is started, so
        // we just do it here.
        //
        // TODO: be less lame.
        mVideoEncoder.setTextureId(mTextureId);

        // Tell the video encoder thread that a new frame is available.
        // This will be ignored if we're not actually recording.
        mVideoEncoder.frameAvailable(mSurfaceTexture);

        if (mIncomingWidth <= 0 || mIncomingHeight <= 0) {
            // Texture size isn't set yet.  This is only used for the filters, but to be
            // safe we can just skip drawing while we wait for the various races to resolve.
            // (This seems to happen if you toggle the screen off/on with power button.)
            Timber.i("Drawing before incoming texture size set; skipping");
            return;
        }
        // Update the filter, if necessary.
        if (mCurrentFilter != mNewFilter) {
            updateFilter();
        }
        if (mIncomingSizeUpdated) {
            mFullScreen.getProgram().setTexSize(mIncomingWidth, mIncomingHeight);
            mIncomingSizeUpdated = false;
        }

        // Draw the video frame.
        mSurfaceTexture.getTransformMatrix(mSTMatrix);
        mFullScreen.drawFrame(mTextureId, mSTMatrix);

        // Draw a flashing box if we're recording.  This only appears on screen.
        showBox = (mRecordingStatus == RECORDING_ON);
        if (showBox && (++mFrameCount & 0x04) == 0) {
            drawBox();
        }
    }

    /**
     * Draws a red box in the corner.
     */
    private void drawBox() {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(0, 0, 100, 100);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
}
