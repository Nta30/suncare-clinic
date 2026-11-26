package com.suncare.clinic.modules.billing.dto;

import java.util.List;

public record CashierWorkspaceResponse(Statistics statistics, List<Item> items, int page, int pageSize,
        long totalElements, int totalPages) {
    public record Statistics(long pending, long paid, String receivedToday, String date) {}
    public record Item(long id, String code, String patientCode, String patientName, String phone,
            String issuedAt, String totalAmount, String paymentStatus) {}
}
