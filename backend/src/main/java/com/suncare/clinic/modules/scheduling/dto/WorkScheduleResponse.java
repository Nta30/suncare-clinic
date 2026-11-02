package com.suncare.clinic.modules.scheduling.dto;

public record WorkScheduleResponse(
        long id,
        long departmentId,
        String departmentName,
        String periodStart,
        String periodEnd,
        String status,
        int assignmentCount,
        String version,
        String createdAt
) { }
