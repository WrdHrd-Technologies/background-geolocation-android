package com.wrdhrd.geofence;

import android.content.Context;
import android.database.Cursor;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.DAOFactory;
import com.wrdhrd.geofence.data.Geofence;
import com.wrdhrd.geofence.data.GeofenceDAO;
import java.util.List;
import java.util.Objects;

public class GeofenceHelper {
    Context mContext;
    private GeofenceDAO mGeofenceDAO;
    private Geofence lastGeofence;
    private boolean firstCheck = true;
    public GeofenceHelper(Context context){
        mContext = context;
        mGeofenceDAO = DAOFactory.createGeofenceDAO(context);
    }

    public boolean isPointInPolygon(double lat, double lon, List<double[]> polygon) {
        int intersectCount = 0;
        for (int i = 0; i < polygon.size(); i++) {
            double[] point1 = polygon.get(i);
            double[] point2 = polygon.get((i + 1) % polygon.size());

            // Check if the ray crosses the edge of the polygon
            if ((point1[0] > lon) != (point2[0] > lon)) {
                double intersection = (point2[1] - point1[1]) * (lon - point1[0]) / (point2[0] - point1[0]) + point1[1];
                if (intersection > lat) {
                    intersectCount++;
                }
            }
        }

        // If intersect count is odd, point is inside the polygon
        return (intersectCount % 2) == 1;
    }
    private boolean isPointInBoundingBox(BackgroundLocation location, Geofence geofence) {
        double lat = location.getLatitude(),lon = location.getLongitude();
        double minLat = geofence.getMinLatitude(), maxLat = geofence.getMaxLatitude(), minLon = geofence.getMinLongitude(),maxLon = geofence.getMaxLongitude();

        // Check if the point is within the bounding box
        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
    }

    public BackgroundLocation checkGeofence(BackgroundLocation location){
        Geofence geofence = null;
        // If User is still, no need to check for geofence, use the last geofence only if this is not first location
        if(Objects.equals(location.getActivity(), "Still") && !firstCheck){
            geofence = lastGeofence;
        }
        //Check for the last geofence
        else if(lastGeofence != null){
            if (isPointInBoundingBox(location,lastGeofence)) {
                if (isPointInPolygon(location.getLatitude(),location.getLongitude(),lastGeofence.getCoordinates())) {
                    geofence = lastGeofence;
                }
            }
        }
        if(geofence == null) {
            Cursor cursor = mGeofenceDAO.getCursor();
            while (cursor.moveToNext()) {
                Geofence currGeofence = mGeofenceDAO.hydrate(cursor);

                // First, check if the point is in the bounding box
                if (isPointInBoundingBox(location, currGeofence)) {
                    // Then check if the point is inside the polygon
                    if (isPointInPolygon(location.getLatitude(), location.getLongitude(), currGeofence.getCoordinates())) {
                        geofence = currGeofence;
                        break;
                    }
                }
            }
            cursor.close();
        }

        if(geofence != null){
            location.setGeofenceName(geofence.getName());
            location.setGeofenceId(geofence.getId());
        }
        lastGeofence = geofence;

        return location;
    }
}
