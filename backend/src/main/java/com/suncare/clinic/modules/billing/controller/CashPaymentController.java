package com.suncare.clinic.modules.billing.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.billing.dto.CashPaymentRequest;
import com.suncare.clinic.modules.billing.dto.CashPaymentResponse;
import com.suncare.clinic.modules.billing.dto.InvoiceBalanceResponse;
import com.suncare.clinic.modules.billing.service.CashPaymentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cashier/invoices")
public class CashPaymentController {
    private final CashPaymentService payments;

    public CashPaymentController(CashPaymentService payments) { this.payments = payments; }

    @GetMapping
    public java.util.List<InvoiceBalanceResponse> find(@RequestParam(required = false) String code) {
        return payments.find(code);
    }

    @GetMapping("/search")
    public com.suncare.clinic.modules.billing.dto.CashierWorkspaceResponse search(
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return payments.search(search, status, page, pageSize);
    }

    @GetMapping("/{invoiceId}/detail")
    public com.suncare.clinic.modules.billing.dto.CashierInvoiceDetailResponse detail(@PathVariable long invoiceId) {
        return payments.detail(invoiceId);
    }

    @GetMapping("/{invoiceId}")
    public InvoiceBalanceResponse balance(@PathVariable long invoiceId) { return payments.balance(invoiceId); }

    @PostMapping("/{invoiceId}/cash-payments")
    public CashPaymentResponse collect(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long invoiceId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CashPaymentRequest request) {
        return payments.collect(account, invoiceId, key, request);
    }
}
