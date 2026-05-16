package com.santana.carpool.domain;

import java.util.List;

public record RouteRequest(Stop driverStart, List<Stop> pickups, Stop office) {
    public RouteRequest {
        if (driverStart == null) {
            throw new IllegalArgumentException("Driver start is required.");
        }
        if (pickups == null) {
            throw new IllegalArgumentException("Pickup list is required.");
        }
        if (office == null) {
            throw new IllegalArgumentException("Office stop is required.");
        }
    }
}
