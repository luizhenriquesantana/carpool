package com.santana.carpool.routing;

import com.santana.carpool.domain.GeoPoint;
import com.santana.carpool.domain.Stop;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NearestNeighborRoutePlanner {
    public List<Stop> planPickupOrder(Stop driverStart, List<Stop> pickups) {
        if (driverStart == null) {
            throw new IllegalArgumentException("driverStart is required.");
        }
        if (pickups == null) {
            throw new IllegalArgumentException("pickups list is required.");
        }

        List<Stop> remaining = new ArrayList<>(pickups);
        List<Stop> ordered = new ArrayList<>();

        Stop current = driverStart;
        while (!remaining.isEmpty()) {
            Stop nearest = findNearest(current.coordinates(), remaining);
            ordered.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }

        return ordered;
    }

    private Stop findNearest(GeoPoint from, List<Stop> candidates) {
        Stop nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Stop candidate : candidates) {
            double distance = haversineKm(from, candidate.coordinates());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    public double haversineKm(GeoPoint a, GeoPoint b) {
        double earthRadiusKm = 6371.0;

        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());

        double sinDLat = Math.sin(dLat / 2.0);
        double sinDLon = Math.sin(dLon / 2.0);

        double value = sinDLat * sinDLat
                + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;

        double c = 2.0 * Math.atan2(Math.sqrt(value), Math.sqrt(1.0 - value));
        return earthRadiusKm * c;
    }
}
