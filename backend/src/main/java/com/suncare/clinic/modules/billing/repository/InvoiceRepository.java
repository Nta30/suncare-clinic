package com.suncare.clinic.modules.billing.repository;

import com.suncare.clinic.modules.billing.dto.InvoiceResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Repository
public class InvoiceRepository {
    private final JdbcTemplate jdbc;
    public InvoiceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Source source(long encounterId) {
        return jdbc.queryForObject("""
                SELECT a.patient_id,a.price_id,a.examination_amount,p.full_name,p.address,
                       s.id AS service_id,s.name AS service_name,s.service_type
                FROM dbo.Encounter e JOIN dbo.Appointment a ON a.id=e.appointment_id
                JOIN dbo.Patient p WITH (HOLDLOCK) ON p.id=a.patient_id
                JOIN dbo.ServicePrice price WITH (HOLDLOCK) ON price.id=a.price_id
                JOIN dbo.Service s WITH (HOLDLOCK) ON s.id=price.service_id
                WHERE e.id=?
                """, (rs,n) -> new Source(rs.getLong("patient_id"),rs.getLong("price_id"),rs.getLong("service_id"),
                rs.getBigDecimal("examination_amount"),rs.getString("full_name"),rs.getString("address"),
                rs.getString("service_name"),rs.getString("service_type")), encounterId);
    }

    public boolean hasExamination(long encounterId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Invoice i WITH (UPDLOCK,HOLDLOCK)
                JOIN dbo.InvoiceLine l WITH (UPDLOCK,HOLDLOCK) ON l.invoice_id=i.id
                WHERE i.encounter_id=? AND i.status<>'VOID' AND l.line_type='EXAMINATION'
                """, Integer.class,encounterId) > 0;
    }

    public long insertDraft(long encounterId, long actorId, Source source, String facilityName, String facilityAddress) {
        return jdbc.queryForObject("""
                INSERT dbo.Invoice(code,patient_id,encounter_id,status,issued_by,issuer_type,patient_name_snapshot,
                    patient_address_snapshot,facility_name_snapshot,facility_address_snapshot,total_amount,currency)
                OUTPUT INSERTED.id VALUES (?,?,?,'DRAFT',?,'STAFF',?,?,?,?,?,'VND')
                """,Long.class,"INV"+UUID.randomUUID().toString().replace("-",""),source.patientId(),encounterId,
                actorId,source.patientName(),source.patientAddress(),facilityName,facilityAddress,source.amount());
    }

    public void insertExamination(long invoiceId, Source source) {
        jdbc.update("""
                INSERT dbo.InvoiceLine(invoice_id,line_no,line_type,service_id,price_id,service_name,unit_name,
                    quantity,unit_price,amount,status) VALUES (?,1,'EXAMINATION',?,?,?,N'Lượt',1,?,?,'ACTIVE')
                """,invoiceId,source.serviceId(),source.priceId(),source.serviceName(),source.amount(),source.amount());
    }

    public InvoiceResponse detail(long encounterId, long invoiceId, boolean replayed) {
        var lines = jdbc.query("""
                SELECT l.* FROM dbo.InvoiceLine l JOIN dbo.Invoice i ON i.id=l.invoice_id
                WHERE i.encounter_id=? AND i.id=? ORDER BY l.line_no
                """,(rs,n) -> new InvoiceResponse.Line(rs.getLong("id"),rs.getInt("line_no"),rs.getString("line_type"),
                rs.getLong("service_id"),rs.getLong("price_id"),rs.getString("service_name"),rs.getString("unit_name"),
                rs.getBigDecimal("quantity"),rs.getBigDecimal("unit_price"),rs.getBigDecimal("amount"),rs.getString("status")),encounterId,invoiceId);
        return jdbc.query("""
                SELECT *,CONVERT(varchar(23),issued_at,126)+'Z' AS issued_utc FROM dbo.Invoice
                WHERE encounter_id=? AND id=?
                """,(rs,n) -> new InvoiceResponse(rs.getLong("id"),rs.getString("code"),encounterId,rs.getLong("patient_id"),
                rs.getString("status"),rs.getString("patient_name_snapshot"),rs.getString("patient_address_snapshot"),
                rs.getString("facility_name_snapshot"),rs.getString("facility_address_snapshot"),rs.getBigDecimal("total_amount"),
                rs.getString("currency"),rs.getString("issued_utc"),Base64.getEncoder().encodeToString(rs.getBytes("version")),replayed,lines),
                encounterId,invoiceId).stream().findFirst().orElse(null);
    }

    public List<Long> ids(long encounterId) {
        return jdbc.query("SELECT id FROM dbo.Invoice WHERE encounter_id=? ORDER BY id",(rs,n)->rs.getLong(1),encounterId);
    }

    public void lockInvoice(long encounterId,long invoiceId) {
        jdbc.query("SELECT id FROM dbo.Invoice WITH (UPDLOCK,HOLDLOCK) WHERE encounter_id=? AND id=?",(rs,n)->rs.getLong(1),encounterId,invoiceId);
    }

    public int issue(long invoiceId, byte[] version, long actorId, Source source, String facilityName, String facilityAddress) {
        return jdbc.update("""
                UPDATE dbo.Invoice SET status='ISSUED',issued_by=?,issued_at=SYSUTCDATETIME(),updated_at=SYSUTCDATETIME(),
                    patient_name_snapshot=?,patient_address_snapshot=?,facility_name_snapshot=?,facility_address_snapshot=?
                WHERE id=? AND status='DRAFT' AND version=?
                """,actorId,source.patientName(),source.patientAddress(),facilityName,facilityAddress,invoiceId,version);
    }

    public void snapshotLine(long invoiceId, String serviceName) {
        jdbc.update("UPDATE dbo.InvoiceLine SET service_name=?,updated_at=SYSUTCDATETIME() WHERE invoice_id=? AND line_type='EXAMINATION'",serviceName,invoiceId);
    }

    public void lockCommand(String type, String key) {
        Integer result=jdbc.queryForObject("""
                DECLARE @result int;
                EXEC @result=sys.sp_getapplock @Resource=?,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
                SELECT @result;
                """,Integer.class,"billing:"+type+":"+key);
        if(result==null || result<0) throw new com.suncare.clinic.common.exception.ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"INVOICE_BUSY","Hóa đơn đang được xử lý. Hãy thử lại cùng yêu cầu.");
    }

    public Receipt receipt(String type,String key) {
        return jdbc.query("SELECT account_id,request_hash,resource_id FROM dbo.CommandReceipt WHERE command_type=? AND idempotency_key=?",
                (rs,n)->new Receipt(rs.getLong(1),rs.getString(2),rs.getLong(3)),type,key).stream().findFirst().orElse(null);
    }

    public void receipt(long actorId,String type,String key,String hash,long invoiceId) {
        jdbc.update("INSERT dbo.CommandReceipt(account_id,command_type,idempotency_key,request_hash,resource_type,resource_id) VALUES (?,?,?,?,'Invoice',?)",
                actorId,type,key,hash,invoiceId);
    }

    public void audit(long actorId,long invoiceId,String action) {
        jdbc.update("""
                INSERT dbo.AuditEvent(actor_id,actor_type,action,entity_type,entity_id,occurred_at,correlation_id)
                VALUES (?,'USER',?,'Invoice',?,SYSUTCDATETIME(),?)
                """,actorId,action,invoiceId,UUID.randomUUID().toString());
    }

    public record Source(long patientId,long priceId,long serviceId,BigDecimal amount,String patientName,
            String patientAddress,String serviceName,String serviceType) {}
    public record Receipt(long accountId,String hash,long invoiceId) {}
}
