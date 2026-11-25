package com.suncare.clinic.modules.encounter.repository;

import com.suncare.clinic.modules.encounter.dto.AllergenRequest;
import com.suncare.clinic.modules.encounter.dto.AllergenResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Base64;
import java.util.List;

@Repository
public class AllergenRepository {
    private final JdbcTemplate jdbc;
    public AllergenRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<AllergenResponse> list(boolean activeOnly) {
        return jdbc.query("SELECT id, code, name, is_active, version FROM dbo.Allergen "
                + (activeOnly ? "WHERE is_active = 1 " : "") + "ORDER BY name, id",
                (rs, n) -> new AllergenResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getBoolean("is_active"), Base64.getEncoder().encodeToString(rs.getBytes("version"))));
    }

    public AllergenResponse find(long id) {
        return jdbc.query("SELECT id, code, name, is_active, version FROM dbo.Allergen WITH (UPDLOCK, HOLDLOCK) WHERE id = ?",
                (rs, n) -> new AllergenResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getBoolean("is_active"), Base64.getEncoder().encodeToString(rs.getBytes("version"))), id).stream().findFirst().orElse(null);
    }

    public long create(AllergenRequest request) {
        return jdbc.queryForObject("INSERT INTO dbo.Allergen (code, name, is_active) OUTPUT INSERTED.id VALUES (?, ?, ?)",
                Long.class, request.code().toUpperCase(java.util.Locale.ROOT), cleanName(request.name()), request.isActive());
    }

    public int update(long id, AllergenRequest request, byte[] version) {
        return jdbc.update("UPDATE dbo.Allergen SET name = ?, is_active = ?, updated_at = SYSUTCDATETIME() WHERE id = ? AND version = ?",
                cleanName(request.name()), request.isActive(), id, version);
    }

    private String cleanName(String name) { return name.strip().replaceAll("\\s+", " "); }
}
