package com.suncare.clinic.modules.scheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record ShiftDefinitionUpdateRequest(
        @NotBlank(message = "Tên ca không được để trống.")
        @Size(max = 100, message = "Tên ca không được vượt quá 100 ký tự.")
        String name,

        @NotNull(message = "Giờ bắt đầu không được để trống.")
        LocalTime localStart,

        @NotNull(message = "Giờ kết thúc không được để trống.")
        LocalTime localEnd,

        @NotNull(message = "Trạng thái hoạt động không được để trống.")
        Boolean isActive,

        @NotBlank(message = "Phiên bản dữ liệu không được để trống.")
        String version
) {
}
