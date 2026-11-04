package com.suncare.clinic.modules.organization.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.organization.dto.HeadAssignmentRequest;
import com.suncare.clinic.modules.organization.dto.StaffAssignmentRequest;
import com.suncare.clinic.modules.organization.dto.StaffSpecialtyRequest;
import com.suncare.clinic.modules.organization.repository.OrganizationCatalogRepository;
import com.suncare.clinic.modules.organization.repository.StaffAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StaffAssignmentService {
    private static final DateTimeFormatter VIETNAMESE_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final StaffAssignmentRepository repository;
    private final OrganizationCatalogRepository audit;

    public StaffAssignmentService(StaffAssignmentRepository repository, OrganizationCatalogRepository audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public Map<String, Object> list(CurrentAccountResponse actor) {
        requireManager(actor);
        return Map.of("departments", repository.departments(), "specialties", repository.specialties(), "heads", repository.heads());
    }

    @Transactional
    public Map<String, Object> assignDepartment(CurrentAccountResponse actor, StaffAssignmentRequest request) {
        requireManager(actor);
        validPeriod(request.validFrom(), request.validTo());
        if (!repository.lockStaff(request.staffId()) || !repository.activeStaff(request.staffId())
                || repository.lockDepartment(request.departmentId()) == null) {
            throw bad("Nhân viên hoặc khoa không hoạt động.");
        }
        if (repository.overlaps("StaffDepartment", "staff_id", request.staffId(), request.validFrom(), request.validTo(), null)) {
            throw conflict("Khoảng phân khoa của nhân viên bị chồng nhau.");
        }
        long id = repository.insertDepartment(request.staffId(), request.departmentId(), request.validFrom(), request.validTo(),
                Boolean.TRUE.equals(request.primary()));
        audit.audit(actor.accountId(), "STAFF_DEPARTMENT_ASSIGNED", "StaffDepartment", id);
        return list(actor);
    }

    @Transactional
    public Map<String, Object> endDepartment(CurrentAccountResponse actor, long id, LocalDateTime end) {
        requireManager(actor);
        Map<String, Object> period = repository.period("StaffDepartment", id);
        if (period == null) throw missing();
        long staffId = ((Number) period.get("staff_id")).longValue();
        if (!repository.lockStaff(staffId)) throw bad("Không tìm thấy nhân viên.");
        validateEnd(period, end);
        List<StaffAssignmentRepository.HeadTerm> overlappingHeads = repository.headTermsOverlappingDepartmentEnd(id, end);
        if (!overlappingHeads.isEmpty()) {
            String terms = overlappingHeads.stream().map(term -> term.staffName() + " ("
                    + formatVietnamese(term.validFrom()) + " đến "
                    + (term.validTo() == null ? "chưa xác định" : formatVietnamese(term.validTo())) + ")")
                    .collect(Collectors.joining("; "));
            throw conflict("Cần kết thúc nhiệm kỳ trưởng khoa trước khi kết thúc phân khoa. Nhiệm kỳ đang vướng: " + terms + ".");
        }
        if (repository.end("StaffDepartment", id, end) != 1) throw bad("Thời điểm kết thúc không hợp lệ.");
        audit.audit(actor.accountId(), "STAFF_DEPARTMENT_ENDED", "StaffDepartment", id);
        return list(actor);
    }

    @Transactional
    public Map<String, Object> assignHead(CurrentAccountResponse actor, HeadAssignmentRequest request) {
        requireManager(actor);
        validPeriod(request.validFrom(), request.validTo());
        if (!repository.lockStaff(request.staffId()) || !repository.activeStaff(request.staffId())
                || !"CLINICAL".equals(repository.lockDepartment(request.departmentId()))) {
            throw bad("Trưởng khoa cần là nhân viên đang hoạt động của khoa khám.");
        }
        if (!repository.hasRole(request.staffId(), "DOCTOR")) throw bad("Trưởng khoa phải đồng thời có vai trò bác sĩ.");
        if (!repository.hasRole(request.staffId(), "DEPARTMENT_HEAD")) throw bad("Nhân viên chưa có vai trò trưởng khoa.");
        if (!repository.coversMembership(request.staffId(), request.departmentId(), request.validFrom(), request.validTo())) {
            throw bad("Nhiệm kỳ phải nằm trọn trong khoảng phân khoa của nhân viên.");
        }
        if (repository.overlaps("DepartmentHeadAssignment", "department_id", request.departmentId(), request.validFrom(), request.validTo(), null)) {
            throw conflict("Khoa đã có trưởng khoa trong khoảng này.");
        }
        long id = repository.insertHead(request.staffId(), request.departmentId(), request.validFrom(), request.validTo(), actor.accountId());
        audit.audit(actor.accountId(), "DEPARTMENT_HEAD_ASSIGNED", "DepartmentHeadAssignment", id);
        return list(actor);
    }

    @Transactional
    public Map<String, Object> endHead(CurrentAccountResponse actor, long id, LocalDateTime end) {
        requireManager(actor);
        Map<String, Object> period = repository.period("DepartmentHeadAssignment", id);
        if (period == null) throw missing();
        if (!repository.lockStaff(((Number) period.get("staff_id")).longValue())) throw bad("Không tìm thấy nhân viên.");
        repository.lockDepartment(((Number) period.get("department_id")).longValue());
        validateEnd(period, end);
        if (repository.end("DepartmentHeadAssignment", id, end) != 1) throw bad("Thời điểm kết thúc không hợp lệ.");
        audit.audit(actor.accountId(), "DEPARTMENT_HEAD_ENDED", "DepartmentHeadAssignment", id);
        return list(actor);
    }

    @Transactional
    public Map<String, Object> specialty(CurrentAccountResponse actor, StaffSpecialtyRequest request) {
        requireManager(actor);
        if (!repository.lockStaff(request.staffId()) || !repository.activeStaff(request.staffId())
                || !repository.activeSpecialty(request.specialtyId())) {
            throw bad("Nhân viên hoặc chuyên khoa không hoạt động.");
        }
        if (request.active() && (!repository.hasRole(request.staffId(), "DOCTOR")
                || !repository.linkedSpecialty(request.staffId(), request.specialtyId()))) {
            throw bad("Bác sĩ phải được phân vào khoa đang liên kết với chuyên khoa này.");
        }
        repository.specialty(request.staffId(), request.specialtyId(), request.active());
        audit.audit(actor.accountId(), request.active() ? "STAFF_SPECIALTY_ENABLED" : "STAFF_SPECIALTY_DISABLED",
                "StaffSpecialty", request.staffId(), "specialty_id=" + request.specialtyId());
        return list(actor);
    }

    private void requireManager(CurrentAccountResponse actor) {
        if (!actor.permissions().contains("ACCOUNT_MANAGE")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Không có quyền phân công nhân viên.");
        }
    }

    private void validPeriod(LocalDateTime from, LocalDateTime to) {
        if (to != null && !to.isAfter(from)) throw bad("Thời điểm kết thúc phải sau thời điểm bắt đầu.");
    }

    private void validateEnd(Map<String, Object> period, LocalDateTime end) {
        LocalDateTime from = asLocalDateTime(period.get("valid_from"));
        LocalDateTime currentEnd = asLocalDateTime(period.get("valid_to"));
        if (end == null || !end.isAfter(from)) {
            throw bad("Thời điểm kết thúc phải sau thời điểm bắt đầu " + formatVietnamese(from) + ".");
        }
        if (currentEnd != null && !end.isBefore(currentEnd)) {
            throw bad("Thời điểm kết thúc mới phải trước thời điểm kết thúc hiện tại " + formatVietnamese(currentEnd) + ".");
        }
    }

    private String formatVietnamese(LocalDateTime utc) {
        return utc.plusHours(7).format(VIETNAMESE_DATE_TIME);
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return (LocalDateTime) value;
    }

    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNMENT", message); }
    private ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, "ASSIGNMENT_OVERLAP", message); }
    private ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "Không tìm thấy phân công."); }
}
