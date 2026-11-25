package com.suncare.clinic.modules.encounter.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterHistoryResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterWorkItemResponse;
import com.suncare.clinic.modules.encounter.dto.PatientHistoryRequest;
import com.suncare.clinic.modules.encounter.dto.PatientAllergyRequest;
import com.suncare.clinic.modules.encounter.dto.EncounterWorklistResponse;
import com.suncare.clinic.modules.encounter.repository.EncounterHistoryRepository;
import com.suncare.clinic.modules.encounter.repository.EncounterRepository;
import com.suncare.clinic.modules.encounter.repository.AllergenRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.text.Normalizer;
import java.util.Arrays;

@Service
public class EncounterHistoryService {
    private final EncounterRepository encounters;
    private final EncounterHistoryRepository histories;
    private final AllergenRepository allergens;

    public EncounterHistoryService(EncounterRepository encounters, EncounterHistoryRepository histories, AllergenRepository allergens) {
        this.encounters = encounters;
        this.histories = histories;
        this.allergens = allergens;
    }

    @Transactional(readOnly = true)
    public EncounterWorklistResponse worklist(CurrentAccountResponse account, LocalDate date, String status,
            String search, int page, int pageSize) {
        long staffId = doctorId(account);
        if (page < 1 || !List.of(10, 20, 50).contains(pageSize)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Trang phải từ 1 và số dòng mỗi trang là 10, 20 hoặc 50.");
        }
        if (!List.of("ALL", "PLANNED", "IN_PROGRESS", "WAITING_CLS", "COMPLETED", "CANCELLED", "NO_SHOW").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ENCOUNTER_STATUS", "Trạng thái lượt khám không hợp lệ.");
        }
        String text = search == null ? "" : search.strip();
        if (text.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH", "Từ khóa tìm kiếm tối đa 100 ký tự.");
        }
        text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D');
        List<String> tokens = text.isBlank() ? List.of() : Arrays.asList(text.split("\\s+"));
        return histories.worklist(staffId, date, status, tokens, page, pageSize);
    }

    @Transactional(readOnly = true)
    public EncounterHistoryResponse detail(CurrentAccountResponse account, long encounterId) {
        var item = histories.find(doctorId(account), encounterId);
        if (item == null) throw notFound();
        Long appointmentId = encounters.appointmentId(encounterId);
        long patientId = encounters.patientId(appointmentId);
        return new EncounterHistoryResponse(item, editable(item.status(), item.appointmentStatus()),
                histories.histories(patientId), histories.allergies(patientId), histories.snapshot(encounterId), allergens.list(true));
    }

    @Transactional
    public EncounterHistoryResponse saveHistory(CurrentAccountResponse account, long encounterId, Long itemId,
            PatientHistoryRequest request) {
        long patientId = lockEditablePatient(account, encounterId);
        long savedId;
        if (itemId == null) savedId = histories.createHistory(patientId, account.accountId(), request);
        else {
            if (histories.updateHistory(patientId, itemId, account.accountId(), request, version(request.version())) != 1) throw changed();
            savedId = itemId;
        }
        histories.audit(account.accountId(), encounterId, itemId == null ? "PATIENT_HISTORY_CREATED" : "PATIENT_HISTORY_UPDATED", savedId);
        return detail(account, encounterId);
    }

    @Transactional
    public EncounterHistoryResponse saveAllergy(CurrentAccountResponse account, long encounterId, Long itemId,
            PatientAllergyRequest request) {
        long patientId = lockEditablePatient(account, encounterId);
        String substance;
        if (request.allergenId() == null) {
            if (request.otherNote() == null || request.otherNote().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ALLERGEN_NOTE_REQUIRED", "Chưa chọn chất trong danh mục: cần ghi rõ chất nghi gây dị ứng.");
            }
            substance = request.otherNote().strip();
            if (substance.length() > 200) substance = "Chất chưa có trong danh mục";
        } else {
            if (request.otherNote() != null && !request.otherNote().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ALLERGEN_SOURCE", "Chọn chất trong danh mục hoặc ghi chất khác, không dùng cả hai.");
            }
            var allergen = allergens.find(request.allergenId());
            var existing = itemId == null ? null : histories.allergies(patientId).stream().filter(item -> item.id() == itemId).findFirst().orElse(null);
            boolean unchangedActive = existing != null && existing.isActive() && request.allergenId().equals(existing.allergenId());
            if (allergen == null || (!allergen.isActive() && (itemId == null || (request.isActive() && !unchangedActive)))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ALLERGEN_UNAVAILABLE", "Chất gây dị ứng không tồn tại hoặc đã ngừng áp dụng.");
            }
            substance = allergen.name();
        }
        long savedId;
        try {
            if (itemId == null) savedId = histories.createAllergy(patientId, account.accountId(), request, substance);
            else {
                if (histories.updateAllergy(patientId, itemId, account.accountId(), request, version(request.version()), substance) != 1) throw changed();
                savedId = itemId;
            }
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "PATIENT_ALLERGEN_EXISTS", "Bệnh nhân đã có chất gây dị ứng này đang áp dụng. Hãy sửa bản ghi hiện có.");
        }
        histories.audit(account.accountId(), encounterId, itemId == null ? "PATIENT_ALLERGY_CREATED" : "PATIENT_ALLERGY_UPDATED", savedId);
        return detail(account, encounterId);
    }

    private long lockEditablePatient(CurrentAccountResponse account, long encounterId) {
        long doctorId = doctorId(account);
        Long appointmentId = encounters.appointmentId(encounterId);
        if (appointmentId == null) throw notFound();
        var appointment = encounters.lockAppointment(appointmentId, doctorId);
        if (appointment == null || !"ONSITE".equals(appointment.visitMode())) throw notFound();
        String state = encounters.lockStatus(encounterId);
        if (!editable(state, appointment.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "ENCOUNTER_READ_ONLY", "Lượt khám không còn cho phép cập nhật tiền sử/dị ứng.");
        }
        encounters.lockPatient(appointment.patientId());
        return appointment.patientId();
    }

    private boolean editable(String status, String appointmentStatus) {
        return "BOOKED".equals(appointmentStatus) && List.of("PLANNED", "IN_PROGRESS", "WAITING_CLS").contains(status);
    }

    private long doctorId(CurrentAccountResponse account) {
        if (account.staffId() == null) throw new AccessDeniedException("Tài khoản không gắn hồ sơ bác sĩ.");
        return account.staffId();
    }

    private byte[] version(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length == 8) return bytes;
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Rowversion is an eight-byte opaque value, never a date or counter supplied by the client.
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERSION", "Phiên bản dữ liệu không hợp lệ. Hãy tải lại.");
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ENCOUNTER_NOT_FOUND", "Không tìm thấy lượt khám.");
    }

    private ApiException changed() {
        return new ApiException(HttpStatus.CONFLICT, "PATIENT_HISTORY_CHANGED", "Dữ liệu không còn khớp hoặc đã thay đổi. Hãy tải lại trước khi sửa.");
    }
}
