package com.suncare.clinic.modules.appointment.dto;

import java.time.LocalDate;

public record OnsiteAppointmentBookingResponse(
        long appointmentId,
        String appointmentCode,
        long encounterId,
        String encounterCode,
        long doctorId,
        String doctorName,
        long departmentId,
        String departmentName,
        long specialtyId,
        String specialtyName,
        String roomCode,
        String roomName,
        LocalDate date,
        long shiftId,
        String shiftName,
        String examinationAmount,
        String appointmentStatus,
        String encounterStatus,
        boolean replayed
) { }
