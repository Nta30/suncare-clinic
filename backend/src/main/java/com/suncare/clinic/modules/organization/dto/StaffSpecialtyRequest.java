package com.suncare.clinic.modules.organization.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StaffSpecialtyRequest(@NotNull @Positive Long staffId,
                                    @NotNull @Positive Long specialtyId,
                                    @NotNull Boolean active) { }
