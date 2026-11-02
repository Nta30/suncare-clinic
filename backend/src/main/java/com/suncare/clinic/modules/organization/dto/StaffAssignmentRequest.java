package com.suncare.clinic.modules.organization.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record StaffAssignmentRequest(@NotNull @Positive Long staffId,
                                     @NotNull @Positive Long departmentId,
                                     @NotNull LocalDateTime validFrom,
                                     LocalDateTime validTo,
                                     Boolean primary) { }
