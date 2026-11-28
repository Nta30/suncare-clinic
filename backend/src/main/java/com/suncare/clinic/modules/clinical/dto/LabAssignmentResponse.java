package com.suncare.clinic.modules.clinical.dto;

public record LabAssignmentResponse(long id, long roomId, String roomName, String departmentName,
        String staffName, String startsAt, String endsAt) { }
