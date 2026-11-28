package com.suncare.clinic.modules.billing.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.billing.dto.InvoiceResponse;
import com.suncare.clinic.modules.billing.dto.InvoicePrintResponse;
import com.suncare.clinic.modules.billing.dto.EncounterBillingSummaryResponse;
import com.suncare.clinic.modules.billing.repository.CashPaymentRepository;
import com.suncare.clinic.modules.billing.dto.IssueInvoiceRequest;
import com.suncare.clinic.modules.billing.repository.InvoiceRepository;
import com.suncare.clinic.modules.encounter.repository.EncounterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final EncounterRepository encounters;
    private final CashPaymentRepository payments;
    private final String facilityName;
    private final String facilityAddress;

    public InvoiceService(InvoiceRepository invoices, EncounterRepository encounters, CashPaymentRepository payments,
            @Value("${suncare.billing.facility-name:}") String facilityName,
            @Value("${suncare.billing.facility-address:}") String facilityAddress) {
        this.invoices=invoices;
        this.encounters=encounters;
        this.payments=payments;
        this.facilityName=facilityName.strip();
        this.facilityAddress=facilityAddress.strip();
    }

    @Transactional
    public List<InvoiceResponse> list(CurrentAccountResponse account,long encounterId) {
        owned(account,encounterId);
        return invoices.ids(encounterId).stream().map(id->invoices.detail(encounterId,id,false)).toList();
    }

    @Transactional
    public InvoicePrintResponse printDetail(CurrentAccountResponse account, long encounterId, long invoiceId) {
        owned(account, encounterId);
        var detail = invoices.printDetail(encounterId, invoiceId);
        if (detail == null) throw new ApiException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "Không tìm thấy hóa đơn của lượt khám.");
        return detail;
    }

    @Transactional
    public EncounterBillingSummaryResponse summary(CurrentAccountResponse account, long encounterId) {
        owned(account, encounterId);
        var source = invoices.source(encounterId);
        var documents = invoices.ids(encounterId).stream().map(id -> invoices.detail(encounterId, id, false)).toList();
        var costs = new java.util.ArrayList<EncounterBillingSummaryResponse.Cost>();
        var billingInvoices = new java.util.ArrayList<EncounterBillingSummaryResponse.BillingInvoice>();
        java.math.BigDecimal invoiced = java.math.BigDecimal.ZERO;
        java.math.BigDecimal received = java.math.BigDecimal.ZERO;
        boolean examinationInvoiced = false;
        for (var invoice : documents) {
            invoices.lockInvoice(encounterId, invoice.id());
            var balances = payments.balances(invoice.id());
            var paid = invoices.receivedAmount(invoice.id());
            boolean settled = balances.stream().allMatch(line -> line.outstanding().signum() == 0);
            boolean hasPayableLines = balances.stream().anyMatch(line -> "ACTIVE".equals(line.status()));
            String paymentStatus = "VOID".equals(invoice.status()) || !hasPayableLines ? "NOT_APPLICABLE"
                    : !"ISSUED".equals(invoice.status()) ? "NOT_DUE"
                    : settled && paid.signum() > 0 ? "PAID" : paid.signum() > 0 ? "PARTIAL" : "UNPAID";
            var outstanding = "VOID".equals(invoice.status()) ? java.math.BigDecimal.ZERO
                    : balances.stream().filter(line -> "ACTIVE".equals(line.status()))
                        .map(line -> line.outstanding()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            billingInvoices.add(new EncounterBillingSummaryResponse.BillingInvoice(invoice, money(paid), paymentStatus, money(outstanding)));
            received = received.add(paid);
            if ("VOID".equals(invoice.status())) continue;
            invoiced = invoiced.add(invoice.totalAmount());
            for (var line : invoice.lines()) {
                if ("EXAMINATION".equals(line.lineType())) examinationInvoiced = true;
                costs.add(new EncounterBillingSummaryResponse.Cost(line.serviceName(), line.unitName(),
                        line.quantity(), money(line.unitPrice()), money(line.amount()), line.status()));
            }
        }
        if (!examinationInvoiced) costs.addFirst(new EncounterBillingSummaryResponse.Cost(source.serviceName(),
                "Lượt", java.math.BigDecimal.ONE, money(source.amount()), money(source.amount()), "ACTIVE"));
        return new EncounterBillingSummaryResponse(money(source.amount()), money(invoiced),
                money(examinationInvoiced ? java.math.BigDecimal.ZERO : source.amount()), money(received),
                examinationInvoiced, costs, billingInvoices);
    }

    private String money(java.math.BigDecimal amount) { return amount.toBigIntegerExact().toString(); }

    @Transactional
    public InvoiceResponse create(CurrentAccountResponse account,long encounterId,String key) {
        String state=owned(account,encounterId);
        String type="ONSITE_EXAM_INVOICE_CREATE";
        String hash=hash(Long.toString(encounterId));
        var replay=replay(account,encounterId,type,key,hash);
        if(replay!=null) return replay;
        writable(state);
        configured();
        if(invoices.hasExamination(encounterId)) throw conflict("EXAMINATION_ALREADY_INVOICED","Lượt khám đã có hóa đơn phí khám. Hãy dùng hóa đơn hiện có.");
        var source=invoices.source(encounterId);
        requireSource(source);
        long id=invoices.insertDraft(encounterId,account.accountId(),source,facilityName,facilityAddress);
        invoices.insertExamination(id,source);
        invoices.audit(account.accountId(),id,"INVOICE_DRAFT_CREATED");
        invoices.receipt(account.accountId(),type,key,hash,id);
        return invoices.detail(encounterId,id,false);
    }

    @Transactional
    public InvoiceResponse issue(CurrentAccountResponse account,long encounterId,long invoiceId,String key,IssueInvoiceRequest request) {
        String state=owned(account,encounterId);
        String type="ONSITE_EXAM_INVOICE_ISSUE";
        String hash=hash(encounterId+":"+invoiceId+":"+request.version());
        var replay=replay(account,encounterId,type,key,hash);
        if(replay!=null) return replay;
        writable(state);
        configured();
        invoices.lockClinicalOrders(invoiceId);
        invoices.lockInvoice(encounterId,invoiceId);
        var invoice=invoices.detail(encounterId,invoiceId,false);
        if(invoice==null) throw notFound();
        if(!"DRAFT".equals(invoice.status())) throw conflict("INVOICE_NOT_DRAFT","Hóa đơn đã phát hành hoặc đã hủy, không thể phát hành lại.");
        var source=invoices.source(encounterId);
        requireSource(source);
        validateDraft(encounterId,invoice,source);
        if(invoices.issue(invoiceId,version(request.version()),account.accountId(),source,facilityName,facilityAddress)!=1)
            throw conflict("INVOICE_CHANGED","Hóa đơn đã thay đổi. Hãy tải lại trước khi phát hành.");
        invoices.snapshotLine(invoiceId,source.serviceName());
        invoices.audit(account.accountId(),invoiceId,"INVOICE_ISSUED");
        invoices.receipt(account.accountId(),type,key,hash,invoiceId);
        return invoices.detail(encounterId,invoiceId,false);
    }

    private void validateDraft(long encounterId,InvoiceResponse invoice,InvoiceRepository.Source source) {
        if(invoice.patientId()!=source.patientId() || invoice.lines().isEmpty() || !"VND".equals(invoice.currency()))
            throw invalidInvoice();
        java.math.BigDecimal total=java.math.BigDecimal.ZERO;
        for(var line:invoice.lines()) {
            if(!"ACTIVE".equals(line.status()) || line.serviceName()==null || line.serviceName().isBlank()
                    || line.unitName()==null || line.unitName().isBlank()
                    || line.quantity().compareTo(java.math.BigDecimal.ONE)!=0 || line.unitPrice().signum()<=0
                    || line.amount().compareTo(line.unitPrice())!=0) throw invalidInvoice();
            if("EXAMINATION".equals(line.lineType())) {
                if(line.serviceId()!=source.serviceId() || line.priceId()!=source.priceId()
                        || line.amount().compareTo(source.amount())!=0) throw invalidInvoice();
            } else if(!"CLS".equals(line.lineType())) throw invalidInvoice();
            total=total.add(line.amount());
        }
        var examinations=invoices.examinationLines(encounterId);
        if(examinations.size()!=1 || !"ACTIVE".equals(examinations.getFirst().lineStatus())) throw invalidInvoice();
        var examination=examinations.getFirst();
        if(examination.invoiceId()!=invoice.id() && !"ISSUED".equals(examination.invoiceStatus())) throw invalidInvoice();
        if(total.compareTo(invoice.totalAmount())!=0 || !invoices.validClinicalLines(encounterId,invoice.id())) throw invalidInvoice();
    }

    private String owned(CurrentAccountResponse account,long encounterId) {
        Long appointmentId=encounters.appointmentId(encounterId);
        if(account.staffId()==null || appointmentId==null) throw notFound();
        var appointment=encounters.lockAppointment(appointmentId,account.staffId());
        if(appointment==null) throw notFound();
        if(!"ONSITE".equals(appointment.visitMode())) throw conflict("ONSITE_INVOICE_ONLY","Phí khám online thuộc hóa đơn giữ chỗ, không lập lại tại đây.");
        String state=encounters.lockStatus(encounterId);
        return "BOOKED".equals(appointment.status()) ? state : "READ_ONLY";
    }

    private InvoiceResponse replay(CurrentAccountResponse account,long encounterId,String type,String key,String hash) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{1,100}")) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","Khóa yêu cầu không hợp lệ.");
        invoices.lockCommand(type,key);
        var receipt=invoices.receipt(type,key);
        if(receipt==null) return null;
        if(receipt.accountId()!=account.accountId() || !receipt.hash().equals(hash))
            throw conflict("IDEMPOTENCY_CONFLICT","Khóa yêu cầu đã dùng cho nội dung khác.");
        var result=invoices.detail(encounterId,receipt.invoiceId(),true);
        if(result==null) throw notFound();
        return result;
    }

    private void writable(String state) {
        if(!List.of("IN_PROGRESS","WAITING_CLS").contains(state))
            throw conflict("ENCOUNTER_NOT_IN_PROGRESS","Chỉ lập hoặc phát hành hóa đơn sau khi đã bắt đầu khám và trước khi hoàn tất.");
    }

    private void configured() {
        if(facilityName.isBlank() || facilityName.length()>200 || facilityAddress.isBlank() || facilityAddress.length()>500)
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"BILLING_FACILITY_NOT_CONFIGURED","Chưa cấu hình tên và địa chỉ cơ sở để lập hóa đơn. Hãy liên hệ quản trị viên.");
    }

    private void requireSource(InvoiceRepository.Source source) {
        if(!"EXAMINATION".equals(source.serviceType()) || source.amount()==null || source.amount().signum()<=0
                || source.patientName()==null || source.patientName().isBlank() || source.serviceName()==null || source.serviceName().isBlank()) throw invalidInvoice();
    }

    private byte[] version(String value) {
        try { byte[] bytes=Base64.getDecoder().decode(value); if(bytes.length==8) return bytes; }
        catch(IllegalArgumentException ignored) { }
        throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_VERSION","Phiên bản hóa đơn không hợp lệ. Hãy tải lại.");
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"INVOICE_NOT_FOUND","Không tìm thấy hóa đơn hoặc lượt khám."); }
    private ApiException conflict(String code,String message) { return new ApiException(HttpStatus.CONFLICT,code,message); }
    private ApiException invalidInvoice() { return conflict("INVOICE_INVALID","Hóa đơn thiếu phí khám, chỉ định không hợp lệ hoặc tổng/snapshot không khớp. Hãy tải lại và kiểm tra."); }
}
