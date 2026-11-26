package com.suncare.clinic.modules.billing.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.billing.dto.CashPaymentRequest;
import com.suncare.clinic.modules.billing.dto.CashPaymentResponse;
import com.suncare.clinic.modules.billing.dto.InvoiceBalanceResponse;
import com.suncare.clinic.modules.billing.repository.CashPaymentRepository;
import com.suncare.clinic.modules.billing.repository.InvoiceRepository;
import com.suncare.clinic.modules.billing.repository.CashierWorkspaceRepository;
import com.suncare.clinic.modules.billing.dto.CashierWorkspaceResponse;
import com.suncare.clinic.modules.billing.dto.CashierInvoiceDetailResponse;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashPaymentService {
    private final CashPaymentRepository payments;
    private final InvoiceRepository invoices;
    private final CashierWorkspaceRepository workspace;

    public CashPaymentService(CashPaymentRepository payments, InvoiceRepository invoices, CashierWorkspaceRepository workspace) {
        this.payments = payments;
        this.invoices = invoices;
        this.workspace = workspace;
    }

    @Transactional(readOnly = true)
    public CashierWorkspaceResponse search(String search, String status, int page, int pageSize) {
        if (search == null || search.length() > 100 || !java.util.List.of("PENDING", "PAID", "ALL").contains(status)
                || page < 1 || !java.util.List.of(10, 20, 50).contains(pageSize))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CASHIER_SEARCH", "Từ khóa tối đa 100 ký tự; kiểm tra bộ lọc và trang.");
        return workspace.search(search.strip().replaceAll("(?U)\\s+", " "), status, page, pageSize);
    }

    @Transactional
    public CashierInvoiceDetailResponse detail(long invoiceId) {
        var source = lock(invoiceId);
        var invoice = invoices.detail(source.encounterId(), invoiceId, false);
        var lines = payments.balances(invoiceId);
        BigDecimal due = lines.stream().map(InvoiceBalanceResponse.LineBalance::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean active = lines.stream().anyMatch(line -> "ACTIVE".equals(line.status()));
        String status = !"ISSUED".equals(invoice.status()) || !active ? "NOT_APPLICABLE" : due.signum() > 0 ? "PENDING" : "PAID";
        boolean canCollect = "PENDING".equals(status) && lines.stream().noneMatch(line -> "CLS".equals(line.lineType()) && "ACTIVE".equals(line.status()));
        var patient = workspace.patient(invoice.patientId());
        return new CashierInvoiceDetailResponse(invoice, patient[0], patient[1], due.toBigIntegerExact().toString(), status, canCollect);
    }

    @Transactional
    public java.util.List<InvoiceBalanceResponse> find(String code) {
        if (code == null || !code.strip().matches("[A-Za-z0-9_-]{1,40}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVOICE_CODE", "Nhập mã hóa đơn hợp lệ, tối đa 40 ký tự.");
        Long id = payments.findByCode(code.strip());
        return id == null ? java.util.List.of() : java.util.List.of(balance(id));
    }

    @Transactional
    public InvoiceBalanceResponse balance(long invoiceId) {
        var source = lock(invoiceId);
        var invoice = invoices.detail(source.encounterId(), invoiceId, false);
        if (invoice == null) throw notFound();
        var lines = payments.balances(invoiceId);
        return new InvoiceBalanceResponse(invoice,
                lines.stream().map(InvoiceBalanceResponse.LineBalance::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add), lines);
    }

    @Transactional
    public CashPaymentResponse collect(CurrentAccountResponse actor, long invoiceId, String key, CashPaymentRequest request) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,100}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Khóa yêu cầu không hợp lệ.");
        var source = lock(invoiceId);
        invoices.lockCommand("CASH_PAYMENT", key);
        var existing = payments.existing(key);
        if (existing != null) {
            if (existing.invoiceId() != invoiceId || !Long.valueOf(actor.accountId()).equals(existing.actorId())
                    || !"CASH".equals(existing.method()) || existing.amount().compareTo(request.amount()) != 0)
                throw conflict("IDEMPOTENCY_CONFLICT", "Khóa yêu cầu đã dùng cho giao dịch khác.");
            return payments.detail(existing.id(), true);
        }
        var invoice = invoices.detail(source.encounterId(), invoiceId, false);
        if (invoice == null) throw notFound();
        if (!"ISSUED".equals(invoice.status()))
            throw conflict("INVOICE_NOT_ISSUED", "Chỉ thu tiền cho hóa đơn đã phát hành.");
        var lines = payments.balances(invoiceId);
        // CLS readiness/expiry is implemented together with order payment at P24.
        if (lines.stream().anyMatch(line -> "CLS".equals(line.lineType()) && "ACTIVE".equals(line.status())))
            throw conflict("CLS_PAYMENT_NOT_AVAILABLE", "Thu tiền chỉ định cận lâm sàng chưa được triển khai.");
        BigDecimal outstanding = lines.stream().map(InvoiceBalanceResponse.LineBalance::outstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.amount().compareTo(outstanding) > 0)
            throw conflict("PAYMENT_EXCEEDS_BALANCE", "Số tiền không khớp khoản thanh toán. Hãy tải lại hóa đơn.");
        if (request.amount().compareTo(outstanding) < 0)
            throw conflict("FULL_PAYMENT_REQUIRED", "Hóa đơn phải được thanh toán toàn bộ. Hãy tải lại hóa đơn.");
        long paymentId = payments.insert(invoiceId, actor.accountId(), key, request.amount());
        BigDecimal remaining = request.amount();
        for (var line : lines) {
            BigDecimal allocated = remaining.min(line.outstanding());
            if (allocated.signum() > 0) payments.allocate(paymentId, line.lineId(), allocated);
            remaining = remaining.subtract(allocated);
            if (remaining.signum() == 0) break;
        }
        payments.audit(actor.accountId(), paymentId);
        return payments.detail(paymentId, false);
    }

    private CashPaymentRepository.Source lock(long invoiceId) {
        var source = payments.source(invoiceId);
        if (source == null) throw notFound();
        if (source.holdId() != null)
            throw conflict("ONLINE_HOLD_CASH_NOT_ALLOWED", "Không thu tiền mặt cho hóa đơn giữ chỗ khám online.");
        if (source.encounterId() == null || source.appointmentId() == null) throw notFound();
        payments.lockSource(source);
        invoices.lockInvoice(source.encounterId(), invoiceId);
        return source;
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "Không tìm thấy hóa đơn.");
    }

    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
}
