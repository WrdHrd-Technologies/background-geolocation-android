package com.wrdhrd.bgloc;

import android.annotation.SuppressLint;
import android.content.Context;
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
    private static final long INTERVAL_CITY = 15000;     // > 40 km/h
    private static final long INTERVAL_RUNNING = 30000;  // > 10 km/h

    private static final float SPEED_STILL_MAX = 0.5f;
    private static final float SPEED_WALKING_MAX = 5.0f;

    public static final String ACTIVITY_STILL = "STILL";
    public static final String ACTIVITY_WALKING = "WALKING";
    public static final String ACTIVITY_DRIVING = "DRIVING";

    private String currentActivityState = ACTIVITY_STILL;
    private String pendingActivityState = ACTIVITY_STILL;
    private int stateConfidenceCount = 0;

    public FusedDistanceFilterLocationProvider(Context context) {
        super(context, Config.FUSED_DISTANCE_FILTER_PROVIDER);
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

                    float speed = location.hasSpeed() ? location.getSpeed() : 0.0f;
                    if (speed == 0.0f && lastLocation != null) {
                        float distance = location.distanceTo(lastLocation);
                        long timeDeltaMillis = location.getTime() - lastLocation.getTime();
                        if (timeDeltaMillis > 0) {
                            speed = distance / (timeDeltaMillis / 1000.0f);
                            location.setSpeed(speed);
                        }
                    }

                    float accuracy = location.hasAccuracy() ? location.getAccuracy() : 999.0f;
                    boolean isHallucination = false;

                    if (speed < 2.0f) {
                        if (accuracy > 50.0f) isHallucination = true;
                    } else {
                        if (accuracy > 150.0f) isHallucination = true;
                    }

                    if (isHallucination) {
                        logger.warn("SENTRY BLOCKED: Accuracy {}m is too blurry for velocity {}m/s.", accuracy, speed);
                        continue;
                    }

                    if (!isMoving) {
                        if (lastLocation != null) {
                            float breakoutDistance = location.distanceTo(lastLocation);
                            if (breakoutDistance < mConfig.getStationaryRadius()) {
                                logger.debug("Sentry ignored. Distance ({}m) inside {}m shield.", breakoutDistance, mConfig.getStationaryRadius());
                                continue;
                            }
                        }
                        logger.info("Hardware Displacement Shield broken! Waking up engine.");
                        setPace(true);
                    }

                    String votedState;
                    if (speed <= SPEED_STILL_MAX) {
                        votedState = ACTIVITY_STILL;
                    } else if (speed <= SPEED_WALKING_MAX) {
                        votedState = ACTIVITY_WALKING;
                    } else {
                        votedState = ACTIVITY_DRIVING;
                    }

                    if (votedState.equals(currentActivityState)) {
                        stateConfidenceCount = 0;
                    } else if (votedState.equals(pendingActivityState)) {
                        stateConfidenceCount++;
                    } else {
                        pendingActivityState = votedState;
                        stateConfidenceCount = 1;
                    }

                    if (stateConfidenceCount >= 3) {
                        logger.info("Activity Shift: {} -> {}", currentActivityState, pendingActivityState);
                        currentActivityState = pendingActivityState;
                        stateConfidenceCount = 0;
                    }

                    String rawProvider = location.getProvider();
                    if (rawProvider != null) {
                        String baseProvider = rawProvider.split("\\|")[0];
                        location.setProvider(baseProvider + "|" + currentActivityState);
                    } else {
                        location.setProvider("unknown|" + currentActivityState);
                    }

                    adjustPaceBasedOnSpeed(speed);

                    if (currentActivityState.equals(ACTIVITY_STILL)) {
                        stationaryCount++;
                        if (stationaryCount >= 3) {
                            stationaryCount = 0;
                            logger.info("User profoundly stationary. Engaging Heartbeat.");
                            handleStationary(location, mConfig.getStationaryRadius());
                            if (mConfig.isDebugging()) playDebugTone(ToneGenerator.Tone.LONG_BEEP);
                            setPace(false);

                            continue; // CRITICAL FIX: 'continue', not 'return'.
                        }
                    } else {
                        stationaryCount = 0;
                    }

                    if (lastLocation != null) {
                        float distance = location.distanceTo(lastLocation);
                        int dynamicFilter = calculateDynamicDistanceFilter(speed);
                        if (distance < dynamicFilter) {
                            logger.debug("Elastic Filter: Ignored {}m movement. Dynamic limit is {}m.", distance, dynamicFilter);
                            continue;
                        }
                    }

                    if (mConfig.isDebugging()) playDebugTone(ToneGenerator.Tone.BEEP);
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
            desiredInterval = baseInterval; 
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
                logger.info("GPS suspended. Deploying Hardware Displacement Shield.");

                LocationRequest sentryRequest = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15 * 60 * 1000)
                        .setMinUpdateIntervalMillis(5 * 60 * 1000)
                        .setMinUpdateDistanceMeters(mConfig.getStationaryRadius())
                        .build();

                mFusedLocationClient.requestLocationUpdates(sentryRequest, mLocationCallback, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            handleSecurityException(e);
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
            return Priority.PRIORITY_LOW_POWER;
        }
        if (accuracy >= 100) {
            return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        }
        if (accuracy >= 0) {
            return Priority.PRIORITY_HIGH_ACCURACY;
        }
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
        if (speed < 100.0f) {
            float roundedSpeed = (Math.round(speed / 5.0f) * 5.0f);
            int dynamicFilter = (int) Math.pow(roundedSpeed, 2) + baseFilter;
            return Math.min(dynamicFilter, 1000);
        }
        return baseFilter;
    }
}