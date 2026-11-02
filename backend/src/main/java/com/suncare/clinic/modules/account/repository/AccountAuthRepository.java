package com.suncare.clinic.modules.account.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.sql.Timestamp;

@Repository
public class AccountAuthRepository {
    private final JdbcTemplate jdbc;

    public AccountAuthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Account(long id, String loginName, String passwordHash, String accountType,
                          Long patientId, Long staffId, String status) { }

    public Account findByLogin(String loginName) {
        List<Account> rows = jdbc.query("""
                SELECT id, login_name, password_hash, account_type, patient_id, staff_id, status
                FROM dbo.[Account] WHERE login_name = ?
                """, (rs, row) -> new Account(rs.getLong("id"), rs.getString("login_name"),
                rs.getString("password_hash"), rs.getString("account_type"),
                rs.getObject("patient_id", Long.class), rs.getObject("staff_id", Long.class),
                rs.getString("status")), loginName);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Account findById(long id) {
        List<Account> rows = jdbc.query("""
                SELECT id, login_name, password_hash, account_type, patient_id, staff_id, status
                FROM dbo.[Account] WHERE id = ?
                """, (rs, row) -> new Account(rs.getLong("id"), rs.getString("login_name"),
                rs.getString("password_hash"), rs.getString("account_type"),
                rs.getObject("patient_id", Long.class), rs.getObject("staff_id", Long.class),
                rs.getString("status")), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<String> roles(long accountId) {
        return jdbc.queryForList("""
                SELECT r.code FROM dbo.AccountRole ar JOIN dbo.[Role] r ON r.id = ar.role_id
                JOIN dbo.[Account] a ON a.id = ar.account_id
                WHERE ar.account_id = ? AND r.account_type = a.account_type ORDER BY r.code
                """, String.class, accountId);
    }

    public List<String> permissions(long accountId) {
        return jdbc.queryForList("""
                SELECT DISTINCT p.code FROM dbo.AccountRole ar
                JOIN dbo.[Role] r ON r.id = ar.role_id
                JOIN dbo.[Account] a ON a.id = ar.account_id AND a.account_type = r.account_type
                JOIN dbo.RolePermission rp ON rp.role_id = r.id
                JOIN dbo.[Permission] p ON p.id = rp.permission_id
                WHERE ar.account_id = ? ORDER BY p.code
                """, String.class, accountId);
    }

    public void recordLogin(long accountId) {
        jdbc.update("UPDATE dbo.[Account] SET last_login_at = SYSUTCDATETIME() WHERE id = ?", accountId);
    }

    public void createToken(UUID id, long accountId, Instant expiresAt) {
        jdbc.update("INSERT INTO dbo.AuthToken (id, account_id, expires_at) VALUES (?, ?, ?)",
                id, accountId, Timestamp.from(expiresAt));
    }

    public boolean isTokenActive(UUID id, long accountId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.AuthToken
                WHERE id = ? AND account_id = ? AND revoked_at IS NULL AND expires_at > SYSUTCDATETIME()
                """, Integer.class, id, accountId);
        return count != null && count == 1;
    }

    public void revokeToken(UUID id, long accountId) {
        jdbc.update("UPDATE dbo.AuthToken SET revoked_at = SYSUTCDATETIME() WHERE id = ? AND account_id = ? AND revoked_at IS NULL",
                id, accountId);
    }
}
