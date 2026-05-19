package com.santana.carpool.api.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberRequest(
        @NotBlank(message = "member name is required") String name,
        @NotBlank(message = "member postalCode is required") String postalCode,
        Boolean canDrive
) {
}

