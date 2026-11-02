package com.suncare.clinic.modules.clinical.dto;

import jakarta.validation.constraints.NotNull;

public record RoomServiceToggleRequest(
        @NotNull(message = "Trạng thái hỗ trợ không được để trống.")
        Boolean active
) {
}
