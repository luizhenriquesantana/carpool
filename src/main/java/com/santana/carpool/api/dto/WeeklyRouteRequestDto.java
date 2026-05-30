package com.santana.carpool.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WeeklyRouteRequestDto(
        @NotBlank(message = "country is required") String country,
        @NotBlank(message = "officeName is required") String officeName,
        @NotBlank(message = "officePostalCode is required") String officePostalCode,
        String officeStreet,
        String officeHouseNumber,
        @NotEmpty(message = "At least one member is required") List<@Valid MemberRequest> members,
        List<@Valid DayRequest> days
) {
}
