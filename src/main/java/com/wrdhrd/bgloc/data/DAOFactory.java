package com.wrdhrd.bgloc.data;

import android.content.Context;

import com.wrdhrd.bgloc.data.provider.ContentProviderLocationDAO;
import com.wrdhrd.bgloc.data.sqlite.SQLiteLocationDAO;
import com.wrdhrd.bgloc.data.sqlite.SQLiteConfigurationDAO;

public abstract class DAOFactory {
    public static LocationDAO createLocationDAO(Context context) {
        return new ContentProviderLocationDAO(context);
    }

    public static ConfigurationDAO createConfigurationDAO(Context context) {
        return new SQLiteConfigurationDAO(context);
    }
}
