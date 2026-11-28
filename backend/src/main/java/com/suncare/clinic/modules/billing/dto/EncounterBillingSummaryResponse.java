package com.suncare.clinic.modules.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public record EncounterBillingSummaryResponse(String examinationAmount, String invoicedAmount,
        String unbilledAmount, String receivedAmount, boolean examinationInvoiced,
        List<Cost> costs, List<BillingInvoice> invoices) {
    public record Cost(String serviceName, String unitName, BigDecimal quantity, String unitPrice,
            String amount, String status) {}
    public record BillingInvoice(InvoiceResponse invoice, String receivedAmount, String paymentStatus, String outstandingAmount) {}
}
