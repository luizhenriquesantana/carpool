package com.santana.carpool.api.dto;

public record ApiStopDto(
        String id,
        String name,
        String postalCode,
        double latitude,
        double longitude
) {
}
