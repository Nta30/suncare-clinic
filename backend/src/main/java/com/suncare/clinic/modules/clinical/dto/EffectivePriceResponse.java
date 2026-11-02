package com.suncare.clinic.modules.clinical.dto;

import java.math.BigDecimal;

public record EffectivePriceResponse(
        Long priceId,
        Long serviceId,
        BigDecimal amount,
        String validFrom,
        String validTo
) {
}
