package com.suncare.clinic.modules.prescription.repository;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.prescription.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Repository
public class PrescriptionRepository {
    private final JdbcTemplate jdbc;
    public PrescriptionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Long> ids(long encounterId) {
        return jdbc.query("SELECT id FROM dbo.Prescription WHERE encounter_id=? ORDER BY id", (rs,n)->rs.getLong(1), encounterId);
    }

    public PrescriptionResponse detail(long encounterId, long id, boolean replayed) {
        var items = jdbc.query("""
                SELECT pi.* FROM dbo.PrescriptionItem pi JOIN dbo.Prescription p ON p.id=pi.prescription_id
                WHERE p.encounter_id=? AND p.id=? ORDER BY pi.line_no
                """, (rs,n)->new PrescriptionResponse.Item(rs.getLong("id"),rs.getInt("line_no"),rs.getLong("drug_id"),
                rs.getString("drug_name"),rs.getString("ingredient"),rs.getString("strength"),rs.getString("dosage_form"),
                rs.getString("unit"),rs.getString("dose"),rs.getString("route"),rs.getString("frequency"),
                rs.getInt("duration_days"),rs.getBigDecimal("quantity"),rs.getString("instruction"),
                rs.getString("dose_unit"),rs.getString("dispensing_unit")),encounterId,id);
        return jdbc.query("""
                SELECT *,CONVERT(varchar(23),issued_at,126)+'Z' AS issued_utc FROM dbo.Prescription
                WHERE encounter_id=? AND id=?
                """,(rs,n)->new PrescriptionResponse(rs.getLong("id"),rs.getString("code"),encounterId,
                rs.getString("status"),rs.getLong("prescribed_by"),rs.getString("issued_utc"),rs.getString("instruction"),
                Base64.getEncoder().encodeToString(rs.getBytes("version")),replayed,items),encounterId,id)
                .stream().findFirst().orElse(null);
    }

    public void lock(long encounterId, long id) {
        jdbc.query("SELECT id FROM dbo.Prescription WITH (UPDLOCK,HOLDLOCK) WHERE encounter_id=? AND id=?",
                (rs,n)->rs.getLong(1),encounterId,id);
    }

    public long create(long encounterId, long actorId) {
        return jdbc.queryForObject("""
                INSERT dbo.Prescription(code,encounter_id,status,prescribed_by)
                OUTPUT INSERTED.id VALUES (?,?,'DRAFT',?)
                """,Long.class,"RX"+UUID.randomUUID().toString().replace("-",""),encounterId,actorId);
    }

    public int update(long id, byte[] version, String instruction) {
        return jdbc.update("""
                UPDATE dbo.Prescription SET instruction=?,updated_at=SYSUTCDATETIME()
                WHERE id=? AND status='DRAFT' AND version=?
                """,clean(instruction),id,version);
    }

    public void clearItems(long id) { jdbc.update("DELETE FROM dbo.PrescriptionItem WHERE prescription_id=?",id); }

    public void insertItem(long id, int line, PrescriptionSaveRequest.Item item, DrugResponse drug) {
        jdbc.update("""
                INSERT dbo.PrescriptionItem(prescription_id,line_no,drug_id,drug_name,ingredient,strength,dosage_form,
                    unit,dose,route,frequency,duration_days,quantity,instruction,dose_unit,dispensing_unit)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,id,line,item.drugId(),drug.name(),drug.ingredient(),drug.strength(),drug.dosageForm(),drug.dispensingUnit(),
                clean(item.dose()),clean(item.route()),clean(item.frequency()),item.durationDays(),item.quantity(),clean(item.instruction()),
                drug.doseUnit(),drug.dispensingUnit());
    }

    public int issue(long id, byte[] version) {
        return jdbc.update("""
                UPDATE dbo.Prescription SET status='ISSUED',issued_at=SYSUTCDATETIME(),updated_at=SYSUTCDATETIME()
                WHERE id=? AND status='DRAFT' AND version=?
                """,id,version);
    }

    public int discard(long id, byte[] version) {
        return jdbc.update("DELETE FROM dbo.Prescription WHERE id=? AND status='DRAFT' AND version=?",id,version);
    }

    public void lockCommand(String type, String key) {
        Integer result = jdbc.queryForObject("""
                DECLARE @r int;
                EXEC @r=sys.sp_getapplock @Resource=?,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
                SELECT @r;
                """,Integer.class,"prescription:"+type+":"+key);
        if (result==null || result<0) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "PRESCRIPTION_BUSY","Đơn thuốc đang được xử lý. Hãy thử lại cùng yêu cầu.");
    }

    public Receipt receipt(String type, String key) {
        return jdbc.query("SELECT account_id,request_hash,resource_id FROM dbo.CommandReceipt WHERE command_type=? AND idempotency_key=?",
                (rs,n)->new Receipt(rs.getLong(1),rs.getString(2),rs.getLong(3)),type,key).stream().findFirst().orElse(null);
    }

    public void receipt(long actorId, String type, String key, String hash, long id) {
        jdbc.update("""
                INSERT dbo.CommandReceipt(account_id,command_type,idempotency_key,request_hash,resource_type,resource_id)
                VALUES (?,?,?,?,'Prescription',?)
                """,actorId,type,key,hash,id);
    }

    public void audit(long actorId, long id, String action) {
        jdbc.update("""
                INSERT dbo.AuditEvent(actor_id,actor_type,action,entity_type,entity_id,occurred_at,correlation_id)
                VALUES (?,'USER',?,'Prescription',?,SYSUTCDATETIME(),?)
                """,actorId,action,id,UUID.randomUUID().toString());
    }

    private static String clean(String value) { return value==null || value.isBlank()?null:value.strip(); }
    public record Receipt(long accountId,String hash,long id) {}
}
