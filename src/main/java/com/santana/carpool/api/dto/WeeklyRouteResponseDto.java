package com.santana.carpool.api.dto;

import java.util.List;
import java.util.Map;

public record WeeklyRouteResponseDto(
        ApiStopDto office,
        List<DailyRoutePlanDto> days,
        Map<String, Integer> driverAssignments,
        Map<String, Long> cacheStats
) {
}
