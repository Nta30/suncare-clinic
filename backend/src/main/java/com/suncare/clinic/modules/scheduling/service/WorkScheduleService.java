package com.suncare.clinic.modules.scheduling.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.scheduling.dto.DraftScheduleRequest;
import com.suncare.clinic.modules.scheduling.dto.ScheduleOptionsResponse;
import com.suncare.clinic.modules.scheduling.dto.WorkAssignmentCreateRequest;
import com.suncare.clinic.modules.scheduling.dto.WorkAssignmentResponse;
import com.suncare.clinic.modules.scheduling.dto.WorkScheduleResponse;
import com.suncare.clinic.modules.scheduling.repository.WorkScheduleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class WorkScheduleService {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final WorkScheduleRepository repository;

    public WorkScheduleService(WorkScheduleRepository repository) {
        this.repository = repository;
    }

    public List<WorkScheduleResponse> list(CurrentAccountResponse account) {
        requireSchedulePermission(account);
        return repository.listForHead(account.staffId());
    }

    public ScheduleOptionsResponse options(CurrentAccountResponse account, Long departmentId) {
        requireSchedulePermission(account);
        List<ScheduleOptionsResponse.DepartmentOption> departments = repository.headDepartments(account.staffId());
        if (departmentId == null) {
            return new ScheduleOptionsResponse(departments, List.of(), List.of(), List.of(), repository.activeShiftOptions());
        }
        if (!repository.currentHeadOf(account.staffId(), departmentId) || !repository.clinicalDepartment(departmentId)) {
            throw forbidden("DEPARTMENT_SCOPE", "Bạn chỉ có thể xem dữ liệu trong khoa đang phụ trách.");
        }
        return new ScheduleOptionsResponse(departments, repository.staffOptions(departmentId),
                repository.roomOptions(departmentId), repository.specialtyOptions(departmentId), repository.activeShiftOptions());
    }

    @Transactional
    public WorkScheduleResponse createDraft(CurrentAccountResponse account, DraftScheduleRequest request) {
        requireSchedulePermission(account);
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw bad("INVALID_PERIOD", "Ngày kết thúc phải từ ngày bắt đầu trở đi.");
        }
        if (!repository.clinicalDepartment(request.departmentId())
                || !repository.currentHeadOf(account.staffId(), request.departmentId())) {
            throw forbidden("DEPARTMENT_SCOPE", "Bạn chưa có nhiệm kỳ trưởng khoa đang hiệu lực tại khoa đã chọn. Hãy nhờ quản trị viên kiểm tra phân công khoa và thời hạn nhiệm kỳ.");
        }
        if (!repository.headCoversPeriod(account.staffId(), request.departmentId(), request.periodStart(), request.periodEnd())) {
            throw forbidden("HEAD_TERM_OUTSIDE_PERIOD", periodScopeMessage(account.staffId(), request.departmentId(), request.periodStart(), request.periodEnd()));
        }
        long id = repository.insertSchedule(request.departmentId(), request.periodStart(), request.periodEnd());
        repository.audit(account.accountId(), "WORK_SCHEDULE_DRAFT_CREATED", "WorkSchedule", id,
                "department_id=" + request.departmentId() + ", period=" + request.periodStart() + ".." + request.periodEnd());
        return repository.findSchedule(id);
    }

    public List<WorkAssignmentResponse> assignments(CurrentAccountResponse account, long scheduleId) {
        requireSchedulePermission(account);
        ensureScheduleAccess(account, scheduleId);
        return repository.listAssignments(scheduleId);
    }

    @Transactional
    public List<WorkAssignmentResponse> addAssignment(CurrentAccountResponse account, long scheduleId,
                                                      WorkAssignmentCreateRequest request) {
        requireSchedulePermission(account);
        WorkScheduleRepository.ScheduleIdentity schedule = lockEditableSchedule(account, scheduleId);
        LocalDate date = request.workDate();
        if (date.isBefore(schedule.periodStart()) || date.isAfter(schedule.periodEnd())) {
            throw bad("DATE_OUTSIDE_SCHEDULE", "Ngày phân công nằm ngoài khoảng thời gian của đợt lịch.");
        }
        if (date.getDayOfWeek().getValue() == 7) {
            throw bad("INVALID_WORK_DAY", "Lịch làm việc chỉ nhận từ thứ Hai đến thứ Bảy.");
        }

        WorkScheduleRepository.StaffIdentity staff = repository.staff(request.staffId());
        if (staff == null || !staff.active() || !repository.hasDepartmentMembership(staff.id(), schedule.departmentId(), date)) {
            throw bad("STAFF_OUTSIDE_DEPARTMENT", "Nhân viên phải đang hoạt động và thuộc khoa vào ngày phân công.");
        }
        if (!repository.hasRole(staff.id(), "DOCTOR")) {
            throw bad("INVALID_STAFF_ROLE", "Phân công ONSITE chỉ áp dụng cho bác sĩ.");
        }
        if (!repository.hasSpecialty(staff.id(), schedule.departmentId(), request.specialtyId())) {
            throw bad("INVALID_SPECIALTY", "Bác sĩ phải được phân chuyên khoa và khoa phải liên kết chuyên khoa đó.");
        }

        WorkScheduleRepository.RoomIdentity room = repository.lockRoom(request.roomId());
        if (room == null || !room.active() || room.departmentId() != schedule.departmentId()
                || !"CONSULTATION".equals(room.type())) {
            throw bad("INVALID_ROOM", "Phòng khám phải đang hoạt động, thuộc khoa và có loại CONSULTATION.");
        }
        WorkScheduleRepository.ShiftIdentity shift = repository.lockShift(request.shiftId());
        if (shift == null || !shift.active()) {
            throw bad("INVALID_SHIFT", "Ca làm việc không tồn tại hoặc đã ngừng hoạt động.");
        }

        // Work dates and shift times are entered in Saigon time; datetime2 stores UTC.
        LocalDateTime startsAt = date.atTime(LocalTime.parse(shift.localStart())).minusHours(7);
        LocalDateTime endsAt = date.atTime(LocalTime.parse(shift.localEnd())).minusHours(7);
        if (repository.staffConflict(scheduleId, staff.id(), startsAt, endsAt)) {
            throw conflict("STAFF_TIME_CONFLICT", "Bác sĩ đã có phân công giao thời gian trong đợt lịch này.");
        }
        if (repository.roomConflict(scheduleId, room.id(), startsAt, endsAt)) {
            throw conflict("ROOM_TIME_CONFLICT", "Phòng đã có phân công giao thời gian trong đợt lịch này.");
        }

        long id = repository.insertAssignment(scheduleId, request, startsAt, endsAt);
        repository.audit(account.accountId(), "WORK_ASSIGNMENT_ADDED", "WorkAssignment", id,
                "schedule_id=" + scheduleId + ", staff_id=" + staff.id() + ", work_date=" + date);
        return repository.listAssignments(scheduleId);
    }

    @Transactional
    public List<WorkAssignmentResponse> removeAssignment(CurrentAccountResponse account, long scheduleId, long assignmentId) {
        requireSchedulePermission(account);
        lockEditableSchedule(account, scheduleId);
        if (repository.deleteAssignment(scheduleId, assignmentId) != 1) {
            throw missing("ASSIGNMENT_NOT_FOUND", "Không tìm thấy phân công trong đợt lịch này.");
        }
        repository.audit(account.accountId(), "WORK_ASSIGNMENT_REMOVED", "WorkAssignment", assignmentId,
                "schedule_id=" + scheduleId);
        return repository.listAssignments(scheduleId);
    }

    private WorkScheduleRepository.ScheduleIdentity lockEditableSchedule(CurrentAccountResponse account, long scheduleId) {
        WorkScheduleRepository.ScheduleIdentity schedule = repository.lockSchedule(scheduleId);
        if (schedule == null) throw missing("SCHEDULE_NOT_FOUND", "Không tìm thấy đợt lịch.");
        if (!repository.currentHeadOf(account.staffId(), schedule.departmentId())
                || !repository.clinicalDepartment(schedule.departmentId())) {
            throw forbidden("DEPARTMENT_SCOPE", "Chỉ trưởng khoa đang phụ trách mới được sửa lịch của khoa hiện tại.");
        }
        if (!repository.headCoversPeriod(account.staffId(), schedule.departmentId(), schedule.periodStart(), schedule.periodEnd())) {
            throw forbidden("HEAD_TERM_OUTSIDE_PERIOD", periodScopeMessage(account.staffId(), schedule.departmentId(), schedule.periodStart(), schedule.periodEnd()));
        }
        if (!"DRAFT".equals(schedule.status())) throw conflict("SCHEDULE_LOCKED", "Chỉ được sửa đợt lịch DRAFT.");
        return schedule;
    }

    private void ensureScheduleAccess(CurrentAccountResponse account, long scheduleId) {
        WorkScheduleRepository.ScheduleIdentity schedule = repository.lockSchedule(scheduleId);
        if (schedule == null) throw missing("SCHEDULE_NOT_FOUND", "Không tìm thấy đợt lịch.");
        if (!repository.currentHeadOf(account.staffId(), schedule.departmentId())
                || !repository.clinicalDepartment(schedule.departmentId())) {
            throw forbidden("DEPARTMENT_SCOPE", "Chỉ trưởng khoa đang phụ trách mới được xem lịch sử của khoa này.");
        }
    }

    private void requireSchedulePermission(CurrentAccountResponse account) {
        if (account == null || account.staffId() == null || account.permissions() == null
                || !account.permissions().contains("SCHEDULE_PUBLISH_DEPARTMENT")) {
            throw forbidden("SCHEDULE_PERMISSION_REQUIRED", "Tài khoản chưa được cấp quyền điều phối lịch khoa.");
        }
    }

    private String periodScopeMessage(long staffId, long departmentId, LocalDate start, LocalDate end) {
        WorkScheduleRepository.HeadTerm term = repository.currentHeadTerm(staffId, departmentId);
        if (term == null) {
            return "Bạn không có nhiệm kỳ trưởng khoa đang hiệu lực tại khoa này. Hãy nhờ quản trị viên kiểm tra phân công và thời hạn nhiệm kỳ.";
        }
        LocalDateTime validFrom = LocalDateTime.parse(term.validFrom());
        String validTo = term.validTo() == null ? "chưa xác định" : formatVietnamese(LocalDateTime.parse(term.validTo()));
        return "Nhiệm kỳ trưởng khoa hiện tại: " + formatVietnamese(validFrom) + " đến " + validTo
                + ". Đợt lịch cần nằm trọn từ " + start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " đến " + end.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " (giờ Việt Nam). Hãy chọn khoảng phù hợp hoặc nhờ quản trị viên gia hạn nhiệm kỳ.";
    }

    private String formatVietnamese(LocalDateTime utc) {
        return utc.plusHours(7).format(DATE_TIME_FORMAT);
    }

    private ApiException bad(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private ApiException forbidden(String code, String message) { return new ApiException(HttpStatus.FORBIDDEN, code, message); }
    private ApiException missing(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}
