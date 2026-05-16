package com.santana.carpool.domain;

public record RouteLeg(Stop from, Stop to, double distanceKm, int durationMinutes) {
    public RouteLeg {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Route leg endpoints are required.");
        }
        if (distanceKm < 0) {
            throw new IllegalArgumentException("Distance cannot be negative.");
        }
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("Duration cannot be negative.");
        }
    }
}
