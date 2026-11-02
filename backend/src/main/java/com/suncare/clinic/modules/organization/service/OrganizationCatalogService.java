package com.suncare.clinic.modules.organization.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.organization.dto.DepartmentRequest;
import com.suncare.clinic.modules.organization.dto.OrganizationCatalogResponse;
import com.suncare.clinic.modules.organization.dto.SpecialtyRequest;
import com.suncare.clinic.modules.organization.repository.OrganizationCatalogRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

@Service
public class OrganizationCatalogService {
    private final OrganizationCatalogRepository repository;

    public OrganizationCatalogService(OrganizationCatalogRepository repository) {
        this.repository = repository;
    }

    public OrganizationCatalogResponse list(CurrentAccountResponse account) {
        requireManager(account);
        return repository.list();
    }

    @Transactional
    public OrganizationCatalogResponse createDepartment(CurrentAccountResponse account, DepartmentRequest request) {
        requireManager(account);
        try {
            long id = repository.createDepartment(request);
            repository.audit(account.accountId(), "DEPARTMENT_CREATED", "Department", id);
        } catch (DuplicateKeyException error) {
            throw duplicateCode();
        }
        return repository.list();
    }

    @Transactional
    public OrganizationCatalogResponse updateDepartment(CurrentAccountResponse account, long id, DepartmentRequest request) {
        requireManager(account);
        if (!repository.departmentExists(id)) throw notFound();
        if ("LAB".equals(request.departmentType()) && repository.hasActiveLinks(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_LINKS", "Hãy ngừng liên kết chuyên khoa trước khi đổi khoa sang loại CLS.");
        }
        try {
            if (repository.updateDepartment(id, request, version(request.version())) != 1) throw changed();
            repository.audit(account.accountId(), "DEPARTMENT_UPDATED", "Department", id);
        } catch (DuplicateKeyException error) {
            throw duplicateCode();
        }
        return repository.list();
    }

    @Transactional
    public OrganizationCatalogResponse createSpecialty(CurrentAccountResponse account, SpecialtyRequest request) {
        requireManager(account);
        try {
            long id = repository.createSpecialty(request);
            repository.audit(account.accountId(), "SPECIALTY_CREATED", "Specialty", id);
        } catch (DuplicateKeyException error) {
            throw duplicateCode();
        }
        return repository.list();
    }

    @Transactional
    public OrganizationCatalogResponse updateSpecialty(CurrentAccountResponse account, long id, SpecialtyRequest request) {
        requireManager(account);
        if (!repository.specialtyExists(id)) throw notFound();
        try {
            if (repository.updateSpecialty(id, request, version(request.version())) != 1) throw changed();
            repository.audit(account.accountId(), "SPECIALTY_UPDATED", "Specialty", id);
        } catch (DuplicateKeyException error) {
            throw duplicateCode();
        }
        return repository.list();
    }

    @Transactional
    public OrganizationCatalogResponse setLink(CurrentAccountResponse account, long departmentId, long specialtyId, boolean active) {
        requireManager(account);
        if (active && (!repository.activeClinicalDepartment(departmentId) || !repository.activeSpecialty(specialtyId))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INACTIVE_CATALOG", "Cần khoa khám và chuyên khoa đang hoạt động để liên kết.");
        }
        Boolean current = repository.linkStatus(departmentId, specialtyId);
        if (current == null) {
            if (!active) throw notFound();
            try {
                repository.createLink(departmentId, specialtyId);
            } catch (DuplicateKeyException error) {
                throw changed();
            }
        } else if (current != active) {
            repository.setLinkStatus(departmentId, specialtyId, active);
        } else {
            return repository.list();
        }
        repository.audit(account.accountId(), active ? "DEPARTMENT_SPECIALTY_ENABLED" : "DEPARTMENT_SPECIALTY_DISABLED",
                "DepartmentSpecialty", departmentId, "specialty_id=" + specialtyId);
        return repository.list();
    }

    private void requireManager(CurrentAccountResponse account) {
        if (!account.permissions().contains("CATALOG_MANAGE")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Không có quyền quản lý danh mục.");
        }
    }

    private byte[] version(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length != 8) throw new IllegalArgumentException();
            return bytes;
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERSION", "Phiên bản dữ liệu không hợp lệ.");
        }
    }

    private ApiException duplicateCode() {
        return new ApiException(HttpStatus.CONFLICT, "CODE_EXISTS", "Mã đã được sử dụng.");
    }

    private ApiException changed() {
        return new ApiException(HttpStatus.CONFLICT, "CATALOG_CHANGED", "Dữ liệu đã thay đổi. Hãy tải lại trước khi lưu.");
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CATALOG_NOT_FOUND", "Không tìm thấy mục danh mục.");
    }
}
