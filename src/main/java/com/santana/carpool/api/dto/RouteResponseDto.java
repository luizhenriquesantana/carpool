package com.santana.carpool.api.dto;

import java.util.List;
import java.util.Map;

public record RouteResponseDto(
        String tripType,
        ApiStopDto driver,
        ApiStopDto office,
        List<ApiStopDto> pickupOrder,
        List<ApiStopDto> dropoffOrder,
        double totalEstimatedKm,
        long totalEstimatedDurationMinutes,
        Map<String, Long> cacheStats
) {
}
