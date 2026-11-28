package com.suncare.clinic.modules.clinical.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.billing.repository.InvoiceRepository;
import com.suncare.clinic.modules.clinical.dto.ClinicalOrderResponse;
import com.suncare.clinic.modules.clinical.dto.CreateClinicalOrderRequest;
import com.suncare.clinic.modules.clinical.dto.ClinicalOrderOptionResponse;
import com.suncare.clinic.modules.clinical.dto.ClinicalOrderListResponse;
import com.suncare.clinic.modules.clinical.repository.ClinicalOrderRepository;
import com.suncare.clinic.modules.clinical.repository.ClinicalServiceRepository;
import com.suncare.clinic.modules.encounter.repository.EncounterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.math.BigDecimal;

@Service
public class ClinicalOrderService {
    private final ClinicalOrderRepository orders;
    private final ClinicalServiceRepository catalog;
    private final EncounterRepository encounters;
    private final InvoiceRepository invoices;
    private final String facilityName;
    private final String facilityAddress;

    public ClinicalOrderService(ClinicalOrderRepository orders, ClinicalServiceRepository catalog,
            EncounterRepository encounters, InvoiceRepository invoices,
            @Value("${suncare.billing.facility-name:}") String facilityName,
            @Value("${suncare.billing.facility-address:}") String facilityAddress) {
        this.orders=orders;
        this.catalog=catalog;
        this.encounters=encounters;
        this.invoices=invoices;
        this.facilityName=facilityName.strip();
        this.facilityAddress=facilityAddress.strip();
    }

    @Transactional
    public List<ClinicalOrderListResponse> list(CurrentAccountResponse account,long encounterId) {
        readable(account,encounterId);
        return orders.list(encounterId);
    }

    @Transactional
    public List<ClinicalOrderOptionResponse> options(CurrentAccountResponse account,long encounterId,String search) {
        readable(account,encounterId);
        if(search==null || search.length()>100)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CLINICAL_SEARCH","Từ khóa tối đa 100 ký tự.");
        return orders.options(search.strip());
    }

    private void readable(CurrentAccountResponse account,long encounterId) {
        Long appointmentId=encounters.appointmentId(encounterId);
        if(!account.roles().contains("DOCTOR") || account.staffId()==null || appointmentId==null) throw notFound();
        if(encounters.lockAppointment(appointmentId,account.staffId())==null) throw notFound();
        encounters.lockStatus(encounterId);
    }

    @Transactional
    public ClinicalOrderResponse create(CurrentAccountResponse account, long encounterId, String key,
            CreateClinicalOrderRequest request) {
        Long appointmentId=encounters.appointmentId(encounterId);
        if (!account.roles().contains("DOCTOR") || account.staffId()==null || appointmentId==null) throw notFound();
        var appointment=encounters.lockAppointment(appointmentId,account.staffId());
        if (appointment==null) throw notFound();
        String state=encounters.lockStatus(encounterId);
        if (key==null || !key.matches("[A-Za-z0-9_-]{1,100}"))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","Khóa yêu cầu không hợp lệ.");
        String hash=hash(encounterId+":"+request.serviceId()+":"+request.performingDepartmentId()+":"
                +request.requestedRoomId()+":"+request.indication().strip());
        invoices.lockCommand("CLINICAL_ORDER_CREATE",key);
        var receipt=invoices.receipt("CLINICAL_ORDER_CREATE",key);
        if (receipt!=null) {
            if (receipt.accountId()!=account.accountId() || !receipt.hash().equals(hash))
                throw conflict("IDEMPOTENCY_CONFLICT","Khóa yêu cầu đã dùng cho nội dung khác.");
            var result=orders.detail(encounterId,receipt.invoiceId(),true);
            if (result==null) throw notFound();
            return result;
        }
        // Online billing depends on the hold/payment flow implemented in P33–P36.
        if (!"ONSITE".equals(appointment.visitMode())) throw conflict("ONSITE_ORDER_ONLY","Chỉ định online chưa được mở ở bước này.");
        if (!"BOOKED".equals(appointment.status()) || !List.of("IN_PROGRESS","WAITING_CLS").contains(state))
            throw conflict("ENCOUNTER_NOT_IN_PROGRESS","Chỉ lập chỉ định sau khi bắt đầu khám và trước khi hoàn tất.");
        var service=catalog.lockService(request.serviceId());
        if (service==null || !service.isActive() || !"CLS".equals(service.serviceType()))
            throw conflict("CLINICAL_SERVICE_UNAVAILABLE","Dịch vụ CLS không tồn tại hoặc đã ngừng hoạt động.");
        if (!orders.supports(service.id(),request.performingDepartmentId(),request.requestedRoomId()))
            throw conflict("CLINICAL_ROOM_UNSUPPORTED","Khoa/phòng không hoạt động hoặc không hỗ trợ dịch vụ này.");
        var now=orders.now();
        var effective=catalog.findEffectivePrice(service.id(),null,null,now);
        if (effective==null) throw conflict("CLINICAL_PRICE_UNAVAILABLE","Dịch vụ chưa có giá hiệu lực.");
        var price=catalog.lockPrice(effective.priceId());
        if (price==null || price.amount().signum()<=0 || price.validFrom().isAfter(now)
                || (price.validTo()!=null && !price.validTo().isAfter(now)))
            throw conflict("CLINICAL_PRICE_UNAVAILABLE","Giá dịch vụ đã thay đổi. Hãy thử lại.");
        var drafts=orders.drafts(encounterId);
        if (drafts.size()>1) throw conflict("MULTIPLE_DRAFT_INVOICES","Có nhiều hóa đơn nháp. Cần kiểm tra trước khi lập chỉ định.");
        long invoiceId;
        if (!drafts.isEmpty()) invoiceId=drafts.getFirst();
        else {
            if (facilityName.isBlank() || facilityName.length()>200 || facilityAddress.isBlank() || facilityAddress.length()>500)
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"BILLING_FACILITY_NOT_CONFIGURED","Chưa cấu hình tên và địa chỉ cơ sở để lập hóa đơn.");
            var source=invoices.source(encounterId);
            boolean examination=invoices.hasExamination(encounterId);
            var draftSource=new InvoiceRepository.Source(source.patientId(),source.priceId(),source.serviceId(),
                    examination ? BigDecimal.ZERO : source.amount(),source.patientName(),source.patientAddress(),source.serviceName(),source.serviceType());
            invoiceId=invoices.insertDraft(encounterId,account.accountId(),draftSource,facilityName,facilityAddress);
            if (!examination) invoices.insertExamination(invoiceId,source);
            invoices.audit(account.accountId(),invoiceId,"INVOICE_DRAFT_CREATED");
        }
        long id=orders.insert(encounterId,account.accountId(),request);
        orders.line(invoiceId,id,service.id(),price.id(),service.name(),price.amount());
        orders.event(id,account.accountId());
        orders.receipt(account.accountId(),key,hash,id);
        return orders.detail(encounterId,id,false);
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"ENCOUNTER_NOT_FOUND","Không tìm thấy lượt khám trong phạm vi phụ trách."); }
    private ApiException conflict(String code,String message) { return new ApiException(HttpStatus.CONFLICT,code,message); }
}
