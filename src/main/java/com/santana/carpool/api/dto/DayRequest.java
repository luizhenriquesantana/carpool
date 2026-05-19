package com.santana.carpool.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DayRequest(
        @NotBlank(message = "day is required") String day,
        String fixedDriverName,
        String tripType
) {
}
