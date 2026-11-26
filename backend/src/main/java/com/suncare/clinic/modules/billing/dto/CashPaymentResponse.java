package com.suncare.clinic.modules.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashPaymentResponse(long id, String code, long invoiceId, BigDecimal amount,
        String currency, String method, String status, long confirmedBy, String paidAt,
        boolean replayed, List<Allocation> allocations) {
    public record Allocation(long invoiceLineId, BigDecimal amount) {}
}
