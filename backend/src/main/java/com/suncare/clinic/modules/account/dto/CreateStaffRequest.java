package com.suncare.clinic.modules.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateStaffRequest(
        @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "[A-Za-z0-9._@-]+") String loginName,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @PastOrPresent LocalDate birthDate,
        @Pattern(regexp = "UNKNOWN|MALE|FEMALE|OTHER") String sex,
        @Size(max = 20) String phone,
        @Email @Size(max = 254) String email,
        @Size(max = 500) String address,
        @Size(max = 100) String professionalLicense,
        @Size(max = 150) String qualification,
        @NotEmpty List<@Pattern(regexp = "DOCTOR|LAB_STAFF|DEPARTMENT_HEAD|CASHIER") String> roles) { }
