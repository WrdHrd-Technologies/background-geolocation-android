package com.marianhello.bgloc.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import com.marianhello.logging.LoggerManager;

class HeartbeatManager {
    private static final String ACTION_HEARTBEAT = "com.marianhello.bgloc.service.ACTION_HEARTBEAT";
    private final Context mContext;
    private final AlarmManager mAlarmManager;
    private PendingIntent mHeartbeatIntent;
    private final org.slf4j.Logger logger;
    private long mIntervalMillis = 5 * 60 * 1000;
    private boolean mIsRunning = false;

    public HeartbeatManager(Context context) {
        mContext = context;
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        logger = LoggerManager.getLogger(HeartbeatManager.class);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mContext.registerReceiver(heartbeatReceiver, new IntentFilter(ACTION_HEARTBEAT), Context.RECEIVER_NOT_EXPORTED);
        } else {
            mContext.registerReceiver(heartbeatReceiver, new IntentFilter(ACTION_HEARTBEAT));
        }
    }

    public void setInterval(long intervalMillis) {
        long floorMillis = Math.max(intervalMillis, 60000); 

        if (this.mIntervalMillis != floorMillis) {
            this.mIntervalMillis = floorMillis;
            logger.info("Heartbeat interval updated to: {}ms", this.mIntervalMillis);
            
            if (mIsRunning) {
                // Cancel the old alarm and schedule a new one immediately
                stop();
                start();
            }
        }
    }

    public void start() {
        mIsRunning = true;
        logger.info("Starting stationary heartbeat. Interval: {}ms", mIntervalMillis);
        scheduleNextBeat();
    }

    public void stop() {
        mIsRunning = false;
        logger.info("Stopping stationary heartbeat.");
        if (mHeartbeatIntent != null) {
            mAlarmManager.cancel(mHeartbeatIntent);
            mHeartbeatIntent = null;
        }
    }

    public void destroy() {
        stop();
        try {
            mContext.unregisterReceiver(heartbeatReceiver);
        } catch (IllegalArgumentException e) {
            // Already unregistered
        }
    }

    private void scheduleNextBeat() {
        Intent intent = new Intent(ACTION_HEARTBEAT);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        mHeartbeatIntent = PendingIntent.getBroadcast(mContext, 0, intent, flags);

        long triggerAtMillis = System.currentTimeMillis() + mIntervalMillis;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mAlarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, mHeartbeatIntent);
            } else {
                mAlarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, mHeartbeatIntent);
            }
            logger.debug("Inexact AlarmManager successfully armed for heartbeat.");
        } catch (Exception e) {
            logger.error("Failed to schedule heartbeat.", e);
        }

        // // Wake the CPU even in Doze mode
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        //     mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ]triggerAtMillis, mHeartbeatIntent);
        // } else {
        //     mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, mHeartbeatIntent);
        // }
    }

    private final BroadcastReceiver heartbeatReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logger.debug("Heartbeat fired!");
            
            Intent serviceIntent = new Intent(context, com.marianhello.bgloc.service.LocationServiceImpl.class);
            serviceIntent.putExtra("command", CommandId.HEARTBEAT_PING);
            
            try {
                context.startService(serviceIntent);
            } catch (IllegalStateException e) {
                logger.error("Failed to route heartbeat to service", e);
            }

            scheduleNextBeat();
        }
    };
}