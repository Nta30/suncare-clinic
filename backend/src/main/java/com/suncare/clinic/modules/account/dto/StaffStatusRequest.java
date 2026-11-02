package com.suncare.clinic.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StaffStatusRequest(@NotBlank @Pattern(regexp = "ACTIVE|LOCKED") String status) { }
