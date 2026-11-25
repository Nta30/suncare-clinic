package com.suncare.clinic.modules.encounter.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.encounter.dto.AllergenRequest;
import com.suncare.clinic.modules.encounter.dto.AllergenResponse;
import com.suncare.clinic.modules.encounter.service.AllergenService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/allergens")
public class AllergenController {
    private final AllergenService service;
    public AllergenController(AllergenService service) { this.service = service; }
    @GetMapping
    public List<AllergenResponse> list() { return service.list(); }
    @PostMapping
    public List<AllergenResponse> create(@AuthenticationPrincipal CurrentAccountResponse account,
            @Valid @RequestBody AllergenRequest request) { return service.save(account, null, request); }
    @PutMapping("/{id}")
    public List<AllergenResponse> update(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long id, @Valid @RequestBody AllergenRequest request) { return service.save(account, id, request); }
}
