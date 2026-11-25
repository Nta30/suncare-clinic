package com.suncare.clinic.modules.encounter.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record EncounterStartRequest(
        @NotNull(message = "Cần xác nhận bệnh nhân có mặt.")
        @AssertTrue(message = "Chỉ bắt đầu khám sau khi xác nhận bệnh nhân có mặt.") Boolean presenceConfirmed) {}
