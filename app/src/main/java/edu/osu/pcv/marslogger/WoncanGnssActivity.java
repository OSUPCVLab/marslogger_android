package edu.osu.pcv.marslogger;

import static java.lang.Thread.sleep;

import static edu.osu.pcv.marslogger.WoncanUtils.convertStatusToString;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.woncan.device.Device;
import com.woncan.device.NMEA;
import com.woncan.device.RTCM;
import com.woncan.device.RTCMInterval;
import com.woncan.device.bean.SatelliteInfo;
import com.woncan.device.ScanManager;
import com.woncan.device.bean.DeviceInfo;
import com.woncan.device.bean.DeviceNtripAccount;
import com.woncan.device.bean.WLocation;
import com.woncan.device.device.DeviceInterval;
import com.woncan.device.listener.DeviceStatesListener;
import com.woncan.device.listener.RTCMListener;
import com.woncan.device.listener.SatelliteListener;
import com.woncan.device.listener.WLocationListener;;

import java.util.List;
import java.util.Locale;

public class WoncanGnssActivity extends Activity {
    private static final String TAG = WoncanGnssActivity.class.getName();;

    private TextView tvDeviceInfo;
    private TextView tvLocation;
    private TextView tvLog;
    private Device mDevice = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DeviceAdapter adapter = new DeviceAdapter();
        setContentView(R.layout.activity_gnss);

        Button cancelButton = (Button)findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(v -> ScanManager.cancelDiscovery(this));

        tvLog = (TextView)findViewById(R.id.tv_log);
        tvLog.setMovementMethod(ScrollingMovementMethod.getInstance());

        tvDeviceInfo = (TextView)findViewById(R.id.tv_device_info);
        tvLocation = (TextView)findViewById(R.id.tv_location);

        Button searchButton = (Button)findViewById(R.id.btn_search);
        searchButton.setOnClickListener(v -> {
            adapter.setNewInstance(null);
            ScanManager.scanDevice(this, device -> {
                Log.i(TAG, "onCreate: " + device.getName());
                if (!adapter.getData().contains(device)) {
                    adapter.addData(device);
                }
            });
        });

        Button returnButton = (Button) findViewById(R.id.btn_return);
        returnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WoncanGnssActivity.this.finish();
            }
        });

        RecyclerView recyclerView = (RecyclerView)findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        adapter.setOnItemClickListener((adapter1, view, position) -> {
            ScanManager.stopScan(this);
            mDevice = adapter.getItem(position);
            connect(mDevice);
        });
    }

    private void connect(Device device) {
        device.registerSatesListener(new DeviceStatesListener() {
            @Override
            public void onConnectionStateChange(boolean isConnect) {
                tvLog.append(isConnect ? "设备已连接\n" : "断开连接\n");
            }

            @Override
            public void onDeviceAccountChange(@NonNull DeviceNtripAccount account) {
                super.onDeviceAccountChange(account);

            }

            @Override
            public void onDeviceInfoChange(@NonNull DeviceInfo deviceInfo) {
                tvDeviceInfo.setText(String.format(Locale.CHINA, "型号：%s\n设备ID：%s\n产品名：%s", deviceInfo.getModel(), deviceInfo.getDeviceID(), deviceInfo.getProductNameZH()));
            }

            @Override
            public void onLaserStateChange(boolean isOpen) {

            }
        });

        device.registerLocationListener(new WLocationListener() {
            @Override
            public void onReceiveLocation(@NonNull WLocation wLocation) {
                Log.i(TAG, "onReceiveLocation: wLocation");
                tvLocation.setText(String.format(Locale.CHINA, "纬度：%.8f\n经度：%.8f\n海拔：%.3f\n解状态：%s",
                        wLocation.getLatitude(), wLocation.getLongitude(), wLocation.getAltitude(),
                        convertStatusToString(wLocation.getFixStatus())));
            }

            @Override
            public void onError(int i, @NonNull String s) {
                tvLog.append(String.format(Locale.CHINA, "onError:%d  %s\n", i, s));
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

    @Override
    protected void onStop() {
        super.onStop();
        if (mDevice != null) {
            mDevice.closeRTCM();
            mDevice.disconnect();
            mDevice = null;
        }
    }
}
