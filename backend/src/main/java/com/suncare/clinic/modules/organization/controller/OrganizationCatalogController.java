package com.suncare.clinic.modules.organization.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.organization.dto.DepartmentRequest;
import com.suncare.clinic.modules.organization.dto.OrganizationCatalogResponse;
import com.suncare.clinic.modules.organization.dto.SpecialtyRequest;
import com.suncare.clinic.modules.organization.service.OrganizationCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/organization")
public class OrganizationCatalogController {
    private final OrganizationCatalogService service;

    public OrganizationCatalogController(OrganizationCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public OrganizationCatalogResponse list(@AuthenticationPrincipal CurrentAccountResponse account) {
        return service.list(account);
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationCatalogResponse createDepartment(@AuthenticationPrincipal CurrentAccountResponse account,
                                                         @Valid @RequestBody DepartmentRequest request) {
        return service.createDepartment(account, request);
    }

    @PutMapping("/departments/{id}")
    public OrganizationCatalogResponse updateDepartment(@AuthenticationPrincipal CurrentAccountResponse account,
                                                         @PathVariable long id,
                                                         @Valid @RequestBody DepartmentRequest request) {
        return service.updateDepartment(account, id, request);
    }

    @PostMapping("/specialties")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationCatalogResponse createSpecialty(@AuthenticationPrincipal CurrentAccountResponse account,
                                                        @Valid @RequestBody SpecialtyRequest request) {
        return service.createSpecialty(account, request);
    }

    @PutMapping("/specialties/{id}")
    public OrganizationCatalogResponse updateSpecialty(@AuthenticationPrincipal CurrentAccountResponse account,
                                                        @PathVariable long id,
                                                        @Valid @RequestBody SpecialtyRequest request) {
        return service.updateSpecialty(account, id, request);
    }

    @PutMapping("/departments/{departmentId}/specialties/{specialtyId}")
    public OrganizationCatalogResponse enableLink(@AuthenticationPrincipal CurrentAccountResponse account,
                                                   @PathVariable long departmentId, @PathVariable long specialtyId) {
        return service.setLink(account, departmentId, specialtyId, true);
    }

    @DeleteMapping("/departments/{departmentId}/specialties/{specialtyId}")
    public OrganizationCatalogResponse disableLink(@AuthenticationPrincipal CurrentAccountResponse account,
                                                    @PathVariable long departmentId, @PathVariable long specialtyId) {
        return service.setLink(account, departmentId, specialtyId, false);
    }
}
