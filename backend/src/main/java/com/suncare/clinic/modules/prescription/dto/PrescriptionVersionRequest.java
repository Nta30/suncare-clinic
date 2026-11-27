package com.suncare.clinic.modules.prescription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrescriptionVersionRequest(@NotBlank @Size(max = 24) String version) {}
