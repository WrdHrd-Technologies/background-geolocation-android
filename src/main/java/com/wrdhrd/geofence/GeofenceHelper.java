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

    public double distanceToSegment(double lat, double lon, double lat1, double lon1, double lat2, double lon2) {
        double A = lat - lat1;
        double B = lon - lon1;
        double C = lat2 - lat1;
        double D = lon2 - lon1;

        double dot = A * C + B * D;
        double lenSq = C * C + D * D;
        double param = dot / lenSq;

        double xx, yy;

        if (param < 0 || (lat1 == lat2 && lon1 == lon2)) {
            xx = lat1;
            yy = lon1;
        } else if (param > 1) {
            xx = lat2;
            yy = lon2;
        } else {
            xx = lat1 + param * C;
            yy = lon1 + param * D;
        }

        double dx = lat - xx;
        double dy = lon - yy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    boolean isWithinAccuracy(double lat, double lon, List<double[]> polygonVertices, float accuracy) {
        for (int i = 0; i < polygonVertices.size() - 1; i++) {
            double[] vertex1 = polygonVertices.get(i);
            double[] vertex2 = polygonVertices.get(i + 1);

            double distance = distanceToSegment(lat, lon, vertex1[1], vertex1[0], vertex2[1], vertex2[0]);
            if (distance <= accuracy) {
                return true; // Location is within the buffer zone
            }
        }
        return false;
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
    private boolean isPointInBoundingBox(double lat, double lon, Geofence geofence) {
        double minLat = geofence.getMinLatitude(), maxLat = geofence.getMaxLatitude(), minLon = geofence.getMinLongitude(),maxLon = geofence.getMaxLongitude();

        // Check if the point is within the bounding box
        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
    }

    private Geofence checkGeofence(double lat, double lon,float accuracy){
        Geofence geofence = null;
        Cursor cursor = mGeofenceDAO.getCursor();
        while (cursor.moveToNext()) {
          Geofence currGeofence = mGeofenceDAO.hydrate(cursor);

          // First, check if the point is in the bounding box
          if (isPointInBoundingBox(lat,lon, currGeofence)) {
              // Then check if the point is inside the polygon
              if (isPointInPolygon(lat, lon, currGeofence.getCoordinates())) {
                 geofence = currGeofence;
                 break;
              }
              if (isWithinAccuracy(lat, lon, currGeofence.getCoordinates(), accuracy)) {
                geofence = currGeofence;
                break;
              }
          }
        }
        cursor.close();

        return geofence;
    }

    public BackgroundLocation checkGeofence(BackgroundLocation location){
        Geofence geofence = null;
        // If User is still, no need to check for geofence, use the last geofence only if this is not first location
        if(Objects.equals(location.getActivity(), "Still") && !firstCheck){
            geofence = lastGeofence;
        }
        //Check for the last geofence
        else if(lastGeofence != null){
            if (isPointInBoundingBox(location.getLatitude(),location.getLongitude(),lastGeofence)) {
                if (isPointInPolygon(location.getLatitude(),location.getLongitude(),lastGeofence.getCoordinates())) {
                    geofence = lastGeofence;
                }
                if (isWithinAccuracy(location.getLatitude(), location.getLongitude(), lastGeofence.getCoordinates(), location.getAccuracy())) {
                    geofence = lastGeofence;
                }
            }
        }
        if(geofence == null) {
            geofence = checkGeofence(location.getLatitude(), location.getLongitude(),location.getAccuracy());
        }

        if(geofence != null){
            location.setGeofenceName(geofence.getName());
            location.setGeofenceId(geofence.getId());
        }
        lastGeofence = geofence;
        this.firstCheck = false;
        return location;
    }
}
