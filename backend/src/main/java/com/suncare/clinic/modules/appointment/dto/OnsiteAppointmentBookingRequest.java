package com.suncare.clinic.modules.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record OnsiteAppointmentBookingRequest(
        @NotNull @Positive Long specialtyId,
        @NotNull LocalDate date,
        @NotNull @Positive Long shiftId,
        @Positive Long doctorId,
        @Size(max = 2000) String symptoms
) {
    public OnsiteAppointmentBookingRequest(Long specialtyId, LocalDate date, Long shiftId, Long doctorId) {
        this(specialtyId, date, shiftId, doctorId, null);
    }
}
