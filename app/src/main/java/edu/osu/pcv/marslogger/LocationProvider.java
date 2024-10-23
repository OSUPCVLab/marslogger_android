package edu.osu.pcv.marslogger;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.util.Consumer;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;


public class LocationProvider {

    private final Context context;
    private HandlerThread locationThread;
    private LocationCallback locationCallback;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationManager locationManager;

    private static final int DEFAULT_UPDATE_INTERVAL = 1000; // Interval in milliseconds
    private static final int FAST_UPDATE_INTERVAL = 500; // Fast interval in milliseconds

    private final Consumer<Location> consumer;
    private Location currLocation;

    public LocationProvider(Context context, Consumer<Location> consumer) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.consumer = consumer;
    }

    public Location getCurrLocation() {
        return currLocation;
    }

    public void createLocationListener() {
        locationThread = new HandlerThread("locationThread");
        locationThread.start();

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(DEFAULT_UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FAST_UPDATE_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationService", "Location permission not granted!");
            return; // Exit early if permission isn't granted
        }

        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);

        if (resultCode == ConnectionResult.SUCCESS) {
            // Google Play Services is available, use FusedLocationProviderClient
            Log.d("LocationService", "Google Play Services available, using FusedLocationProviderClient");
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    currLocation = location;
                    if (consumer != null) {
                        consumer.accept(location);
                    }
                } else {
                    Log.w("LocationService", "No last known location available");
                }
            });

            // Setup the callback for continuous location updates
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    super.onLocationResult(locationResult);
                    for (Location location : locationResult.getLocations()) {
                        if (location != null && consumer != null) {
                            consumer.accept(location);
                            currLocation = location;
                            Log.d("LocationService", "New location: " + location.toString());
                        }
                    }
                }
            };

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, locationThread.getLooper());

        } else {
            Log.w("LocationService", "Google Play Services unavailable, falling back to LocationManager");
            fallbackToLocationManager();
        }
    }

    private void fallbackToLocationManager() {
        try {
            boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!isGPSEnabled && !isNetworkEnabled) {
                Log.w("LocationService", "No GPS or Network provider is enabled!");
                return;
            }

            // Use the best available provider (GPS if available, otherwise network)
            String provider = isGPSEnabled ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;

            locationManager.requestLocationUpdates(provider, 1000, 0.f, locationListener);

            Location lastKnownLocation = locationManager.getLastKnownLocation(provider);
            if (lastKnownLocation != null) {
                currLocation = lastKnownLocation;
                if (consumer != null)
                    consumer.accept(lastKnownLocation);
                Log.d("LocationService", "Fallback last known location: " + lastKnownLocation);
            }

        } catch (SecurityException e) {
            Log.e("LocationService", "Location permission not granted for LocationManager.", e);
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (consumer != null && location != null) {
                consumer.accept(location);
            }
            currLocation = location;
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) { }

        @Override
        public void onProviderEnabled(String provider) { }

        @Override
        public void onProviderDisabled(String provider) { }
    };

    public void quitThread() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        if (locationThread != null) {
            locationThread.quitSafely();
        }
    }
}

