package com.suncare.clinic.modules.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import com.suncare.clinic.modules.prescription.dto.PrescriptionSaveRequest.Item;

public record PrescriptionCreateRequest(
        @Size(max = 2000, message = "Lời dặn chung tối đa 2000 ký tự.") String instruction,
        @NotNull @Size(min = 1, max = 100, message = "Đơn thuốc cần từ 1 đến 100 dòng thuốc.")
        List<@NotNull @Valid Item> items) {}
