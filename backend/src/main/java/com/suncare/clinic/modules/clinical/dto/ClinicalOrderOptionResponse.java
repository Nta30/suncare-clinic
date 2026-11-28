package com.suncare.clinic.modules.clinical.dto;

import java.util.List;

public record ClinicalOrderOptionResponse(long serviceId,String code,String name,long priceId,String amount,
        List<Room> rooms) {
    public record Room(long roomId,String roomCode,String roomName,long departmentId,String departmentName) {}
}
