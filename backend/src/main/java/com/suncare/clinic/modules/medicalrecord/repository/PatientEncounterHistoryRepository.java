package com.suncare.clinic.modules.medicalrecord.repository;

import com.suncare.clinic.modules.medicalrecord.dto.PatientEncounterHistoryResponse.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class PatientEncounterHistoryRepository {
    private static final String FROM = """
            FROM dbo.Encounter e JOIN dbo.Appointment a ON a.id=e.appointment_id
            JOIN dbo.Staff doctor ON doctor.id=a.doctor_id
            JOIN dbo.Specialty sp ON sp.id=a.specialty_id
            JOIN dbo.Department d ON d.id=a.department_id
            LEFT JOIN dbo.Diagnosis dx ON dx.encounter_id=e.id AND dx.diagnosis_type='FINAL' AND dx.is_primary=1
            WHERE a.patient_id=? AND e.status='COMPLETED' AND e.id<>?
            """;
    private static final String SELECT = """
            SELECT e.id,e.code,CONVERT(varchar(23),e.completed_at,126)+'Z' AS completed_at,a.visit_mode,
                   doctor.full_name AS doctor_name,sp.name AS specialty_name,d.name AS department_name,
                   dx.description AS primary_diagnosis
            """;
    private final JdbcTemplate jdbc;
    public PatientEncounterHistoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Long contextPatient(long contextId, long doctorId) {
        return jdbc.query("SELECT a.patient_id FROM dbo.Encounter e JOIN dbo.Appointment a ON a.id=e.appointment_id WHERE e.id=? AND a.doctor_id=?",
                (rs,n) -> rs.getLong(1), contextId, doctorId).stream().findFirst().orElse(null);
    }
    public Page list(long patientId, long contextId, int requestedPage) {
        long total = jdbc.queryForObject("SELECT COUNT_BIG(*) " + FROM, Long.class, patientId, contextId);
        int pages = (int) Math.min(Integer.MAX_VALUE, (total + 9) / 10);
        int page = Math.min(requestedPage, Math.max(1, pages));
        var items = jdbc.query(SELECT + FROM + " ORDER BY e.completed_at DESC,e.id DESC OFFSET ? ROWS FETCH NEXT 10 ROWS ONLY",
                (rs,n) -> summary(rs), patientId, contextId, (long)(page-1)*10);
        return new Page(items,total,page,10,pages);
    }
    public Summary find(long patientId, long contextId, long previousId) {
        return jdbc.query(SELECT + FROM + " AND e.id=?", (rs,n) -> summary(rs), patientId, contextId, previousId)
                .stream().findFirst().orElse(null);
    }
    public List<Prescription> prescriptions(long encounterId) {
        var rows = jdbc.query("""
                SELECT id,code,CONVERT(varchar(23),issued_at,126)+'Z' AS issued_at,instruction
                FROM dbo.Prescription WHERE encounter_id=? AND status='ISSUED' ORDER BY id
                """, (rs,n) -> new Prescription(rs.getLong("id"),rs.getString("code"),rs.getString("issued_at"),rs.getString("instruction"),List.of()), encounterId);
        return rows.stream().map(row -> new Prescription(row.id(),row.code(),row.issuedAt(),row.instruction(),jdbc.query("""
                SELECT drug_name,strength,dose,route,frequency,duration_days,quantity,unit,instruction
                FROM dbo.PrescriptionItem WHERE prescription_id=? ORDER BY line_no
                """, (rs,n) -> new DrugItem(rs.getString("drug_name"),rs.getString("strength"),rs.getString("dose"),
                rs.getString("route"),rs.getString("frequency"),rs.getInt("duration_days"),rs.getBigDecimal("quantity"),
                rs.getString("unit"),rs.getString("instruction")),row.id()))).toList();
    }
    public List<Result> results(long encounterId) {
        var rows = jdbc.query("""
                SELECT rv.id,s.name,rv.version_no,CONVERT(varchar(23),rv.published_at,126)+'Z' AS published_at,
                       rv.description,rv.conclusion
                FROM dbo.ClinicalOrder co JOIN dbo.Service s ON s.id=co.service_id
                CROSS APPLY (SELECT TOP 1 * FROM dbo.ResultVersion v WHERE v.order_id=co.id AND v.status='PUBLISHED'
                             ORDER BY v.version_no DESC) rv
                WHERE co.encounter_id=? ORDER BY co.id
                """, (rs,n) -> new Result(rs.getLong("id"),rs.getString("name"),rs.getInt("version_no"),rs.getString("published_at"),
                rs.getString("description"),rs.getString("conclusion"),List.of()),encounterId);
        return rows.stream().map(row -> new Result(row.id(),row.serviceName(),row.versionNo(),row.publishedAt(),row.description(),row.conclusion(),
                jdbc.query("""
                SELECT analyte_name,numeric_value,text_value,unit,reference_text,abnormal_flag
                FROM dbo.ResultValue WHERE result_version_id=? ORDER BY sort_order,line_no
                """, (rs,n) -> new Value(rs.getString("analyte_name"),rs.getBigDecimal("numeric_value"),rs.getString("text_value"),
                rs.getString("unit"),rs.getString("reference_text"),rs.getString("abnormal_flag")),row.id()))).toList();
    }
    private Summary summary(ResultSet rs) throws SQLException {
        return new Summary(rs.getLong("id"),rs.getString("code"),rs.getString("completed_at"),rs.getString("visit_mode"),
                rs.getString("doctor_name"),rs.getString("specialty_name"),rs.getString("department_name"),rs.getString("primary_diagnosis"));
    }
}
