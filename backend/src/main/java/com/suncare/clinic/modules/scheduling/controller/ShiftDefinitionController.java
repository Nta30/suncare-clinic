package com.suncare.clinic.modules.scheduling.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.scheduling.dto.ShiftDefinitionCreateRequest;
import com.suncare.clinic.modules.scheduling.dto.ShiftDefinitionResponse;
import com.suncare.clinic.modules.scheduling.dto.ShiftDefinitionUpdateRequest;
import com.suncare.clinic.modules.scheduling.service.ShiftDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/scheduling/shifts")
public class ShiftDefinitionController {
    private final ShiftDefinitionService service;

    public ShiftDefinitionController(ShiftDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public List<ShiftDefinitionResponse> listShifts(@AuthenticationPrincipal CurrentAccountResponse account) {
        return service.listShifts(account);
    }

    @GetMapping("/{id}")
    public ShiftDefinitionResponse getShift(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long id) {
        return service.getShift(account, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftDefinitionResponse createShift(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @Valid @RequestBody ShiftDefinitionCreateRequest request) {
        return service.createShift(account, request);
    }

    @PutMapping("/{id}")
    public ShiftDefinitionResponse updateShift(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long id,
            @Valid @RequestBody ShiftDefinitionUpdateRequest request) {
        return service.updateShift(account, id, request);
    }

    @PostMapping("/init-defaults")
    public List<ShiftDefinitionResponse> initDefaultShifts(@AuthenticationPrincipal CurrentAccountResponse account) {
        return service.initDefaultShiftsIfEmpty(account);
    }
}
