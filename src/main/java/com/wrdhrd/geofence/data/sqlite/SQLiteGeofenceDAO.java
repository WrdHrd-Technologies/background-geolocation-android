package com.wrdhrd.geofence.data.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper;
import com.wrdhrd.geofence.data.GeofenceDAO;
import com.wrdhrd.geofence.data.Geofence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SQLiteGeofenceDAO implements GeofenceDAO {
    private SQLiteDatabase db;

    public SQLiteGeofenceDAO(Context context) {
        SQLiteOpenHelper helper = SQLiteOpenHelper.getHelper(context);
        this.db = helper.getWritableDatabase();
    }

    public SQLiteGeofenceDAO(SQLiteDatabase db) {
        this.db = db;
    }

    /**
     * Persist geofence into database
     * @param geofence
     * @return rowId or -1 when error occured
     */
    private void persistGeofence(Geofence geofence) {
        ContentValues values = getContentValues(geofence);
        db.insertOrThrow(SQLiteGeofenceContract.GeofenceEntry.TABLE_NAME, SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_NULLABLE, values);
    }

    /**
     * Persist geofences into database
     * @param geofences
     */
    public void persistGeofence(Collection<Geofence> geofences) {
        db.beginTransaction();
        for(Geofence geofence: geofences) {
            persistGeofence(geofence);
        }
        db.endTransaction();
    }

    /**
     * Delete Geofence by given id
     *
     * @param id
     */
    public void deleteGeofenceById(long id) {
        String whereClause = SQLiteGeofenceContract.GeofenceEntry._ID + " = ?";
        String[] whereArgs = { String.valueOf(id) };

        db.delete(SQLiteGeofenceContract.GeofenceEntry.TABLE_NAME, whereClause, whereArgs);
    }

    /**
     * Delete all Geofences
     *
     */
    public int deleteAllGeofence() {

        return db.delete(SQLiteGeofenceContract.GeofenceEntry.TABLE_NAME, null, null);
    }

    private ContentValues getContentValues(Geofence geofence) {

        ContentValues values = new ContentValues();
        values.put(SQLiteGeofenceContract.GeofenceEntry._ID, geofence.getId());
        values.put(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_NAME, geofence.getName());
        values.put(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_COORDINATES, geofence.getPolygon());
        values.put(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MIN_LATITUDE, geofence.getMinLatitude());
        values.put(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MAX_LATITUDE, geofence.getMaxLatitude());
        values.put(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MIN_LONGITUDE, geofence.getMinLongitude());
        values.put(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MAX_LONGITUDE, geofence.getMaxLongitude());
        return values;
    }

    public Geofence hydrate(Cursor c) {
        int id = c.getInt(c.getColumnIndex(SQLiteGeofenceContract.GeofenceEntry._ID));
        String name = c.getString(c.getColumnIndex(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_NAME));
        double minLatitude = c.getDouble(c.getColumnIndex(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MIN_LATITUDE));
        double maxLatitude = c.getDouble(c.getColumnIndex(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MAX_LATITUDE));
        double minLongitude = c.getDouble(c.getColumnIndex(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MIN_LONGITUDE));
        double maxLongitude = c.getDouble(c.getColumnIndex(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MAX_LONGITUDE));
        String polygon = c.getString(c.getColumnIndex(SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_COORDINATES));
        Geofence geofence = new Geofence(id,name,polygon,minLatitude,maxLatitude,minLongitude,maxLongitude);
        return geofence;
    }

    private Collection<Geofence> getGeofence(String whereClause, String[] whereArgs) {
        Collection<Geofence> geofences = new ArrayList<Geofence>();

        String[] columns = queryColumns();
        String groupBy = null;
        String having = null;
        String orderBy = SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_NAME + " ASC";
        Cursor cursor = null;

        try {
            cursor = db.query(
                    SQLiteGeofenceContract.GeofenceEntry.TABLE_NAME,  // The table to query
                    columns,                   // The columns to return
                    whereClause,               // The columns for the WHERE clause
                    whereArgs,                 // The values for the WHERE clause
                    groupBy,                   // don't group the rows
                    having,                    // don't filter by row groups
                    orderBy                    // The sort order
            );
            while (cursor.moveToNext()) {
                geofences.add(hydrate(cursor));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return geofences;
    }

    public Cursor getCursor() {

        String[] columns = queryColumns();
        String groupBy = null;
        String having = null;
        String orderBy = SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_NAME + " ASC";
        Cursor cursor  = db.query(
                    SQLiteGeofenceContract.GeofenceEntry.TABLE_NAME,  // The table to query
                    columns,                   // The columns to return
                    "",                        // The columns for the WHERE clause
                    null,                      // The values for the WHERE clause
                    groupBy,                   // don't group the rows
                    having,                    // don't filter by row groups
                    orderBy                    // The sort order
            );

        return cursor;
    }

    public Collection<Geofence> getAllGeofence() {
        return getGeofence(null, null);
    }
    private String[] queryColumns() {
        String[] columns = {
                SQLiteGeofenceContract.GeofenceEntry._ID,
                SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_NAME,
                SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_COORDINATES,
                SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MIN_LATITUDE,
                SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MAX_LATITUDE,
                SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MIN_LONGITUDE,
                SQLiteGeofenceContract.GeofenceEntry.COLUMN_NAME_MAX_LONGITUDE
        };

        return columns;
    }
}
