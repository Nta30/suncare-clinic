package com.suncare.clinic.modules.prescription.repository;

import com.suncare.clinic.modules.prescription.dto.DrugRequest;
import com.suncare.clinic.modules.prescription.dto.DrugResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Repository
public class DrugRepository {
    private final JdbcTemplate jdbc;
    private static final String COLUMNS = "id, code, name, ingredient, strength, dosage_form, unit, is_active, version, dose_unit, dispensing_unit";
    private static final RowMapper<DrugResponse> MAPPER = (rs, n) -> new DrugResponse(
            rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("ingredient"),
            rs.getString("strength"), rs.getString("dosage_form"), rs.getString("unit"),
            rs.getBoolean("is_active"), Base64.getEncoder().encodeToString(rs.getBytes("version")),
            rs.getString("dose_unit"), rs.getString("dispensing_unit"));

    public DrugRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<DrugResponse> list() {
        return list(null, false);
    }

    public List<DrugResponse> list(String search, boolean activeOnly) {
        String term = search == null ? "" : search.strip().replace("~", "~~").replace("%", "~%").replace("_", "~_").replace("[", "~[");
        String sql = "SELECT " + COLUMNS + " FROM dbo.Drug WHERE " + (activeOnly ? "is_active=1 AND " : "")
                + "REPLACE(CONCAT(code,N' ',name,N' ',ingredient) COLLATE Latin1_General_100_CI_AI,N'đ',N'd') "
                + "LIKE REPLACE(? COLLATE Latin1_General_100_CI_AI,N'đ',N'd') ESCAPE '~' ORDER BY name,id";
        return jdbc.query(sql, MAPPER, "%" + term + "%");
    }

    public DrugResponse find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM dbo.Drug WITH (UPDLOCK, HOLDLOCK) WHERE id = ?",
                MAPPER, id).stream().findFirst().orElse(null);
    }

    public long create(DrugRequest request) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Drug (code, name, ingredient, strength, dosage_form, unit, is_active, dose_unit, dispensing_unit)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Long.class, request.code().toUpperCase(Locale.ROOT), clean(request.name()),
                clean(request.ingredient()), clean(request.strength()), clean(request.dosageForm()),
                clean(request.dispensingUnit()), request.isActive(), clean(request.doseUnit()), clean(request.dispensingUnit()));
    }

    public int update(long id, DrugRequest request, byte[] version) {
        return jdbc.update("""
                UPDATE dbo.Drug SET name = ?, ingredient = ?, strength = ?, dosage_form = ?,
                    unit = ?, is_active = ?, dose_unit = ?, dispensing_unit = ?, updated_at = SYSUTCDATETIME()
                WHERE id = ? AND version = ?
                """, clean(request.name()), clean(request.ingredient()), clean(request.strength()),
                clean(request.dosageForm()), clean(request.dispensingUnit()), request.isActive(),
                clean(request.doseUnit()), clean(request.dispensingUnit()), id, version);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
