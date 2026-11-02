package com.suncare.clinic.modules.clinical.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.clinical.dto.ClinicalServiceCreateRequest;
import com.suncare.clinic.modules.clinical.dto.ClinicalServiceResponse;
import com.suncare.clinic.modules.clinical.dto.ClinicalServiceUpdateRequest;
import com.suncare.clinic.modules.clinical.dto.EffectivePriceResponse;
import com.suncare.clinic.modules.clinical.dto.PriceEndRequest;
import com.suncare.clinic.modules.clinical.dto.RoomServiceMappingResponse;
import com.suncare.clinic.modules.clinical.dto.RoomServiceToggleRequest;
import com.suncare.clinic.modules.clinical.dto.ServicePriceCreateRequest;
import com.suncare.clinic.modules.clinical.dto.ServicePriceResponse;
import com.suncare.clinic.modules.clinical.service.ClinicalServiceManagementService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/services")
public class ClinicalServiceController {
    private final ClinicalServiceManagementService service;

    public ClinicalServiceController(ClinicalServiceManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<ClinicalServiceResponse> listServices(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean active) {
        return service.listServices(account, type, active);
    }

    @GetMapping("/{id}")
    public ClinicalServiceResponse getService(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long id) {
        return service.getService(account, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClinicalServiceResponse createService(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @Valid @RequestBody ClinicalServiceCreateRequest request) {
        return service.createService(account, request);
    }

    @PutMapping("/{id}")
    public ClinicalServiceResponse updateService(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long id,
            @Valid @RequestBody ClinicalServiceUpdateRequest request) {
        return service.updateService(account, id, request);
    }

    @GetMapping("/{id}/rooms")
    public List<RoomServiceMappingResponse> listRooms(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long id) {
        return service.listRooms(account, id);
    }

    @PutMapping("/{id}/rooms/{roomId}")
    public List<RoomServiceMappingResponse> toggleRoom(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long id,
            @PathVariable long roomId,
            @Valid @RequestBody RoomServiceToggleRequest request) {
        return service.toggleRoom(account, id, roomId, request.active());
    }

    @GetMapping("/{id}/prices")
    public List<ServicePriceResponse> listPrices(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long id) {
        return service.listPrices(account, id);
    }

    @PostMapping("/{id}/prices")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ServicePriceResponse> createPrice(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long id,
            @Valid @RequestBody ServicePriceCreateRequest request) {
        return service.createClsPrice(account, id, request);
    }

    @PutMapping("/{id}/prices/{priceId}/end")
    public List<ServicePriceResponse> endPrice(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long id,
            @PathVariable long priceId,
            @Valid @RequestBody PriceEndRequest request) {
        return service.endPrice(account, id, priceId, request);
    }

    @GetMapping("/{id}/effective-price")
    public EffectivePriceResponse getEffectivePrice(
            @PathVariable long id,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String visitMode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime targetTime) {
        return service.getEffectivePrice(id, doctorId, visitMode, targetTime);
    }
}
