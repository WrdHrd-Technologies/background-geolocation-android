package com.wrdhrd.geofence.data.sqlite;

import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.COMMA_SEP;
import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.REAL_TYPE;
import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.TEXT_TYPE;

import android.provider.BaseColumns;

public class SQLiteGeofenceContract {
    // To prevent someone from accidentally instantiating the contract class,
    // give it an empty constructor.
    public SQLiteGeofenceContract() {}

    /* Inner class that defines the table contents */
    public static abstract class GeofenceEntry implements BaseColumns {
        public static final String TABLE_NAME = "geofence";
        public static final String COLUMN_NAME_NULLABLE = "NULLHACK";
        public static final String COLUMN_NAME_NAME = "name";
        public static final String COLUMN_NAME_COORDINATES = "coordinates";
        public static final String COLUMN_NAME_MIN_LATITUDE = "min_latitude";
        public static final String COLUMN_NAME_MAX_LATITUDE = "max_latitude";
        public static final String COLUMN_NAME_MIN_LONGITUDE = "min_longitude";
        public static final String COLUMN_NAME_MAX_LONGITUDE = "max_longitude";

        public static final String SQL_CREATE_GEOFENCE_TABLE =
                "CREATE TABLE " + SQLiteGeofenceContract.GeofenceEntry.TABLE_NAME + " (" +
                        SQLiteGeofenceContract.GeofenceEntry._ID + " INTEGER PRIMARY KEY," +
                        SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_NAME + TEXT_TYPE + COMMA_SEP +
                        SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_COORDINATES + TEXT_TYPE + COMMA_SEP +
                        SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MIN_LONGITUDE + REAL_TYPE + COMMA_SEP +
                        SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MAX_LONGITUDE + REAL_TYPE + COMMA_SEP +
                        SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MIN_LATITUDE + REAL_TYPE + COMMA_SEP +
                        SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MAX_LATITUDE + REAL_TYPE + COMMA_SEP +
                        " )";

        public static final String SQL_DROP_GEOFENCE_TABLE =
                "DROP TABLE IF EXISTS " + SQLiteGeofenceContract.GeofenceEntry.TABLE_NAME;

        /**
         * A projection of all columns in the items table
         */
        public static final String[] PROJECTION_ALL = {
                _ID,
                COLUMN_NAME_NAME,
                COLUMN_NAME_COORDINATES,
                COLUMN_NAME_MIN_LATITUDE,
                COLUMN_NAME_MAX_LATITUDE,
                COLUMN_NAME_MIN_LONGITUDE,
                COLUMN_NAME_MAX_LONGITUDE
        };
    }
}
