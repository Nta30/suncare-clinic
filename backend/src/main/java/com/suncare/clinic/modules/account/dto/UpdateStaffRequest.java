package com.suncare.clinic.modules.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateStaffRequest(
        @NotBlank @Size(max = 150) String fullName,
        @PastOrPresent LocalDate birthDate,
        @NotBlank @Pattern(regexp = "UNKNOWN|MALE|FEMALE|OTHER") String sex,
        @Size(max = 20) String phone,
        @Email @Size(max = 254) String email,
        @Size(max = 500) String address,
        @Size(max = 100) String professionalLicense,
        @Size(max = 150) String qualification,
        @NotBlank String version) { }
