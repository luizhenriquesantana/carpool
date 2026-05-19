package com.santana.carpool.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "savedPostalCodes")
public record SavedPostalCode(
        @Id
        String id,
        String userId,
        String label,
        String postalCode,
        String country
) {
    public SavedPostalCode {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label is required.");
        }
        if (postalCode == null || postalCode.isBlank()) {
            throw new IllegalArgumentException("Postal code is required.");
        }
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("Country is required.");
        }
    }
}
