package com.suncare.clinic.modules.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record PrescriptionSaveRequest(
        @NotBlank(message = "Cần phiên bản đơn thuốc.") @Size(max = 24) String version,
        @Size(max = 2000, message = "Dặn dò tối đa 2000 ký tự.") String instruction,
        @NotNull @Size(min = 1, max = 100, message = "Đơn thuốc cần từ 1 đến 100 dòng thuốc.") List<@NotNull @Valid Item> items) {
    public record Item(
            @NotNull @Positive Long drugId,
            @NotBlank(message = "Cần nhập liều mỗi lần.") @Size(max = 200) @PositiveDose String dose,
            @NotBlank(message = "Cần nhập đường dùng.") @Size(max = 100) String route,
            @NotBlank(message = "Cần nhập số lần dùng.") @Size(max = 200) String frequency,
            @NotNull @Positive(message = "Số ngày dùng phải lớn hơn 0.") Integer durationDays,
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "Số lượng phải lớn hơn 0.")
            @Digits(integer = 9, fraction = 3, message = "Số lượng tối đa 9 chữ số nguyên và 3 chữ số thập phân.") BigDecimal quantity,
            @Size(max = 1000) String instruction) {}
}
