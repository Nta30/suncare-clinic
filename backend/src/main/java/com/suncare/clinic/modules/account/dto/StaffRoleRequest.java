package com.suncare.clinic.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StaffRoleRequest(@NotBlank @Pattern(regexp = "DOCTOR|LAB_STAFF|DEPARTMENT_HEAD|CASHIER")
                               String roleCode) { }
