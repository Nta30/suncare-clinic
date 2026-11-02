package com.suncare.clinic.modules.clinical.dto;

import java.math.BigDecimal;

public record ConsultationPriceResponse(
        Long id,
        Long serviceId,
        String serviceCode,
        String serviceName,
        Long doctorId,
        String doctorCode,
        String doctorName,
        String visitMode,
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
