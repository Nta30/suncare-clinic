package com.suncare.clinic.modules.clinical.dto;

public record RoomServiceMappingResponse(
        Long roomId,
        String roomCode,
        String roomName,
        Long departmentId,
        String departmentName,
        Boolean isSupported,
        String updatedAt
) {
}
