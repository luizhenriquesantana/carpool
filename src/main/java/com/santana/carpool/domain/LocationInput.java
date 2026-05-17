package com.santana.carpool.domain;

public record LocationInput(String label, String addressOrPostalCode) {
    public LocationInput {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label is required.");
        }
        if (addressOrPostalCode == null || addressOrPostalCode.isBlank()) {
            throw new IllegalArgumentException("Address or postal code cannot be blank.");
        }
    }
}
