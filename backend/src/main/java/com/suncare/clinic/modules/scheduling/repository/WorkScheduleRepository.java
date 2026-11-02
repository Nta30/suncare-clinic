package com.suncare.clinic.modules.scheduling.repository;

import com.suncare.clinic.modules.scheduling.dto.WorkAssignmentResponse;
import com.suncare.clinic.modules.scheduling.dto.WorkAssignmentCreateRequest;
import com.suncare.clinic.modules.scheduling.dto.ScheduleOptionsResponse;
import com.suncare.clinic.modules.scheduling.dto.WorkScheduleResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class WorkScheduleRepository {
    private final JdbcTemplate jdbc;

    public WorkScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WorkScheduleResponse> listForHead(long staffId) {
        return jdbc.query("""
                SELECT ws.id, ws.department_id, d.name AS department_name, ws.period_start, ws.period_end,
                       ws.status, ws.version, CONVERT(varchar(23), ws.created_at, 126) + 'Z' AS created_at,
                       (SELECT COUNT(*) FROM dbo.WorkAssignment wa WHERE wa.schedule_id = ws.id) AS assignment_count
                FROM dbo.WorkSchedule ws
                JOIN dbo.Department d ON d.id = ws.department_id
                WHERE EXISTS (
                    SELECT 1 FROM dbo.DepartmentHeadAssignment h
                    WHERE h.staff_id = ? AND h.department_id = ws.department_id
                      AND h.valid_from <= SYSUTCDATETIME()
                      AND (h.valid_to IS NULL OR h.valid_to > SYSUTCDATETIME())
                )
                ORDER BY ws.period_start DESC, ws.id DESC
                """, (rs, rowNum) -> schedule(rs), staffId);
    }

    public ScheduleIdentity lockSchedule(long scheduleId) {
        List<ScheduleIdentity> rows = jdbc.query("""
                SELECT ws.id, ws.department_id, ws.period_start, ws.period_end, ws.status
                FROM dbo.WorkSchedule ws WITH (UPDLOCK, HOLDLOCK)
                WHERE ws.id = ?
                """, (rs, rowNum) -> new ScheduleIdentity(rs.getLong("id"), rs.getLong("department_id"),
                rs.getDate("period_start").toLocalDate(), rs.getDate("period_end").toLocalDate(), rs.getString("status")), scheduleId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean headCoversPeriod(long staffId, long departmentId, LocalDate start, LocalDate end) {
        LocalDateTime startUtc = start.atStartOfDay().minusHours(7);
        LocalDateTime endUtc = end.plusDays(1).atStartOfDay().minusHours(7);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.DepartmentHeadAssignment WITH (UPDLOCK, HOLDLOCK)
                WHERE staff_id = ? AND department_id = ? AND valid_from <= ?
                  AND (valid_to IS NULL OR valid_to >= ?)
                """, Integer.class, staffId, departmentId, startUtc, endUtc);
        return count != null && count > 0;
    }

    public boolean clinicalDepartment(long departmentId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Department WHERE id = ? AND is_active = 1 AND department_type = 'CLINICAL'", Integer.class, departmentId);
        return count != null && count > 0;
    }

    public List<ScheduleOptionsResponse.DepartmentOption> headDepartments(long staffId) {
        return jdbc.query("""
                SELECT d.id, d.code, d.name,
                       CONVERT(varchar(23), h.valid_from, 126) AS valid_from,
                       CONVERT(varchar(23), h.valid_to, 126) AS valid_to
                FROM dbo.DepartmentHeadAssignment h
                JOIN dbo.Department d ON d.id = h.department_id
                WHERE h.staff_id = ? AND h.valid_from <= SYSUTCDATETIME()
                  AND (h.valid_to IS NULL OR h.valid_to > SYSUTCDATETIME())
                  AND d.is_active = 1 AND d.department_type = 'CLINICAL'
                ORDER BY d.name
                """, (rs, rowNum) -> new ScheduleOptionsResponse.DepartmentOption(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("valid_from"), rs.getString("valid_to")), staffId);
    }

    public HeadTerm currentHeadTerm(long staffId, long departmentId) {
        List<HeadTerm> terms = jdbc.query("""
                SELECT CONVERT(varchar(23), valid_from, 126) AS valid_from,
                       CONVERT(varchar(23), valid_to, 126) AS valid_to
                FROM dbo.DepartmentHeadAssignment
                WHERE staff_id = ? AND department_id = ? AND valid_from <= SYSUTCDATETIME()
                  AND (valid_to IS NULL OR valid_to > SYSUTCDATETIME())
                ORDER BY valid_from DESC
                """, (rs, rowNum) -> new HeadTerm(rs.getString("valid_from"), rs.getString("valid_to")), staffId, departmentId);
        return terms.isEmpty() ? null : terms.getFirst();
    }

    public boolean currentHeadOf(long staffId, long departmentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.DepartmentHeadAssignment
                WHERE staff_id = ? AND department_id = ? AND valid_from <= SYSUTCDATETIME()
                  AND (valid_to IS NULL OR valid_to > SYSUTCDATETIME())
                """, Integer.class, staffId, departmentId);
        return count != null && count > 0;
    }

    public List<ScheduleOptionsResponse.StaffOption> staffOptions(long departmentId) {
        Map<Long, StaffOptionBuilder> staff = new LinkedHashMap<>();
        jdbc.query("""
                SELECT s.id, s.code, s.full_name, r.code AS role_code,
                       sp.id AS specialty_id, sp.code AS specialty_code, sp.name AS specialty_name
                FROM dbo.Staff s
                LEFT JOIN dbo.[Account] a ON a.staff_id = s.id AND a.status = 'ACTIVE'
                LEFT JOIN dbo.AccountRole ar ON ar.account_id = a.id
                LEFT JOIN dbo.[Role] r ON r.id = ar.role_id AND r.account_type = a.account_type
                LEFT JOIN dbo.StaffSpecialty ss ON ss.staff_id = s.id AND ss.is_active = 1
                LEFT JOIN dbo.Specialty sp ON sp.id = ss.specialty_id AND sp.is_active = 1
                WHERE s.is_active = 1 AND EXISTS (
                    SELECT 1 FROM dbo.StaffDepartment sd
                    WHERE sd.staff_id = s.id AND sd.department_id = ?
                      AND sd.valid_from <= SYSUTCDATETIME()
                      AND (sd.valid_to IS NULL OR sd.valid_to > SYSUTCDATETIME())
                )
                ORDER BY s.code, r.code, sp.code
                """, rs -> {
            long id = rs.getLong("id");
            StaffOptionBuilder person = staff.computeIfAbsent(id, ignored -> new StaffOptionBuilder(
                    id, rsString(rs, "code"), rsString(rs, "full_name")));
            String role = rsString(rs, "role_code");
            if (role != null) person.roles.add(role);
            long specialtyId = rs.getLong("specialty_id");
            if (!rs.wasNull()) {
                person.specialties.putIfAbsent(specialtyId, new ScheduleOptionsResponse.SpecialtyOption(
                        specialtyId, rsString(rs, "specialty_code"), rsString(rs, "specialty_name")));
            }
        }, departmentId);
        return staff.values().stream().map(person -> new ScheduleOptionsResponse.StaffOption(
                person.id, person.code, person.name, List.copyOf(person.roles),
                List.copyOf(person.specialties.values()))).toList();
    }

    public List<ScheduleOptionsResponse.RoomOption> roomOptions(long departmentId) {
        return jdbc.query("""
                SELECT id, code, name FROM dbo.Room
                WHERE department_id = ? AND room_type = 'CONSULTATION' AND is_active = 1
                ORDER BY code
                """, (rs, rowNum) -> new ScheduleOptionsResponse.RoomOption(rs.getLong("id"), rs.getString("code"), rs.getString("name")), departmentId);
    }

    public List<ScheduleOptionsResponse.SpecialtyOption> specialtyOptions(long departmentId) {
        return jdbc.query("""
                SELECT s.id, s.code, s.name FROM dbo.DepartmentSpecialty ds
                JOIN dbo.Specialty s ON s.id = ds.specialty_id
                WHERE ds.department_id = ? AND ds.is_active = 1 AND s.is_active = 1
                ORDER BY s.name
                """, (rs, rowNum) -> new ScheduleOptionsResponse.SpecialtyOption(rs.getLong("id"), rs.getString("code"), rs.getString("name")), departmentId);
    }

    public List<ScheduleOptionsResponse.ShiftOption> activeShiftOptions() {
        return jdbc.query("""
                SELECT id, code, name, CONVERT(varchar(8), local_start, 108) AS local_start,
                       CONVERT(varchar(8), local_end, 108) AS local_end
                FROM dbo.ShiftDefinition WHERE is_active = 1 ORDER BY local_start
                """, (rs, rowNum) -> new ScheduleOptionsResponse.ShiftOption(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("local_start"), rs.getString("local_end")));
    }

    public long insertSchedule(long departmentId, LocalDate start, LocalDate end) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.WorkSchedule (department_id, period_start, period_end, status)
                OUTPUT INSERTED.id VALUES (?, ?, ?, 'DRAFT')
                """, Long.class, departmentId, start, end);
    }

    public WorkScheduleResponse findSchedule(long scheduleId) {
        List<WorkScheduleResponse> rows = jdbc.query("""
                SELECT ws.id, ws.department_id, d.name AS department_name, ws.period_start, ws.period_end,
                       ws.status, ws.version, CONVERT(varchar(23), ws.created_at, 126) + 'Z' AS created_at,
                       (SELECT COUNT(*) FROM dbo.WorkAssignment wa WHERE wa.schedule_id = ws.id) AS assignment_count
                FROM dbo.WorkSchedule ws JOIN dbo.Department d ON d.id = ws.department_id
                WHERE ws.id = ?
                """, (rs, rowNum) -> schedule(rs), scheduleId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public StaffIdentity staff(long staffId) {
        List<StaffIdentity> rows = jdbc.query("""
                SELECT id, code, full_name, is_active FROM dbo.Staff WITH (UPDLOCK, HOLDLOCK) WHERE id = ?
                """, (rs, rowNum) -> new StaffIdentity(rs.getLong("id"), rs.getString("code"), rs.getString("full_name"), rs.getBoolean("is_active")), staffId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean hasDepartmentMembership(long staffId, long departmentId, LocalDate date) {
        LocalDateTime startUtc = date.atStartOfDay().minusHours(7);
        LocalDateTime endUtc = date.plusDays(1).atStartOfDay().minusHours(7);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.StaffDepartment WITH (UPDLOCK, HOLDLOCK)
                WHERE staff_id = ? AND department_id = ? AND valid_from <= ? AND (valid_to IS NULL OR valid_to >= ?)
                """, Integer.class, staffId, departmentId, startUtc, endUtc);
        return count != null && count > 0;
    }

    public boolean hasRole(long staffId, String role) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.[Account] a
                JOIN dbo.AccountRole ar ON ar.account_id = a.id
                JOIN dbo.[Role] r ON r.id = ar.role_id
                WHERE a.staff_id = ? AND a.status = 'ACTIVE' AND r.code = ?
                """, Integer.class, staffId, role);
        return count != null && count > 0;
    }

    public boolean hasSpecialty(long staffId, long departmentId, long specialtyId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.StaffSpecialty ss
                JOIN dbo.DepartmentSpecialty ds ON ds.specialty_id = ss.specialty_id AND ds.department_id = ? AND ds.is_active = 1
                JOIN dbo.Specialty sp ON sp.id = ss.specialty_id AND sp.is_active = 1
                WHERE ss.staff_id = ? AND ss.specialty_id = ? AND ss.is_active = 1
                """, Integer.class, departmentId, staffId, specialtyId);
        return count != null && count > 0;
    }

    public RoomIdentity lockRoom(long roomId) {
        List<RoomIdentity> rows = jdbc.query("""
                SELECT id, department_id, room_type, is_active FROM dbo.Room WITH (UPDLOCK, HOLDLOCK) WHERE id = ?
                """, (rs, rowNum) -> new RoomIdentity(rs.getLong("id"), rs.getLong("department_id"), rs.getString("room_type"), rs.getBoolean("is_active")), roomId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public ShiftIdentity lockShift(long shiftId) {
        List<ShiftIdentity> rows = jdbc.query("""
                SELECT id, code, name, local_start, local_end, is_active FROM dbo.ShiftDefinition WITH (UPDLOCK, HOLDLOCK) WHERE id = ?
                """, (rs, rowNum) -> new ShiftIdentity(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getTime("local_start").toLocalTime().toString(), rs.getTime("local_end").toLocalTime().toString(), rs.getBoolean("is_active")), shiftId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean roomConflict(long scheduleId, long roomId, LocalDateTime startsAt, LocalDateTime endsAt) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.WorkAssignment wa WITH (UPDLOCK, HOLDLOCK)
                JOIN dbo.WorkSchedule ws ON ws.id = wa.schedule_id
                WHERE wa.room_id = ? AND wa.mode = 'ONSITE'
                  AND wa.starts_at < ? AND wa.ends_at > ?
                  AND (wa.schedule_id = ? OR ws.status = 'PUBLISHED')
                """, Integer.class, roomId, endsAt, startsAt, scheduleId);
        return count != null && count > 0;
    }

    public boolean staffConflict(long scheduleId, long staffId, LocalDateTime startsAt, LocalDateTime endsAt) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.WorkAssignment wa WITH (UPDLOCK, HOLDLOCK)
                JOIN dbo.WorkSchedule ws ON ws.id = wa.schedule_id
                WHERE wa.staff_id = ? AND wa.starts_at < ? AND wa.ends_at > ?
                  AND (wa.schedule_id = ? OR ws.status = 'PUBLISHED')
                """, Integer.class, staffId, endsAt, startsAt, scheduleId);
        return count != null && count > 0;
    }

    public long insertAssignment(long scheduleId, WorkAssignmentCreateRequest request, LocalDateTime startsAt, LocalDateTime endsAt) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.WorkAssignment (schedule_id, staff_id, room_id, specialty_id, work_date, shift_id, mode, starts_at, ends_at, capacity)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, 'ONSITE', ?, ?, ?)
                """, Long.class, scheduleId, request.staffId(), request.roomId(), request.specialtyId(), request.workDate(), request.shiftId(), startsAt, endsAt, request.capacity());
    }

    public int deleteAssignment(long scheduleId, long assignmentId) {
        return jdbc.update("DELETE FROM dbo.WorkAssignment WHERE id = ? AND schedule_id = ?", assignmentId, scheduleId);
    }

    public List<WorkAssignmentResponse> listAssignments(long scheduleId) {
        return jdbc.query("""
                SELECT wa.id, wa.schedule_id, wa.staff_id, st.code AS staff_code, st.full_name AS staff_name,
                       wa.room_id, r.code AS room_code, r.name AS room_name,
                       wa.specialty_id, sp.name AS specialty_name, wa.shift_id, sh.code AS shift_code, sh.name AS shift_name,
                       CONVERT(varchar(8), sh.local_start, 108) AS local_start,
                       CONVERT(varchar(8), sh.local_end, 108) AS local_end,
                       CONVERT(varchar(10), wa.work_date, 23) AS work_date,
                       CONVERT(varchar(23), wa.starts_at, 126) + 'Z' AS starts_at,
                       CONVERT(varchar(23), wa.ends_at, 126) + 'Z' AS ends_at,
                       wa.capacity, wa.version
                FROM dbo.WorkAssignment wa
                JOIN dbo.Staff st ON st.id = wa.staff_id
                JOIN dbo.Room r ON r.id = wa.room_id
                JOIN dbo.Specialty sp ON sp.id = wa.specialty_id
                JOIN dbo.ShiftDefinition sh ON sh.id = wa.shift_id
                WHERE wa.schedule_id = ?
                ORDER BY wa.work_date, sh.local_start, st.code
                """, (rs, rowNum) -> new WorkAssignmentResponse(rs.getLong("id"), rs.getLong("schedule_id"),
                rs.getLong("staff_id"), rs.getString("staff_code"), rs.getString("staff_name"),
                rs.getLong("room_id"), rs.getString("room_code"), rs.getString("room_name"),
                rs.getLong("specialty_id"), rs.getString("specialty_name"), rs.getLong("shift_id"),
                rs.getString("shift_code"), rs.getString("shift_name"), rs.getString("local_start"), rs.getString("local_end"),
                rs.getString("work_date"), rs.getString("starts_at"), rs.getString("ends_at"), rs.getInt("capacity"),
                Base64.getEncoder().encodeToString(rs.getBytes("version"))), scheduleId);
    }

    public void audit(long actorId, String action, String entity, long entityId, String detail) {
        jdbc.update("""
                INSERT INTO dbo.AuditEvent (actor_id, actor_type, action, entity_type, entity_id, occurred_at, correlation_id, redacted_change)
                VALUES (?, 'USER', ?, ?, ?, SYSUTCDATETIME(), ?, ?)
                """, actorId, action, entity, entityId, UUID.randomUUID().toString(), detail);
    }

    private WorkScheduleResponse schedule(ResultSet rs) throws SQLException {
        return new WorkScheduleResponse(rs.getLong("id"), rs.getLong("department_id"), rs.getString("department_name"),
                rs.getString("period_start"), rs.getString("period_end"), rs.getString("status"), rs.getInt("assignment_count"),
                Base64.getEncoder().encodeToString(rs.getBytes("version")), rs.getString("created_at"));
    }

    private String rsString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException exception) {
            throw new org.springframework.dao.DataRetrievalFailureException("Không đọc được cột " + column, exception);
        }
    }

    private static class StaffOptionBuilder {
        private final long id;
        private final String code;
        private final String name;
        private final LinkedHashSet<String> roles = new LinkedHashSet<>();
        private final Map<Long, ScheduleOptionsResponse.SpecialtyOption> specialties = new LinkedHashMap<>();

        private StaffOptionBuilder(long id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }
    }

    public record ScheduleIdentity(long id, long departmentId, LocalDate periodStart, LocalDate periodEnd, String status) { }
    public record StaffIdentity(long id, String code, String name, boolean active) { }
    public record RoomIdentity(long id, long departmentId, String type, boolean active) { }
    public record ShiftIdentity(long id, String code, String name, String localStart, String localEnd, boolean active) { }
    public record HeadTerm(String validFrom, String validTo) { }
}
