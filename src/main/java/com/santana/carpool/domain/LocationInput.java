package com.santana.carpool.domain;

public record LocationInput(String label, String addressOrEircode) {
    public LocationInput {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label cannot be blank.");
        }
        if (addressOrEircode == null || addressOrEircode.isBlank()) {
            throw new IllegalArgumentException("Address or Eircode cannot be blank.");
        }
    }
}
