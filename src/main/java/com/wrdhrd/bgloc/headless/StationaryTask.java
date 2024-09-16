package com.wrdhrd.bgloc.headless;

import android.os.Bundle;

import com.wrdhrd.bgloc.data.BackgroundLocation;

import org.json.JSONException;

public abstract class StationaryTask extends LocationTask {
    public StationaryTask(BackgroundLocation location) {
        super(location);
    }

    @Override
    public String getName() {
        return "stationary";
    }

    @Override
    public Bundle getBundle() {
        Bundle bundle = super.getBundle();

        return bundle;
    }
}
