package com.suncare.clinic.modules.scheduling.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record WorkAssignmentCreateRequest(
        @NotNull Long staffId,
        @NotNull Long roomId,
        @NotNull Long specialtyId,
        Long shiftId,
        LocalDate workDate,
        @Positive Integer capacity,
        String mode,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 62) List<@NotNull LocalDate> workDates,
        @Size(max = 2) List<@NotNull Long> shiftIds,
        @Size(max = 124) List<@NotNull @Valid SlotSelection> workSlots
) {
    public record SlotSelection(@NotNull LocalDate workDate, @NotNull @Positive Long shiftId) { }
}
