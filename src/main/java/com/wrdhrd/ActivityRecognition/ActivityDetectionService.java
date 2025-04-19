package com.wrdhrd.ActivityRecognition;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.provider.ProviderDelegate;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class ActivityDetectionService {
    private static final String TAG = ActivityDetectionService.class.getSimpleName();
    private static final String P_NAME = " com.wrdhrd";
    private static final String DETECTED_ACTIVITY_UPDATE = P_NAME + ".DETECTED_ACTIVITY_UPDATE";
    private Integer PROVIDER_ID;
    private Context mContext;
    private Config mConfig;
    private ProviderDelegate mDelegate;

    private PendingIntent mPendingIntent;
    private ActivityRecognitionClient mActivityRecognitionClient;
    private DetectedActivity lastActivity = new DetectedActivity(DetectedActivity.UNKNOWN, 100);

    public ActivityDetectionService(Context context,Config config,ProviderDelegate delegate,Integer provider) {
        this.mContext = context;
        this.mConfig = config;
        this.mDelegate = delegate;
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

    public void startActivity() {
        mActivityRecognitionClient = ActivityRecognition.getClient(mContext);

        // Registering our BroadcastReceiver
        final int flag =  Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_CANCEL_CURRENT;
        Intent detectedActivitiesIntent = new Intent(DETECTED_ACTIVITY_UPDATE);
        detectedActivitiesIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 9004, detectedActivitiesIntent, flag);

        registerReceiver(receiver, new IntentFilter(DETECTED_ACTIVITY_UPDATE));

        Task<Void> task = mActivityRecognitionClient.requestActivityUpdates(
                10000, // Detect activity every 10 seconds
                mPendingIntent
        );
        task.addOnSuccessListener(result -> Log.i("ActivityRecognition", "Successfully requested activity updates"));
        task.addOnFailureListener(e -> Log.e("ActivityRecognition", "Requesting activity updates failed to start", e));
    }

    public void removeActivityUpdatesButtonHandler() {
        Task<Void> task = mActivityRecognitionClient.removeActivityUpdates(
                mPendingIntent);
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d("ActivityRecognition","Removed activity recognition");
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("ActivityRecognition","Error on remove activity recognition");
            }
        });

        unregisterReceiver(receiver);
    }

    public void stopActivity() {
        removeActivityUpdatesButtonHandler();
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

    public  DetectedActivity getProbableActivity(ArrayList<DetectedActivity> detectedActivities) {
        int highestConfidence = 0;
        DetectedActivity mostLikelyActivity = new DetectedActivity(0, DetectedActivity.UNKNOWN);

        for(DetectedActivity da: detectedActivities) {
            if(da.getType() != DetectedActivity.TILTING && da.getType() != DetectedActivity.UNKNOWN) {
                int confidence = da.getConfidence();
                int activityType = da.getType();
                String activityName = getActivityName(activityType);
                String info = "Received a Activity: " + activityName + ", Confidence: " + confidence + "%";
                Log.w("ActivityRecognition", info);
                if (highestConfidence < da.getConfidence()) {
                    highestConfidence = da.getConfidence();
                    mostLikelyActivity = da;
                }
            }
        }
        return mostLikelyActivity;
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ActivityRecognitionResult.hasResult(intent)) {
                ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
                ArrayList<DetectedActivity> detectedActivities = (ArrayList) result.getProbableActivities();

                //Find the activity with the highest percentage
                lastActivity = getProbableActivity(detectedActivities);

                //logger.debug("Detected activity={} confidence={}", BackgroundActivity.getActivityString(lastActivity.getType()), lastActivity.getConfidence());

                handleActivity(lastActivity);
                // Get the confidence and type of activity
                int confidence = lastActivity.getConfidence();
                int activityType = lastActivity.getType();
                String activityName = getActivityName(activityType);
                String info = "Detected activity: " + activityName + ", Confidence: " + confidence + "%";
                Log.d("ActivityRecognition", info);
                //showDebugToast(info);
            }
        }


    };
}
