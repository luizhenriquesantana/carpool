package com.santana.carpool.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ColleagueRequest(
        @NotBlank(message = "colleague name is required") String name,
        @NotBlank(message = "colleague postalCode is required") String postalCode
) {
}
