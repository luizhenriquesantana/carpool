package com.santana.carpool.domain;

import java.util.List;

public record RoutePlan(List<Stop> orderedStops, List<RouteLeg> legs, double totalDistanceKm, int totalDurationMinutes) {
    public RoutePlan {
        if (orderedStops == null || orderedStops.isEmpty()) {
            throw new IllegalArgumentException("Ordered stops are required.");
        }
        if (legs == null) {
            throw new IllegalArgumentException("Route legs list is required.");
        }
        if (totalDistanceKm < 0) {
            throw new IllegalArgumentException("Total distance cannot be negative.");
        }
        if (totalDurationMinutes < 0) {
            throw new IllegalArgumentException("Total duration cannot be negative.");
        }
    }
}
