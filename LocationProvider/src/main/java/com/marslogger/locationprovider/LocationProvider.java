package com.marslogger.locationprovider;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.util.Consumer;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.LinkedHashMap;

public class LocationProvider {

    private final Context context;

    // background thread that listens for gps changes
    private HandlerThread locationThread;
    LocationCallback locationCallback;
    FusedLocationProviderClient fusedLocationClient;

    // for handling gps updates
    private static final int DEFAULT_UPDATE_INTERVAL = 1;
    private static final int FAST_UPDATE_INTERVAL = 1;

    // to consume locations other than the latest one
    private final Consumer<Location> consumer;

    public LocationProvider(Context context, Consumer<Location> consumer) {
        this.context = context;
        this.consumer = consumer;
    }

    // the most recent location
    private Location currLocation;

    public Location getCurrLocation() {
        return currLocation;
    }

    public void createLocationListener() {
        locationThread = new HandlerThread("locationThread");
        locationThread.start();
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(1000 * DEFAULT_UPDATE_INTERVAL);
        locationRequest.setFastestInterval(1000 * FAST_UPDATE_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

        // check if you have permission before trying to get the location
        int fineLocationPermissionCode = ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
        );
        int coarseLocationPermissionCode = ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
        );
        if (fineLocationPermissionCode == PackageManager.PERMISSION_GRANTED
                && coarseLocationPermissionCode == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> currLocation = location);
        }

        // triggered whenever the location is updated
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if (consumer != null) {
                    Log.d("something", "locations " + locationResult.getLocations().size());
                    for (Location location : locationResult.getLocations()) {
                        consumer.accept(location);
                    }
                }
                currLocation = locationResult.getLastLocation();
            }
        };
        fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, locationThread.getLooper()
        );
    }

    public void quitThread() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        locationThread.quitSafely();
    }
}
