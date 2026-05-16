package com.santana.carpool.api.dto;

import java.util.List;

public record WeeklyRouteRequestDto(
        String country,
        String officeName,
        String officePostalCode,
        List<MemberRequest> members,
        List<DayRequest> days
) {
}
