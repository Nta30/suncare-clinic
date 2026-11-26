package com.suncare.clinic.modules.encounter.dto;

import jakarta.validation.constraints.*;

public record DiagnosisRequest(Long diagnosisConceptId,
        @NotBlank @Size(max = 1000) String description,
        @NotBlank @Pattern(regexp = "PRELIMINARY|FINAL") String diagnosisType,
        @NotNull Boolean isPrimary, String version) {}
