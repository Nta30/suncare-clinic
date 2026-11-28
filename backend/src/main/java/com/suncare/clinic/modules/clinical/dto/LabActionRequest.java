package com.suncare.clinic.modules.clinical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LabActionRequest(@Positive long roomId, @NotBlank @Size(max = 24) String version,
        boolean patientPresent) { }
