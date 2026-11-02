package com.suncare.clinic.modules.clinical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClinicalServiceCreateRequest(
        @NotBlank(message = "Mã dịch vụ không được để trống.")
        @Size(max = 40, message = "Mã dịch vụ tối đa 40 ký tự.")
        String code,

        @NotBlank(message = "Tên dịch vụ không được để trống.")
        @Size(max = 200, message = "Tên dịch vụ tối đa 200 ký tự.")
        String name,

        @NotBlank(message = "Loại dịch vụ không được để trống.")
        String serviceType, // EXAMINATION, CLS

        String resultType, // LAB, IMAGING, or null

        Long specialtyId,

        Boolean isActive
) {
}
