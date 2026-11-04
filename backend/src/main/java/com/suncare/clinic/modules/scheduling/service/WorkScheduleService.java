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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkScheduleService {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final WorkScheduleRepository repository;

    private record AssignmentSlot(LocalDate workDate, Long shiftId) { }

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
        String mode = request.mode() == null ? "ONSITE" : request.mode();
        if (!List.of("ONSITE", "ONLINE").contains(mode)) {
            throw bad("INVALID_ASSIGNMENT_MODE", "Chỉ hỗ trợ ONSITE và ONLINE.");
        }
        List<AssignmentSlot> selectedSlots = resolveWorkSlots(request);
        List<LocalDate> workDates = selectedSlots.stream().map(AssignmentSlot::workDate).distinct().toList();
        List<Long> shiftIds = selectedSlots.stream().map(AssignmentSlot::shiftId).distinct().toList();
        if (request.startTime() != null || request.endTime() != null) {
            throw bad("INVALID_ASSIGNMENT", "Thời gian phân công phải lấy từ ca do quản trị viên cấu hình.");
        }
        if ("ONLINE".equals(mode) && request.capacity() != null) {
            throw bad("INVALID_ASSIGNMENT", "Phân công ONLINE không nhận sức chứa.");
        }
        if ("ONSITE".equals(mode) && (request.capacity() == null || request.capacity() < 1)) {
            throw bad("INVALID_ASSIGNMENT", "Phân công ONSITE cần sức chứa lớn hơn 0.");
        }

        WorkScheduleRepository.StaffIdentity staff = repository.staff(request.staffId());
        if (staff == null || !staff.active()) {
            throw bad("STAFF_OUTSIDE_DEPARTMENT", "Nhân viên phải đang hoạt động và thuộc khoa vào ngày phân công.");
        }
        for (LocalDate date : workDates) {
            if (date.isBefore(schedule.periodStart()) || date.isAfter(schedule.periodEnd())) {
                throw bad("DATE_OUTSIDE_SCHEDULE", "Ngày phân công nằm ngoài khoảng thời gian của đợt lịch.");
            }
            if (date.getDayOfWeek().getValue() == 7) {
                throw bad("INVALID_WORK_DAY", "Lịch làm việc chỉ nhận từ thứ Hai đến thứ Bảy.");
            }
            if (!repository.hasDepartmentMembership(staff.id(), schedule.departmentId(), date)) {
                throw bad("STAFF_OUTSIDE_DEPARTMENT", "Bác sĩ phải đang thuộc khoa vào tất cả ngày được chọn.");
            }
        }
        if (!repository.hasRole(staff.id(), "DOCTOR")) {
            throw bad("INVALID_STAFF_ROLE", "Lịch chỉ có thể phân công cho bác sĩ.");
        }
        if (!repository.hasSpecialty(staff.id(), schedule.departmentId(), request.specialtyId())) {
            throw bad("INVALID_SPECIALTY", "Bác sĩ phải được phân chuyên khoa và khoa phải liên kết chuyên khoa đó.");
        }

        WorkScheduleRepository.RoomIdentity room = repository.lockRoom(request.roomId());
        String expectedRoomType = "ONSITE".equals(mode) ? "CONSULTATION" : "ONLINE";
        if (room == null || !room.active() || room.departmentId() != schedule.departmentId()
                || !expectedRoomType.equals(room.type())) {
            throw bad("INVALID_ROOM", "Phòng phải đang hoạt động, thuộc khoa và đúng loại theo hình thức phân công.");
        }
        Map<Long, WorkScheduleRepository.ShiftIdentity> shifts = new LinkedHashMap<>();
        for (Long shiftId : shiftIds.stream().sorted().toList()) {
            WorkScheduleRepository.ShiftIdentity shift = repository.lockShift(shiftId);
            if (shift == null || !shift.active()) {
                throw bad("INVALID_SHIFT", "Ca làm việc không tồn tại hoặc đã ngừng hoạt động.");
            }
            if (!List.of("MORNING", "AFTERNOON").contains(shift.code())) {
                throw bad("INVALID_SHIFT", "Phân công chỉ dùng ca sáng hoặc ca chiều đã cấu hình.");
            }
            shifts.put(shiftId, shift);
        }

        for (AssignmentSlot selectedSlot : selectedSlots) {
            LocalDate date = selectedSlot.workDate();
            Long shiftId = selectedSlot.shiftId();
            WorkScheduleRepository.ShiftIdentity shift = shifts.get(shiftId);
            // Giờ ca nhập theo giờ Saigon; datetime2 lưu UTC.
            LocalDateTime startsAt = date.atTime(LocalTime.parse(shift.localStart())).minusHours(7);
            LocalDateTime endsAt = date.atTime(LocalTime.parse(shift.localEnd())).minusHours(7);
            if (repository.staffConflict(scheduleId, staff.id(), startsAt, endsAt)) {
                throw conflict("STAFF_TIME_CONFLICT", "Bác sĩ đã có phân công giao thời gian trong đợt lịch này.");
            }
            if (repository.roomConflict(scheduleId, room.id(), startsAt, endsAt)) {
                throw conflict("ROOM_TIME_CONFLICT", "Phòng đã có phân công giao thời gian trong đợt lịch này.");
            }

            long id = repository.insertAssignment(scheduleId, request, mode, shiftId,
                    "ONSITE".equals(mode) ? request.capacity() : null, date, startsAt, endsAt);
            repository.audit(account.accountId(), "WORK_ASSIGNMENT_ADDED", "WorkAssignment", id,
                    "schedule_id=" + scheduleId + ", staff_id=" + staff.id() + ", mode=" + mode
                            + ", work_date=" + date + ", shift_id=" + shiftId);
        }
        return repository.listAssignments(scheduleId);
    }

    private List<AssignmentSlot> resolveWorkSlots(WorkAssignmentCreateRequest request) {
        List<AssignmentSlot> slots;
        if (request.workSlots() != null) {
            if (request.workDate() != null || request.workDates() != null
                    || request.shiftId() != null || request.shiftIds() != null) {
                throw bad("INVALID_WORK_SLOTS", "Chỉ gửi workSlots hoặc cặp trường ngày/ca cũ, không gửi cả hai.");
            }
            slots = request.workSlots().stream()
                    .map(slot -> new AssignmentSlot(slot.workDate(), slot.shiftId()))
                    .toList();
        } else {
            List<LocalDate> dates = resolveWorkDates(request);
            List<Long> shiftIds = resolveShiftIds(request);
            List<AssignmentSlot> expandedSlots = new ArrayList<>();
            for (LocalDate date : dates) {
                for (Long shiftId : shiftIds) expandedSlots.add(new AssignmentSlot(date, shiftId));
            }
            slots = expandedSlots;
        }

        if (slots.isEmpty() || slots.size() > 124 || new HashSet<>(slots).size() != slots.size()) {
            throw bad("INVALID_WORK_SLOTS", "Hãy chọn từ 1 đến 124 ô ngày/ca, không chọn trùng.");
        }
        return slots;
    }

    private List<LocalDate> resolveWorkDates(WorkAssignmentCreateRequest request) {
        if (request.workDate() != null && request.workDates() != null) {
            throw bad("INVALID_WORK_DATES", "Chỉ gửi workDate hoặc workDates, không gửi cả hai.");
        }
        List<LocalDate> dates = request.workDates() != null
                ? request.workDates()
                : request.workDate() != null ? List.of(request.workDate()) : List.of();
        if (dates.isEmpty() || dates.size() > 62 || new HashSet<>(dates).size() != dates.size()) {
            throw bad("INVALID_WORK_DATES", "Hãy chọn từ 1 đến 62 ngày không trùng nhau.");
        }
        return dates;
    }

    private List<Long> resolveShiftIds(WorkAssignmentCreateRequest request) {
        if (request.shiftId() != null && request.shiftIds() != null) {
            throw bad("INVALID_SHIFT", "Chỉ gửi shiftId hoặc shiftIds, không gửi cả hai.");
        }
        List<Long> shiftIds = request.shiftIds() != null
                ? request.shiftIds()
                : request.shiftId() != null ? List.of(request.shiftId()) : List.of();
        if (shiftIds.isEmpty() || shiftIds.size() > 2 || new HashSet<>(shiftIds).size() != shiftIds.size()) {
            throw bad("INVALID_SHIFT", "Chọn một hoặc hai ca đã cấu hình trong cùng một hình thức.");
        }
        return shiftIds;
    }

    @Transactional
    public WorkScheduleResponse publish(CurrentAccountResponse account, long scheduleId) {
        requireSchedulePermission(account);
        WorkScheduleRepository.ScheduleIdentity schedule = repository.lockSchedule(scheduleId);
        if (schedule == null) throw missing("SCHEDULE_NOT_FOUND", "Không tìm thấy đợt lịch.");
        if (!repository.currentHeadOf(account.staffId(), schedule.departmentId())
                || !repository.clinicalDepartment(schedule.departmentId())) {
            throw forbidden("DEPARTMENT_SCOPE", "Chỉ trưởng khoa đang phụ trách mới được công bố lịch của khoa hiện tại.");
        }
        if (!repository.headCoversPeriod(account.staffId(), schedule.departmentId(), schedule.periodStart(), schedule.periodEnd())) {
            throw forbidden("HEAD_TERM_OUTSIDE_PERIOD", periodScopeMessage(account.staffId(), schedule.departmentId(), schedule.periodStart(), schedule.periodEnd()));
        }
        if ("PUBLISHED".equals(schedule.status())) return repository.findSchedule(scheduleId);
        if (!"DRAFT".equals(schedule.status())) {
            throw conflict("SCHEDULE_LOCKED", "Đợt lịch không còn ở trạng thái bản nháp.");
        }

        List<WorkScheduleRepository.AssignmentIdentity> assignments = repository.lockAssignments(scheduleId);
        if (assignments.isEmpty()) throw bad("SCHEDULE_EMPTY", "Cần có ít nhất một phân công trước khi công bố lịch.");
        repository.lockAssignmentResources(assignments);
        for (WorkScheduleRepository.AssignmentIdentity assignment : assignments) {
            validateAssignmentForPublish(schedule, assignment);
        }
        if (repository.scheduleHasOverlappingAssignments(scheduleId) || repository.overlapsPublishedSchedule(scheduleId)) {
            throw conflict("SCHEDULE_TIME_CONFLICT", "Lịch có phân công trùng giờ với bác sĩ hoặc phòng đã được công bố.");
        }

        List<WorkScheduleRepository.OnlineSlotData> onlineSlots = buildOnlineSlots(assignments);
        if (repository.publishSchedule(scheduleId, account.accountId()) != 1) {
            throw conflict("SCHEDULE_CHANGED", "Đợt lịch đã thay đổi. Hãy tải lại trước khi công bố.");
        }
        repository.insertOnlineSlots(onlineSlots);
        repository.audit(account.accountId(), "WORK_SCHEDULE_PUBLISHED", "WorkSchedule", scheduleId,
                "assignment_count=" + assignments.size() + ", online_slot_count=" + onlineSlots.size());
        return repository.findSchedule(scheduleId);
    }

    private void validateAssignmentForPublish(WorkScheduleRepository.ScheduleIdentity schedule,
                                              WorkScheduleRepository.AssignmentIdentity assignment) {
        String mode = assignment.mode();
        if (!List.of("ONSITE", "ONLINE").contains(mode)
                || assignment.workDate().isBefore(schedule.periodStart())
                || assignment.workDate().isAfter(schedule.periodEnd())
                || assignment.workDate().getDayOfWeek().getValue() == 7) {
            throw conflict("INVALID_ASSIGNMENT", "Có phân công không hợp lệ hoặc nằm ngoài đợt lịch.");
        }

        WorkScheduleRepository.StaffIdentity staff = repository.staff(assignment.staffId());
        if (staff == null || !staff.active()
                || !repository.hasDepartmentMembership(staff.id(), schedule.departmentId(), assignment.workDate())
                || !repository.hasRole(staff.id(), "DOCTOR")) {
            throw conflict("INVALID_STAFF", "Bác sĩ trong phân công không còn hoạt động hoặc không thuộc khoa vào ngày làm việc.");
        }
        if (assignment.specialtyId() == null
                || !repository.hasSpecialty(staff.id(), schedule.departmentId(), assignment.specialtyId())) {
            throw conflict("INVALID_SPECIALTY", "Chuyên khoa của phân công không còn hợp lệ trong khoa.");
        }

        WorkScheduleRepository.RoomIdentity room = assignment.roomId() == null
                ? null : repository.lockRoom(assignment.roomId());
        String expectedRoomType = "ONSITE".equals(mode) ? "CONSULTATION" : "ONLINE";
        if (room == null || !room.active() || room.departmentId() != schedule.departmentId()
                || !expectedRoomType.equals(room.type())) {
            throw conflict("INVALID_ROOM", "Phòng trong phân công không còn hoạt động hoặc không đúng loại.");
        }

        if (assignment.shiftId() == null) {
            throw conflict("INVALID_SHIFT", "Phân công phải gắn với ca làm việc đã cấu hình.");
        }
        WorkScheduleRepository.ShiftIdentity shift = repository.lockShift(assignment.shiftId());
        if (shift == null || !shift.active()) {
            throw conflict("INVALID_SHIFT", "Ca làm việc trong phân công đã ngừng hoạt động.");
        }
        if (!List.of("MORNING", "AFTERNOON").contains(shift.code())) {
            throw conflict("INVALID_SHIFT", "Phân công chỉ dùng ca sáng hoặc ca chiều.");
        }
        if ("ONSITE".equals(mode)
                ? assignment.capacity() == null || assignment.capacity() < 1
                : assignment.capacity() != null) {
            throw conflict("INVALID_ASSIGNMENT", "Sức chứa chỉ áp dụng cho phân công ONSITE và phải lớn hơn 0.");
        }
        LocalDateTime expectedStart = assignment.workDate().atTime(LocalTime.parse(shift.localStart())).minusHours(7);
        LocalDateTime expectedEnd = assignment.workDate().atTime(LocalTime.parse(shift.localEnd())).minusHours(7);
        if (!assignment.startsAt().equals(expectedStart) || !assignment.endsAt().equals(expectedEnd)) {
            throw conflict("INVALID_SHIFT_TIME", "Thời gian phân công không còn khớp với ca đã cấu hình.");
        }

        if (!repository.hasExaminationPrice(staff.id(), assignment.specialtyId(), mode,
                assignment.startsAt(), assignment.endsAt())) {
            throw conflict("EXAMINATION_PRICE_UNAVAILABLE", "Bác sĩ chưa có giá khám đang hiệu lực cho toàn bộ khung giờ đã phân công.");
        }
    }

    private List<WorkScheduleRepository.OnlineSlotData> buildOnlineSlots(
            List<WorkScheduleRepository.AssignmentIdentity> assignments) {
        List<WorkScheduleRepository.OnlineSlotData> slots = new ArrayList<>();
        for (WorkScheduleRepository.AssignmentIdentity assignment : assignments) {
            if (!"ONLINE".equals(assignment.mode())) continue;
            LocalDateTime start = assignment.startsAt();
            while (!start.plusMinutes(20).isAfter(assignment.endsAt())) {
                slots.add(new WorkScheduleRepository.OnlineSlotData(assignment.id(), start, start.plusMinutes(20)));
                start = start.plusMinutes(20);
            }
        }
        return slots;
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
