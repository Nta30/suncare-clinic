package com.suncare.clinic.modules.clinical.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.billing.repository.InvoiceRepository;
import com.suncare.clinic.modules.clinical.dto.*;
import com.suncare.clinic.modules.clinical.repository.LabWorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Service
public class LabWorkspaceService {
    private final LabWorkspaceRepository lab;
    private final InvoiceRepository commands;
    public LabWorkspaceService(LabWorkspaceRepository lab,InvoiceRepository commands) {
        this.lab=lab; this.commands=commands;
    }

    private long staff(CurrentAccountResponse account) {
        if(account==null || account.staffId()==null || !account.roles().contains("LAB_STAFF"))
            throw new ApiException(HttpStatus.FORBIDDEN,"LAB_ACCESS_DENIED","Cần quyền nhân viên cận lâm sàng.");
        return account.staffId();
    }

    public List<LabAssignmentResponse> assignments(CurrentAccountResponse account,boolean current) {
        return lab.assignments(staff(account),current);
    }

    public List<LabOrderResponse> queue(CurrentAccountResponse account,long roomId) {
        long staffId=staff(account);
        boolean duty=lab.assignments(staffId,true).stream().anyMatch(a->a.roomId()==roomId);
        return lab.queue(staffId,roomId,duty);
    }

    public java.util.Map<String,Integer> counts(CurrentAccountResponse account,long roomId) {
        long staffId=staff(account);
        if(lab.assignments(staffId,true).stream().noneMatch(a->a.roomId()==roomId)) return java.util.Map.of();
        var counts=lab.counts(roomId);
        counts.put("READY",(int)lab.queue(staffId,roomId,true).stream().filter(o->"READY".equals(o.status())).count());
        return counts;
    }

    public LabOrderResponse detail(CurrentAccountResponse account,long id,long roomId) {
        long staffId=staff(account);
        var order=lab.detail(id);
        if(order==null) throw notFound();
        if(order.acceptedBy()!=null && order.acceptedBy()==staffId
                && List.of("ACCEPTED","IN_PROGRESS").contains(order.status())) return order;
        if(!lab.assignments(staffId,true).stream().anyMatch(a->a.roomId()==roomId)
                || !lab.supports(id,roomId) || !"READY".equals(order.status()) || !order.paid()
                || order.expiresAt()==null || !java.time.Instant.parse(order.expiresAt()).isAfter(java.time.Instant.now()))
            throw notFound();
        return order;
    }

    @Transactional
    public LabOrderResponse act(CurrentAccountResponse account,long id,String key,LabActionRequest request,boolean start) {
        long staffId=staff(account);
        Long encounterId=lab.encounterId(id);
        if(encounterId==null) throw notFound();
        lab.lock(encounterId,staffId,id);
        String type=start?"LAB_START":"LAB_ACCEPT";
        if(key==null || !key.matches("[A-Za-z0-9_-]{1,100}"))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","Khóa yêu cầu không hợp lệ.");
        String hash=hash(id+":"+request.roomId()+":"+request.version()+":"+request.patientPresent());
        commands.lockCommand(type,key);
        var receipt=commands.receipt(type,key);
        var order=lab.detail(id);
        if(receipt!=null) {
            if(receipt.accountId()!=account.accountId() || !receipt.hash().equals(hash) || receipt.invoiceId()!=id)
                throw conflict("IDEMPOTENCY_CONFLICT","Khóa yêu cầu đã dùng cho nội dung khác.");
            if(order.acceptedBy()==null || order.acceptedBy()!=staffId) throw notFound();
            return order;
        }
        var now=lab.now();
        if(!lab.onDuty(staffId,request.roomId(),now) || !lab.supports(id,request.roomId()))
            throw conflict("LAB_NOT_ON_DUTY","Không đúng phòng hỗ trợ dịch vụ hoặc không còn ca trực hợp lệ.");
        if(start && (order.acceptedBy()==null || order.acceptedBy()!=staffId
                || order.roomId()==null || order.roomId()!=request.roomId()))
            throw conflict("LAB_NOT_ASSIGNED","Chỉ nhân viên đã tiếp nhận tại phòng này mới được bắt đầu.");
        if(!order.version().equals(request.version()))
            throw conflict("LAB_VERSION_CONFLICT","Chỉ định đã thay đổi. Hãy tải lại dữ liệu.");
        if(!order.paid() || order.fullyPaidAt()==null)
            throw conflict("LAB_PAYMENT_REQUIRED","Chỉ định chưa thanh toán đầy đủ hoặc dòng hóa đơn không còn hiệu lực.");
        if(order.expiresAt()==null || !java.time.Instant.parse(order.expiresAt())
                .isAfter(now.toInstant(java.time.ZoneOffset.UTC)))
            throw conflict("LAB_ORDER_EXPIRED","Chỉ định đã hết hạn thực hiện.");
        if(start) {
            if(!"ACCEPTED".equals(order.status())) throw conflict("LAB_STATE_CONFLICT","Chỉ định không còn ở trạng thái đã tiếp nhận.");
            lab.start(id,account.accountId(),now);
        } else {
            if(!request.patientPresent()) throw new ApiException(HttpStatus.BAD_REQUEST,"PATIENT_NOT_PRESENT","Cần xác nhận đúng bệnh nhân và bệnh nhân đã có mặt.");
            if(!"READY".equals(order.status()) || order.acceptedBy()!=null)
                throw conflict("LAB_STATE_CONFLICT","Chỉ định đã được tiếp nhận hoặc không còn sẵn sàng.");
            lab.accept(id,request.roomId(),account.accountId(),staffId,now);
        }
        lab.receipt(account.accountId(),type,key,hash,id);
        return lab.detail(id);
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"LAB_ORDER_NOT_FOUND","Không tìm thấy chỉ định trong phạm vi phụ trách."); }
    private ApiException conflict(String code,String message) { return new ApiException(HttpStatus.CONFLICT,code,message); }
}
