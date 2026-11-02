package com.suncare.clinic.modules.account.repository;

import com.suncare.clinic.modules.account.dto.CreateStaffRequest;
import com.suncare.clinic.modules.account.dto.StaffSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Repository
public class StaffManagementRepository {
    private final JdbcTemplate jdbc;

    public StaffManagementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean loginExists(String loginName) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[Account] WHERE login_name = ?", Integer.class, loginName);
        return count != null && count > 0;
    }

    public long createStaff(String code, CreateStaffRequest request) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, birth_date, sex, phone, email, address,
                                       professional_license, qualification, is_active)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, Long.class, code, request.fullName().trim(), request.birthDate(),
                request.sex() == null ? "UNKNOWN" : request.sex(), request.phone(), request.email(),
                request.address(), request.professionalLicense(), request.qualification());
    }

    public long createAccount(String loginName, String passwordHash, long staffId) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, loginName, passwordHash, staffId);
    }

    public List<Role> findStaffRoles(List<String> codes) {
        if (codes.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
        return jdbc.query("SELECT id, code FROM dbo.[Role] WHERE account_type = 'STAFF' AND code IN (" + placeholders + ")",
                (rs, row) -> new Role(rs.getLong("id"), rs.getString("code")), codes.toArray());
    }

    public void grantRole(long accountId, long roleId, long adminAccountId) {
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                VALUES (?, ?, ?, SYSUTCDATETIME())
                """, accountId, roleId, adminAccountId);
    }

    public List<StaffSummaryResponse> listStaff() {
        return jdbc.query("""
                SELECT a.id, a.login_name, a.status, s.code, s.full_name, r.code AS role_code
                FROM dbo.[Account] a JOIN dbo.Staff s ON s.id = a.staff_id
                LEFT JOIN dbo.AccountRole ar ON ar.account_id = a.id
                LEFT JOIN dbo.[Role] r ON r.id = ar.role_id AND r.account_type = 'STAFF'
                WHERE a.account_type = 'STAFF' ORDER BY s.code, r.code
                """, rs -> {
                    Map<Long, StaffRow> rows = new LinkedHashMap<>();
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        StaffRow row = rows.computeIfAbsent(id, key -> new StaffRow(id, rsString(rs, "code"),
                                rsString(rs, "full_name"), rsString(rs, "login_name"), rsString(rs, "status")));
                        String role = rs.getString("role_code");
                        if (role != null) row.roles.add(role);
                    }
                    return rows.values().stream().map(row -> new StaffSummaryResponse(row.id, row.code,
                            row.name, row.login, row.status, List.copyOf(row.roles))).toList();
                });
    }

    private String rsString(java.sql.ResultSet rs, String column) {
        try { return rs.getString(column); }
        catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
    }

    public StaffAccount lockStaff(long accountId) {
        List<StaffAccount> rows = jdbc.query("""
                SELECT id, status FROM dbo.[Account] WITH (UPDLOCK, HOLDLOCK)
                WHERE id = ? AND account_type = 'STAFF'
                """, (rs, row) -> new StaffAccount(rs.getLong("id"), rs.getString("status")), accountId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean hasAdminRole(long accountId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.AccountRole ar JOIN dbo.[Role] r ON r.id = ar.role_id
                WHERE ar.account_id = ? AND r.code = 'SYSTEM_ADMIN'
                """, Integer.class, accountId);
        return count != null && count > 0;
    }

    public Role findStaffRole(String code) {
        List<Role> roles = findStaffRoles(List.of(code));
        return roles.isEmpty() ? null : roles.getFirst();
    }

    public boolean hasRole(long accountId, long roleId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AccountRole WHERE account_id = ? AND role_id = ?",
                Integer.class, accountId, roleId);
        return count != null && count > 0;
    }

    public void revokeRole(long accountId, long roleId) {
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ? AND role_id = ?", accountId, roleId);
    }

    public void updateStatus(long accountId, String status) {
        jdbc.update("UPDATE dbo.[Account] SET status = ?, updated_at = SYSUTCDATETIME() WHERE id = ?", status, accountId);
    }

    public void audit(long actorId, String action, long accountId, String detail) {
        jdbc.update("""
                INSERT INTO dbo.AuditEvent (actor_id, actor_type, action, entity_type, entity_id,
                                            occurred_at, correlation_id, redacted_change)
                VALUES (?, 'USER', ?, 'Account', ?, SYSUTCDATETIME(), ?, ?)
                """, actorId, action, accountId, UUID.randomUUID().toString(), detail);
    }

    public record StaffAccount(long id, String status) { }

    private static class StaffRow {
        final long id;
        final String code;
        final String name;
        final String login;
        final String status;
        final List<String> roles = new ArrayList<>();

        StaffRow(long id, String code, String name, String login, String status) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.login = login;
            this.status = status;
        }
    }

    public record Role(long id, String code) { }
}
