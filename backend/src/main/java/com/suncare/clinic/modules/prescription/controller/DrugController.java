package com.suncare.clinic.modules.prescription.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.prescription.dto.DrugRequest;
import com.suncare.clinic.modules.prescription.dto.DrugResponse;
import com.suncare.clinic.modules.prescription.service.DrugService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/drugs")
public class DrugController {
    private final DrugService service;
    public DrugController(DrugService service) { this.service = service; }

    @GetMapping
    public List<DrugResponse> list(@RequestParam(required=false) String search) { return service.list(search); }

    @PostMapping
    public List<DrugResponse> create(@AuthenticationPrincipal CurrentAccountResponse account,
            @Valid @RequestBody DrugRequest request) { return service.save(account, null, request); }

    @PutMapping("/{id}")
    public List<DrugResponse> update(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long id, @Valid @RequestBody DrugRequest request) { return service.save(account, id, request); }
}
