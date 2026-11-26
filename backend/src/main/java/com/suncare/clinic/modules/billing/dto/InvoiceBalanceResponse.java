package com.suncare.clinic.modules.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceBalanceResponse(InvoiceResponse invoice, BigDecimal outstanding,
        List<LineBalance> lines) {
    public record LineBalance(long lineId, int lineNo, String lineType, String status,
            BigDecimal amount, BigDecimal allocated, BigDecimal refunded, BigDecimal outstanding) {}
}
