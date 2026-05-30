package com.santana.carpool.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RouteRequestDto(
        @NotBlank(message = "country is required") String country,
        @NotBlank(message = "driverName is required") String driverName,
        @NotBlank(message = "driverPostalCode is required") String driverPostalCode,
        String driverStreet,
        String driverHouseNumber,
        @NotBlank(message = "officeName is required") String officeName,
        @NotBlank(message = "officePostalCode is required") String officePostalCode,
        String officeStreet,
        String officeHouseNumber,
        String tripType,
        @NotEmpty(message = "At least one colleague is required") List<@Valid ColleagueRequest> colleagues
) {
}
