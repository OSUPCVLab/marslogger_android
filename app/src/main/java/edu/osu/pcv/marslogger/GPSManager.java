package edu.osu.pcv.marslogger;

import android.app.Activity;
import android.location.Location;
import android.os.SystemClock;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import timber.log.Timber;

/**
 * this class is mostly similar to IMUManager because gps data must be logged at the same time
 * as the imu data (but in a separate file)
 */
public class GPSManager {

    public static String GpsHeader = "uptime[sec],lat[deg],lon[deg],ellipsoid height[m],Unix time[sec]\n";

    private final LocationProvider locationProvider;
    private BufferedWriter mLatestLocationWriter = null;
    private BufferedWriter mAllLocationsWriter = null;
    private volatile boolean mRecordingGpsData = false;
    private volatile boolean mRecordingAllGpsData = false;

    public GPSManager(Activity activity) {
        locationProvider = new LocationProvider(activity, location -> {
            if (mRecordingAllGpsData) {
                long unixTimeMillis = location.getTime();
                long upTimeMillis = SystemClock.uptimeMillis();
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%d.%03d %.9f %.9f %.6f %d.%03d",
                        upTimeMillis / 1000, upTimeMillis % 1000,
                        location.getLatitude(), location.getLongitude(),
                        location.getAltitude(), unixTimeMillis / 1000, unixTimeMillis % 1000
                ));

                try {
                    mAllLocationsWriter.write(sb.toString());
                } catch (IOException ioe) {
                    Timber.e(ioe);
                }
            }
        });
        locationProvider.createLocationListener();
    }

    public void startRecording(String captureResultFile, String allGpsResultFile) {
        try {
            // this will only record the last known location
            mLatestLocationWriter = new BufferedWriter(
                    new FileWriter(captureResultFile, false));
            if (locationProvider == null) {
                String warning = "Unable to initialize locationProvider!\n";
                mLatestLocationWriter.write(warning);
            } else {
                mLatestLocationWriter.write(GpsHeader);
            }
            mRecordingGpsData = true;
        } catch (IOException err) {
            Timber.e(err,"IOException in opening gps data writer at %s", captureResultFile);
        }

        try {
            // this will record all the locations we have buffered
            mAllLocationsWriter = new BufferedWriter(
                    new FileWriter(allGpsResultFile, false));
            if (locationProvider == null) {
                String warning = "Unable to initialize locationProvider!\n";
                mAllLocationsWriter.write(warning);
            } else {
                mAllLocationsWriter.write(GpsHeader);
            }
            mRecordingAllGpsData = true;
        } catch (IOException err) {
            Timber.e(err,"IOException in opening gps data writer at %s", allGpsResultFile);
        }
    }

    public void stopRecording() {
        mRecordingGpsData = false;
        mRecordingAllGpsData = false;
        try {
            mLatestLocationWriter.flush();
            mLatestLocationWriter.close();
            mAllLocationsWriter.flush();
            mAllLocationsWriter.close();
        } catch (IOException err) {
            Timber.e(err, "IOException in closing gps data writer");
        }
        mLatestLocationWriter = null;
        mAllLocationsWriter = null;
    }

    public boolean ismRecordingGpsData() {
        return mRecordingGpsData;
    }

    public boolean recordGpsValue(long syncedDataTimestamp, long unixTime) {
        boolean gpsDataRecorded = false;
        if (mRecordingGpsData) {
            Location location = locationProvider.getCurrLocation();
            if (location != null) {
                long unixTimeMillis = location.getTime();
                long upTimeMillis = SystemClock.uptimeMillis();
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%d.%03d %.9f %.9f %.6f %d.%03d",
                        upTimeMillis / 1000, upTimeMillis % 1000,
                        location.getLatitude(), location.getLongitude(),
                        location.getAltitude(), unixTimeMillis / 1000, unixTimeMillis % 1000
                ));
                try {
                    mLatestLocationWriter.write(sb.toString());
                    gpsDataRecorded = true;
                } catch (IOException ioe) {
                    Timber.e(ioe);
                }
            }
        }
        return gpsDataRecorded;
    }

    public void unregister() {
        locationProvider.quitThread();
    }
}

