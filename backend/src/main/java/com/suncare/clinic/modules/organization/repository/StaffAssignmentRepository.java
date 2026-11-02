package com.suncare.clinic.modules.organization.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class StaffAssignmentRepository {
    private final JdbcTemplate jdbc;

    public StaffAssignmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean lockStaff(long id) {
        return !jdbc.query("SELECT id FROM dbo.Staff WITH (UPDLOCK, HOLDLOCK) WHERE id = ?",
                (rs, row) -> rs.getLong(1), id).isEmpty();
    }

    public boolean activeStaff(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Staff WHERE id = ? AND is_active = 1", Integer.class, id);
        return count != null && count > 0;
    }

    public String lockDepartment(long id) {
        List<String> rows = jdbc.query("SELECT department_type FROM dbo.Department WITH (UPDLOCK, HOLDLOCK) WHERE id = ? AND is_active = 1",
                (rs, row) -> rs.getString(1), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean hasRole(long staffId, String role) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.[Account] a JOIN dbo.AccountRole ar ON ar.account_id = a.id
                JOIN dbo.[Role] r ON r.id = ar.role_id
                WHERE a.staff_id = ? AND a.status = 'ACTIVE' AND r.code = ?
                """, Integer.class, staffId, role);
        return count != null && count > 0;
    }

    public boolean activeSpecialty(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Specialty WHERE id = ? AND is_active = 1", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean linkedSpecialty(long staffId, long specialtyId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.StaffDepartment sd
                JOIN dbo.DepartmentSpecialty ds ON ds.department_id = sd.department_id AND ds.is_active = 1
                WHERE sd.staff_id = ? AND ds.specialty_id = ? AND sd.valid_from <= SYSUTCDATETIME()
                  AND (sd.valid_to IS NULL OR sd.valid_to > SYSUTCDATETIME())
                """, Integer.class, staffId, specialtyId);
        return count != null && count > 0;
    }

    public boolean overlaps(String table, String key, long value, LocalDateTime from, LocalDateTime to, Long excludedId) {
        String sql = "SELECT COUNT(*) FROM dbo." + table + " WHERE " + key + " = ? AND valid_from < ? AND (valid_to IS NULL OR valid_to > ?)";
        Integer count = excludedId == null
                ? jdbc.queryForObject(sql, Integer.class, value, to == null ? LocalDateTime.of(9999, 1, 1, 0, 0) : to, from)
                : jdbc.queryForObject(sql + " AND id <> ?", Integer.class, value,
                        to == null ? LocalDateTime.of(9999, 1, 1, 0, 0) : to, from, excludedId);
        return count != null && count > 0;
    }

    public boolean coversMembership(long staffId, long departmentId, LocalDateTime from, LocalDateTime to) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.StaffDepartment WHERE staff_id = ? AND department_id = ?
                  AND valid_from <= ? AND (valid_to IS NULL OR valid_to >= ?)
                """, Integer.class, staffId, departmentId, from,
                to == null ? LocalDateTime.of(9999, 1, 1, 0, 0) : to);
        return count != null && count > 0;
    }

    public boolean headWithinMembership(long assignmentId, LocalDateTime end) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.DepartmentHeadAssignment h
                WHERE h.staff_id = (SELECT staff_id FROM dbo.StaffDepartment WHERE id = ?)
                  AND h.department_id = (SELECT department_id FROM dbo.StaffDepartment WHERE id = ?)
                  AND (h.valid_to IS NULL OR h.valid_to > ?)
                """, Integer.class, assignmentId, assignmentId, end);
        return count != null && count > 0;
    }

    public long insertDepartment(long staffId, long departmentId, LocalDateTime from, LocalDateTime to, boolean primary) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.StaffDepartment(staff_id, department_id, valid_from, valid_to, is_primary)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?)
                """, Long.class, staffId, departmentId, from, to, primary);
    }

    public long insertHead(long staffId, long departmentId, LocalDateTime from, LocalDateTime to, long actorId) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.DepartmentHeadAssignment(staff_id, department_id, valid_from, valid_to, assigned_by)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?)
                """, Long.class, staffId, departmentId, from, to, actorId);
    }

    public int end(String table, long id, LocalDateTime end) {
        return jdbc.update("UPDATE dbo." + table + " SET valid_to = ?, updated_at = SYSUTCDATETIME() " +
                "WHERE id = ? AND valid_from < ? AND (valid_to IS NULL OR valid_to > ?)", end, id, end, end);
    }

    public Map<String, Object> period(String table, long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT staff_id, department_id, valid_from, valid_to FROM dbo." + table + " WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void specialty(long staffId, long specialtyId, boolean active) {
        jdbc.update("""
                MERGE dbo.StaffSpecialty WITH (HOLDLOCK) AS target
                USING (SELECT ? AS staff_id, ? AS specialty_id) AS source
                ON target.staff_id = source.staff_id AND target.specialty_id = source.specialty_id
                WHEN MATCHED THEN UPDATE SET is_active = ?, updated_at = SYSUTCDATETIME()
                WHEN NOT MATCHED THEN INSERT(staff_id, specialty_id, is_active) VALUES(source.staff_id, source.specialty_id, ?);
                """, staffId, specialtyId, active, active);
    }

    public List<Map<String, Object>> departments() {
        return jdbc.queryForList("""
                SELECT sd.id, sd.staff_id AS staffId, s.full_name AS staffName, sd.department_id AS departmentId,
                       d.name AS departmentName,
                       CONVERT(varchar(23), sd.valid_from, 126) AS validFrom,
                       CONVERT(varchar(23), sd.valid_to, 126) AS validTo, sd.is_primary AS isPrimary
                FROM dbo.StaffDepartment sd JOIN dbo.Staff s ON s.id = sd.staff_id
                JOIN dbo.Department d ON d.id = sd.department_id ORDER BY sd.valid_from DESC, sd.id DESC
                """);
    }

    public List<Map<String, Object>> heads() {
        return jdbc.queryForList("""
                SELECT h.id, h.staff_id AS staffId, s.full_name AS staffName, h.department_id AS departmentId,
                       d.name AS departmentName,
                       CONVERT(varchar(23), h.valid_from, 126) AS validFrom,
                       CONVERT(varchar(23), h.valid_to, 126) AS validTo
                FROM dbo.DepartmentHeadAssignment h JOIN dbo.Staff s ON s.id = h.staff_id
                JOIN dbo.Department d ON d.id = h.department_id ORDER BY h.valid_from DESC, h.id DESC
                """);
    }

    public List<Map<String, Object>> specialties() {
        return jdbc.queryForList("""
                SELECT ss.staff_id AS staffId, s.full_name AS staffName, ss.specialty_id AS specialtyId,
                       sp.name AS specialtyName, ss.is_active AS isActive
                FROM dbo.StaffSpecialty ss JOIN dbo.Staff s ON s.id = ss.staff_id
                JOIN dbo.Specialty sp ON sp.id = ss.specialty_id ORDER BY s.full_name, sp.name
                """);
    }
}
