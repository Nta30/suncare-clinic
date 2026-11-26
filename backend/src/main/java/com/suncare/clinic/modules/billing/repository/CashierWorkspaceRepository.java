package com.suncare.clinic.modules.billing.repository;

import com.suncare.clinic.modules.billing.dto.CashierWorkspaceResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CashierWorkspaceRepository {
    private final JdbcTemplate jdbc;
    public CashierWorkspaceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Same per-line net allocation/refund calculation as P21; aggregate before invoice joins.
    private static final String SOURCE = """
            FROM dbo.Invoice i JOIN dbo.Patient p ON p.id=i.patient_id
            JOIN dbo.Encounter e ON e.id=i.encounter_id JOIN dbo.Appointment ap ON ap.id=e.appointment_id
            OUTER APPLY (
                SELECT COUNT(*) active_lines, COALESCE(SUM(CASE WHEN l.amount-COALESCE(a.amount,0)+COALESCE(r.amount,0)>0
                    THEN l.amount-COALESCE(a.amount,0)+COALESCE(r.amount,0) ELSE 0 END),0) due
                FROM dbo.InvoiceLine l
                OUTER APPLY (SELECT SUM(pa.amount) amount FROM dbo.PaymentAllocation pa WHERE pa.invoice_line_id=l.id) a
                OUTER APPLY (SELECT SUM(f.amount) amount FROM dbo.PaymentAllocation pa
                    JOIN dbo.RefundRequest rr ON rr.allocation_id=pa.id JOIN dbo.Refund f ON f.request_id=rr.id
                    WHERE pa.invoice_line_id=l.id) r
                WHERE l.invoice_id=i.id AND l.status='ACTIVE'
            ) b
            WHERE i.status='ISSUED' AND i.hold_id IS NULL AND ap.visit_mode='ONSITE'
            """;

    public CashierWorkspaceResponse search(String term, String status, int page, int pageSize) {
        String normalized = java.text.Normalizer.normalize(term, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).replace('đ', 'd');
        String like = "%" + normalized.replace("~", "~~").replace("%", "~%").replace("_", "~_").replace("[", "~[") + "%";
        String filter = """
                 AND (LOWER(i.code) LIKE ? ESCAPE '~' OR LOWER(p.code) LIKE ? ESCAPE '~'
                  OR LOWER(REPLACE(i.patient_name_snapshot COLLATE Latin1_General_100_CI_AI,N'đ',N'd')) LIKE ? COLLATE Latin1_General_100_CI_AI ESCAPE '~'
                  OR LOWER(REPLACE(p.full_name COLLATE Latin1_General_100_CI_AI,N'đ',N'd')) LIKE ? COLLATE Latin1_General_100_CI_AI ESCAPE '~'
                  OR p.phone LIKE ? ESCAPE '~' OR p.identity_number=?)
                """;
        if ("PENDING".equals(status)) filter += " AND b.due>0";
        if ("PAID".equals(status)) filter += " AND b.active_lines>0 AND b.due=0";
        var args = new ArrayList<Object>(List.of(like, like, like, like, like, term));
        long count = jdbc.queryForObject("SELECT COUNT_BIG(*) " + SOURCE + filter, Long.class, args.toArray());
        int totalPages = (int) Math.min(Integer.MAX_VALUE, (count + pageSize - 1) / pageSize);
        int currentPage = Math.min(page, Math.max(1, totalPages));
        args.add((long) (currentPage - 1) * pageSize);
        args.add(pageSize);
        var items = jdbc.query("""
                SELECT i.id,i.code,p.code patient_code,i.patient_name_snapshot,p.phone,i.issued_at,i.total_amount,
                    CASE WHEN b.due>0 THEN 'PENDING' WHEN b.active_lines>0 THEN 'PAID' ELSE 'NOT_APPLICABLE' END payment_status
                """ + SOURCE + filter + " ORDER BY i.issued_at DESC,i.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, n) -> new CashierWorkspaceResponse.Item(rs.getLong("id"), rs.getString("code"),
                        rs.getString("patient_code"), rs.getString("patient_name_snapshot"), rs.getString("phone"),
                        rs.getTimestamp("issued_at").toLocalDateTime().toInstant(ZoneOffset.UTC).toString(), rs.getBigDecimal("total_amount").toBigIntegerExact().toString(),
                        rs.getString("payment_status")), args.toArray());
        return new CashierWorkspaceResponse(statistics(), items, currentPage, pageSize, count, totalPages);
    }

    private CashierWorkspaceResponse.Statistics statistics() {
        var counts = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN b.due>0 THEN CONVERT(bigint,1) ELSE 0 END),0) pending,"
                + "COALESCE(SUM(CASE WHEN b.active_lines>0 AND b.due=0 THEN CONVERT(bigint,1) ELSE 0 END),0) paid " + SOURCE,
                (rs, n) -> new long[]{rs.getLong("pending"), rs.getLong("paid")});
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Saigon"));
        // SQL datetime2 stores UTC wall-clock values; avoid JDBC's default JVM time zone.
        LocalDateTime start = LocalDateTime.ofInstant(today.atStartOfDay(ZoneId.of("Asia/Saigon")).toInstant(), ZoneOffset.UTC);
        LocalDateTime end = LocalDateTime.ofInstant(today.plusDays(1).atStartOfDay(ZoneId.of("Asia/Saigon")).toInstant(), ZoneOffset.UTC);
        BigDecimal received = jdbc.queryForObject("""
                SELECT COALESCE((SELECT SUM(pay.amount) FROM dbo.Payment pay
                    JOIN dbo.Invoice i ON i.id=pay.invoice_id JOIN dbo.Encounter e ON e.id=i.encounter_id
                    JOIN dbo.Appointment a ON a.id=e.appointment_id
                    WHERE pay.status='CONFIRMED' AND pay.method='CASH' AND a.visit_mode='ONSITE'
                        AND pay.paid_at>=? AND pay.paid_at<?),0)
                - COALESCE((SELECT SUM(f.amount) FROM dbo.Refund f JOIN dbo.RefundRequest rr ON rr.id=f.request_id
                    JOIN dbo.Payment pay ON pay.id=rr.payment_id JOIN dbo.Invoice i ON i.id=pay.invoice_id
                    JOIN dbo.Encounter e ON e.id=i.encounter_id JOIN dbo.Appointment a ON a.id=e.appointment_id
                    WHERE pay.method='CASH' AND a.visit_mode='ONSITE' AND f.refunded_at>=? AND f.refunded_at<?),0)
                """, BigDecimal.class, start, end, start, end);
        return new CashierWorkspaceResponse.Statistics(counts[0], counts[1], received.toBigIntegerExact().toString(), today.toString());
    }

    public String[] patient(long patientId) {
        return jdbc.queryForObject("SELECT code,phone FROM dbo.Patient WHERE id=?", (rs, n) ->
                new String[]{rs.getString("code"), rs.getString("phone")}, patientId);
    }
}
