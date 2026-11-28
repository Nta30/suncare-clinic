package com.suncare.clinic.modules.clinical.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.clinical.dto.ClinicalOrderResponse;
import com.suncare.clinic.modules.clinical.dto.CreateClinicalOrderRequest;
import com.suncare.clinic.modules.clinical.dto.ClinicalOrderOptionResponse;
import com.suncare.clinic.modules.clinical.dto.ClinicalOrderListResponse;
import java.util.List;
import com.suncare.clinic.modules.clinical.service.ClinicalOrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor/encounters/{encounterId}/clinical-orders")
public class ClinicalOrderController {
    private final ClinicalOrderService orders;
    public ClinicalOrderController(ClinicalOrderService orders) { this.orders=orders; }

    @GetMapping
    public List<ClinicalOrderListResponse> list(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long encounterId) {
        return orders.list(account,encounterId);
    }

    @GetMapping("/options")
    public List<ClinicalOrderOptionResponse> options(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long encounterId,@RequestParam(defaultValue="") String search) {
        return orders.options(account,encounterId,search);
    }

    @PostMapping
    public ClinicalOrderResponse create(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long encounterId, @RequestHeader(value="Idempotency-Key",required=false) String key,
            @Valid @RequestBody CreateClinicalOrderRequest request) {
        return orders.create(account,encounterId,key,request);
    }
}
