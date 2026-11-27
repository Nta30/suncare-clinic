package com.suncare.clinic.modules.prescription.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.prescription.dto.*;
import com.suncare.clinic.modules.prescription.service.PrescriptionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/doctor/encounters/{encounterId}/prescriptions")
public class PrescriptionController {
    private final PrescriptionService service;
    public PrescriptionController(PrescriptionService service) { this.service=service; }

    @GetMapping
    public List<PrescriptionResponse> list(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long encounterId) {
        return service.list(account,encounterId);
    }

    @PostMapping
    public PrescriptionResponse create(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long encounterId,
            @RequestHeader(value="Idempotency-Key",required=false) String key,
            @Valid @RequestBody PrescriptionCreateRequest request) {
        return service.create(account,encounterId,key,request);
    }

    @PutMapping("/{id}")
    public PrescriptionResponse save(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long encounterId,
            @PathVariable long id,@RequestHeader(value="Idempotency-Key",required=false) String key,
            @Valid @RequestBody PrescriptionSaveRequest request) {
        return service.save(account,encounterId,id,key,request);
    }

    @PostMapping("/{id}/issue")
    public PrescriptionResponse issue(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long encounterId,
            @PathVariable long id,@RequestHeader(value="Idempotency-Key",required=false) String key,
            @Valid @RequestBody PrescriptionVersionRequest request) {
        return service.issue(account,encounterId,id,key,request);
    }

    @DeleteMapping("/{id}")
    public PrescriptionService.DiscardResponse discard(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long encounterId,
            @PathVariable long id,@RequestHeader(value="Idempotency-Key",required=false) String key,
            @Valid @RequestBody PrescriptionVersionRequest request) {
        return service.discard(account,encounterId,id,key,request);
    }
}
