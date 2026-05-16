package com.santana.carpool.api.dto;

import java.util.List;

public record DailyRoutePlanDto(
        String day,
        String tripType,
        ApiStopDto driver,
        List<ApiStopDto> pickupOrder,
        List<ApiStopDto> dropoffOrder,
        double totalEstimatedKm,
        long totalEstimatedDurationMinutes
) {
}
