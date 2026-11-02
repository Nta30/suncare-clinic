package com.suncare.clinic.modules.organization.repository;

import com.suncare.clinic.modules.organization.dto.DepartmentRequest;
import com.suncare.clinic.modules.organization.dto.DepartmentResponse;
import com.suncare.clinic.modules.organization.dto.DepartmentSpecialtyResponse;
import com.suncare.clinic.modules.organization.dto.OrganizationCatalogResponse;
import com.suncare.clinic.modules.organization.dto.SpecialtyRequest;
import com.suncare.clinic.modules.organization.dto.SpecialtyResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Repository
public class OrganizationCatalogRepository {
    private final JdbcTemplate jdbc;

    public OrganizationCatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OrganizationCatalogResponse list() {
        List<DepartmentResponse> departments = jdbc.query("""
                SELECT id, code, name, department_type, is_active, version
                FROM dbo.Department ORDER BY name, id
                """, (rs, row) -> new DepartmentResponse(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("department_type"), rs.getBoolean("is_active"),
                Base64.getEncoder().encodeToString(rs.getBytes("version"))));
        List<SpecialtyResponse> specialties = jdbc.query("""
                SELECT id, code, name, is_active, version FROM dbo.Specialty ORDER BY name, id
                """, (rs, row) -> new SpecialtyResponse(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getBoolean("is_active"),
                Base64.getEncoder().encodeToString(rs.getBytes("version"))));
        List<DepartmentSpecialtyResponse> links = jdbc.query("""
                SELECT department_id, specialty_id, is_active
                FROM dbo.DepartmentSpecialty ORDER BY department_id, specialty_id
                """, (rs, row) -> new DepartmentSpecialtyResponse(rs.getLong("department_id"),
                rs.getLong("specialty_id"), rs.getBoolean("is_active")));
        return new OrganizationCatalogResponse(departments, specialties, links);
    }

    public long createDepartment(DepartmentRequest request) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?)
                """, Long.class, request.code().trim().toUpperCase(), request.name().trim(),
                request.departmentType(), request.isActive());
    }

    public int updateDepartment(long id, DepartmentRequest request, byte[] version) {
        return jdbc.update("""
                UPDATE dbo.Department SET code = ?, name = ?, department_type = ?, is_active = ?,
                                          updated_at = SYSUTCDATETIME()
                WHERE id = ? AND version = ?
                """, request.code().trim().toUpperCase(), request.name().trim(), request.departmentType(),
                request.isActive(), id, version);
    }

    public long createSpecialty(SpecialtyRequest request) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Specialty (code, name, is_active)
                OUTPUT INSERTED.id VALUES (?, ?, ?)
                """, Long.class, request.code().trim().toUpperCase(), request.name().trim(), request.isActive());
    }

    public int updateSpecialty(long id, SpecialtyRequest request, byte[] version) {
        return jdbc.update("""
                UPDATE dbo.Specialty SET code = ?, name = ?, is_active = ?, updated_at = SYSUTCDATETIME()
                WHERE id = ? AND version = ?
                """, request.code().trim().toUpperCase(), request.name().trim(), request.isActive(), id, version);
    }

    public boolean departmentExists(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Department WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean specialtyExists(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Specialty WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean hasActiveLinks(long departmentId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.DepartmentSpecialty WHERE department_id = ? AND is_active = 1",
                Integer.class, departmentId);
        return count != null && count > 0;
    }

    public boolean activeClinicalDepartment(long id) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Department WITH (UPDLOCK, HOLDLOCK)
                WHERE id = ? AND department_type = 'CLINICAL' AND is_active = 1
                """, Integer.class, id);
        return count != null && count > 0;
    }

    public boolean activeSpecialty(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Specialty WITH (UPDLOCK, HOLDLOCK) WHERE id = ? AND is_active = 1",
                Integer.class, id);
        return count != null && count > 0;
    }

    public Boolean linkStatus(long departmentId, long specialtyId) {
        List<Boolean> rows = jdbc.query("""
                SELECT is_active FROM dbo.DepartmentSpecialty WITH (UPDLOCK, HOLDLOCK)
                WHERE department_id = ? AND specialty_id = ?
                """, (rs, row) -> rs.getBoolean(1), departmentId, specialtyId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void createLink(long departmentId, long specialtyId) {
        jdbc.update("INSERT INTO dbo.DepartmentSpecialty (department_id, specialty_id) VALUES (?, ?)", departmentId, specialtyId);
    }

    public void setLinkStatus(long departmentId, long specialtyId, boolean active) {
        jdbc.update("""
                UPDATE dbo.DepartmentSpecialty SET is_active = ?, updated_at = SYSUTCDATETIME()
                WHERE department_id = ? AND specialty_id = ?
                """, active, departmentId, specialtyId);
    }

    public void audit(long actorId, String action, String entity, long entityId) {
        audit(actorId, action, entity, entityId, null);
    }

    public void audit(long actorId, String action, String entity, long entityId, String detail) {
        jdbc.update("""
                INSERT INTO dbo.AuditEvent (actor_id, actor_type, action, entity_type, entity_id,
                                            occurred_at, correlation_id, redacted_change)
                VALUES (?, 'USER', ?, ?, ?, SYSUTCDATETIME(), ?, ?)
                """, actorId, action, entity, entityId, UUID.randomUUID().toString(), detail);
    }
}
