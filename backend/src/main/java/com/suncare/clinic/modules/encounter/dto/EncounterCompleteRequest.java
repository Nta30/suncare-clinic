package com.suncare.clinic.modules.encounter.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record EncounterCompleteRequest(
        @NotNull(message = "Cần xác nhận hoàn tất lượt khám.")
        @AssertTrue(message = "Cần xác nhận hoàn tất lượt khám.") Boolean completionConfirmed) {}
