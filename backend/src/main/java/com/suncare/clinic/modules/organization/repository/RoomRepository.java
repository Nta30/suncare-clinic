package com.suncare.clinic.modules.organization.repository;

import com.suncare.clinic.modules.organization.dto.RoomRequest;
import com.suncare.clinic.modules.organization.dto.RoomResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Base64;
import java.util.List;

@Repository
public class RoomRepository {
    private final JdbcTemplate jdbc;

    public RoomRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RoomResponse> list() {
        return jdbc.query("""
                SELECT r.id, r.code, r.name, r.room_type, r.department_id, r.is_active, r.version,
                       d.name AS department_name, d.department_type, d.is_active AS department_active
                FROM dbo.Room r JOIN dbo.Department d ON d.id = r.department_id
                ORDER BY d.name, r.name, r.id
                """, (rs, row) -> new RoomResponse(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("room_type"), rs.getLong("department_id"),
                rs.getString("department_name"), rs.getString("department_type"),
                rs.getBoolean("department_active"), rs.getBoolean("is_active"),
                Base64.getEncoder().encodeToString(rs.getBytes("version"))));
    }

    public String activeDepartmentType(long departmentId) {
        List<String> rows = jdbc.query("""
                SELECT department_type FROM dbo.Department WITH (UPDLOCK, HOLDLOCK)
                WHERE id = ? AND is_active = 1
                """, (rs, row) -> rs.getString(1), departmentId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public long create(RoomRequest request) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Room (code, name, room_type, department_id, is_active)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?)
                """, Long.class, request.code().trim().toUpperCase(), request.name().trim(),
                request.roomType(), request.departmentId(), request.isActive());
    }

    public RoomIdentity find(long id) {
        List<RoomIdentity> rows = jdbc.query("""
                SELECT department_id, room_type FROM dbo.Room WITH (UPDLOCK, HOLDLOCK) WHERE id = ?
                """, (rs, row) -> new RoomIdentity(rs.getLong("department_id"), rs.getString("room_type")), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public int update(long id, RoomRequest request, byte[] version) {
        return jdbc.update("""
                UPDATE dbo.Room SET code = ?, name = ?, is_active = ?, updated_at = SYSUTCDATETIME()
                WHERE id = ? AND version = ?
                """, request.code().trim().toUpperCase(), request.name().trim(), request.isActive(), id, version);
    }

    public record RoomIdentity(long departmentId, String roomType) { }
}
