package com.suncare.clinic.modules.prescription.dto;

import jakarta.validation.constraints.*;

public record DrugRequest(
        @NotBlank(message = "Cần nhập mã thuốc.")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,40}", message = "Mã thuốc gồm 1–40 chữ, số, dấu _ hoặc -.") String code,
        @NotBlank(message = "Cần nhập tên thuốc.")
        @Size(max = 200, message = "Tên thuốc tối đa 200 ký tự.") String name,
        @Size(max = 300, message = "Hoạt chất tối đa 300 ký tự.") String ingredient,
        @Size(max = 100, message = "Hàm lượng tối đa 100 ký tự.") String strength,
        @Size(max = 100, message = "Dạng bào chế tối đa 100 ký tự.") String dosageForm,
        @Size(max = 50, message = "Đơn vị tính tối đa 50 ký tự.") String unit,
        @NotBlank(message = "Cần nhập đơn vị liều dùng.") @Size(max = 50) String doseUnit,
        @NotBlank(message = "Cần nhập đơn vị cấp phát.") @Size(max = 50) String dispensingUnit,
        @NotNull(message = "Cần chọn trạng thái sử dụng.") Boolean isActive,
        @Size(max = 24, message = "Phiên bản không hợp lệ. Hãy tải lại danh mục.") String version) {}
