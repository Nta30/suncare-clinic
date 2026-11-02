package com.suncare.clinic.modules.account.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String loginName, @NotBlank String password) { }
