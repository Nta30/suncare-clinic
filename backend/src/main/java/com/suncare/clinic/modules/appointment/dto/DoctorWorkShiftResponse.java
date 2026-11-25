package com.suncare.clinic.modules.appointment.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DoctorWorkShiftResponse(long assignmentId, LocalDate workDate, String shiftCode,
        String shiftName, String visitMode, OffsetDateTime startsAt, OffsetDateTime endsAt,
        String departmentName, String specialtyName, String roomCode, String roomName) {}
