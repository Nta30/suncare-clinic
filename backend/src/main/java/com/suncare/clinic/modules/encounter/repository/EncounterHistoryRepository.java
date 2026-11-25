package com.suncare.clinic.modules.encounter.repository;

import com.suncare.clinic.modules.encounter.dto.EncounterHistoryResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterWorkItemResponse;
import com.suncare.clinic.modules.encounter.dto.PatientHistoryRequest;
import com.suncare.clinic.modules.encounter.dto.PatientAllergyRequest;
import com.suncare.clinic.modules.encounter.dto.EncounterWorklistResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

@Repository
public class EncounterHistoryRepository {
    private static final String WORK_COLUMNS = """
            SELECT e.id, e.code, e.status, a.code AS appointment_code, a.status AS appointment_status,
                   p.code AS patient_code, p.full_name AS patient_name,
                   CONVERT(varchar(23), wa.starts_at, 126) + 'Z' AS starts_at,
                   CONVERT(varchar(23), wa.ends_at, 126) + 'Z' AS ends_at,
                   r.name AS room_name, sp.name AS specialty_name
            """;
    private static final String WORK_FROM = """
            FROM dbo.Encounter e
            JOIN dbo.Appointment a ON a.id = e.appointment_id
            JOIN dbo.Patient p ON p.id = a.patient_id
            JOIN dbo.WorkAssignment wa ON wa.id = a.onsite_assignment_id
            JOIN dbo.Room r ON r.id = wa.room_id
            JOIN dbo.Specialty sp ON sp.id = a.specialty_id
            WHERE a.doctor_id = ? AND a.visit_mode = 'ONSITE'
            """;
    private static final String WORK_ITEMS = WORK_COLUMNS + WORK_FROM;
    private final JdbcTemplate jdbc;

    public EncounterHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EncounterWorklistResponse worklist(long doctorId, LocalDate date, String status,
            List<String> tokens, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" AND wa.work_date = ?");
        List<Object> args = new ArrayList<>(List.of(doctorId, date));
        if (!"ALL".equals(status)) {
            where.append(" AND e.status = ?");
            args.add(status);
        }
        for (String token : tokens) {
            // Treat SQL LIKE metacharacters as ordinary search text, not wildcard instructions.
            String literal = token.replace("~", "~~").replace("%", "~%")
                    .replace("_", "~_").replace("[", "~[");
            String pattern = "%" + literal + "%";
            where.append("""
                     AND (REPLACE(REPLACE(p.full_name, N'đ', N'd'), N'Đ', N'D') COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '~'
                       OR p.code COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '~'
                       OR e.code COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '~'
                       OR a.code COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '~')
                    """);
            args.addAll(List.of(pattern, pattern, pattern, pattern));
        }
        String query = WORK_ITEMS + where;
        Long total = jdbc.queryForObject("SELECT COUNT_BIG(*) " + WORK_FROM + where,
                Long.class, args.toArray());
        long count = total == null ? 0 : total;
        int pages = (int) Math.min(Integer.MAX_VALUE, (count + pageSize - 1) / pageSize);
        int current = Math.min(page, Math.max(1, pages));
        args.add((long) (current - 1) * pageSize);
        args.add(pageSize);
        List<EncounterWorkItemResponse> items = jdbc.query(query + " ORDER BY wa.starts_at, a.booked_at, e.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, n) -> workItem(rs), args.toArray());
        return new EncounterWorklistResponse(items, count, current, pageSize, pages);
    }

    public EncounterWorkItemResponse find(long doctorId, long encounterId) {
        return jdbc.query(WORK_ITEMS + " AND e.id = ?", (rs, n) -> workItem(rs), doctorId, encounterId)
                .stream().findFirst().orElse(null);
    }

    public List<EncounterHistoryResponse.HistoryItem> histories(long patientId) {
        return jdbc.query("""
                SELECT id, history_type, description, is_active, version FROM dbo.PatientHistory
                WHERE patient_id = ? ORDER BY is_active DESC, id
                """, (rs, n) -> new EncounterHistoryResponse.HistoryItem(rs.getLong("id"),
                rs.getString("history_type"), rs.getString("description"), rs.getBoolean("is_active"),
                Base64.getEncoder().encodeToString(rs.getBytes("version"))), patientId);
    }

    public List<EncounterHistoryResponse.AllergyItem> allergies(long patientId) {
        return jdbc.query("""
                SELECT pa.id, pa.substance, pa.allergen_id, al.code AS allergen_code, pa.other_note,
                       pa.reaction, pa.severity, pa.is_active, pa.version FROM dbo.PatientAllergy pa
                LEFT JOIN dbo.Allergen al ON al.id = pa.allergen_id
                WHERE pa.patient_id = ? ORDER BY pa.is_active DESC, pa.id
                """, (rs, n) -> new EncounterHistoryResponse.AllergyItem(rs.getLong("id"),
                rs.getString("substance"), rs.getObject("allergen_id", Long.class), rs.getString("allergen_code"),
                rs.getString("other_note"), rs.getString("reaction"), rs.getString("severity"), rs.getBoolean("is_active"),
                Base64.getEncoder().encodeToString(rs.getBytes("version"))), patientId);
    }

    public EncounterHistoryResponse.Snapshot snapshot(long encounterId) {
        return jdbc.query("""
                SELECT history_text, allergy_text, CONVERT(varchar(23), recorded_at, 126) + 'Z' AS recorded_at
                FROM dbo.EncounterHistorySnapshot WHERE encounter_id = ?
                """, (rs, n) -> new EncounterHistoryResponse.Snapshot(rs.getString("history_text"),
                rs.getString("allergy_text"), rs.getString("recorded_at")), encounterId).stream().findFirst().orElse(null);
    }

    public long createHistory(long patientId, long actorId, PatientHistoryRequest request) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.PatientHistory (patient_id, history_type, description, recorded_by, recorded_at, is_active)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, SYSUTCDATETIME(), ?)
                """, Long.class, patientId, request.historyType(), request.description().strip(), actorId, request.isActive());
    }

    public int updateHistory(long patientId, long itemId, long actorId, PatientHistoryRequest request, byte[] version) {
        return jdbc.update("""
                UPDATE dbo.PatientHistory SET history_type = ?, description = ?, is_active = ?, recorded_by = ?,
                    recorded_at = SYSUTCDATETIME(), updated_at = SYSUTCDATETIME()
                WHERE id = ? AND patient_id = ? AND version = ?
                """, request.historyType(), request.description().strip(), request.isActive(), actorId, itemId, patientId, version);
    }

    public long createAllergy(long patientId, long actorId, PatientAllergyRequest request, String substance) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.PatientAllergy (patient_id, substance, allergen_id, other_note, reaction, severity, recorded_by, is_active)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, Long.class, patientId, substance, request.allergenId(), clean(request.otherNote()), clean(request.reaction()), request.severity(), actorId, request.isActive());
    }

    public int updateAllergy(long patientId, long itemId, long actorId, PatientAllergyRequest request, byte[] version, String substance) {
        return jdbc.update("""
                UPDATE dbo.PatientAllergy SET substance = ?, allergen_id = ?, other_note = ?, reaction = ?, severity = ?, is_active = ?,
                    recorded_by = ?, updated_at = SYSUTCDATETIME()
                WHERE id = ? AND patient_id = ? AND version = ?
                """, substance, request.allergenId(), clean(request.otherNote()), clean(request.reaction()), request.severity(), request.isActive(), actorId, itemId, patientId, version);
    }

    public void audit(long actorId, long encounterId, String action, long itemId) {
        jdbc.update("""
                INSERT INTO dbo.AuditEvent (actor_id, actor_type, action, entity_type, entity_id, occurred_at,
                    correlation_id, redacted_change)
                VALUES (?, 'USER', ?, 'Encounter', ?, SYSUTCDATETIME(), ?, ?)
                """, actorId, action, encounterId, UUID.randomUUID().toString(), "item_id=" + itemId);
    }

    private String clean(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private EncounterWorkItemResponse workItem(ResultSet rs) throws SQLException {
        return new EncounterWorkItemResponse(rs.getLong("id"), rs.getString("code"), rs.getString("status"),
                rs.getString("appointment_code"), rs.getString("appointment_status"), rs.getString("patient_code"),
                rs.getString("patient_name"), rs.getString("starts_at"), rs.getString("ends_at"),
                rs.getString("room_name"), rs.getString("specialty_name"));
    }
}
