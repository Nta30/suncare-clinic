package com.suncare.clinic.modules.billing.dto;

public record CashierInvoiceDetailResponse(InvoiceResponse invoice, String patientCode, String phone,
        String paymentAmount, String paymentStatus, boolean canCollect) {}
