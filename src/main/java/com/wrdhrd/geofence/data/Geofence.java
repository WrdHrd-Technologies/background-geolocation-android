package com.wrdhrd.geofence.data;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class Geofence {
    private long id;
    private String name;
    private String polygon;
    private List<double[]> coordinates;
    private double minLatitude;
    private double maxLatitude;
    private double minLongitude;
    private double maxLongitude;

    public Geofence(int id, String name, String polygon,double minLatitude,double maxLatitude,double minLongitude,double maxLongitude) {
        this.id = id;
        this.name = name;
        this.polygon = polygon;
        this.minLatitude = minLatitude;
        this.maxLatitude = maxLatitude;
        this.minLongitude = minLongitude;
        this.maxLongitude = maxLongitude;
    }

    public long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public List<double[]> getCoordinates() {
        if(this.coordinates == null) {
            try {
                List<double[]> coordinates = new ArrayList<>();
                JSONArray jsonArray = new JSONArray(this.polygon);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONArray jsonPoint = jsonArray.getJSONArray(i);
                    double[] point = new double[2];
                    point[0] = jsonPoint.getDouble(0);
                    point[1] = jsonPoint.getDouble(1);
                    coordinates.add(point);
                }

                this.coordinates = coordinates;
            }
            catch(Exception ignored){

            }
        }
        return this.coordinates;
    }

    public String getPolygon() {
        return polygon;
    }

    public double getMaxLongitude() {
        return maxLongitude;
    }

    public double getMinLongitude() {
        return minLongitude;
    }

    public double getMaxLatitude() {
        return maxLatitude;
    }

    public double getMinLatitude() {
        return minLatitude;
    }
}
