package com.suncare.clinic.modules.appointment.dto;

import java.time.LocalDate;

public record OnsiteAppointmentAvailabilityResponse(
        long doctorId,
        String doctorName,
        String departmentName,
        String roomCode,
        String roomName,
        long specialtyId,
        String specialtyCode,
        String specialtyName,
        LocalDate date,
        long shiftId,
        String shiftCode,
        String shiftName,
        String startTime,
        String endTime,
        long availablePlaces,
        String minimumFee,
        String maximumFee) { }
