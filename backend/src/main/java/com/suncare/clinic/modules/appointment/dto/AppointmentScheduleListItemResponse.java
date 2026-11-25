package com.suncare.clinic.modules.appointment.dto;

import java.time.OffsetDateTime;

public record AppointmentScheduleListItemResponse(
        long appointmentId,
        String appointmentCode,
        String visitMode,
        OffsetDateTime startsAt,
        String appointmentStatus,
        String patientName,
        String doctorName,
        String specialtyName
) { }
