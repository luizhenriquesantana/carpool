package com.santana.carpool.api.dto;

import java.util.List;

public record RouteRequestDto(
        String country,
        String driverName,
        String driverPostalCode,
        String officeName,
        String officePostalCode,
        String tripType,
        List<ColleagueRequest> colleagues
) {
}
