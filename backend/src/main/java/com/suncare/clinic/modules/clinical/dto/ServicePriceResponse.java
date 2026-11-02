package com.suncare.clinic.modules.clinical.dto;

import java.math.BigDecimal;

public record ServicePriceResponse(
        Long id,
        Long serviceId,
        BigDecimal amount,
        String validFrom,
        String validTo,
        Long createdBy,
        String createdByName,
        String createdAt,
        String version,
        Boolean isEffective
) {
}
