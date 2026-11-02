package com.suncare.clinic.modules.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RoomRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "CONSULTATION|LAB") String roomType,
        @Positive long departmentId,
        @NotNull Boolean isActive,
        String version) { }
