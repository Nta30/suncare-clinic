package com.suncare.clinic.modules.account.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AdminBootstrapRepository {
    private final JdbcTemplate jdbc;

    public AdminBootstrapRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long findAccountId(String loginName) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM dbo.[Account] WHERE login_name = ?", Long.class, loginName);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public Long findStaffId(String code) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM dbo.Staff WHERE code = ?", Long.class, code);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public boolean isBootstrapAccount(long accountId, long staffId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.[Account]
                WHERE id = ? AND staff_id = ? AND account_type = 'STAFF' AND status = 'ACTIVE'
                """, Integer.class, accountId, staffId);
        return count != null && count == 1;
    }

    public long findAdminRoleId() {
        return jdbc.queryForObject(
                "SELECT id FROM dbo.[Role] WHERE code = 'SYSTEM_ADMIN'", Long.class);
    }

    public boolean hasRole(long accountId, long roleId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.AccountRole WHERE account_id = ? AND role_id = ?",
                Integer.class, accountId, roleId);
        return count != null && count == 1;
    }

    public long createStaff(String fullName) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id
                VALUES ('DEV_ADMIN', ?, 'UNKNOWN', 1)
                """, Long.class, fullName);
    }

    public long createAccount(String loginName, String passwordHash, long staffId) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id
                VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, loginName, passwordHash, staffId);
    }

    public void grantAdminRole(long accountId, long roleId) {
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                VALUES (?, ?, NULL, SYSUTCDATETIME())
                """, accountId, roleId);
    }
}
