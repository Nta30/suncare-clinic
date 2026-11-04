package com.suncare.clinic.modules.scheduling;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class WorkSchedulePublishIT {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private MockMvc mvc;
    private String prefix;
    private long headStaffId;
    private long headAccountId;
    private long doctorOneId;
    private long doctorOneAccountId;
    private long doctorTwoId;
    private long doctorTwoAccountId;
    private long departmentId;
    private long specialtyId;
    private long consultationRoomId;
    private long onlineRoomId;
    private long shiftId;
    private boolean createdShift;
    private long afternoonShiftId;
    private boolean createdAfternoonShift;
    private long serviceId;
    private long unrelatedSpecialtyId;
    private String headToken;
    private String doctorToken;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalTime shiftStart;
    private LocalTime shiftEnd;
    private LocalTime afternoonStart;
    private LocalTime afternoonEnd;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        prefix = "P14" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        headStaffId = insertStaff(prefix + "H", "P14 Head");
        headAccountId = insertAccount("p14.head." + prefix.toLowerCase(), headStaffId);
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role]
                WHERE code IN ('DOCTOR', 'DEPARTMENT_HEAD')
                """, headAccountId);

        doctorOneId = insertStaff(prefix + "D1", "P14 Doctor One");
        doctorOneAccountId = insertAccount("p14.doctor1." + prefix.toLowerCase(), doctorOneId);
        grantRole(doctorOneAccountId, "DOCTOR");
        doctorTwoId = insertStaff(prefix + "D2", "P14 Doctor Two");
        doctorTwoAccountId = insertAccount("p14.doctor2." + prefix.toLowerCase(), doctorTwoId);
        grantRole(doctorTwoAccountId, "DOCTOR");

        departmentId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P14 khoa kiem thu', 'CLINICAL', 1)
                """, Long.class, prefix + "DEP");
        specialtyId = jdbc.queryForObject("""
                INSERT INTO dbo.Specialty (code, name, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P14 chuyen khoa kiem thu', 1)
                """, Long.class, prefix + "SPEC");
        jdbc.update("INSERT INTO dbo.DepartmentSpecialty (department_id, specialty_id) VALUES (?, ?)", departmentId, specialtyId);
        consultationRoomId = insertRoom("CONSULTATION", "ONSITE");
        onlineRoomId = insertRoom("ONLINE", "ONLINE");

        LocalDate membershipDate = LocalDate.now().minusDays(2);
        LocalDateTime membershipStart = membershipDate.atStartOfDay().minusHours(7);
        jdbc.update("INSERT INTO dbo.StaffDepartment (staff_id, department_id, valid_from, is_primary) VALUES (?, ?, ?, 1)", headStaffId, departmentId, membershipStart);
        jdbc.update("INSERT INTO dbo.StaffDepartment (staff_id, department_id, valid_from, is_primary) VALUES (?, ?, ?, 1)", doctorOneId, departmentId, membershipStart);
        jdbc.update("INSERT INTO dbo.StaffDepartment (staff_id, department_id, valid_from, is_primary) VALUES (?, ?, ?, 1)", doctorTwoId, departmentId, membershipStart);
        jdbc.update("INSERT INTO dbo.DepartmentHeadAssignment (staff_id, department_id, valid_from, assigned_by) VALUES (?, ?, ?, ?)", headStaffId, departmentId, membershipStart, headAccountId);
        jdbc.update("INSERT INTO dbo.StaffSpecialty (staff_id, specialty_id, is_active) VALUES (?, ?, 1)", doctorOneId, specialtyId);
        jdbc.update("INSERT INTO dbo.StaffSpecialty (staff_id, specialty_id, is_active) VALUES (?, ?, 1)", doctorTwoId, specialtyId);

        shiftId = getMorningShift();
        afternoonShiftId = getAfternoonShift();
        periodStart = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        periodEnd = periodStart.plusDays(1);
        serviceId = jdbc.queryForObject("""
                INSERT INTO dbo.[Service] (code, name, service_type, result_type, specialty_id, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P14 dich vu kham', 'EXAMINATION', NULL, ?, 1)
                """, Long.class, prefix + "EXAM", specialtyId);
        addPrice(doctorOneId, "ONSITE");
        addPrice(doctorOneId, "ONLINE");
        addPrice(doctorTwoId, "ONSITE");
        addPrice(doctorTwoId, "ONLINE");

        headToken = login("p14.head." + prefix.toLowerCase());
        doctorToken = login("p14.doctor1." + prefix.toLowerCase());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM dbo.OnlineSlot WHERE assignment_id IN (SELECT wa.id FROM dbo.WorkAssignment wa JOIN dbo.WorkSchedule ws ON ws.id = wa.schedule_id WHERE ws.department_id = ?)", departmentId);
        jdbc.update("DELETE FROM dbo.WorkAssignment WHERE schedule_id IN (SELECT id FROM dbo.WorkSchedule WHERE department_id = ?)", departmentId);
        jdbc.update("DELETE FROM dbo.WorkSchedule WHERE department_id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.AuditEvent WHERE actor_id = ?", headAccountId);
        jdbc.update("DELETE FROM dbo.DepartmentHeadAssignment WHERE staff_id = ?", headStaffId);
        jdbc.update("DELETE FROM dbo.StaffSpecialty WHERE staff_id IN (?, ?)", doctorOneId, doctorTwoId);
        jdbc.update("DELETE FROM dbo.StaffDepartment WHERE staff_id IN (?, ?, ?)", headStaffId, doctorOneId, doctorTwoId);
        jdbc.update("DELETE FROM dbo.DepartmentSpecialty WHERE department_id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.Room WHERE id IN (?, ?)", consultationRoomId, onlineRoomId);
        jdbc.update("DELETE FROM dbo.ServicePrice WHERE service_id = ?", serviceId);
        jdbc.update("DELETE FROM dbo.[Service] WHERE id = ?", serviceId);
        if (unrelatedSpecialtyId > 0) jdbc.update("DELETE FROM dbo.Specialty WHERE id = ?", unrelatedSpecialtyId);
        jdbc.update("DELETE FROM dbo.Specialty WHERE id = ?", specialtyId);
        jdbc.update("DELETE FROM dbo.Department WHERE id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id IN (?, ?, ?)", headAccountId, doctorOneAccountId, doctorTwoAccountId);
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id IN (?, ?, ?)", headAccountId, doctorOneAccountId, doctorTwoAccountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE id IN (?, ?, ?)", headAccountId, doctorOneAccountId, doctorTwoAccountId);
        jdbc.update("DELETE FROM dbo.Staff WHERE id IN (?, ?, ?)", headStaffId, doctorOneId, doctorTwoId);
        if (createdShift) jdbc.update("DELETE FROM dbo.ShiftDefinition WHERE id = ?", shiftId);
        if (createdAfternoonShift) jdbc.update("DELETE FROM dbo.ShiftDefinition WHERE id = ?", afternoonShiftId);
    }

    @Test
    void publishesDraftIdempotentlyAndCreatesOnlyFullOnlineSlots() throws Exception {
        long scheduleId = createDraft();
        addOnsite(scheduleId, doctorOneId, periodStart);
        addOnline(scheduleId, doctorTwoId, List.of(periodStart, periodStart.plusDays(1)),
                List.of(shiftId, afternoonShiftId), onlineRoomId);

        mvc.perform(get("/api/head/schedules/options?departmentId=" + departmentId)
                        .header("Authorization", "Bearer " + headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].roomType").value("CONSULTATION"))
                .andExpect(jsonPath("$.rooms[1].roomType").value("ONLINE"));
        mvc.perform(post("/api/head/schedules/" + scheduleId + "/publish"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/head/schedules/" + scheduleId + "/publish")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/head/schedules/" + scheduleId + "/publish")
                        .header("Authorization", "Bearer " + headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        Integer slots = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.OnlineSlot slot
                JOIN dbo.WorkAssignment assignment ON assignment.id = slot.assignment_id
                WHERE assignment.schedule_id = ? AND slot.is_open = 1
                  AND slot.ends_at = DATEADD(MINUTE, 20, slot.starts_at)
                """, Integer.class, scheduleId);
        int slotsPerDay = (int) (ChronoUnit.MINUTES.between(shiftStart, shiftEnd)
                / 20 + ChronoUnit.MINUTES.between(afternoonStart, afternoonEnd) / 20);
        org.junit.jupiter.api.Assertions.assertEquals(slotsPerDay * 2, slots);
        org.junit.jupiter.api.Assertions.assertEquals(5, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.WorkAssignment WHERE schedule_id = ?", Integer.class, scheduleId));
        org.junit.jupiter.api.Assertions.assertEquals(4, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.WorkAssignment WHERE schedule_id = ? AND mode = 'ONLINE'", Integer.class, scheduleId));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT work_date) FROM dbo.WorkAssignment WHERE schedule_id = ? AND mode = 'ONLINE'", Integer.class, scheduleId));
        mvc.perform(post("/api/head/schedules/" + scheduleId + "/publish")
                        .header("Authorization", "Bearer " + headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        org.junit.jupiter.api.Assertions.assertEquals(slotsPerDay * 2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.OnlineSlot slot JOIN dbo.WorkAssignment assignment ON assignment.id = slot.assignment_id WHERE assignment.schedule_id = ?", Integer.class, scheduleId));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent WHERE actor_id = ? AND entity_type = 'WorkSchedule' AND entity_id = ? AND action = 'WORK_SCHEDULE_PUBLISHED'", Integer.class, headAccountId, scheduleId));
        org.junit.jupiter.api.Assertions.assertEquals(headAccountId, jdbc.queryForObject("SELECT published_by FROM dbo.WorkSchedule WHERE id = ?", Long.class, scheduleId));
        long assignmentId = jdbc.queryForObject("SELECT TOP 1 id FROM dbo.WorkAssignment WHERE schedule_id = ? ORDER BY id", Long.class, scheduleId);
        mvc.perform(delete("/api/head/schedules/" + scheduleId + "/assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + headToken))
                .andExpect(status().isConflict());
    }

    @Test
    void blocksOverlappingDoctorAssignmentsAgainstPublishedSchedules() throws Exception {
        long onsiteSchedule = createDraft();
        long onlineSchedule = createDraft();
        addOnsite(onsiteSchedule, doctorOneId, periodStart);
        addOnline(onlineSchedule, doctorOneId, List.of(periodStart), List.of(shiftId), onlineRoomId);
        publish(onsiteSchedule).andExpect(status().isOk());
        publish(onlineSchedule).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_TIME_CONFLICT"));
        assertDraftHasNoSlots(onlineSchedule);
    }

    @Test
    void blocksOverlappingOnlineRoomAssignmentsForDifferentDoctors() throws Exception {
        long firstSchedule = createDraft();
        long secondSchedule = createDraft();
        addOnline(firstSchedule, doctorOneId, List.of(periodStart), List.of(shiftId), onlineRoomId);
        addOnline(secondSchedule, doctorTwoId, List.of(periodStart), List.of(shiftId), onlineRoomId);
        publish(firstSchedule).andExpect(status().isOk());
        publish(secondSchedule).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_TIME_CONFLICT"));
        assertDraftHasNoSlots(secondSchedule);
    }

    @Test
    void leavesDraftUnchangedWhenExaminationPriceIsNotEffective() throws Exception {
        long scheduleId = createDraft();
        addOnline(scheduleId, doctorOneId, List.of(periodStart), List.of(shiftId), onlineRoomId);
        jdbc.update("DELETE FROM dbo.ServicePrice WHERE doctor_id = ? AND visit_mode = 'ONLINE' AND service_id = ?", doctorOneId, serviceId);
        publish(scheduleId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAMINATION_PRICE_UNAVAILABLE"));
        assertDraftHasNoSlots(scheduleId);
    }

    @Test
    void rejectsExaminationPriceForAnUnrelatedSpecialty() throws Exception {
        long scheduleId = createDraft();
        addOnline(scheduleId, doctorOneId, List.of(periodStart), List.of(shiftId), onlineRoomId);
        unrelatedSpecialtyId = jdbc.queryForObject("""
                INSERT INTO dbo.Specialty (code, name, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P14 chuyen khoa khong lien quan', 1)
                """, Long.class, prefix + "OTHER");
        jdbc.update("UPDATE dbo.[Service] SET specialty_id = ? WHERE id = ?", unrelatedSpecialtyId, serviceId);

        publish(scheduleId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAMINATION_PRICE_UNAVAILABLE"));
        assertDraftHasNoSlots(scheduleId);
    }

    private long createDraft() throws Exception {
        String response = mvc.perform(post("/api/head/schedules")
                        .header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + departmentId + ",\"periodStart\":\"" + periodStart + "\",\"periodEnd\":\"" + periodEnd + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private void addOnsite(long scheduleId, long staffId, LocalDate date) throws Exception {
        String assignment = "{\"staffId\":" + staffId + ",\"roomId\":" + consultationRoomId
                + ",\"specialtyId\":" + specialtyId + ",\"shiftId\":" + shiftId
                + ",\"workDate\":\"" + date + "\",\"capacity\":4}";
        mvc.perform(post("/api/head/schedules/" + scheduleId + "/assignments")
                        .header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON).content(assignment))
                .andExpect(status().isCreated());
    }

    private void addOnline(long scheduleId, long staffId, List<LocalDate> dates, List<Long> shifts, long roomId) throws Exception {
        String workDates = dates.stream().map(date -> "\"" + date + "\"").collect(java.util.stream.Collectors.joining(","));
        String shiftIds = shifts.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String assignment = "{\"staffId\":" + staffId + ",\"roomId\":" + roomId
                + ",\"specialtyId\":" + specialtyId + ",\"workDates\":[" + workDates
                + "],\"mode\":\"ONLINE\",\"shiftIds\":[" + shiftIds + "]}";
        mvc.perform(post("/api/head/schedules/" + scheduleId + "/assignments")
                        .header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON).content(assignment))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions publish(long scheduleId) throws Exception {
        return mvc.perform(post("/api/head/schedules/" + scheduleId + "/publish")
                .header("Authorization", "Bearer " + headToken));
    }

    private void assertDraftHasNoSlots(long scheduleId) {
        org.junit.jupiter.api.Assertions.assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM dbo.WorkSchedule WHERE id = ?", String.class, scheduleId));
        org.junit.jupiter.api.Assertions.assertNull(jdbc.queryForObject("SELECT published_at FROM dbo.WorkSchedule WHERE id = ?", LocalDateTime.class, scheduleId));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.OnlineSlot slot JOIN dbo.WorkAssignment assignment ON assignment.id = slot.assignment_id WHERE assignment.schedule_id = ?", Integer.class, scheduleId));
    }

    private long insertStaff(String code, String name) {
        return jdbc.queryForObject("INSERT INTO dbo.Staff (code, full_name, sex, is_active) OUTPUT INSERTED.id VALUES (?, ?, 'UNKNOWN', 1)", Long.class, code, name);
    }

    private long insertAccount(String loginName, long staffId) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, loginName, new BCryptPasswordEncoder().encode("p14-test-password-123"), staffId);
    }

    private void grantRole(long accountId, String roleCode) {
        jdbc.update("INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at) SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = ?", accountId, roleCode);
    }

    private long insertRoom(String roomType, String suffix) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Room (code, name, room_type, department_id, is_active)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, 1)
                """, Long.class, prefix + suffix, "P14 test " + suffix, roomType, departmentId);
    }

    private long getMorningShift() {
        var existing = jdbc.query("""
                SELECT TOP 1 id, local_start, local_end FROM dbo.ShiftDefinition
                WHERE code = 'MORNING' AND is_active = 1 ORDER BY id
                """, (rs, rowNum) -> new ShiftFixture(rs.getLong("id"), rs.getTime("local_start").toLocalTime(),
                rs.getTime("local_end").toLocalTime()));
        if (existing.isEmpty()) {
            shiftId = jdbc.queryForObject("INSERT INTO dbo.ShiftDefinition (code, name, local_start, local_end, is_active) OUTPUT INSERTED.id VALUES ('MORNING', 'P14 test shift', '08:00', '12:00', 1)", Long.class);
            createdShift = true;
            shiftStart = LocalTime.of(8, 0);
            shiftEnd = LocalTime.of(12, 0);
            return shiftId;
        }
        ShiftFixture shift = existing.getFirst();
        shiftStart = shift.start();
        shiftEnd = shift.end();
        return shift.id();
    }

    private long getAfternoonShift() {
        var existing = jdbc.query("""
                SELECT TOP 1 id, local_start, local_end FROM dbo.ShiftDefinition
                WHERE code = 'AFTERNOON' AND is_active = 1 ORDER BY id
                """, (rs, rowNum) -> new ShiftFixture(rs.getLong("id"), rs.getTime("local_start").toLocalTime(),
                rs.getTime("local_end").toLocalTime()));
        if (existing.isEmpty()) {
            afternoonShiftId = jdbc.queryForObject("INSERT INTO dbo.ShiftDefinition (code, name, local_start, local_end, is_active) OUTPUT INSERTED.id VALUES ('AFTERNOON', 'P14 test afternoon shift', '13:30', '17:30', 1)", Long.class);
            createdAfternoonShift = true;
            afternoonStart = LocalTime.of(13, 30);
            afternoonEnd = LocalTime.of(17, 30);
            return afternoonShiftId;
        }
        ShiftFixture shift = existing.getFirst();
        afternoonStart = shift.start();
        afternoonEnd = shift.end();
        return shift.id();
    }

    private String login(String loginName) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"p14-test-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode body = json.readTree(response);
        return body.get("accessToken").asText();
    }

    private void addPrice(long doctorId, String mode) {
        jdbc.update("""
                INSERT INTO dbo.ServicePrice (service_id, doctor_id, visit_mode, amount, valid_from, valid_to, created_by)
                VALUES (?, ?, ?, 100000, ?, NULL, ?)
                """, serviceId, doctorId, mode, LocalDateTime.now(ZoneOffset.UTC).minusDays(1), headAccountId);
    }

    private record ShiftFixture(long id, LocalTime start, LocalTime end) { }
}
