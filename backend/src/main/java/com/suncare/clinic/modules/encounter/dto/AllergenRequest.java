package com.suncare.clinic.modules.encounter.dto;

import jakarta.validation.constraints.*;

public record AllergenRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String code,
        @NotBlank @Size(max = 200) String name, @NotNull Boolean isActive, String version) {}
