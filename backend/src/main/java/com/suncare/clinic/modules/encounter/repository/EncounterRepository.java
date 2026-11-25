package com.suncare.clinic.modules.encounter.repository;

import com.suncare.clinic.modules.encounter.dto.EncounterStartResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class EncounterRepository {
    private final JdbcTemplate jdbc;

    public EncounterRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long appointmentId(long encounterId) {
        return jdbc.query("SELECT appointment_id FROM dbo.Encounter WHERE id = ?",
                (rs, n) -> rs.getLong(1), encounterId).stream().findFirst().orElse(null);
    }

    public long patientId(long appointmentId) {
        return jdbc.queryForObject("SELECT patient_id FROM dbo.Appointment WHERE id = ?", Long.class, appointmentId);
    }

    public void lockPatient(long patientId) {
        jdbc.queryForObject("SELECT id FROM dbo.Patient WITH (UPDLOCK, HOLDLOCK) WHERE id = ?", Long.class, patientId);
    }

    public Appointment lockAppointment(long appointmentId, long doctorId) {
        return jdbc.query("""
                SELECT patient_id, status, visit_mode FROM dbo.Appointment WITH (UPDLOCK, HOLDLOCK)
                WHERE id = ? AND doctor_id = ?
                """, (rs, n) -> new Appointment(rs.getLong("patient_id"), rs.getString("status"),
                rs.getString("visit_mode")), appointmentId, doctorId).stream().findFirst().orElse(null);
    }

    public String lockStatus(long encounterId) {
        return jdbc.query("SELECT status FROM dbo.Encounter WITH (UPDLOCK, HOLDLOCK) WHERE id = ?",
                (rs, n) -> rs.getString(1), encounterId).stream().findFirst().orElse(null);
    }

    public String history(long patientId) {
        List<String> items = jdbc.query("""
                SELECT history_type, description FROM dbo.PatientHistory WITH (HOLDLOCK)
                WHERE patient_id = ? AND is_active = 1 ORDER BY id
                """, (rs, n) -> rs.getString("history_type") + ": " + rs.getString("description"), patientId);
        return String.join("\n", items);
    }

    public String allergies(long patientId) {
        List<String> items = jdbc.query("""
                SELECT pa.substance, pa.other_note, pa.allergen_id, al.code, pa.reaction, pa.severity
                FROM dbo.PatientAllergy pa WITH (HOLDLOCK)
                LEFT JOIN dbo.Allergen al ON al.id = pa.allergen_id
                WHERE pa.patient_id = ? AND pa.is_active = 1 ORDER BY pa.id
                """, (rs, n) -> (rs.getObject("allergen_id") == null ? rs.getString("other_note")
                : rs.getString("code") + " - " + rs.getString("substance")) + " [" + rs.getString("severity") + "]"
                + (rs.getString("reaction") == null ? "" : ": " + rs.getString("reaction")), patientId);
        return String.join("\n", items);
    }

    public void insertSnapshot(long encounterId, long actorId, String history, String allergies) {
        jdbc.update("""
                INSERT INTO dbo.EncounterHistorySnapshot (encounter_id, history_text, allergy_text, recorded_by, recorded_at)
                VALUES (?, ?, ?, ?, SYSUTCDATETIME())
                """, encounterId, history, allergies, actorId);
    }

    public void start(long encounterId, long actorId) {
        jdbc.update("""
                UPDATE dbo.Encounter SET status = 'IN_PROGRESS', started_at = SYSUTCDATETIME(),
                    updated_at = SYSUTCDATETIME() WHERE id = ? AND status = 'PLANNED'
                """, encounterId);
        jdbc.update("""
                INSERT INTO dbo.EncounterEvent (encounter_id, from_status, to_status, actor_id, occurred_at)
                VALUES (?, 'PLANNED', 'IN_PROGRESS', ?, SYSUTCDATETIME())
                """, encounterId, actorId);
        jdbc.update("""
                INSERT INTO dbo.AuditEvent (actor_id, actor_type, action, entity_type, entity_id, occurred_at, correlation_id)
                VALUES (?, 'USER', 'ENCOUNTER_STARTED', 'Encounter', ?, SYSUTCDATETIME(), ?)
                """, actorId, encounterId, UUID.randomUUID().toString());
    }

    public EncounterStartResponse result(long encounterId, boolean replayed) {
        return jdbc.queryForObject("""
                SELECT e.id, e.code, e.status, CONVERT(varchar(23), e.started_at, 126) + 'Z' AS started_at,
                       s.history_text, s.allergy_text
                FROM dbo.Encounter e JOIN dbo.EncounterHistorySnapshot s ON s.encounter_id = e.id WHERE e.id = ?
                """, (rs, n) -> new EncounterStartResponse(rs.getLong("id"), rs.getString("code"),
                rs.getString("status"), rs.getString("started_at"), rs.getString("history_text"),
                rs.getString("allergy_text"), replayed), encounterId);
    }

    public record Appointment(long patientId, String status, String visitMode) {}
}
