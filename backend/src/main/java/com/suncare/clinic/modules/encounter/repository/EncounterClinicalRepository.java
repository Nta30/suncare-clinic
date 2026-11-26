package com.suncare.clinic.modules.encounter.repository;

import com.suncare.clinic.modules.encounter.dto.DiagnosisRequest;
import com.suncare.clinic.modules.encounter.dto.EncounterClinicalResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterNotesRequest;
import com.suncare.clinic.modules.encounter.dto.VitalSignRequest;
import com.suncare.clinic.modules.encounter.dto.DiagnosisConceptResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Repository
public class EncounterClinicalRepository {
    private final JdbcTemplate jdbc;
    public EncounterClinicalRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public EncounterClinicalResponse detail(long encounterId, boolean editable, boolean replayed) {
        var notes = jdbc.queryForObject("""
                SELECT chief_complaint, examination_note, treatment_plan, followup_note, version
                FROM dbo.Encounter WHERE id = ?
                """, (rs, n) -> new EncounterClinicalResponse.Notes(rs.getString("chief_complaint"),
                rs.getString("examination_note"), rs.getString("treatment_plan"), rs.getString("followup_note"),
                Base64.getEncoder().encodeToString(rs.getBytes("version"))), encounterId);
        var vitals = jdbc.query("""
                SELECT id, CONVERT(varchar(23), measured_at, 126) + 'Z' AS measured_at, temperature_c, pulse_bpm,
                       systolic_mmhg, diastolic_mmhg, respiratory_rate, spo2_percent, weight_kg, height_cm
                FROM dbo.VitalSign WHERE encounter_id = ? ORDER BY measured_at DESC, id DESC
                """, (rs, n) -> new EncounterClinicalResponse.Vital(rs.getLong("id"), rs.getString("measured_at"),
                rs.getBigDecimal("temperature_c"), rs.getObject("pulse_bpm", Integer.class),
                rs.getObject("systolic_mmhg", Integer.class), rs.getObject("diastolic_mmhg", Integer.class),
                rs.getObject("respiratory_rate", Integer.class), rs.getBigDecimal("spo2_percent"),
                rs.getBigDecimal("weight_kg"), rs.getBigDecimal("height_cm")), encounterId);
        var diagnoses = jdbc.query("""
                SELECT d.id, d.diagnosis_concept_id, d.code_system, d.code_version, d.diagnosis_code,
                       d.diagnosis_name_snapshot, d.description, d.diagnosis_type, d.is_primary, d.version,
                       c.primary_allowed
                FROM dbo.Diagnosis d LEFT JOIN dbo.DiagnosisConcept c ON c.id = d.diagnosis_concept_id
                WHERE d.encounter_id = ? ORDER BY d.is_primary DESC, d.id
                """, (rs, n) -> new EncounterClinicalResponse.Diagnosis(rs.getLong("id"), rs.getObject("diagnosis_concept_id", Long.class),
                rs.getString("code_system"), rs.getString("code_version"), rs.getString("diagnosis_code"),
                rs.getString("diagnosis_name_snapshot"), rs.getString("description"), rs.getString("diagnosis_type"),
                rs.getBoolean("is_primary"), Base64.getEncoder().encodeToString(rs.getBytes("version")),
                rs.getObject("primary_allowed", Boolean.class)), encounterId);
        return new EncounterClinicalResponse(notes, vitals, diagnoses, editable, replayed);
    }

    public long insertVital(long encounterId, long actorId, VitalSignRequest request) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.VitalSign (encounter_id, measured_at, temperature_c, pulse_bpm, systolic_mmhg,
                    diastolic_mmhg, respiratory_rate, spo2_percent, weight_kg, height_cm, recorded_by)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Long.class, encounterId, Timestamp.valueOf(request.measuredAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()),
                request.temperatureC(), request.pulseBpm(), request.systolicMmhg(), request.diastolicMmhg(),
                request.respiratoryRate(), request.spo2Percent(), request.weightKg(), request.heightCm(), actorId);
    }

    public int updateNotes(long encounterId, EncounterNotesRequest request, byte[] version) {
        return jdbc.update("""
                UPDATE dbo.Encounter SET chief_complaint = ?, examination_note = ?, treatment_plan = ?,
                    followup_note = ?, updated_at = SYSUTCDATETIME() WHERE id = ? AND version = ?
                """, clean(request.chiefComplaint()), clean(request.examinationNote()), clean(request.treatmentPlan()),
                clean(request.followupNote()), encounterId, version);
    }

    public long insertDiagnosis(long encounterId, long actorId, DiagnosisRequest request, DiagnosisConceptResponse concept) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Diagnosis (encounter_id, diagnosis_concept_id, code_system, code_version, diagnosis_code,
                    diagnosis_name_snapshot, description, diagnosis_type, is_primary, recorded_by)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Long.class, encounterId, concept == null ? null : concept.id(), concept == null ? null : concept.codeSystem(),
                concept == null ? null : concept.codeVersion(), concept == null ? null : concept.code(), concept == null ? null : concept.name(),
                request.description().strip(), request.diagnosisType(), request.isPrimary(), actorId);
    }

    public int updateDiagnosis(long encounterId, long diagnosisId, long actorId, DiagnosisRequest request, byte[] version, DiagnosisConceptResponse concept) {
        return jdbc.update("""
                UPDATE dbo.Diagnosis SET diagnosis_concept_id=?, code_system=?, code_version=?, diagnosis_code=?, diagnosis_name_snapshot=?, description = ?, diagnosis_type = ?,
                    is_primary = ?, recorded_by = ?, updated_at = SYSUTCDATETIME()
                WHERE id = ? AND encounter_id = ? AND version = ?
                """, concept == null ? null : concept.id(), concept == null ? null : concept.codeSystem(),
                concept == null ? null : concept.codeVersion(), concept == null ? null : concept.code(), concept == null ? null : concept.name(), request.description().strip(),
                request.diagnosisType(), request.isPrimary(), actorId, diagnosisId, encounterId, version);
    }

    public void lockCommand(String commandType, String key) {
        Integer result = jdbc.queryForObject("""
                DECLARE @result int;
                EXEC @result = sys.sp_getapplock @Resource = ?, @LockMode = 'Exclusive',
                    @LockOwner = 'Transaction', @LockTimeout = 10000;
                SELECT @result;
                """, Integer.class, "p19:" + commandType + ":" + key);
        if (result == null || result < 0) throw new com.suncare.clinic.common.exception.ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "ENCOUNTER_BUSY", "Lượt khám đang được xử lý. Hãy thử lại cùng yêu cầu.");
    }

    public Receipt receipt(String commandType, String key) {
        return jdbc.query("SELECT account_id, request_hash FROM dbo.CommandReceipt WHERE command_type = ? AND idempotency_key = ?",
                (rs, n) -> new Receipt(rs.getLong("account_id"), rs.getString("request_hash")), commandType, key).stream().findFirst().orElse(null);
    }

    public void saveReceipt(long actorId, String commandType, String key, String hash, String resource, long id) {
        jdbc.update("""
                INSERT INTO dbo.CommandReceipt (account_id, command_type, idempotency_key, request_hash, resource_type, resource_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, actorId, commandType, key, hash, resource, id);
    }

    public void audit(long actorId, long encounterId, String action, long resourceId) {
        jdbc.update("""
                INSERT INTO dbo.AuditEvent (actor_id, actor_type, action, entity_type, entity_id, occurred_at, correlation_id, redacted_change)
                VALUES (?, 'USER', ?, 'Encounter', ?, SYSUTCDATETIME(), ?, ?)
                """, actorId, action, encounterId, UUID.randomUUID().toString(), "resource_id=" + resourceId);
    }

    private String clean(String text) { return text == null || text.isBlank() ? null : text.strip(); }
    public record Receipt(long accountId, String hash) {}
}
