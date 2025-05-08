package com.wrdhrd.ActivityRecognition;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.provider.ProviderDelegate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ActivityTransitionService {
    private static final String TAG = "ActivityTransition";//ActivityTransitionDetectionService.class.getSimpleName();
    private static final String P_NAME = " com.wrdhrd";
    private static final String DETECTED_ACTIVITY_UPDATE = P_NAME + ".DETECTED_ACTIVITY_TRANSITION_UPDATE";
    private Integer PROVIDER_ID;
    private Context mContext;
    private Config mConfig;
    private ProviderDelegate mDelegate;
    private List<ActivityTransition> activityTransitionList;

    private PendingIntent mPendingIntent;
    private DetectedActivity lastActivity = new DetectedActivity(DetectedActivity.UNKNOWN, 100);

    public ActivityTransitionService(Context context){
        this.mContext = context;
    }
    public void setDelegate(ProviderDelegate delegate) {
        mDelegate = delegate;
    }

    public void setProperties(Config config, Integer provider){
        this.mConfig = config;
        this.PROVIDER_ID = provider;
    }

    protected void unregisterReceiver (BroadcastReceiver receiver) {
        mContext.unregisterReceiver(receiver);
    }

    protected Intent registerReceiver (BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                Field receiverExportedField = Context.class.getField("RECEIVER_NOT_EXPORTED");
                int receiverExported = receiverExportedField.getInt(null);
                return mContext.registerReceiver(receiver, filter, receiverExported);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

        }
        return mContext.registerReceiver(receiver, filter);
    }

    public void onCreate() {
        // TODO: Add activity transitions to track.
        activityTransitionList = new ArrayList<>();
        // VEHICLE
        activityTransitionList.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());

        // BICYCLE
        activityTransitionList.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.ON_BICYCLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());

        // WALKING
        activityTransitionList.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());

        // STILL
        activityTransitionList.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());

        // Registering our BroadcastReceiver
        final int flag =  Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
        Intent detectedActivitiesIntent = new Intent(DETECTED_ACTIVITY_UPDATE);
        detectedActivitiesIntent.setPackage(mContext.getPackageName());
        mPendingIntent = PendingIntent.getBroadcast(mContext, 9006, detectedActivitiesIntent, flag);
        registerReceiver(receiver, new IntentFilter(DETECTED_ACTIVITY_UPDATE));
    }

    public void startActivity() {
        ActivityTransitionRequest request = new ActivityTransitionRequest(activityTransitionList);
        Task<Void> task = ActivityRecognition.getClient(mContext)
                .requestActivityTransitionUpdates(request, mPendingIntent);
        task.addOnSuccessListener(result -> {
            Log.i(TAG, "Successfully requested activity updates");

        });
        task.addOnFailureListener(e -> Log.e(TAG, "Requesting activity updates failed to start", e));
    }

    public void stopActivity() {
        ActivityRecognition.getClient(mContext).removeActivityTransitionUpdates(mPendingIntent)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG,"Transitions successfully unregistered.");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG,"Transitions could not registered.");
                    }
                });
    }

    public void onDestroy() {
        unregisterReceiver(receiver);
    }

    protected void handleActivity(DetectedActivity activity) {
        if (mDelegate != null) {
            mDelegate.onActivity(new BackgroundActivity(PROVIDER_ID, activity));
        }
    }

    private String getActivityName(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "In Vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "On Bicycle";
            case DetectedActivity.ON_FOOT:
                return "On Foot";
            case DetectedActivity.RUNNING:
                return "Running";
            case DetectedActivity.STILL:
                return "Still";
            case DetectedActivity.TILTING:
                return "Tilting";
            case DetectedActivity.WALKING:
                return "Walking";
            default:
                return "Unknown";
        }
    }

    private static String toTransitionType(int transitionType) {
        switch (transitionType) {
            case ActivityTransition.ACTIVITY_TRANSITION_ENTER:
                return "ENTER";
            case ActivityTransition.ACTIVITY_TRANSITION_EXIT:
                return "EXIT";
            default:
                return "UNKNOWN";
        }
    }

    protected void showDebugToast (String text) {
        if (mConfig.isDebugging()) {
            Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
        }
    }



    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!TextUtils.equals(DETECTED_ACTIVITY_UPDATE, intent.getAction())) {
                return;
            }

            ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
            int activityType = lastActivity.getType();
            if (result != null){
                for (ActivityTransitionEvent event : result.getTransitionEvents()) {

                    String info = "Transition: " + getActivityName(event.getActivityType()) +
                            " (" + toTransitionType(event.getTransitionType()) + ")" + "   ";

                    Log.i(TAG,info);
                    activityType = event.getActivityType();
                    showDebugToast(info);
                }

                DetectedActivity activity = new DetectedActivity(activityType, 100);
                handleActivity(activity);
            }else{
                Log.i(TAG,"result was null");
            }
        }


    };
}
