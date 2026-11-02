package com.suncare.clinic.modules.scheduling.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.scheduling.dto.DraftScheduleRequest;
import com.suncare.clinic.modules.scheduling.dto.ScheduleOptionsResponse;
import com.suncare.clinic.modules.scheduling.dto.WorkAssignmentCreateRequest;
import com.suncare.clinic.modules.scheduling.dto.WorkAssignmentResponse;
import com.suncare.clinic.modules.scheduling.dto.WorkScheduleResponse;
import com.suncare.clinic.modules.scheduling.service.WorkScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/head/schedules")
public class WorkScheduleController {
    private final WorkScheduleService service;

    public WorkScheduleController(WorkScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkScheduleResponse> list(@AuthenticationPrincipal CurrentAccountResponse account) {
        return service.list(account);
    }

    @GetMapping("/options")
    public ScheduleOptionsResponse options(@AuthenticationPrincipal CurrentAccountResponse account,
                                           @RequestParam(required = false) Long departmentId) {
        return service.options(account, departmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkScheduleResponse createDraft(@AuthenticationPrincipal CurrentAccountResponse account,
                                            @Valid @RequestBody DraftScheduleRequest request) {
        return service.createDraft(account, request);
    }

    @GetMapping("/{scheduleId}/assignments")
    public List<WorkAssignmentResponse> assignments(@AuthenticationPrincipal CurrentAccountResponse account,
                                                    @PathVariable long scheduleId) {
        return service.assignments(account, scheduleId);
    }

    @PostMapping("/{scheduleId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public List<WorkAssignmentResponse> addAssignment(@AuthenticationPrincipal CurrentAccountResponse account,
                                                      @PathVariable long scheduleId,
                                                      @Valid @RequestBody WorkAssignmentCreateRequest request) {
        return service.addAssignment(account, scheduleId, request);
    }

    @DeleteMapping("/{scheduleId}/assignments/{assignmentId}")
    public List<WorkAssignmentResponse> removeAssignment(@AuthenticationPrincipal CurrentAccountResponse account,
                                                         @PathVariable long scheduleId,
                                                         @PathVariable long assignmentId) {
        return service.removeAssignment(account, scheduleId, assignmentId);
    }
}
