package com.suncare.clinic.modules.organization.dto;

public record RoomResponse(long id, String code, String name, String roomType,
                           long departmentId, String departmentName, String departmentType,
                           boolean departmentActive, boolean isActive, String version) { }
