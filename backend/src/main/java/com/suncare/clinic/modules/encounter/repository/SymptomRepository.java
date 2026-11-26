package com.suncare.clinic.modules.encounter.repository;

import com.suncare.clinic.modules.encounter.dto.EncounterClinicalResponse.Symptom;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SymptomRepository {
    private final JdbcTemplate jdbc;
    public SymptomRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Symptom> search(String term) {
        String escaped = term.replace("~", "~~").replace("%", "~%").replace("_", "~_").replace("[", "~[");
        return jdbc.query("""
                SELECT TOP 20 id,name FROM dbo.Symptom WHERE is_active=1
                AND normalized_name LIKE ? COLLATE Latin1_General_100_CI_AI ESCAPE '~'
                ORDER BY CASE WHEN normalized_name=? COLLATE Latin1_General_100_CI_AI THEN 0 ELSE 1 END,name,id
                """, (rs, n) -> new Symptom(rs.getLong("id"), rs.getString("name")), "%" + escaped + "%", term);
    }

    public Symptom active(long id) {
        return jdbc.query("SELECT id,name FROM dbo.Symptom WITH (HOLDLOCK) WHERE id=? AND is_active=1",
                (rs, n) -> new Symptom(rs.getLong(1), rs.getString(2)), id).stream().findFirst().orElse(null);
    }

    public List<Symptom> selections(long encounterId) {
        return jdbc.query("SELECT symptom_id,name_snapshot FROM dbo.EncounterSymptom WHERE encounter_id=? ORDER BY display_order",
                (rs, n) -> new Symptom(rs.getObject(1, Long.class), rs.getString(2)), encounterId);
    }

    public void replace(long encounterId, List<Selection> selections) {
        jdbc.update("DELETE FROM dbo.EncounterSymptom WHERE encounter_id=?", encounterId);
        for (int index = 0; index < selections.size(); index++) {
            Selection item = selections.get(index);
            jdbc.update("""
                    INSERT dbo.EncounterSymptom(encounter_id,symptom_id,name_snapshot,normalized_name,display_order)
                    VALUES (?,?,?,?,?)
                    """, encounterId, item.symptomId(), item.name(), item.normalizedName(), index + 1);
        }
    }

    public record Selection(Long symptomId, String name, String normalizedName) {}
}
