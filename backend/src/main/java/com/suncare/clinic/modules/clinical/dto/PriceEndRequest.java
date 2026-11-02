package com.suncare.clinic.modules.clinical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record PriceEndRequest(
        @NotNull(message = "Thời điểm kết thúc không được để trống.")
        LocalDateTime validTo,

        @NotBlank(message = "Version không được để trống.")
        String version
) {
}
