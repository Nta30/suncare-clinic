package com.suncare.clinic.modules.account.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CreateStaffRequest;
import com.suncare.clinic.modules.account.dto.CreateStaffResponse;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.account.dto.StaffSummaryResponse;
import com.suncare.clinic.modules.account.repository.StaffManagementRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class StaffManagementService {
    private final StaffManagementRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public StaffManagementService(StaffManagementRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreateStaffResponse create(CurrentAccountResponse admin, CreateStaffRequest request) {
        if (!admin.permissions().contains("ACCOUNT_MANAGE")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Không có quyền quản lý tài khoản.");
        }
        List<String> roles = List.copyOf(new LinkedHashSet<>(request.roles()));
        if (roles.size() != request.roles().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_ROLE", "Vai trò bị lặp.");
        }
        var roleRows = repository.findStaffRoles(roles);
        if (roleRows.size() != roles.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Vai trò nhân viên không hợp lệ.");
        }
        String loginName = request.loginName().trim().toLowerCase(Locale.ROOT);
        if (repository.loginExists(loginName)) throw duplicateLogin();
        String staffCode = "STF-" + UUID.randomUUID().toString().replace("-", "");
        try {
            long staffId = repository.createStaff(staffCode, request);
            long accountId = repository.createAccount(loginName, encoder.encode(request.password()), staffId);
            for (var role : roleRows) repository.grantRole(accountId, role.id(), admin.accountId());
            repository.audit(admin.accountId(), "STAFF_CREATED", accountId, "roles=" + String.join(",", roles));
            return new CreateStaffResponse(accountId, staffCode, loginName, roles);
        } catch (DuplicateKeyException error) {
            throw duplicateLogin();
        }
    }

    public List<StaffSummaryResponse> list(CurrentAccountResponse admin) {
        requireAdmin(admin);
        return repository.listStaff();
    }

    @Transactional
    public void grant(CurrentAccountResponse admin, long accountId, String roleCode) {
        requireAdmin(admin);
        requireTarget(admin, accountId);
        var role = repository.findStaffRole(roleCode);
        if (role == null || "SYSTEM_ADMIN".equals(roleCode)) throw invalidRole();
        if (repository.hasRole(accountId, role.id())) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_EXISTS", "Tài khoản đã có vai trò này.");
        }
        repository.grantRole(accountId, role.id(), admin.accountId());
        repository.audit(admin.accountId(), "STAFF_ROLE_GRANTED", accountId, "role=" + roleCode);
    }

    @Transactional
    public void revoke(CurrentAccountResponse admin, long accountId, String roleCode) {
        requireAdmin(admin);
        requireTarget(admin, accountId);
        var role = repository.findStaffRole(roleCode);
        if (role == null || "SYSTEM_ADMIN".equals(roleCode)) throw invalidRole();
        if (!repository.hasRole(accountId, role.id())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "Tài khoản không có vai trò này.");
        }
        repository.revokeRole(accountId, role.id());
        repository.audit(admin.accountId(), "STAFF_ROLE_REVOKED", accountId, "role=" + roleCode);
    }

    @Transactional
    public void setStatus(CurrentAccountResponse admin, long accountId, String status) {
        requireAdmin(admin);
        var target = requireTarget(admin, accountId);
        if (target.status().equals(status)) return;
        repository.updateStatus(accountId, status);
        repository.audit(admin.accountId(), "STAFF_STATUS_CHANGED", accountId, "status=" + status);
    }

    private void requireAdmin(CurrentAccountResponse admin) {
        if (!admin.permissions().contains("ACCOUNT_MANAGE")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Không có quyền quản lý tài khoản.");
        }
    }

    private StaffManagementRepository.StaffAccount requireTarget(CurrentAccountResponse admin, long accountId) {
        var target = repository.lockStaff(accountId);
        if (target == null) throw new ApiException(HttpStatus.NOT_FOUND, "STAFF_NOT_FOUND", "Không tìm thấy tài khoản nhân viên.");
        if (accountId == admin.accountId() || repository.hasAdminRole(accountId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROTECTED_ADMIN", "Không thể thay đổi tài khoản quản trị bằng thao tác này.");
        }
        return target;
    }

    private ApiException invalidRole() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Vai trò nhân viên không hợp lệ.");
    }

    private ApiException duplicateLogin() {
        return new ApiException(HttpStatus.CONFLICT, "LOGIN_EXISTS", "Tên đăng nhập đã được sử dụng.");
    }
}
