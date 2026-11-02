package com.suncare.clinic.modules.account.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.account.dto.PersonalProfileRequest;
import com.suncare.clinic.modules.account.dto.PersonalProfileResponse;
import com.suncare.clinic.modules.account.service.PersonalProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts/profile")
public class PersonalProfileController {
    private final PersonalProfileService service;

    public PersonalProfileController(PersonalProfileService service) {
        this.service = service;
    }

    @GetMapping
    public PersonalProfileResponse get(@AuthenticationPrincipal CurrentAccountResponse account) {
        return service.get(account);
    }

    @PutMapping
    public PersonalProfileResponse update(@AuthenticationPrincipal CurrentAccountResponse account,
                                          @Valid @RequestBody PersonalProfileRequest request) {
        return service.update(account, request);
    }
}
