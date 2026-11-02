package com.suncare.clinic.modules.scheduling.dto;

public record WorkAssignmentResponse(
        long id,
        long scheduleId,
        long staffId,
        String staffCode,
        String staffName,
        long roomId,
        String roomCode,
        String roomName,
        long specialtyId,
        String specialtyName,
        long shiftId,
        String shiftCode,
        String shiftName,
        String localStart,
        String localEnd,
        String workDate,
        String startsAt,
        String endsAt,
        int capacity,
        String version
) { }
