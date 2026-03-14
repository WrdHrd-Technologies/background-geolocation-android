package com.marianhello.bgloc.provider;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.service.GeofenceBroadcastReceiver;

public class FusedLocationProvider extends AbstractLocationProvider {

    private static final String TAG = FusedLocationProvider.class.getSimpleName();
    
    private FusedLocationProviderClient mFusedLocationClient;
    private GeofencingClient mGeofencingClient;
    private LocationCallback mLocationCallback;
    private PendingIntent mGeofencePendingIntent;
    
    private boolean isStarted = false;
    private boolean isMoving = false;
    private int stationaryCount = 0; 
    private Location lastLocation;

    public FusedLocationProvider(Context context) {
        super(context, Config.DISTANCE_FILTER_PROVIDER); 
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext);
        mGeofencingClient = LocationServices.getGeofencingClient(mContext);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                
                for (Location location : locationResult.getLocations()) {
                    logger.debug("FLP Location received: lat={} lon={} acy={} speed={}", 
                            location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getSpeed());
                    
                    lastLocation = location;
                    handleLocation(location);

                    // --- STATIONARY DETECTION ENGINE ---
                    if (isMoving && location.hasSpeed() && location.getSpeed() < 1.0f) {
                        stationaryCount++;
                        logger.debug("Low speed detected. Stationary count: {}", stationaryCount);
                        
                        // Wait for 3 consecutive slow readings to avoid false stops at traffic lights
                        if (stationaryCount >= 3) {
                            stationaryCount = 0;
                            dropGeofenceAnchor(location);
                        }
                    } else if (isMoving) {
                        // User sped back up, reset the counter
                        stationaryCount = 0; 
                    }
                }
            }
        };
    }

    @Override
    public void onStart() {
        if (isStarted) {
            return;
        }
        logger.info("Starting FusedLocationProvider");
        isStarted = true;
        // Default to moving to establish an initial fix and speed vector
        setPace(true); 
    }

    @Override
    public void onStop() {
        if (!isStarted) {
            return;
        }
        logger.info("Stopping FusedLocationProvider");
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            removeGeofenceAnchor();
        } catch (SecurityException e) {
            logger.error("Security exception while stopping provider", e);
        } finally {
            isStarted = false;
        }
    }

    @Override
    public void onCommand(int commandId, int arg1) {
        if (commandId == CMD_SWITCH_MODE) {
            setPace(arg1 != BACKGROUND_MODE);
        }
    }

    @Override
    public void onConfigure(Config config) {
        super.onConfigure(config);
        if (isStarted) {
            logger.info("Reconfiguring FusedLocationProvider");
            onStop();
            onStart();
        }
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    @Override
    public void onDestroy() {
        logger.info("Destroying FusedLocationProvider");
        onStop();
        super.onDestroy();
    }

    /**
     * Controls the core state machine: High-drain GPS vs Zero-drain Geofence.
     */
    private void setPace(boolean moving) {
        if (!isStarted) return;
        
        isMoving = moving;
        stationaryCount = 0; 
        logger.info("Setting pace. isMoving: {}", isMoving);

        try {
            // Always clear existing updates to avoid duplicate callbacks
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);

            if (isMoving) {
                // 1. Remove the stationary geofence
                removeGeofenceAnchor();
                
                // 2. Build modern high-accuracy request
                int priority = translateDesiredAccuracy(mConfig.getDesiredAccuracy());
                LocationRequest locationRequest = new LocationRequest.Builder(priority, mConfig.getInterval())
                        .setMinUpdateDistanceMeters(mConfig.getDistanceFilter())
                        .setMinUpdateIntervalMillis(mConfig.getFastestInterval())
                        .setWaitForAccurateLocation(false) 
                        .build();

                // 3. Start aggressive tracking
                mFusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.getMainLooper());
                logger.info("Engaged high-accuracy moving mode.");
            } else {
                logger.info("Engaged stationary mode. High-accuracy tracking suspended.");
                // Notice we do NOT request location updates here. 
                // We are now relying entirely on the HeartbeatManager and the Geofence.
            }
        } catch (SecurityException e) {
            logger.error("Security exception applying pace", e);
            handleSecurityException(e);
        }
    }

    /**
     * Drops a Geofence around the user and kills the active GPS tracking.
     */
    private void dropGeofenceAnchor(Location location) {
        logger.info("User appears stationary. Dropping Geofence anchor at lat={} lon={} rad={}", 
                location.getLatitude(), location.getLongitude(), mConfig.getStationaryRadius());
        
        // 1. Alert the Service (This triggers the HeartbeatManager loop we built)
        handleStationary(location, mConfig.getStationaryRadius());

        // 2. Build the OS-level Geofence
        Geofence geofence = new Geofence.Builder()
                .setRequestId("STATIONARY_ANCHOR")
                .setCircularRegion(location.getLatitude(), location.getLongitude(), mConfig.getStationaryRadius())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();

        GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
                .addGeofence(geofence)
                .build();

        // 3. Register it with Google Play Services
        try {
            mGeofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent())
                .addOnSuccessListener(aVoid -> {
                    logger.debug("Geofence successfully added. Suspending active GPS.");
                    // Only shut off the GPS *after* the OS confirms the Geofence is active.
                    setPace(false); 
                })
                .addOnFailureListener(e -> {
                    logger.error("Failed to add Geofence. Falling back to continuous tracking.", e);
                    // If the Geofence fails (e.g., missing background permissions), 
                    // we cannot turn off the GPS, or we will lose the user forever.
                });
        } catch (SecurityException e) {
            logger.error("Missing ACCESS_BACKGROUND_LOCATION permission. Cannot drop anchor.", e);
            handleSecurityException(e);
        }
    }

    private void removeGeofenceAnchor() {
        if (mGeofencePendingIntent != null) {
            mGeofencingClient.removeGeofences(mGeofencePendingIntent)
                .addOnSuccessListener(aVoid -> logger.debug("Geofence anchor removed."))
                .addOnFailureListener(e -> logger.error("Failed to remove Geofence anchor", e));
        }
    }

    /**
     * Creates the routing intent to your GeofenceBroadcastReceiver.
     */
    private PendingIntent getGeofencePendingIntent() {
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(mContext, GeofenceBroadcastReceiver.class);
        
        // CRITICAL: Geofence intents MUST be MUTABLE so the OS can append transition data.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        
        mGeofencePendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, flags);
        return mGeofencePendingIntent;
    }

    /**
     * Translates legacy 0, 10, 100, 1000 accuracy integers to modern Priority constants.
     */
    private int translateDesiredAccuracy(Integer accuracy) {
        if (accuracy >= 1000) {
            return Priority.PRIORITY_LOW_POWER; 
        }
        if (accuracy >= 100) {
            return Priority.PRIORITY_BALANCED_POWER_ACCURACY; 
        }
        return Priority.PRIORITY_HIGH_ACCURACY; 
    }
}