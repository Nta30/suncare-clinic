package com.suncare.clinic.modules.clinical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClinicalServiceUpdateRequest(
        @NotBlank(message = "Tên dịch vụ không được để trống.")
        @Size(max = 200, message = "Tên dịch vụ tối đa 200 ký tự.")
        String name,

        String resultType, // LAB, IMAGING, or null

        Long specialtyId,

        @NotNull(message = "Trạng thái hoạt động không được để trống.")
        Boolean isActive,

        @NotBlank(message = "Version không được để trống.")
        String version
) {
}
