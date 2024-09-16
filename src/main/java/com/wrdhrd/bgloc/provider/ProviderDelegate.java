package com.wrdhrd.bgloc.provider;

import com.wrdhrd.bgloc.PluginException;
import com.wrdhrd.bgloc.data.BackgroundActivity;
import com.wrdhrd.bgloc.data.BackgroundLocation;

public interface ProviderDelegate {
    void onLocation(BackgroundLocation location);
    void onStationary(BackgroundLocation location);
    void onActivity(BackgroundActivity activity);
    void onError(PluginException error);
}
