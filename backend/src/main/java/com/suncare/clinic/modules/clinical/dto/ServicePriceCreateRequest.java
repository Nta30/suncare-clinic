package com.suncare.clinic.modules.clinical.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServicePriceCreateRequest(
        @NotNull(message = "Giá dịch vụ không được để trống.")
        @Positive(message = "Giá dịch vụ phải lớn hơn 0.")
        BigDecimal amount,

        @NotNull(message = "Thời điểm bắt đầu không được để trống.")
        LocalDateTime validFrom,

        LocalDateTime validTo
) {
}
