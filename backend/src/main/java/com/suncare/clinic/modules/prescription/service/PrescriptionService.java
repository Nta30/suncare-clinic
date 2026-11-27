package com.suncare.clinic.modules.prescription.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.encounter.repository.EncounterRepository;
import com.suncare.clinic.modules.prescription.dto.*;
import com.suncare.clinic.modules.prescription.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class PrescriptionService {
    private final PrescriptionRepository prescriptions;
    private final EncounterRepository encounters;
    private final DrugRepository drugs;
    private final ObjectMapper json;

    public PrescriptionService(PrescriptionRepository prescriptions, EncounterRepository encounters, DrugRepository drugs, ObjectMapper json) {
        this.prescriptions=prescriptions;
        this.encounters=encounters;
        this.drugs=drugs;
        this.json=json;
    }

    public List<DrugResponse> activeDrugs(String search) {
        DrugService.validateSearch(search);
        return drugs.list(search, true);
    }

    @Transactional
    public List<PrescriptionResponse> list(CurrentAccountResponse account,long encounterId) {
        owned(account,encounterId);
        return prescriptions.ids(encounterId).stream().map(id->prescriptions.detail(encounterId,id,false)).toList();
    }

    @Transactional
    public PrescriptionResponse create(CurrentAccountResponse account,long encounterId,String key,PrescriptionCreateRequest request) {
        String state=owned(account,encounterId);
        String type="PRESCRIPTION_CREATE";
        String hash=hash(encounterId,0,request);
        var replay=replay(account,type,key,hash);
        if(replay!=null) return result(encounterId,replay.id(),true);
        writable(state);
        // Một bản làm việc/Encounter; sửa bản nháp hiện có hoặc bỏ nháp trước khi tạo lại.
        if(!prescriptions.ids(encounterId).isEmpty())
            throw conflict("PRESCRIPTION_EXISTS","Lượt khám đã có đơn thuốc. Hãy mở đơn hiện có.");
        var catalog=lockActiveDrugs(request.items().stream().map(PrescriptionSaveRequest.Item::drugId).toList());
        long id=prescriptions.create(encounterId,account.accountId());
        var created=result(encounterId,id,false);
        if(prescriptions.update(id,version(created.version()),request.instruction())!=1) throw changed();
        int line=0;
        for(var item:request.items()) prescriptions.insertItem(id,++line,item,catalog.get(item.drugId()));
        record(account,type,key,hash,id,"PRESCRIPTION_DRAFT_CREATED");
        return result(encounterId,id,false);
    }

    @Transactional
    public PrescriptionResponse save(CurrentAccountResponse account,long encounterId,long id,String key,PrescriptionSaveRequest request) {
        String state=owned(account,encounterId);
        String type="PRESCRIPTION_SAVE";
        String hash=hash(encounterId,id,request);
        var replay=replay(account,type,key,hash);
        if(replay!=null) return result(encounterId,replay.id(),true);
        writable(state);
        draft(encounterId,id);
        byte[] version=version(request.version());
        var catalog=lockActiveDrugs(request.items().stream().map(PrescriptionSaveRequest.Item::drugId).toList());
        if(prescriptions.update(id,version,request.instruction())!=1) throw changed();
        prescriptions.clearItems(id);
        int line=0;
        for(var item:request.items()) prescriptions.insertItem(id,++line,item,catalog.get(item.drugId()));
        record(account,type,key,hash,id,"PRESCRIPTION_DRAFT_SAVED");
        return result(encounterId,id,false);
    }

    @Transactional
    public PrescriptionResponse issue(CurrentAccountResponse account,long encounterId,long id,String key,PrescriptionVersionRequest request) {
        String state=owned(account,encounterId);
        String type="PRESCRIPTION_ISSUE";
        String hash=hash(encounterId,id,request);
        var replay=replay(account,type,key,hash);
        if(replay!=null) return result(encounterId,replay.id(),true);
        writable(state);
        var draft=draft(encounterId,id);
        boolean alreadyIssued=prescriptions.ids(encounterId).stream()
                .filter(otherId->otherId!=id)
                .map(otherId->prescriptions.detail(encounterId,otherId,false))
                .anyMatch(other->"ISSUED".equals(other.status()));
        if(alreadyIssued) throw conflict("PRESCRIPTION_ALREADY_ISSUED","Lượt khám đã có đơn phát hành.");
        if(draft.items().isEmpty()) throw conflict("EMPTY_PRESCRIPTION","Cần ít nhất một thuốc trước khi phát hành.");
        lockActiveDrugs(draft.items().stream().map(PrescriptionResponse.Item::drugId).toList());
        for(var item:draft.items()) {
            if(blank(item.drugName()) || blank(item.doseUnit()) || blank(item.dispensingUnit()) || blank(item.dose()) || blank(item.route())
                    || !PositiveDoseValidator.valid(item.dose()) || blank(item.frequency()) || item.durationDays()<=0
                    || item.quantity()==null || item.quantity().signum()<=0 || item.quantity().scale()>3
                    || item.quantity().compareTo(new java.math.BigDecimal("999999999.999"))>0)
                throw conflict("PRESCRIPTION_INVALID","Đơn thuốc còn dòng thiếu hoặc sai liều, đường dùng, số lần, số ngày hoặc số lượng.");
        }
        // Giữ snapshot đã lưu và đã cho bác sĩ xem; không tự thay bằng tên/hàm lượng mới lúc phát hành.
        if(prescriptions.issue(id,version(request.version()))!=1) throw changed();
        record(account,type,key,hash,id,"PRESCRIPTION_ISSUED");
        return result(encounterId,id,false);
    }

    @Transactional
    public DiscardResponse discard(CurrentAccountResponse account,long encounterId,long id,String key,PrescriptionVersionRequest request) {
        String state=owned(account,encounterId);
        String type="PRESCRIPTION_DISCARD";
        String hash=hash(encounterId,id,request);
        var replay=replay(account,type,key,hash);
        if(replay!=null) return new DiscardResponse(replay.id(),true,true);
        writable(state);
        draft(encounterId,id);
        prescriptions.clearItems(id);
        if(prescriptions.discard(id,version(request.version()))!=1) throw changed();
        record(account,type,key,hash,id,"PRESCRIPTION_DRAFT_DISCARDED");
        return new DiscardResponse(id,true,false);
    }

    private Map<Long,DrugResponse> lockActiveDrugs(List<Long> ids) {
        var result=new HashMap<Long,DrugResponse>();
        // Khóa theo ID để hai đơn nhiều thuốc không khóa ngược thứ tự.
        for(long id:new TreeSet<>(ids)) {
            var drug=drugs.find(id);
            if(drug==null || !drug.isActive())
                throw conflict("DRUG_UNAVAILABLE","Thuốc không còn sử dụng. Hãy kiểm tra lại danh mục và đơn thuốc chưa phát hành.");
            if(blank(drug.doseUnit()) || blank(drug.dispensingUnit()))
                throw conflict("DRUG_UNITS_REQUIRED","Thuốc chưa được xác nhận đơn vị liều dùng/cấp phát. Cần cập nhật danh mục trước khi kê.");
            result.put(id,drug);
        }
        return result;
    }

    private String owned(CurrentAccountResponse account,long encounterId) {
        if(!account.roles().contains("DOCTOR") || account.staffId()==null)
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chỉ bác sĩ phụ trách được thao tác đơn thuốc.");
        Long appointmentId=encounters.appointmentId(encounterId);
        if(appointmentId==null) throw notFound();
        var appointment=encounters.lockAppointment(appointmentId,account.staffId());
        if(appointment==null) throw notFound();
        String state=encounters.lockStatus(encounterId);
        return "BOOKED".equals(appointment.status())?state:"READ_ONLY";
    }

    private PrescriptionResponse draft(long encounterId,long id) {
        prescriptions.lock(encounterId,id);
        var result=result(encounterId,id,false);
        if(!"DRAFT".equals(result.status()))
            throw conflict("PRESCRIPTION_IMMUTABLE","Đơn đã phát hành, không thể sửa, bỏ hoặc phát hành lại.");
        return result;
    }

    private PrescriptionResponse result(long encounterId,long id,boolean replayed) {
        var result=prescriptions.detail(encounterId,id,replayed);
        if(result==null) throw notFound();
        return result;
    }

    private PrescriptionRepository.Receipt replay(CurrentAccountResponse account,String type,String key,String hash) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{1,100}"))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","Khóa yêu cầu không hợp lệ.");
        prescriptions.lockCommand(type,key);
        var receipt=prescriptions.receipt(type,key);
        if(receipt!=null && (receipt.accountId()!=account.accountId() || !receipt.hash().equals(hash)))
            throw conflict("IDEMPOTENCY_CONFLICT","Khóa yêu cầu đã dùng cho nội dung khác.");
        return receipt;
    }

    private void record(CurrentAccountResponse account,String type,String key,String hash,long id,String action) {
        prescriptions.audit(account.accountId(),id,action);
        prescriptions.receipt(account.accountId(),type,key,hash,id);
    }

    private void writable(String state) {
        if(!"IN_PROGRESS".equals(state))
            throw conflict("ENCOUNTER_NOT_IN_PROGRESS","Chỉ thay đổi đơn thuốc khi lượt khám đang khám.");
    }

    private byte[] version(String value) {
        try { var bytes=Base64.getDecoder().decode(value); if(bytes.length==8) return bytes; }
        catch(IllegalArgumentException | NullPointerException ignored) {}
        throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_VERSION","Phiên bản đơn thuốc không hợp lệ. Hãy tải lại.");
    }

    private String hash(long encounterId,long id,Object body) {
        try {
            String value=encounterId+":"+id+":"+json.writeValueAsString(body);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch(NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    private boolean blank(String value) { return value==null || value.isBlank(); }
    private ApiException changed() { return conflict("PRESCRIPTION_CHANGED","Đơn thuốc đã thay đổi. Hãy tải lại trước khi lưu hoặc phát hành."); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"PRESCRIPTION_NOT_FOUND","Không tìm thấy đơn thuốc hoặc lượt khám."); }
    private ApiException conflict(String code,String message) { return new ApiException(HttpStatus.CONFLICT,code,message); }
    public record DiscardResponse(long id,boolean discarded,boolean replayed) {}
}
