package com.santana.carpool.api.dto;

public record MemberRequest(String name, String postalCode, Boolean canDrive) {
}
