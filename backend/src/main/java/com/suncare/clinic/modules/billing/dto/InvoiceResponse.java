package com.suncare.clinic.modules.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceResponse(long id, String code, long encounterId, long patientId, String status,
        String patientName, String patientAddress, String facilityName, String facilityAddress,
        BigDecimal totalAmount, String currency, String issuedAt, String version, boolean replayed,
        List<Line> lines) {
    public record Line(long id, int lineNo, String lineType, long serviceId, long priceId,
            String serviceName, String unitName, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal amount, String status) {}
}
