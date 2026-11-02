package com.suncare.clinic.modules.scheduling.repository;

import com.suncare.clinic.modules.scheduling.dto.ShiftDefinitionResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;

@Repository
public class ShiftDefinitionRepository {
    private final JdbcTemplate jdbc;

    public ShiftDefinitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ShiftDefinitionResponse> listAll() {
        String sql = """
                SELECT id, code, name, local_start, local_end, is_active,
                       CONVERT(varchar(23), created_at, 126) + 'Z' AS created_at,
                       CONVERT(varchar(23), updated_at, 126) + 'Z' AS updated_at,
                       version
                FROM dbo.ShiftDefinition
                ORDER BY local_start ASC, id ASC
                """;
        return jdbc.query(sql, (rs, rowNum) -> new ShiftDefinitionResponse(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getTime("local_start").toLocalTime(),
                rs.getTime("local_end").toLocalTime(),
                rs.getBoolean("is_active"),
                rs.getString("created_at"),
                rs.getString("updated_at"),
                Base64.getEncoder().encodeToString(rs.getBytes("version"))
        ));
    }

    public ShiftDefinitionResponse findById(long id) {
        String sql = """
                SELECT id, code, name, local_start, local_end, is_active,
                       CONVERT(varchar(23), created_at, 126) + 'Z' AS created_at,
                       CONVERT(varchar(23), updated_at, 126) + 'Z' AS updated_at,
                       version
                FROM dbo.ShiftDefinition
                WHERE id = ?
                """;
        List<ShiftDefinitionResponse> list = jdbc.query(sql, (rs, rowNum) -> new ShiftDefinitionResponse(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getTime("local_start").toLocalTime(),
                rs.getTime("local_end").toLocalTime(),
                rs.getBoolean("is_active"),
                rs.getString("created_at"),
                rs.getString("updated_at"),
                Base64.getEncoder().encodeToString(rs.getBytes("version"))
        ), id);
        return list.isEmpty() ? null : list.getFirst();
    }

    public ShiftDefinitionIdentity lockShift(long id) {
        String sql = """
                SELECT id, code, name, local_start, local_end, is_active, version
                FROM dbo.ShiftDefinition WITH (UPDLOCK, HOLDLOCK)
                WHERE id = ?
                """;
        List<ShiftDefinitionIdentity> list = jdbc.query(sql, (rs, rowNum) -> new ShiftDefinitionIdentity(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getTime("local_start").toLocalTime(),
                rs.getTime("local_end").toLocalTime(),
                rs.getBoolean("is_active"),
                rs.getBytes("version")
        ), id);
        return list.isEmpty() ? null : list.getFirst();
    }

    public boolean existsByCode(String code) {
        String sql = "SELECT COUNT(*) FROM dbo.ShiftDefinition WHERE code = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, code);
        return count != null && count > 0;
    }

    public long insert(String code, String name, LocalTime start, LocalTime end, boolean isActive) {
        String sql = """
                INSERT INTO dbo.ShiftDefinition (code, name, local_start, local_end, is_active)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?)
                """;
        return jdbc.queryForObject(sql, Long.class, code, name, Time.valueOf(start), Time.valueOf(end), isActive);
    }

    public int update(long id, String name, LocalTime start, LocalTime end, boolean isActive, byte[] version) {
        String sql = """
                UPDATE dbo.ShiftDefinition
                SET name = ?, local_start = ?, local_end = ?, is_active = ?, updated_at = SYSUTCDATETIME()
                WHERE id = ? AND version = ?
                """;
        return jdbc.update(sql, name, Time.valueOf(start), Time.valueOf(end), isActive, id, version);
    }

    public void audit(long actorId, String action, String entity, Long entityId, String detail) {
        jdbc.update("""
                INSERT INTO dbo.AuditEvent (actor_id, actor_type, action, entity_type, entity_id,
                                            occurred_at, correlation_id, redacted_change)
                VALUES (?, 'USER', ?, ?, ?, SYSUTCDATETIME(), ?, ?)
                """, actorId, action, entity, entityId, java.util.UUID.randomUUID().toString(), detail);
    }

    public record ShiftDefinitionIdentity(
            long id,
            String code,
            String name,
            LocalTime localStart,
            LocalTime localEnd,
            boolean isActive,
            byte[] version
    ) {
    }
}
