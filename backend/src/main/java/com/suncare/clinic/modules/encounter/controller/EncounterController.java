package com.suncare.clinic.modules.encounter.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterStartResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterCompleteRequest;
import com.suncare.clinic.modules.encounter.dto.EncounterCompleteResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterStartRequest;
import jakarta.validation.Valid;
import com.suncare.clinic.modules.encounter.service.EncounterService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import com.suncare.clinic.modules.encounter.dto.EncounterHistoryResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterWorkItemResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterWorklistResponse;
import com.suncare.clinic.modules.encounter.dto.PatientHistoryRequest;
import com.suncare.clinic.modules.encounter.dto.PatientAllergyRequest;
import com.suncare.clinic.modules.encounter.service.EncounterHistoryService;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/doctor/encounters")
public class EncounterController {
    private final EncounterService service;
    private final EncounterHistoryService historyService;

    public EncounterController(EncounterService service, EncounterHistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
    }

    @PostMapping("/{encounterId}/start")
    public EncounterStartResponse start(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long encounterId, @Valid @RequestBody EncounterStartRequest request) {
        return service.start(account, encounterId);
    }

    @PostMapping("/{encounterId}/complete")
    public EncounterCompleteResponse complete(
            @AuthenticationPrincipal CurrentAccountResponse account, @PathVariable long encounterId,
            @Valid @RequestBody EncounterCompleteRequest request) {
        return service.complete(account, encounterId);
    }

    @GetMapping
    public EncounterWorklistResponse worklist(@AuthenticationPrincipal CurrentAccountResponse account,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return historyService.worklist(account, date, status, search, page, pageSize);
    }

    @GetMapping("/{encounterId}")
    public EncounterHistoryResponse detail(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long encounterId) {
        return historyService.detail(account, encounterId);
    }

    @PostMapping("/{encounterId}/histories")
    public EncounterHistoryResponse createHistory(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long encounterId, @Valid @RequestBody PatientHistoryRequest request) {
        return historyService.saveHistory(account, encounterId, null, request);
    }

    @PutMapping("/{encounterId}/histories/{itemId}")
    public EncounterHistoryResponse updateHistory(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long encounterId, @PathVariable long itemId, @Valid @RequestBody PatientHistoryRequest request) {
        return historyService.saveHistory(account, encounterId, itemId, request);
    }

    @PostMapping("/{encounterId}/allergies")
    public EncounterHistoryResponse createAllergy(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long encounterId, @Valid @RequestBody PatientAllergyRequest request) {
        return historyService.saveAllergy(account, encounterId, null, request);
    }

    @PutMapping("/{encounterId}/allergies/{itemId}")
    public EncounterHistoryResponse updateAllergy(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long encounterId, @PathVariable long itemId, @Valid @RequestBody PatientAllergyRequest request) {
        return historyService.saveAllergy(account, encounterId, itemId, request);
    }
}
