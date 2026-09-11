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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.LocationManager;

import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

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

import edu.osu.pcv.marslogger.benchmark.BenchmarkSessionManager;
import edu.osu.pcv.marslogger.benchmark.PipelinePerformanceLogger;

/**
 *  RosActivity
 *  └── LidarCaptureActivity
 *      └── has CameraCapture
 *          └── has Camera2Proxy which refs CameraCapture
 */


public class LidarCaptureActivity extends RosActivity implements OnItemSelectedListener {
    public static final String TAG = "MarsLogger";
    private CameraCapture mCameraCapture;
    private GLES20SurfaceView mPCGLView;
    private TextView mOutputDirText;

    private CameraHandler mCameraHandler;
    private boolean mRecordingEnabled;      // controls button state

    private IMUManager mImuManager;
    private GPSManager mGpsManager;
    private TimeBaseManager mTimeBaseManager;
    private BenchmarkSessionManager mBenchmarkSessionManager;

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

    private String pandarTimeType;

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

    ///@{ // to satisfy RosActivity
    public LidarCaptureActivity() {
        super("RosAndroidExample", "RosAndroidExample");
    }
    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    ///@} // to satisfy RosActivity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Occasionally some device show landscape views despite the portrait in manifest. See
        // https://stackoverflow.com/questions/47228194/android-8-1-screen-orientation-issue-flipping-to-landscape-a-portrait-screen
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        setContentView(R.layout.activity_lidar_capture);
        mBenchmarkSessionManager = BenchmarkSessionManager.getInstance(this);
        mCameraCapture = new CameraCapture(this);

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
        pandarTimeType = mSharedPreferences.getString("prefPandarTimeType", "sensor");
        if (pandarTimeType.isEmpty())
            pandarTimeType = "sensor";
        lidarIdTextView.setText(lidarType.toString() + " " + lidarIdStr + " time_type: " + pandarTimeType);

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
                    ScanManager.scanDevice(LidarCaptureActivity.this, device -> {
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
        mCameraCapture.initCamera2Proxy();

        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mCameraHandler = new CameraHandler(mCameraCapture);

        mRecordingEnabled = mCameraCapture.sVideoEncoder.isRecording();

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
        // Configure the GLSurfaceView for point clouds
        // https://www.dre.vanderbilt.edu/~schmidt/android/android-4.0/out/target/common/docs/doc-comment-check/resources/articles/glsurfaceview.html
        mPCGLView = (GLES20SurfaceView) findViewById(R.id.gl_surface_view);
        mPCGLView.setPerformanceLogger(mBenchmarkSessionManager.getPipelineLogger());

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
        mCameraCapture.pause();

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
            String outputDir = mCameraCapture.renewOutputDir();
            String outputFile = outputDir + File.separator + "movie.mp4";
            String timeFile = outputDir + File.separator + "frame_timestamps.txt";
            String basename = outputDir.substring(outputDir.lastIndexOf("/")+1);
            mOutputDirText.setText(basename);
            mCameraCapture.mRenderer.resetOutputFiles(outputFile, timeFile); // this will not cause sync issues
            String inertialFile = outputDir + File.separator + "gyro_accel.csv";
            String gpsFile = outputDir + File.separator + "gps.csv";
            String allGpsFile = outputDir + File.separator + "all_gps.csv";
            String gnssRtkFile = outputDir + File.separator + "woncan_gnss.csv";
            String edgeEpochFile = outputDir + File.separator + "edge_epochs.txt";
            mTimeBaseManager.startRecording(edgeEpochFile, mCameraCapture.mCamera2Proxy.getmTimeSourceValue());
            mGpsManager.startRecording(gpsFile, allGpsFile);
            mImuManager.startRecording(inertialFile);
            startGnssRtkRecording(gnssRtkFile);
            mCameraCapture.mCamera2Proxy.startRecordingCaptureResult(
                    outputDir + File.separator + "movie_metadata.csv");
            startFasterLio(outputDir);
            startLaserLogging2(outputDir + File.separator + "lidar.bag");
            rosListenerNode.setRecording(true);
        } else {
            mCameraCapture.mCamera2Proxy.stopRecordingCaptureResult();
            stopGnssRtkRecording();
            mImuManager.stopRecording();
            mGpsManager.stopRecording();
            mTimeBaseManager.stopRecording();
            rosListenerNode.setRecording(false);
            stopLaserLogging2();
            stopFasterLio();
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
        String[] extraArgs = new String[3];
        extraArgs[0] = lidarip;
        extraArgs[1] = lidarCorrectionFile;
        extraArgs[2] = pandarTimeType;
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
        rosListenerNode.setPerformanceLogger(mBenchmarkSessionManager.getPipelineLogger());
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
                PipelinePerformanceLogger performanceLogger =
                        mBenchmarkSessionManager.getPipelineLogger();
                PipelinePerformanceLogger.FrameTiming timing = null;
                if (performanceLogger.isActive()) {
                    long frameId = ((long) msg.getHeader().getSeq()) & 0xffffffffL;
                    long sensorTimestampNs = msg.getHeader().getStamp().totalNsecs();
                    timing = performanceLogger.onMappingOutputReceived(
                            frameId, sensorTimestampNs);
                }
                List<Point> points = PCConverter.toPointList(msg);
                if (timing != null) {
                    performanceLogger.onPreprocessingComplete(timing, points.size());
                }
                mPCGLView.appendPoints(points);
                if (timing != null) {
                    performanceLogger.onOpenGlHandoff(timing);
                }
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
