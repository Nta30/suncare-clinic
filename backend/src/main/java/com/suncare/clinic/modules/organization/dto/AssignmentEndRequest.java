package com.suncare.clinic.modules.organization.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AssignmentEndRequest(@NotNull LocalDateTime validTo) { }
