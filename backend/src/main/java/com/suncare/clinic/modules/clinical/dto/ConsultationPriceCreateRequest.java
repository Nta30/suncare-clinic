package com.suncare.clinic.modules.clinical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ConsultationPriceCreateRequest(
        @NotNull(message = "Bác sĩ không được để trống.")
        Long doctorId,

        @NotBlank(message = "Hình thức khám không được để trống.")
        @Pattern(regexp = "^(ONSITE|ONLINE)$", message = "Hình thức khám chỉ có thể là ONSITE hoặc ONLINE.")
        String visitMode,

        @NotNull(message = "Giá khám không được để trống.")
        @Positive(message = "Giá khám phải lớn hơn 0.")
        BigDecimal amount,

        @NotNull(message = "Thời điểm bắt đầu không được để trống.")
        LocalDateTime validFrom,

        LocalDateTime validTo
) {
}
