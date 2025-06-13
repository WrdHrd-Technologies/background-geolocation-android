package com.wrdhrd.geofence.data;
import android.database.Cursor;

import java.util.Collection;

public interface GeofenceDAO {
    public void persistGeofence(Collection<Geofence> geofence);
    public Collection<Geofence> getAllGeofence();
    public void deleteGeofenceById(long id);
    public int deleteAllGeofence();
    public Cursor getCursor();
    public Geofence hydrate(Cursor c);
}
