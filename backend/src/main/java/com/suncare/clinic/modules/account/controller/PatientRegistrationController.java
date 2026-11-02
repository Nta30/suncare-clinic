package com.suncare.clinic.modules.account.controller;

import com.suncare.clinic.modules.account.dto.PatientRegistrationRequest;
import com.suncare.clinic.modules.account.dto.PatientRegistrationResponse;
import com.suncare.clinic.modules.account.service.PatientRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class PatientRegistrationController {
    private final PatientRegistrationService service;

    public PatientRegistrationController(PatientRegistrationService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public ResponseEntity<PatientRegistrationResponse> register(
            @Valid @RequestBody PatientRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
    }
}
