package com.suncare.clinic.modules.account.controller;

import com.suncare.clinic.modules.account.dto.CreateStaffRequest;
import com.suncare.clinic.modules.account.dto.CreateStaffResponse;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.account.dto.StaffRoleRequest;
import com.suncare.clinic.modules.account.dto.StaffStatusRequest;
import com.suncare.clinic.modules.account.dto.StaffSummaryResponse;
import com.suncare.clinic.modules.account.service.StaffManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/admin/staff")
public class StaffManagementController {
    private final StaffManagementService service;

    public StaffManagementController(StaffManagementService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateStaffResponse create(@AuthenticationPrincipal CurrentAccountResponse admin,
                                      @Valid @RequestBody CreateStaffRequest request) {
        return service.create(admin, request);
    }

    @GetMapping
    public List<StaffSummaryResponse> list(@AuthenticationPrincipal CurrentAccountResponse admin) {
        return service.list(admin);
    }

    @PostMapping("/{accountId}/roles")
    public ResponseEntity<Void> grant(@AuthenticationPrincipal CurrentAccountResponse admin,
                                      @PathVariable long accountId, @Valid @RequestBody StaffRoleRequest request) {
        service.grant(admin, accountId, request.roleCode());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{accountId}/roles/{roleCode}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal CurrentAccountResponse admin,
                                       @PathVariable long accountId, @PathVariable String roleCode) {
        service.revoke(admin, accountId, roleCode);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{accountId}/status")
    public ResponseEntity<Void> status(@AuthenticationPrincipal CurrentAccountResponse admin,
                                       @PathVariable long accountId, @Valid @RequestBody StaffStatusRequest request) {
        service.setStatus(admin, accountId, request.status());
        return ResponseEntity.noContent().build();
    }
}
