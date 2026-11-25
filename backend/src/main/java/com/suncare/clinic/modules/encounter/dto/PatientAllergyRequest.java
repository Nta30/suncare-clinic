package com.suncare.clinic.modules.encounter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatientAllergyRequest(
        Long allergenId, @Size(max = 1000) String otherNote,
        @Size(max = 1000) String reaction,
        @NotBlank @Pattern(regexp = "UNKNOWN|MILD|MODERATE|SEVERE") String severity,
        @NotNull Boolean isActive, String version) {}
