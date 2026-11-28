package com.suncare.clinic.modules.clinical.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.clinical.dto.*;
import com.suncare.clinic.modules.clinical.service.LabWorkspaceService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/lab")
public class LabWorkspaceController {
    private final LabWorkspaceService lab;
    public LabWorkspaceController(LabWorkspaceService lab) { this.lab=lab; }

    @GetMapping("/assignments")
    public List<LabAssignmentResponse> assignments(@AuthenticationPrincipal CurrentAccountResponse account,
            @RequestParam(defaultValue="true") boolean current) { return lab.assignments(account,current); }

    @GetMapping("/orders")
    public List<LabOrderResponse> queue(@AuthenticationPrincipal CurrentAccountResponse account,@RequestParam long roomId) {
        return lab.queue(account,roomId);
    }

    @GetMapping("/orders/{id}")
    public LabOrderResponse detail(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long id,@RequestParam long roomId) {
        return lab.detail(account,id,roomId);
    }

    @GetMapping("/counts")
    public java.util.Map<String,Integer> counts(@AuthenticationPrincipal CurrentAccountResponse account,@RequestParam long roomId) {
        return lab.counts(account,roomId);
    }

    @PostMapping("/orders/{id}/accept")
    public LabOrderResponse accept(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long id,
            @RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody LabActionRequest request) {
        return lab.act(account,id,key,request,false);
    }

    @PostMapping("/orders/{id}/start")
    public LabOrderResponse start(@AuthenticationPrincipal CurrentAccountResponse account,@PathVariable long id,
            @RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody LabActionRequest request) {
        return lab.act(account,id,key,request,true);
    }
}
