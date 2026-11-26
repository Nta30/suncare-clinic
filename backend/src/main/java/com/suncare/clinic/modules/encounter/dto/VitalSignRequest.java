package com.suncare.clinic.modules.encounter.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record VitalSignRequest(
        @NotNull OffsetDateTime measuredAt,
        @Positive @Digits(integer = 3, fraction = 2) BigDecimal temperatureC,
        @Positive @DecimalMax("32767") @Digits(integer = 5, fraction = 0) BigDecimal pulseBpm,
        @Positive @DecimalMax("32767") @Digits(integer = 5, fraction = 0) BigDecimal systolicMmhg,
        @Positive @DecimalMax("32767") @Digits(integer = 5, fraction = 0) BigDecimal diastolicMmhg,
        @Positive @DecimalMax("32767") @Digits(integer = 5, fraction = 0) BigDecimal respiratoryRate,
        @Positive @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal spo2Percent,
        @Positive @Digits(integer = 5, fraction = 2) BigDecimal weightKg,
        @Positive @Digits(integer = 4, fraction = 2) BigDecimal heightCm) {}
