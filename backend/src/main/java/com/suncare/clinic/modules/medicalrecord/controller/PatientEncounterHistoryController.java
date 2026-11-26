package com.suncare.clinic.modules.medicalrecord.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.medicalrecord.dto.PatientEncounterHistoryResponse.*;
import com.suncare.clinic.modules.medicalrecord.service.PatientEncounterHistoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor/encounters/{contextId}/history")
public class PatientEncounterHistoryController {
    private final PatientEncounterHistoryService service;
    public PatientEncounterHistoryController(PatientEncounterHistoryService service) {this.service=service;}
    @GetMapping
    public Page list(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long contextId,
            @RequestParam(defaultValue="1") int page) {return service.list(account,contextId,page);}
    @GetMapping("/{previousEncounterId}")
    public Detail detail(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long contextId,
            @PathVariable long previousEncounterId) {return service.detail(account,contextId,previousEncounterId);}
}
