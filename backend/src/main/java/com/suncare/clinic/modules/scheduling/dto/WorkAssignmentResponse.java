package com.suncare.clinic.modules.scheduling.dto;

public record WorkAssignmentResponse(
        long id,
        long scheduleId,
        String mode,
        long staffId,
        String staffCode,
        String staffName,
        long roomId,
        String roomCode,
        String roomName,
        long specialtyId,
        String specialtyName,
        Long shiftId,
        String shiftCode,
        String shiftName,
        String localStart,
        String localEnd,
        String workDate,
        String startsAt,
        String endsAt,
        Integer capacity,
        String version
) { }
