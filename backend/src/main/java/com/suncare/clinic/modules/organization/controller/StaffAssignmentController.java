package com.suncare.clinic.modules.organization.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.organization.dto.AssignmentEndRequest;
import com.suncare.clinic.modules.organization.dto.HeadAssignmentRequest;
import com.suncare.clinic.modules.organization.dto.StaffAssignmentRequest;
import com.suncare.clinic.modules.organization.dto.StaffSpecialtyRequest;
import com.suncare.clinic.modules.organization.service.StaffAssignmentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/staff-assignments")
public class StaffAssignmentController {
    private final StaffAssignmentService service;

    public StaffAssignmentController(StaffAssignmentService service) { this.service = service; }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal CurrentAccountResponse actor) { return service.list(actor); }

    @PostMapping("/departments")
    public Map<String, Object> department(@AuthenticationPrincipal CurrentAccountResponse actor,
                                          @Valid @RequestBody StaffAssignmentRequest request) {
        return service.assignDepartment(actor, request);
    }

    @PutMapping("/departments/{id}/end")
    public Map<String, Object> endDepartment(@AuthenticationPrincipal CurrentAccountResponse actor, @PathVariable long id,
                                             @Valid @RequestBody AssignmentEndRequest request) {
        return service.endDepartment(actor, id, request.validTo());
    }

    @PostMapping("/heads")
    public Map<String, Object> head(@AuthenticationPrincipal CurrentAccountResponse actor,
                                    @Valid @RequestBody HeadAssignmentRequest request) {
        return service.assignHead(actor, request);
    }

    @PutMapping("/heads/{id}/end")
    public Map<String, Object> endHead(@AuthenticationPrincipal CurrentAccountResponse actor, @PathVariable long id,
                                       @Valid @RequestBody AssignmentEndRequest request) {
        return service.endHead(actor, id, request.validTo());
    }

    @PutMapping("/specialties")
    public Map<String, Object> specialty(@AuthenticationPrincipal CurrentAccountResponse actor,
                                         @Valid @RequestBody StaffSpecialtyRequest request) {
        return service.specialty(actor, request);
    }
}
