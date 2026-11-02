package com.suncare.clinic.modules.scheduling.dto;

import java.time.LocalTime;

public record ShiftDefinitionResponse(
        Long id,
        String code,
        String name,
        LocalTime localStart,
        LocalTime localEnd,
        Boolean isActive,
        String createdAt,
        String updatedAt,
        String version
) {
}
