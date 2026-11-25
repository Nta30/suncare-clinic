package com.suncare.clinic.modules.appointment.dto;

import java.time.OffsetDateTime;

public record AppointmentScheduleDetailResponse(
        long appointmentId,
        String appointmentCode,
        String visitMode,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String appointmentStatus,
        String encounterStatus,
        String patientCode,
        String patientName,
        String doctorCode,
        String doctorName,
        String departmentName,
        String specialtyName,
        String roomCode,
        String roomName,
        String symptoms,
        String examinationAmount
) { }
