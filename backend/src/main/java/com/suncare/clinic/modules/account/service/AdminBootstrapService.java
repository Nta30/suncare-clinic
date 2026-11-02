package com.suncare.clinic.modules.account.service;

import com.suncare.clinic.modules.account.repository.AdminBootstrapRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AdminBootstrapService {
    private final AdminBootstrapRepository repository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminBootstrapService(AdminBootstrapRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void bootstrap(String login, String password, String fullName) {
        if (login == null || login.isBlank() || password == null || password.length() < 12
                || fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Cần login, tên và mật khẩu admin từ biến môi trường (ít nhất 12 ký tự).");
        }

        String loginName = login.trim().toLowerCase(Locale.ROOT);
        Long accountId = repository.findAccountId(loginName);
        Long staffId = repository.findStaffId("DEV_ADMIN");
        long roleId = repository.findAdminRoleId();

        if (accountId != null || staffId != null) {
            if (accountId == null || staffId == null || !repository.isBootstrapAccount(accountId, staffId)) {
                throw new IllegalStateException("Login hoặc mã DEV_ADMIN đã được sử dụng cho tài khoản khác.");
            }
            if (!repository.hasRole(accountId, roleId)) {
                repository.grantAdminRole(accountId, roleId);
            }
            return;
        }

        long newStaffId = repository.createStaff(fullName.trim());
        long newAccountId = repository.createAccount(loginName, passwordEncoder.encode(password), newStaffId);
        repository.grantAdminRole(newAccountId, roleId);
    }
}
