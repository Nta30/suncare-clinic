package com.suncare.clinic.modules.billing.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.billing.dto.EncounterBillingSummaryResponse;
import com.suncare.clinic.modules.billing.service.InvoiceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor/encounters/{encounterId}/billing-summary")
public class EncounterBillingController {
    private final InvoiceService invoices;
    public EncounterBillingController(InvoiceService invoices) { this.invoices = invoices; }
    @GetMapping
    public EncounterBillingSummaryResponse summary(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long encounterId) {
        return invoices.summary(account, encounterId);
    }
}
