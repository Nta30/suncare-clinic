package com.suncare.clinic.modules.billing.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.billing.dto.InvoiceResponse;
import com.suncare.clinic.modules.billing.dto.InvoicePrintResponse;
import com.suncare.clinic.modules.billing.dto.IssueInvoiceRequest;
import com.suncare.clinic.modules.billing.service.InvoiceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor/encounters/{encounterId}/invoices")
public class InvoiceController {
    private final InvoiceService invoices;
    public InvoiceController(InvoiceService invoices) { this.invoices=invoices; }
    @GetMapping
    public List<InvoiceResponse> list(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long encounterId) {
        return invoices.list(account,encounterId);
    }
    @GetMapping("/{invoiceId}/print-detail")
    public InvoicePrintResponse printDetail(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long encounterId, @PathVariable long invoiceId) {
        return invoices.printDetail(account, encounterId, invoiceId);
    }
    @PostMapping
    public InvoiceResponse create(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long encounterId,
            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return invoices.create(account,encounterId,key);
    }
    @PostMapping("/{invoiceId}/issue")
    public InvoiceResponse issue(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long encounterId,
            @PathVariable long invoiceId,@RequestHeader(value="Idempotency-Key",required=false) String key,
            @Valid @RequestBody IssueInvoiceRequest request) {
        return invoices.issue(account,encounterId,invoiceId,key,request);
    }
}
