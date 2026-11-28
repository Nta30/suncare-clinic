package com.suncare.clinic.modules.clinical.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateClinicalOrderRequest(
        @NotNull @Positive Long serviceId,
        @NotNull @Positive Long performingDepartmentId,
        @Positive Long requestedRoomId,
        @Size(max = 2000) String indication) {
    public CreateClinicalOrderRequest {
        indication = indication == null ? "" : indication.strip();
    }
}
