package com.suncare.clinic.modules.encounter.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.encounter.dto.*;
import com.suncare.clinic.modules.encounter.service.EncounterClinicalService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor/encounters/{encounterId}/clinical")
public class EncounterClinicalController {
    private final EncounterClinicalService service;
    public EncounterClinicalController(EncounterClinicalService service) { this.service = service; }
    @GetMapping
    public EncounterClinicalResponse detail(@AuthenticationPrincipal CurrentAccountResponse account, @PathVariable long encounterId) {
        return service.detail(account, encounterId);
    }
    @PutMapping("/notes")
    public EncounterClinicalResponse notes(@AuthenticationPrincipal CurrentAccountResponse account, @PathVariable long encounterId,
            @Valid @RequestBody EncounterNotesRequest request) { return service.notes(account, encounterId, request); }
    @PostMapping("/vitals")
    public EncounterClinicalResponse vital(@AuthenticationPrincipal CurrentAccountResponse account, @PathVariable long encounterId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody VitalSignRequest request) { return service.vital(account, encounterId, key, request); }
    @PostMapping("/diagnoses")
    public EncounterClinicalResponse diagnosis(@AuthenticationPrincipal CurrentAccountResponse account, @PathVariable long encounterId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DiagnosisRequest request) { return service.diagnosis(account, encounterId, null, key, request); }
    @PutMapping("/diagnoses/{diagnosisId}")
    public EncounterClinicalResponse updateDiagnosis(@AuthenticationPrincipal CurrentAccountResponse account, @PathVariable long encounterId,
            @PathVariable long diagnosisId, @Valid @RequestBody DiagnosisRequest request) {
        return service.diagnosis(account, encounterId, diagnosisId, null, request);
    }
}
