package com.suncare.clinic.modules.scheduling.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DraftScheduleRequest(
        @NotNull Long departmentId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
) { }
