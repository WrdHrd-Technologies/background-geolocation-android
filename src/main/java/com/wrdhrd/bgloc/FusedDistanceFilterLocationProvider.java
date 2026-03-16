package com.wrdhrd.bgloc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.provider.AbstractLocationProvider;
import com.marianhello.logging.LoggerManager;
import com.marianhello.utils.ToneGenerator;

public class FusedDistanceFilterLocationProvider extends AbstractLocationProvider {
    private static final org.slf4j.Logger logger = LoggerManager.getLogger(FusedDistanceFilterLocationProvider.class);

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    
    private boolean isStarted = false;
    private boolean isMoving = false;
    private int stationaryCount = 0;
    private Location lastLocation;
    private long currentActiveInterval = -1;

    private static final long INTERVAL_HIGHWAY = 10000;  // > 80 km/h
    private static final long INTERVAL_CITY = 15000;    // > 40 km/h
    private static final long INTERVAL_RUNNING = 30000; // > 10 km/h

    public FusedDistanceFilterLocationProvider(Context context) {
        super(context,Config.FUSED_DISTANCE_FILTER_PROVIDER);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    if (location.getAccuracy() > 100.0f) {
                        logger.debug("Garbage location ignored. Accuracy: {}m", location.getAccuracy());
                        continue;
                    }

                    float speed = 0.0f;
                    if (location.hasSpeed() && location.getSpeed() > 0.0f) {
                        // Hardware gave us a valid doppler-shift speed
                        speed = location.getSpeed();
                    } else if (lastLocation != null) {
                        // Hardware failed. Calculate Software Speed: v = d / t
                        float distance = location.distanceTo(lastLocation);
                        long timeDeltaMillis = location.getTime() - lastLocation.getTime();

                        if (timeDeltaMillis > 0) {
                            speed = distance / (timeDeltaMillis / 1000.0f);
                        }
                        logger.debug("Hardware speed missing. Calculated Software Speed: {} m/s", speed);
                    }

                    adjustPaceBasedOnSpeed(speed);

                    if (isMoving && speed < 0.5f) {
                        stationaryCount++;
                        logger.debug("Low speed detected ({} m/s). Stationary count: {}", speed, stationaryCount);
                        
                        if (stationaryCount >= 3) {
                            stationaryCount = 0;
                            logger.info("User is stationary. Engaging Heartbeat and killing GPS.");

                            if (mConfig.isDebugging()) {
                                playDebugTone(ToneGenerator.Tone.LONG_BEEP); // Long tone so you know it went to sleep
                            }
                            
                            // Tell LocationServiceImpl to start the HeartbeatManager
                            handleStationary(location, mConfig.getStationaryRadius()); 
                            
                            // Turn off the continuous GPS radio
                            setPace(false); 
                            return; 
                        }
                    } else if (isMoving) {
                        stationaryCount = 0; // Reset if they are moving
                    }

                    if (lastLocation != null) {
                        float distance = location.distanceTo(lastLocation);
                        int dynamicFilter = calculateDynamicDistanceFilter(speed);
                        if (distance < dynamicFilter) {
                            logger.debug("Elastic Filter: Ignored {}m movement. Dynamic limit at {}m/s is {}m.",
                                    distance, speed, dynamicFilter);
                            continue; // Skip the DB save
                        }
                    }

                    if (mConfig.isDebugging()) {
                        playDebugTone(ToneGenerator.Tone.BEEP); // Short beep every time a location passes the filters
                    }

                    logger.debug("Valid movement detected. Saving location.");
                    lastLocation = location;
                    handleLocation(location);
                }
            }
        };
    }

    @SuppressLint("MissingPermission")
    private void adjustPaceBasedOnSpeed(float speedMetersPerSecond) {
        if (!isMoving || !isStarted) return;

        long desiredInterval;
        long baseInterval = mConfig.getInterval();
        long absoluteFastest = mConfig.getFastestInterval();

        if (speedMetersPerSecond > 22.0f) {
            desiredInterval = INTERVAL_HIGHWAY;
        } else if (speedMetersPerSecond > 11.0f) {
            desiredInterval = INTERVAL_CITY;
        } else if (speedMetersPerSecond > 3.0f) {
            desiredInterval = INTERVAL_RUNNING;
        } else {
            desiredInterval = baseInterval; // Walking/Traffic
        }

        desiredInterval = Math.max(absoluteFastest, desiredInterval);
        desiredInterval = Math.min(baseInterval, desiredInterval);

        if (desiredInterval != currentActiveInterval) {
            logger.info("Shifting tracking gear. New Interval: {} ms", desiredInterval);
            currentActiveInterval = desiredInterval;
            
            long hardwareFastestInterval = desiredInterval / 2; 
            int priority = translateDesiredAccuracy(mConfig.getDesiredAccuracy());

            LocationRequest locationRequest = new LocationRequest.Builder(priority, desiredInterval)
                    .setMinUpdateIntervalMillis(hardwareFastestInterval)
                    .setWaitForAccurateLocation(false)
                    .build();

            try {
                mFusedLocationClient.removeLocationUpdates(mLocationCallback);
                mFusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.getMainLooper());
            } catch (SecurityException e) {
                handleSecurityException(e);
            }
        }
    }

    @Override
    @SuppressLint("MissingPermission")
    public void onResume() {
        logger.info("Resuming FusedDistanceFilterLocationProvider");
        super.onResume();
        setPace(true);
    }

    @Override
    @SuppressLint("MissingPermission")
    public void onStart() {
        if (isStarted) return;
        logger.info("Starting FusedDistanceFilterLocationProvider");
        super.onStart();
        isStarted = true;
        setPace(true); 
    }

    @Override
    public void onStop() {
        if (!isStarted) return;
        super.onStop();
        isStarted = false;
        logger.info("Stopping FusedDistanceFilterLocationProvider");
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        } catch (SecurityException e) {
            handleSecurityException(e);
        }
        currentActiveInterval = -1;
    }

    private void setPace(boolean moving) {
        if (!isStarted) return;
        isMoving = moving;
        stationaryCount = 0;
        
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            currentActiveInterval = -1; 

            if (isMoving) {
                logger.info("Engaging continuous GPS tracking.");
                adjustPaceBasedOnSpeed(0.0f); 
            } else {
                logger.info("GPS suspended. Yielding to HeartbeatManager.");
            }
        } catch (SecurityException e) {
            handleSecurityException(e);
        }
    }

    /**
     * Called exclusively by the HeartbeatManager from LocationServiceImpl 
     * to perform the One-Shot GPS Ping while the app is stationary.
     */
    @SuppressLint("MissingPermission")
    public void requestSingleFreshLocation(OnSuccessListener<Location> listener, OnFailureListener failureListener) {
        logger.debug("Waking GPS radio for one-shot heartbeat ping...");
        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        
        try {
            mFusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(listener)
                .addOnFailureListener(failureListener);
        } catch (SecurityException e) {
            handleSecurityException(e);
            if (failureListener != null) {
                failureListener.onFailure(e);
            }
        }
    }

    private int translateDesiredAccuracy(Integer accuracy) {
        if (accuracy == null) {
            return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        }

        if (accuracy >= 1000) {
            // City block level accuracy, best for battery
            return Priority.PRIORITY_LOW_POWER;
        }
        if (accuracy >= 100) {
            // ~100m accuracy, good for general urban tracking
            return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        }
        if (accuracy >= 0) {
            // ~10m accuracy, forces the GPS chip on
            return Priority.PRIORITY_HIGH_ACCURACY;
        }

        // Default fallback
        return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
    }

    @Override
    public void onConfigure(Config config) {
        super.onConfigure(config);
        if (isStarted) {
            onStop();
            onStart();
        }
    }

    @Override
    public void onDestroy() {
        onStop();
        super.onDestroy();
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    private int calculateDynamicDistanceFilter(float speed) {
        int baseFilter = mConfig.getDistanceFilter();

        // Sanity check: If speed is insane (over 360 km/h), fallback to base
        if (speed < 100.0f) {
            // Round to nearest 5 for clean math, then square it
            float roundedSpeed = (Math.round(speed / 5.0f) * 5.0f);
            int dynamicFilter = (int) Math.pow(roundedSpeed, 2) + baseFilter;

            // Cap the stretch at 1000 meters so we don't go totally blind
            return Math.min(dynamicFilter, 1000);
        }

        return baseFilter;
    }
}