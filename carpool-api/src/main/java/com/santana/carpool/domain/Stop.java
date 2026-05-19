package com.santana.carpool.domain;

public record Stop(String id, String label, StopType type, String addressOrPostalCode, GeoPoint coordinates) {
    public Stop {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Stop id cannot be blank.");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Stop label cannot be blank.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Stop type is required.");
        }
        if (addressOrPostalCode == null || addressOrPostalCode.isBlank()) {
            throw new IllegalArgumentException("Address or postal code is required.");
        }
        if (coordinates == null) {
            throw new IllegalArgumentException("Coordinates are required.");
        }
    }
}
