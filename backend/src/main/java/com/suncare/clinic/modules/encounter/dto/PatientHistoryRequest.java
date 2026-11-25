package com.suncare.clinic.modules.encounter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatientHistoryRequest(
        @NotBlank @Pattern(regexp = "PERSONAL|FAMILY|SURGERY") String historyType,
        @NotBlank @Size(max = 2000) String description,
        @NotNull Boolean isActive, String version) {}
