package com.suncare.clinic.modules.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CashPaymentRequest(
        @NotNull(message = "Cần nhập số tiền thực thu.")
        @DecimalMin(value = "1", message = "Số tiền phải lớn hơn 0.")
        @Digits(integer = 19, fraction = 0, message = "Số tiền phải là số nguyên VND, tối đa 19 chữ số.")
        BigDecimal amount) {}
