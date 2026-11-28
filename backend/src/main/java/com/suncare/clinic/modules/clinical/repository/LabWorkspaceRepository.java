package com.suncare.clinic.modules.clinical.repository;

import com.suncare.clinic.modules.clinical.dto.LabAssignmentResponse;
import com.suncare.clinic.modules.clinical.dto.LabOrderResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Repository
public class LabWorkspaceRepository {
    private final JdbcTemplate jdbc;
    public LabWorkspaceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Use the database clock throughout the transaction; all timestamps are UTC.
    public java.time.LocalDateTime now() {
        return jdbc.queryForObject("SELECT CAST(SYSUTCDATETIME() AS datetime2(3))", java.time.LocalDateTime.class);
    }

    public List<LabAssignmentResponse> assignments(long staffId, boolean current) {
        return jdbc.query("""
                SELECT wa.id,wa.room_id,r.name AS room_name,d.name AS department_name,s.full_name,
                    CONVERT(varchar(23),wa.starts_at,126)+'Z' AS starts_utc,
                    CONVERT(varchar(23),wa.ends_at,126)+'Z' AS ends_utc
                FROM dbo.WorkAssignment wa JOIN dbo.WorkSchedule ws ON ws.id=wa.schedule_id
                JOIN dbo.Room r ON r.id=wa.room_id JOIN dbo.Department d ON d.id=r.department_id
                JOIN dbo.Staff s ON s.id=wa.staff_id
                WHERE wa.staff_id=? AND ws.status='PUBLISHED' AND wa.mode='LAB'
                    AND s.is_active=1 AND r.is_active=1 AND d.is_active=1 AND r.room_type='LAB'
                    AND d.id=ws.department_id AND d.department_type='LAB'
                    AND EXISTS (SELECT 1 FROM dbo.StaffDepartment sd WHERE sd.staff_id=wa.staff_id
                        AND sd.department_id=d.id AND sd.valid_from<=SYSUTCDATETIME()
                        AND (sd.valid_to IS NULL OR sd.valid_to>SYSUTCDATETIME()))
                """ + (current ? " AND wa.starts_at<=SYSUTCDATETIME() AND wa.ends_at>SYSUTCDATETIME()"
                : " AND wa.ends_at>DATEADD(day,-1,SYSUTCDATETIME()) AND wa.starts_at<DATEADD(day,30,SYSUTCDATETIME())")
                + " ORDER BY wa.starts_at,r.code,wa.id",
                (rs,n)->new LabAssignmentResponse(rs.getLong("id"),rs.getLong("room_id"),rs.getString("room_name"),
                    rs.getString("department_name"),rs.getString("full_name"),rs.getString("starts_utc"),rs.getString("ends_utc")),staffId);
    }

    public boolean onDuty(long staffId,long roomId,java.time.LocalDateTime now) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.WorkAssignment wa WITH (HOLDLOCK)
                JOIN dbo.WorkSchedule ws WITH (HOLDLOCK) ON ws.id=wa.schedule_id
                JOIN dbo.Room r WITH (HOLDLOCK) ON r.id=wa.room_id
                JOIN dbo.Department d WITH (HOLDLOCK) ON d.id=r.department_id
                WHERE wa.staff_id=? AND wa.room_id=? AND wa.mode='LAB' AND ws.status='PUBLISHED'
                  AND wa.starts_at<=? AND wa.ends_at>? AND r.is_active=1 AND r.room_type='LAB'
                  AND d.id=ws.department_id AND d.is_active=1 AND d.department_type='LAB'
                  AND EXISTS (SELECT 1 FROM dbo.StaffDepartment sd WITH (HOLDLOCK)
                    WHERE sd.staff_id=wa.staff_id AND sd.department_id=d.id AND sd.valid_from<=?
                    AND (sd.valid_to IS NULL OR sd.valid_to>?))
                """,Integer.class,staffId,roomId,now,now,now,now)>0;
    }

    private static final String SELECT = """
            SELECT o.*,s.result_type,l.service_name,p.id AS patient_id,p.code AS patient_code,
                p.full_name AS patient_name,p.birth_date,p.sex,ds.full_name AS doctor_name,
                q.room_id,r.name AS room_name,q.accepted_by,st.full_name AS staff_name,
                CONVERT(varchar(23),o.created_at,126)+'Z' AS ordered_utc,
                CONVERT(varchar(23),o.fully_paid_at,126)+'Z' AS paid_utc,
                CONVERT(varchar(23),o.expires_at,126)+'Z' AS expires_utc,
                CONVERT(varchar(23),o.started_at,126)+'Z' AS started_utc,
                CONVERT(varchar(23),q.accepted_at,126)+'Z' AS accepted_utc,
                CASE WHEN i.status='ISSUED' AND l.status='ACTIVE'
                  AND COALESCE((SELECT SUM(pa.amount) FROM dbo.PaymentAllocation pa WHERE pa.invoice_line_id=l.id),0)
                    -COALESCE((SELECT SUM(f.amount) FROM dbo.PaymentAllocation pa
                        JOIN dbo.RefundRequest rr ON rr.allocation_id=pa.id JOIN dbo.Refund f ON f.request_id=rr.id
                        WHERE pa.invoice_line_id=l.id),0)>=l.amount THEN 1 ELSE 0 END AS paid
            FROM dbo.ClinicalOrder o JOIN dbo.Service s ON s.id=o.service_id
            JOIN dbo.Encounter e ON e.id=o.encounter_id JOIN dbo.Appointment ap ON ap.id=e.appointment_id
            JOIN dbo.Patient p ON p.id=ap.patient_id
            JOIN dbo.Account da ON da.id=o.ordered_by JOIN dbo.Staff ds ON ds.id=da.staff_id
            JOIN dbo.InvoiceLine l ON l.order_id=o.id JOIN dbo.Invoice i ON i.id=l.invoice_id
            LEFT JOIN dbo.ClinicalQueueEntry q ON q.order_id=o.id AND q.status IN ('WAITING','ACCEPTED','STARTED')
            LEFT JOIN dbo.Room r ON r.id=q.room_id LEFT JOIN dbo.Staff st ON st.id=q.accepted_by
            """;

    private LabOrderResponse map(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        return new LabOrderResponse(rs.getLong("id"),rs.getString("code"),rs.getLong("service_id"),
            rs.getString("service_name"),rs.getString("result_type"),rs.getString("status"),
            Base64.getEncoder().encodeToString(rs.getBytes("version")),rs.getLong("patient_id"),
            rs.getString("patient_code"),rs.getString("patient_name"),rs.getString("birth_date"),rs.getString("sex"),
            rs.getString("doctor_name"),rs.getString("indication"),rs.getString("ordered_utc"),rs.getString("paid_utc"),
            rs.getString("expires_utc"),rs.getString("started_utc"),rs.getObject("room_id",Long.class),
            rs.getString("room_name"),rs.getObject("accepted_by",Long.class),rs.getString("staff_name"),
            rs.getString("accepted_utc"),rs.getBoolean("paid"));
    }

    public LabOrderResponse detail(long id) {
        return jdbc.query(SELECT+" WHERE o.id=?",this::map,id).stream().findFirst().orElse(null);
    }

    public List<LabOrderResponse> queue(long staffId,long roomId,boolean duty) {
        return jdbc.query(SELECT+"""
                WHERE (q.accepted_by=? AND o.status IN ('ACCEPTED','IN_PROGRESS'))
                  OR (?=1 AND o.status='READY' AND o.fully_paid_at IS NOT NULL AND o.expires_at>SYSUTCDATETIME()
                    AND i.status='ISSUED' AND l.status='ACTIVE' AND q.id IS NULL
                    AND EXISTS (SELECT 1 FROM dbo.Room r JOIN dbo.RoomService rs ON rs.room_id=r.id
                      WHERE r.id=? AND r.department_id=o.performing_department_id AND rs.service_id=o.service_id
                        AND r.is_active=1 AND rs.is_active=1))
                ORDER BY CASE WHEN q.accepted_by=? THEN 0 ELSE 1 END,o.fully_paid_at,o.id
                """,this::map,staffId,duty?1:0,roomId,staffId).stream()
                .filter(o->o.acceptedBy()!=null || o.paid()).toList();
    }

    public boolean supports(long orderId,long roomId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.ClinicalOrder o JOIN dbo.Room r WITH (HOLDLOCK)
                  ON r.department_id=o.performing_department_id
                JOIN dbo.RoomService rs WITH (HOLDLOCK) ON rs.room_id=r.id AND rs.service_id=o.service_id
                WHERE o.id=? AND r.id=? AND rs.is_active=1 AND r.is_active=1
                """,Integer.class,orderId,roomId)>0;
    }

    public Long encounterId(long id) {
        return jdbc.query("SELECT encounter_id FROM dbo.ClinicalOrder WHERE id=?",(rs,n)->rs.getLong(1),id)
                .stream().findFirst().orElse(null);
    }

    public void lock(long encounterId,long staffId,long orderId) {
        jdbc.queryForList("SELECT id FROM dbo.Encounter WITH (UPDLOCK,HOLDLOCK) WHERE id=?",encounterId);
        var active=jdbc.queryForList("SELECT id FROM dbo.Staff WITH (UPDLOCK,HOLDLOCK) WHERE id=? AND is_active=1",staffId);
        if(active.isEmpty()) throw new com.suncare.clinic.common.exception.ApiException(
            org.springframework.http.HttpStatus.FORBIDDEN,"LAB_STAFF_INACTIVE","Nhân viên không còn hoạt động.");
        jdbc.queryForList("SELECT id FROM dbo.ClinicalOrder WITH (UPDLOCK,HOLDLOCK) WHERE id=?",orderId);
    }

    public java.util.Map<String,Integer> counts(long roomId) {
        var result=new java.util.HashMap<String,Integer>();
        jdbc.query("""
                SELECT o.status,COUNT(*) AS total FROM dbo.ClinicalOrder o
                JOIN dbo.ClinicalQueueEntry q ON q.order_id=o.id
                WHERE q.room_id=? AND ((q.status='ACCEPTED' AND o.status='ACCEPTED')
                    OR (q.status='STARTED' AND o.status='IN_PROGRESS')) GROUP BY o.status
                """,rs->{result.put(rs.getString("status"),rs.getInt("total"));},roomId);
        return result;
    }

    public void accept(long id,long roomId,long accountId,long staffId,java.time.LocalDateTime now) {
        jdbc.update("""
                INSERT dbo.ClinicalQueueEntry(order_id,room_id,checked_in_by,checked_in_at,status,accepted_by,accepted_at)
                VALUES (?,?,?,?,'ACCEPTED',?,?)
                """,id,roomId,accountId,now,staffId,now);
        jdbc.update("UPDATE dbo.ClinicalOrder SET status='ACCEPTED',updated_at=? WHERE id=?",now,id);
        event(id,accountId,"READY","QUEUED",now);
        event(id,accountId,"QUEUED","ACCEPTED",now);
    }

    public void start(long id,long accountId,java.time.LocalDateTime now) {
        jdbc.update("UPDATE dbo.ClinicalOrder SET status='IN_PROGRESS',started_at=?,updated_at=? WHERE id=?",now,now,id);
        jdbc.update("UPDATE dbo.ClinicalQueueEntry SET status='STARTED',updated_at=? WHERE order_id=? AND status='ACCEPTED'",now,id);
        event(id,accountId,"ACCEPTED","IN_PROGRESS",now);
    }

    public void event(long id,long accountId,String from,String to,java.time.LocalDateTime now) {
        jdbc.update("INSERT dbo.ClinicalOrderEvent(order_id,from_status,to_status,actor_id,occurred_at) VALUES (?,?,?,?,?)",
                id,from,to,accountId,now);
        jdbc.update("""
                INSERT dbo.AuditEvent(actor_id,actor_type,action,entity_type,entity_id,occurred_at,correlation_id)
                VALUES (?,'USER',?,'ClinicalOrder',?,?,?)
                """,accountId,"CLINICAL_"+to,id,now,UUID.randomUUID().toString());
    }

    public void receipt(long actor,String type,String key,String hash,long id) {
        jdbc.update("""
                INSERT dbo.CommandReceipt(account_id,command_type,idempotency_key,request_hash,resource_type,resource_id)
                VALUES (?,?,?,?,'ClinicalOrder',?)
                """,actor,type,key,hash,id);
    }
}
