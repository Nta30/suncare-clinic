package com.suncare.clinic.modules.clinical.repository;

import com.suncare.clinic.modules.clinical.dto.ClinicalOrderResponse;
import com.suncare.clinic.modules.clinical.dto.CreateClinicalOrderRequest;
import com.suncare.clinic.modules.clinical.dto.ClinicalOrderOptionResponse;
import com.suncare.clinic.modules.clinical.dto.ClinicalOrderListResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public class ClinicalOrderRepository {
    private final JdbcTemplate jdbc;

    public ClinicalOrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public LocalDateTime now() {
        return jdbc.queryForObject("SELECT SYSUTCDATETIME()", LocalDateTime.class);
    }

    public List<ClinicalOrderOptionResponse> options(String search) {
        String pattern="%"+search.replace("[","[[]").replace("%","[%]").replace("_","[_]")+"%";
        var services=jdbc.query("""
                DECLARE @now datetime2(3)=SYSUTCDATETIME();
                SELECT TOP (30) s.id,s.code,s.name,p.id AS price_id,p.amount
                FROM dbo.Service s CROSS APPLY (
                    SELECT TOP (1) pr.id,pr.amount FROM dbo.ServicePrice pr
                    WHERE pr.service_id=s.id AND pr.doctor_id IS NULL AND pr.visit_mode IS NULL
                      AND pr.valid_from<=@now AND (pr.valid_to IS NULL OR pr.valid_to>@now)
                    ORDER BY pr.valid_from DESC,pr.id DESC) p
                WHERE s.service_type='CLS' AND s.is_active=1 AND p.amount>0
                  AND (s.code LIKE ? OR s.name LIKE ?)
                  AND EXISTS (SELECT 1 FROM dbo.RoomService rs JOIN dbo.Room r ON r.id=rs.room_id
                      JOIN dbo.Department d ON d.id=r.department_id
                      WHERE rs.service_id=s.id AND rs.is_active=1 AND r.is_active=1 AND r.room_type='LAB'
                        AND d.is_active=1 AND d.department_type='LAB')
                ORDER BY s.code,s.id
                """,(rs,n)->new ClinicalOrderOptionResponse(rs.getLong("id"),rs.getString("code"),rs.getString("name"),
                rs.getLong("price_id"),rs.getBigDecimal("amount").toBigIntegerExact().toString(),List.of()),pattern,pattern);
        if(services.isEmpty()) return services;
        String placeholders=String.join(",",java.util.Collections.nCopies(services.size(),"?"));
        var rooms=new java.util.HashMap<Long,java.util.List<ClinicalOrderOptionResponse.Room>>();
        jdbc.query("""
                SELECT rs.service_id,r.id,r.code,r.name,r.department_id,d.name AS department_name
                FROM dbo.RoomService rs JOIN dbo.Room r ON r.id=rs.room_id
                JOIN dbo.Department d ON d.id=r.department_id
                WHERE rs.is_active=1 AND r.is_active=1 AND r.room_type='LAB'
                  AND d.is_active=1 AND d.department_type='LAB' AND rs.service_id IN (
                """+placeholders+") ORDER BY d.name,r.code,r.id",rs->{
                    rooms.computeIfAbsent(rs.getLong("service_id"),id->new java.util.ArrayList<>()).add(
                            new ClinicalOrderOptionResponse.Room(rs.getLong("id"),rs.getString("code"),rs.getString("name"),
                            rs.getLong("department_id"),rs.getString("department_name")));
                },services.stream().map(ClinicalOrderOptionResponse::serviceId).toArray());
        return services.stream().filter(s->rooms.containsKey(s.serviceId())).map(s->new ClinicalOrderOptionResponse(
                s.serviceId(),s.code(),s.name(),s.priceId(),s.amount(),rooms.get(s.serviceId()))).toList();
    }

    public List<ClinicalOrderListResponse> list(long encounterId) {
        return jdbc.query("""
                SELECT o.*,s.code AS service_code,COALESCE(l.service_name,s.name) AS service_name,d.name AS department_name,r.name AS room_name,
                    i.id AS invoice_id,i.code AS invoice_code,i.status AS invoice_status,l.status AS line_status,l.amount,
                    CASE WHEN l.id IS NULL THEN 'UNKNOWN'
                         WHEN l.status='CANCELLED' OR i.status='VOID' THEN 'NOT_APPLICABLE'
                         WHEN i.status<>'ISSUED' THEN 'NOT_DUE'
                         WHEN COALESCE(pa.amount,0)-COALESCE(rf.amount,0)>=l.amount THEN 'PAID'
                         WHEN COALESCE(pa.amount,0)-COALESCE(rf.amount,0)>0 THEN 'PARTIAL' ELSE 'UNPAID' END AS payment_status,
                    CASE WHEN l.id IS NULL THEN NULL WHEN l.status='CANCELLED' THEN 0 ELSE net.paid END AS paid_amount,
                    CASE WHEN l.id IS NULL THEN NULL WHEN l.status='CANCELLED' THEN 0 ELSE l.amount-net.paid END AS outstanding_amount,
                    CONVERT(varchar(23),o.created_at,126)+'Z' AS created_utc,
                    CONVERT(varchar(23),o.fully_paid_at,126)+'Z' AS paid_utc,
                    CONVERT(varchar(23),o.expires_at,126)+'Z' AS expires_utc
                FROM dbo.ClinicalOrder o JOIN dbo.Service s ON s.id=o.service_id
                JOIN dbo.Department d ON d.id=o.performing_department_id
                LEFT JOIN dbo.Room r ON r.id=o.requested_room_id
                LEFT JOIN dbo.InvoiceLine l ON l.order_id=o.id LEFT JOIN dbo.Invoice i ON i.id=l.invoice_id
                OUTER APPLY (SELECT SUM(a.amount) AS amount FROM dbo.PaymentAllocation a WHERE a.invoice_line_id=l.id) pa
                OUTER APPLY (SELECT SUM(f.amount) AS amount FROM dbo.PaymentAllocation a
                    JOIN dbo.RefundRequest rr ON rr.allocation_id=a.id JOIN dbo.Refund f ON f.request_id=rr.id
                    WHERE a.invoice_line_id=l.id) rf
                OUTER APPLY (SELECT CASE WHEN COALESCE(pa.amount,0)-COALESCE(rf.amount,0)<0 THEN 0
                    WHEN COALESCE(pa.amount,0)-COALESCE(rf.amount,0)>l.amount THEN l.amount
                    ELSE COALESCE(pa.amount,0)-COALESCE(rf.amount,0) END AS paid) net
                WHERE o.encounter_id=? ORDER BY o.id DESC
                """,(rs,n)->new ClinicalOrderListResponse(rs.getLong("id"),rs.getString("code"),rs.getLong("service_id"),
                rs.getString("service_name"),rs.getString("indication"),rs.getLong("performing_department_id"),
                rs.getString("department_name"),rs.getObject("requested_room_id",Long.class),rs.getString("room_name"),
                rs.getString("status"),rs.getString("created_utc"),rs.getString("paid_utc"),rs.getObject("validity_days",Integer.class),
                rs.getString("expires_utc"),rs.getObject("invoice_id",Long.class),rs.getString("invoice_code"),
                rs.getString("invoice_status"),rs.getString("line_status"),
                rs.getBigDecimal("amount")==null?null:rs.getBigDecimal("amount").toBigIntegerExact().toString(),
                rs.getString("payment_status"),rs.getString("service_code"),
                rs.getBigDecimal("paid_amount")==null?null:rs.getBigDecimal("paid_amount").toBigIntegerExact().toString(),
                rs.getBigDecimal("outstanding_amount")==null?null:rs.getBigDecimal("outstanding_amount").toBigIntegerExact().toString()),encounterId);
    }

    public boolean supports(long serviceId, long departmentId, Long roomId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Department d WITH (HOLDLOCK)
                JOIN dbo.Room r WITH (HOLDLOCK) ON r.department_id=d.id
                JOIN dbo.RoomService rs WITH (HOLDLOCK) ON rs.room_id=r.id
                WHERE d.id=? AND d.is_active=1 AND d.department_type='LAB' AND r.is_active=1 AND r.room_type='LAB'
                  AND rs.service_id=? AND rs.is_active=1 AND (? IS NULL OR r.id=?)
                """, Integer.class, departmentId, serviceId, roomId, roomId) > 0;
    }

    public List<Long> drafts(long encounterId) {
        return jdbc.query("SELECT id FROM dbo.Invoice WITH (UPDLOCK,HOLDLOCK) WHERE encounter_id=? AND status='DRAFT' ORDER BY id",
                (rs,n) -> rs.getLong(1), encounterId);
    }

    public long insert(long encounterId, long actorId, CreateClinicalOrderRequest request) {
        return jdbc.queryForObject("""
                INSERT dbo.ClinicalOrder(code,encounter_id,service_id,performing_department_id,requested_room_id,
                    ordered_by,indication,status) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,'PENDING_PAYMENT')
                """, Long.class, "CLS" + UUID.randomUUID().toString().replace("-", ""), encounterId,
                request.serviceId(), request.performingDepartmentId(), request.requestedRoomId(), actorId, request.indication().strip());
    }

    public void line(long invoiceId, long orderId, long serviceId, long priceId, String name, java.math.BigDecimal amount) {
        jdbc.update("""
                INSERT dbo.InvoiceLine(invoice_id,line_no,order_id,line_type,service_id,price_id,service_name,
                    unit_name,quantity,unit_price,amount,status)
                SELECT ?,COALESCE(MAX(line_no),0)+1,?,'CLS',?,?,?,N'Lần',1,?,?,'ACTIVE'
                FROM dbo.InvoiceLine WHERE invoice_id=?
                """, invoiceId, orderId, serviceId, priceId, name, amount, amount, invoiceId);
        jdbc.update("""
                UPDATE dbo.Invoice SET total_amount=(SELECT SUM(amount) FROM dbo.InvoiceLine WHERE invoice_id=?),
                    updated_at=SYSUTCDATETIME() WHERE id=? AND status='DRAFT'
                """, invoiceId, invoiceId);
    }

    public void event(long orderId, long actorId) {
        jdbc.update("""
                INSERT dbo.ClinicalOrderEvent(order_id,to_status,actor_id,occurred_at)
                VALUES (?,'PENDING_PAYMENT',?,SYSUTCDATETIME())
                """, orderId, actorId);
        jdbc.update("""
                INSERT dbo.AuditEvent(actor_id,actor_type,action,entity_type,entity_id,occurred_at,correlation_id)
                VALUES (?,'USER','CLINICAL_ORDER_CREATED','ClinicalOrder',?,SYSUTCDATETIME(),?)
                """, actorId, orderId, UUID.randomUUID().toString());
    }

    public void receipt(long actorId, String key, String hash, long orderId) {
        jdbc.update("""
                INSERT dbo.CommandReceipt(account_id,command_type,idempotency_key,request_hash,resource_type,resource_id)
                VALUES (?,'CLINICAL_ORDER_CREATE',?,?,'ClinicalOrder',?)
                """, actorId, key, hash, orderId);
    }

    public ClinicalOrderResponse detail(long encounterId, long orderId, boolean replayed) {
        return jdbc.query("""
                SELECT o.*,l.id AS line_id,l.invoice_id,l.price_id,l.service_name,l.amount
                FROM dbo.ClinicalOrder o JOIN dbo.InvoiceLine l ON l.order_id=o.id
                WHERE o.encounter_id=? AND o.id=?
                """, (rs,n) -> new ClinicalOrderResponse(rs.getLong("id"),rs.getString("code"),encounterId,
                rs.getLong("service_id"),rs.getLong("performing_department_id"),
                rs.getObject("requested_room_id",Long.class),rs.getString("indication"),rs.getString("status"),
                rs.getLong("invoice_id"),rs.getLong("line_id"),rs.getLong("price_id"),rs.getString("service_name"),
                rs.getBigDecimal("amount"),replayed),encounterId,orderId).stream().findFirst().orElse(null);
    }
}
