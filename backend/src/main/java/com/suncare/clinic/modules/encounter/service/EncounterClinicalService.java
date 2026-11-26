package com.suncare.clinic.modules.encounter.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.encounter.dto.DiagnosisRequest;
import com.suncare.clinic.modules.encounter.dto.EncounterClinicalResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterNotesRequest;
import com.suncare.clinic.modules.encounter.dto.VitalSignRequest;
import com.suncare.clinic.modules.encounter.repository.EncounterClinicalRepository;
import com.suncare.clinic.modules.encounter.repository.EncounterRepository;
import com.suncare.clinic.modules.encounter.repository.DiagnosisConceptRepository;
import com.suncare.clinic.modules.encounter.dto.DiagnosisConceptResponse;
import com.suncare.clinic.modules.encounter.dto.DiagnosisConceptSearchResponse;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.stream.Stream;

@Service
public class EncounterClinicalService {
    private final EncounterRepository encounters;
    private final EncounterClinicalRepository clinical;
    private final ObjectMapper json;
    private final DiagnosisConceptRepository concepts;
    public EncounterClinicalService(EncounterRepository encounters, EncounterClinicalRepository clinical, ObjectMapper json, DiagnosisConceptRepository concepts) {
        this.encounters = encounters;
        this.clinical = clinical;
        this.json = json;
        this.concepts = concepts;
    }

    public List<DiagnosisConceptResponse> searchConcepts(String query, int page) {
        return searchConceptPage(query, page, false).items();
    }

    public DiagnosisConceptSearchResponse searchConceptPage(String query, int page, boolean groups) {
        if (page < 1 || (query != null && query.length() > 100)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH", "Từ khóa tối đa 100 ký tự, trang từ 1.");
        if (!groups && (query == null || query.strip().length() < 2)) return new DiagnosisConceptSearchResponse(List.of(), page, 20, 0, 0);
        return concepts.searchPage(query == null ? "" : query, page, groups);
    }

    @Transactional(readOnly = true)
    public EncounterClinicalResponse detail(CurrentAccountResponse account, long encounterId) {
        String state = owned(account, encounterId);
        return clinical.detail(encounterId, "IN_PROGRESS".equals(state), false);
    }

    @Transactional
    public EncounterClinicalResponse notes(CurrentAccountResponse account, long encounterId, EncounterNotesRequest request) {
        requireWritable(owned(account, encounterId));
        if (clinical.updateNotes(encounterId, request, version(request.version())) != 1) throw changed();
        clinical.audit(account.accountId(), encounterId, "ENCOUNTER_NOTES_UPDATED", encounterId);
        return clinical.detail(encounterId, true, false);
    }

    @Transactional
    public EncounterClinicalResponse vital(CurrentAccountResponse account, long encounterId, String key, VitalSignRequest request) {
        String state = owned(account, encounterId);
        String type = "ENCOUNTER_VITAL_SIGN";
        String hash = hash(encounterId, request);
        if (replayed(account, type, key, hash)) return clinical.detail(encounterId, "IN_PROGRESS".equals(state), true);
        requireWritable(state);
        int measuredYear = request.measuredAt().withOffsetSameInstant(java.time.ZoneOffset.UTC).getYear();
        if (measuredYear < 1 || measuredYear > 9999) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEASURED_AT", "Thời điểm đo không nằm trong miền ngày lưu trữ hợp lệ.");
        if (Stream.of(request.temperatureC(), request.pulseBpm(), request.systolicMmhg(), request.diastolicMmhg(),
                request.respiratoryRate(), request.spo2Percent(), request.weightKg(), request.heightCm()).allMatch(value -> value == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VITAL_SIGN_REQUIRED", "Nhập ít nhất một số đo sinh hiệu.");
        }
        long id = clinical.insertVital(encounterId, account.accountId(), request);
        clinical.audit(account.accountId(), encounterId, "VITAL_SIGN_RECORDED", id);
        clinical.saveReceipt(account.accountId(), type, key, hash, "VitalSign", id);
        return clinical.detail(encounterId, true, false);
    }

    @Transactional
    public EncounterClinicalResponse diagnosis(CurrentAccountResponse account, long encounterId, Long diagnosisId,
            String key, DiagnosisRequest request) {
        String state = owned(account, encounterId);
        String type = "ENCOUNTER_DIAGNOSIS_CREATE";
        String hash = hash(encounterId, request);
        if (diagnosisId == null && replayed(account, type, key, hash)) return clinical.detail(encounterId, "IN_PROGRESS".equals(state), true);
        requireWritable(state);
        DiagnosisConceptResponse concept = request.diagnosisConceptId() == null ? null : concepts.findSelectable(request.diagnosisConceptId());
        if (request.diagnosisConceptId() != null && concept == null) throw new ApiException(HttpStatus.BAD_REQUEST, "DIAGNOSIS_CONCEPT_UNAVAILABLE", "Mã bệnh không tồn tại, đã hủy hoặc không dùng cho chẩn đoán.");
        if ("FINAL".equals(request.diagnosisType()) && concept == null) throw new ApiException(HttpStatus.BAD_REQUEST, "FINAL_CODE_REQUIRED", "Chẩn đoán FINAL phải chọn mã hợp lệ từ danh mục.");
        if (concept != null && request.isPrimary() && !concept.primaryAllowed()) throw new ApiException(HttpStatus.BAD_REQUEST, "DIAGNOSIS_PRIMARY_NOT_ALLOWED", "Danh mục không cho dùng mã này làm bệnh chính.");
        long id;
        try {
            if (diagnosisId == null) id = clinical.insertDiagnosis(encounterId, account.accountId(), request, concept);
            else {
                if (clinical.updateDiagnosis(encounterId, diagnosisId, account.accountId(), request, version(request.version()), concept) != 1) throw changed();
                id = diagnosisId;
            }
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "PRIMARY_FINAL_EXISTS", "Lượt khám đã có một chẩn đoán chính FINAL. Hãy sửa bản ghi hiện có hoặc bỏ đánh dấu chính trước.");
        }
        clinical.audit(account.accountId(), encounterId, diagnosisId == null ? "DIAGNOSIS_CREATED" : "DIAGNOSIS_UPDATED", id);
        if (diagnosisId == null) clinical.saveReceipt(account.accountId(), type, key, hash, "Diagnosis", id);
        return clinical.detail(encounterId, true, false);
    }

    private String owned(CurrentAccountResponse account, long encounterId) {
        Long appointmentId = encounters.appointmentId(encounterId);
        if (account.staffId() == null || appointmentId == null) throw notFound();
        var appointment = encounters.lockAppointment(appointmentId, account.staffId());
        if (appointment == null || !"ONSITE".equals(appointment.visitMode())) throw notFound();
        String state = encounters.lockStatus(encounterId);
        return "BOOKED".equals(appointment.status()) ? state : "READ_ONLY";
    }

    private void requireWritable(String state) {
        if (!"IN_PROGRESS".equals(state)) throw new ApiException(HttpStatus.CONFLICT, "ENCOUNTER_NOT_IN_PROGRESS", "Chỉ ghi dữ liệu khám khi lượt khám đang diễn ra. Hãy bắt đầu khám hoặc tải lại trạng thái.");
    }

    private boolean replayed(CurrentAccountResponse account, String type, String key, String hash) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,100}")) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Khóa yêu cầu không hợp lệ.");
        clinical.lockCommand(type, key);
        var receipt = clinical.receipt(type, key);
        if (receipt == null) return false;
        if (receipt.accountId() != account.accountId() || !receipt.hash().equals(hash)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Khóa yêu cầu đã dùng cho dữ liệu khác.");
        }
        return true;
    }

    private String hash(long encounterId, Object request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((encounterId + ":" + json.writeValueAsString(request)).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private byte[] version(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length == 8) return bytes;
        } catch (IllegalArgumentException | NullPointerException ignored) { }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERSION", "Phiên bản không hợp lệ. Hãy tải lại dữ liệu.");
    }
    private ApiException changed() { return new ApiException(HttpStatus.CONFLICT, "CLINICAL_DATA_CHANGED", "Dữ liệu đã đổi hoặc không thuộc lượt khám này. Hãy tải lại trước khi sửa."); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "ENCOUNTER_NOT_FOUND", "Không tìm thấy lượt khám."); }
}
