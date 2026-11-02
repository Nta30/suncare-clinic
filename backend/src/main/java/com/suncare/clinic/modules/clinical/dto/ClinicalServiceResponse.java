package com.suncare.clinic.modules.clinical.dto;

import java.math.BigDecimal;

public record ClinicalServiceResponse(
        Long id,
        String code,
        String name,
        String serviceType,
        String resultType,
        Long specialtyId,
        String specialtyName,
        Boolean isActive,
        String version,
        BigDecimal currentPrice,
        Integer supportedRoomsCount,
        String createdAt,
        String updatedAt
) {
}
