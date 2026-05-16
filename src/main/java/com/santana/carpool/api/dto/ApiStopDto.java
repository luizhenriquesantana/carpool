package com.santana.carpool.api.dto;

public record ApiStopDto(
        String id,
        String name,
        String eircode,
        double latitude,
        double longitude
) {
}
