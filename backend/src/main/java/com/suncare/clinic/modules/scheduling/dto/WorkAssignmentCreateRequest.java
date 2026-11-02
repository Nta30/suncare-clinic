package com.suncare.clinic.modules.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record WorkAssignmentCreateRequest(
        @NotNull Long staffId,
        @NotNull Long roomId,
        @NotNull Long specialtyId,
        @NotNull Long shiftId,
        @NotNull LocalDate workDate,
        @NotNull @Positive Integer capacity
) { }
