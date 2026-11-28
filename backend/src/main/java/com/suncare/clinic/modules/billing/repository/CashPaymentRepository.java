package com.suncare.clinic.modules.billing.repository;

import com.suncare.clinic.modules.billing.dto.CashPaymentResponse;
import com.suncare.clinic.modules.billing.dto.InvoiceBalanceResponse.LineBalance;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CashPaymentRepository {
    private final JdbcTemplate jdbc;

    public CashPaymentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Long findByCode(String code) {
        return jdbc.query("SELECT id FROM dbo.Invoice WHERE code=? AND encounter_id IS NOT NULL",
                (rs, n) -> rs.getLong(1), code).stream().findFirst().orElse(null);
    }

    public Source source(long invoiceId) {
        return jdbc.query("""
                SELECT i.encounter_id, e.appointment_id, i.hold_id
                FROM dbo.Invoice i LEFT JOIN dbo.Encounter e ON e.id=i.encounter_id WHERE i.id=?
                """, (rs, n) -> new Source(rs.getObject("encounter_id", Long.class),
                rs.getObject("appointment_id", Long.class), rs.getObject("hold_id", Long.class)), invoiceId)
                .stream().findFirst().orElse(null);
    }

    // Same source-first order as issuing invoices; payment/cancellation must share these locks.
    public void lockSource(Source source) {
        jdbc.query("SELECT id FROM dbo.Appointment WITH (UPDLOCK,HOLDLOCK) WHERE id=?",
                (rs, n) -> rs.getLong(1), source.appointmentId());
        jdbc.query("SELECT id FROM dbo.Encounter WITH (UPDLOCK,HOLDLOCK) WHERE id=?",
                (rs, n) -> rs.getLong(1), source.encounterId());
    }

    public List<LineBalance> balances(long invoiceId) {
        // Aggregate allocations and refunds separately to avoid multiplying money through JOINs.
        return jdbc.query("""
                SELECT l.id,l.line_no,l.line_type,l.status,l.amount,
                       COALESCE(a.allocated,0) allocated,COALESCE(r.refunded,0) refunded
                FROM dbo.InvoiceLine l WITH (UPDLOCK,HOLDLOCK)
                OUTER APPLY (SELECT SUM(pa.amount) allocated FROM dbo.PaymentAllocation pa
                             WHERE pa.invoice_line_id=l.id) a
                OUTER APPLY (SELECT SUM(f.amount) refunded FROM dbo.PaymentAllocation pa
                             JOIN dbo.RefundRequest rr ON rr.allocation_id=pa.id
                             JOIN dbo.Refund f ON f.request_id=rr.id WHERE pa.invoice_line_id=l.id) r
                WHERE l.invoice_id=? ORDER BY l.line_no
                """, (rs, n) -> {
                    BigDecimal amount = rs.getBigDecimal("amount");
                    BigDecimal allocated = rs.getBigDecimal("allocated");
                    BigDecimal refunded = rs.getBigDecimal("refunded");
                    BigDecimal outstanding = "ACTIVE".equals(rs.getString("status"))
                            ? amount.subtract(allocated.subtract(refunded)).max(BigDecimal.ZERO) : BigDecimal.ZERO;
                    return new LineBalance(rs.getLong("id"), rs.getInt("line_no"), rs.getString("line_type"),
                            rs.getString("status"), amount, allocated, refunded, outstanding);
                }, invoiceId);
    }

    public Existing existing(String key) {
        return jdbc.query("SELECT id,invoice_id,confirmed_by,method,amount FROM dbo.Payment WHERE idempotency_key=?",
                (rs, n) -> new Existing(rs.getLong("id"), rs.getLong("invoice_id"),
                rs.getObject("confirmed_by", Long.class), rs.getString("method"), rs.getBigDecimal("amount")), key)
                .stream().findFirst().orElse(null);
    }

    public List<OrderLine> orderLines(long invoiceId) {
        return jdbc.query("""
                SELECT l.id AS line_id,l.service_id AS line_service,o.id,o.encounter_id,o.service_id,o.status,
                       o.fully_paid_at,o.expires_at,o.validity_days,o.started_at,o.completed_at
                FROM dbo.InvoiceLine l JOIN dbo.ClinicalOrder o ON o.id=l.order_id
                WHERE l.invoice_id=? AND l.line_type='CLS' AND l.status='ACTIVE' ORDER BY o.id
                """,(rs,n)->new OrderLine(rs.getLong("id"),rs.getLong("line_id"),rs.getLong("encounter_id"),
                rs.getLong("service_id")==rs.getLong("line_service"),rs.getString("status"),
                rs.getObject("fully_paid_at")==null && rs.getObject("expires_at")==null
                    && rs.getObject("validity_days")==null && rs.getObject("started_at")==null
                    && rs.getObject("completed_at")==null),invoiceId);
    }

    public int validityDays() {
        var values=jdbc.query("""
                SELECT CASE WHEN TRY_CONVERT(int,setting_value) BETWEEN 1 AND
                    DATEDIFF(DAY,SYSUTCDATETIME(),CONVERT(datetime2,'9999-12-31'))
                    THEN TRY_CONVERT(int,setting_value) END
                FROM dbo.SystemSetting WITH (HOLDLOCK) WHERE setting_key='CLS_VALIDITY_DAYS'
                """,(rs,n)->rs.getObject(1,Integer.class));
        if(values.isEmpty()) return 10;
        if(values.getFirst()==null) throw new com.suncare.clinic.common.exception.ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"CLS_VALIDITY_INVALID","Cấu hình hạn CLS không hợp lệ. Hãy liên hệ quản trị viên.");
        return values.getFirst();
    }

    public void ready(long orderId,long paymentId,int days) {
        int changed=jdbc.update("""
                UPDATE o SET status='READY',fully_paid_at=p.paid_at,validity_days=?,
                    expires_at=DATEADD(DAY,?,p.paid_at),updated_at=SYSUTCDATETIME()
                FROM dbo.ClinicalOrder o CROSS JOIN dbo.Payment p
                WHERE o.id=? AND p.id=? AND o.status='PENDING_PAYMENT' AND o.fully_paid_at IS NULL
                    AND o.validity_days IS NULL AND o.expires_at IS NULL AND o.started_at IS NULL
                """,days,days,orderId,paymentId);
        if(changed!=1) throw new com.suncare.clinic.common.exception.ApiException(
                org.springframework.http.HttpStatus.CONFLICT,"CLINICAL_ORDER_NOT_PAYABLE","Chỉ định đã thay đổi. Hãy tải lại hóa đơn.");
        jdbc.update("""
                INSERT dbo.ClinicalOrderEvent(order_id,from_status,to_status,occurred_at,reason)
                SELECT ?,'PENDING_PAYMENT','READY',paid_at,N'Đã phân bổ đủ tiền' FROM dbo.Payment WHERE id=?
                """,orderId,paymentId);
        jdbc.update("""
                INSERT dbo.AuditEvent(actor_type,action,entity_type,entity_id,occurred_at,correlation_id,redacted_change)
                VALUES ('SYSTEM','CLINICAL_ORDER_READY','ClinicalOrder',?,SYSUTCDATETIME(),?,?)
                """,orderId,UUID.randomUUID().toString(),"payment_id="+paymentId);
    }

    public long insert(long invoiceId, long actorId, String key, BigDecimal amount) {
        return jdbc.queryForObject("""
                INSERT dbo.Payment(code,invoice_id,method,amount,currency,status,paid_at,confirmed_by,idempotency_key)
                OUTPUT INSERTED.id VALUES (?,?,'CASH',?,'VND','CONFIRMED',SYSUTCDATETIME(),?,?)
                """, Long.class, "PAY" + UUID.randomUUID().toString().replace("-", ""), invoiceId, amount, actorId, key);
    }

    public void allocate(long paymentId, long lineId, BigDecimal amount) {
        jdbc.update("INSERT dbo.PaymentAllocation(payment_id,invoice_line_id,amount) VALUES (?,?,?)",
                paymentId, lineId, amount);
    }

    public void audit(long actorId, long paymentId) {
        jdbc.update("""
                INSERT dbo.AuditEvent(actor_id,actor_type,action,entity_type,entity_id,occurred_at,correlation_id)
                VALUES (?,'USER','CASH_PAYMENT_CONFIRMED','Payment',?,SYSUTCDATETIME(),?)
                """, actorId, paymentId, UUID.randomUUID().toString());
    }

    public CashPaymentResponse detail(long paymentId, boolean replayed) {
        var allocations = jdbc.query("""
                SELECT pa.invoice_line_id,pa.amount FROM dbo.PaymentAllocation pa
                JOIN dbo.InvoiceLine l ON l.id=pa.invoice_line_id WHERE pa.payment_id=? ORDER BY l.line_no
                """, (rs, n) -> new CashPaymentResponse.Allocation(rs.getLong(1), rs.getBigDecimal(2)), paymentId);
        return jdbc.queryForObject("""
                SELECT *,CONVERT(varchar(23),paid_at,126)+'Z' paid_utc FROM dbo.Payment WHERE id=?
                """, (rs, n) -> new CashPaymentResponse(rs.getLong("id"), rs.getString("code"),
                rs.getLong("invoice_id"), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getString("method"), rs.getString("status"), rs.getLong("confirmed_by"),
                rs.getString("paid_utc"), replayed, allocations), paymentId);
    }

    public record Source(Long encounterId, Long appointmentId, Long holdId) {}
    public record Existing(long id, long invoiceId, Long actorId, String method, BigDecimal amount) {}
    public record OrderLine(long orderId,long lineId,long encounterId,boolean serviceMatches,String status,boolean unpaid) {}
}
