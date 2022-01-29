package com.marslogger.locationprovider;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.util.Consumer;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class LocationProvider {

    private final Context context;

    // background thread that listens for gps changes
    private HandlerThread locationThread;
    LocationCallback locationCallback;
    FusedLocationProviderClient fusedLocationClient;

    // for handling gps updates
    private static final int DEFAULT_UPDATE_INTERVAL = 1;
    private static final int FAST_UPDATE_INTERVAL = 1;

    public LocationProvider(Context context) {
        this.context = context;
    }

    public void createLocationListener(Consumer<Location> locationConsumer) {
        locationThread = new HandlerThread("locationThread");
        locationThread.start();
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(500);
        locationRequest.setFastestInterval(500);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

        // check if you have permission before trying to get the location
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(locationConsumer::accept);
        }

        // triggered whenever the location is updated
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                locationConsumer.accept(locationResult.getLastLocation());
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
