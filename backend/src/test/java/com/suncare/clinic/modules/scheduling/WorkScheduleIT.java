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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class WorkScheduleIT {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private MockMvc mvc;
    private String prefix;
    private long headStaffId;
    private long headAccountId;
    private long doctorStaffId;
    private long doctorAccountId;
    private long outsideDoctorStaffId;
    private long outsideDoctorAccountId;
    private long departmentId;
    private long otherDepartmentId;
    private long specialtyId;
    private long roomId;
    private long otherRoomId;
    private long shiftId;
    private boolean createdShift;
    private String headToken;
    private String doctorToken;
    private long scheduleId;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        prefix = "P13" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        headStaffId = insertStaff(prefix + "H", "P13 Head");
        headAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, "p13." + prefix.toLowerCase(), new BCryptPasswordEncoder().encode("p13-test-password-123"), headStaffId);
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'DEPARTMENT_HEAD'
                """, headAccountId);
        doctorStaffId = insertStaff(prefix + "D", "P13 Doctor");
        doctorAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, "p13.doctor." + prefix.toLowerCase(), new BCryptPasswordEncoder().encode("p13-test-password-123"), doctorStaffId);
        outsideDoctorStaffId = insertStaff(prefix + "X", "P13 Outside Doctor");
        outsideDoctorAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, "p13.outside." + prefix.toLowerCase(), new BCryptPasswordEncoder().encode("p13-test-password-123"), outsideDoctorStaffId);
        departmentId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P13 Khoa test', 'CLINICAL', 1)
                """, Long.class, prefix + "DEP");
        otherDepartmentId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P13 Khoa khac', 'CLINICAL', 1)
                """, Long.class, prefix + "DEP2");
        specialtyId = jdbc.queryForObject("""
                INSERT INTO dbo.Specialty (code, name, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P13 Chuyên khoa test', 1)
                """, Long.class, prefix + "SPEC");
        jdbc.update("INSERT INTO dbo.DepartmentSpecialty (department_id, specialty_id) VALUES (?, ?)", departmentId, specialtyId);
        roomId = jdbc.queryForObject("""
                INSERT INTO dbo.Room (code, name, room_type, department_id, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P13 Phòng test', 'CONSULTATION', ?, 1)
                """, Long.class, prefix + "ROOM", departmentId);
        otherRoomId = jdbc.queryForObject("""
                INSERT INTO dbo.Room (code, name, room_type, department_id, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P13 Other Department Room', 'CONSULTATION', ?, 1)
                """, Long.class, prefix + "ROOM2", otherDepartmentId);
        LocalDate membershipStart = LocalDate.now().minusDays(10);
        LocalDateTime startUtc = membershipStart.atStartOfDay().minusHours(7);
        jdbc.update("INSERT INTO dbo.StaffDepartment (staff_id, department_id, valid_from, is_primary) VALUES (?, ?, ?, 1)", doctorStaffId, departmentId, startUtc);
        jdbc.update("INSERT INTO dbo.StaffDepartment (staff_id, department_id, valid_from, is_primary) VALUES (?, ?, ?, 1)", headStaffId, departmentId, startUtc);
        jdbc.update("INSERT INTO dbo.DepartmentHeadAssignment (staff_id, department_id, valid_from, assigned_by) VALUES (?, ?, ?, ?)", headStaffId, departmentId, startUtc, headAccountId);
        jdbc.update("INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at) SELECT a.id, r.id, NULL, SYSUTCDATETIME() FROM dbo.[Account] a CROSS JOIN dbo.[Role] r WHERE a.staff_id = ? AND r.code = 'DOCTOR'", doctorStaffId);
        jdbc.update("INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at) SELECT a.id, r.id, NULL, SYSUTCDATETIME() FROM dbo.[Account] a CROSS JOIN dbo.[Role] r WHERE a.staff_id = ? AND r.code = 'DOCTOR'", outsideDoctorStaffId);
        jdbc.update("INSERT INTO dbo.StaffSpecialty (staff_id, specialty_id, is_active) VALUES (?, ?, 1)", doctorStaffId, specialtyId);

        Integer existingShift = jdbc.queryForObject("SELECT TOP 1 id FROM dbo.ShiftDefinition WHERE code = 'MORNING' AND is_active = 1 ORDER BY id", Integer.class);
        if (existingShift == null) {
            shiftId = jdbc.queryForObject("INSERT INTO dbo.ShiftDefinition (code, name, local_start, local_end, is_active) OUTPUT INSERTED.id VALUES ('MORNING', 'P13 test shift', '08:00', '12:00', 1)", Long.class);
            createdShift = true;
        } else {
            shiftId = existingShift;
        }
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        periodStart = nextMonday;
        periodEnd = nextMonday.plusDays(6);

        String loginResponse = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"p13." + prefix.toLowerCase() + "\",\"password\":\"p13-test-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        headToken = json.readTree(loginResponse).get("accessToken").asText();
        String doctorLoginResponse = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"p13.doctor." + prefix.toLowerCase() + "\",\"password\":\"p13-test-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        doctorToken = json.readTree(doctorLoginResponse).get("accessToken").asText();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM dbo.WorkAssignment WHERE schedule_id IN (SELECT id FROM dbo.WorkSchedule WHERE department_id = ?)", departmentId);
        jdbc.update("DELETE FROM dbo.WorkSchedule WHERE department_id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.AuditEvent WHERE actor_id = ?", headAccountId);
        jdbc.update("DELETE FROM dbo.DepartmentHeadAssignment WHERE staff_id = ?", headStaffId);
        jdbc.update("DELETE FROM dbo.StaffSpecialty WHERE staff_id = ?", doctorStaffId);
        jdbc.update("DELETE FROM dbo.StaffDepartment WHERE staff_id IN (?, ?)", headStaffId, doctorStaffId);
        jdbc.update("DELETE FROM dbo.StaffDepartment WHERE staff_id = ?", outsideDoctorStaffId);
        jdbc.update("DELETE FROM dbo.DepartmentSpecialty WHERE department_id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.Room WHERE id = ?", roomId);
        jdbc.update("DELETE FROM dbo.Room WHERE id = ?", otherRoomId);
        jdbc.update("DELETE FROM dbo.Specialty WHERE id = ?", specialtyId);
        jdbc.update("DELETE FROM dbo.Department WHERE id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.Department WHERE id = ?", otherDepartmentId);
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id = ?", headAccountId);
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id = ?", doctorAccountId);
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id = ?", outsideDoctorAccountId);
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", headAccountId);
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", doctorAccountId);
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", outsideDoctorAccountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE id = ?", headAccountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE id = ?", doctorAccountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE id = ?", outsideDoctorAccountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE staff_id = ?", doctorStaffId);
        jdbc.update("DELETE FROM dbo.Staff WHERE id IN (?, ?, ?)", headStaffId, doctorStaffId, outsideDoctorStaffId);
        if (createdShift) jdbc.update("DELETE FROM dbo.ShiftDefinition WHERE id = ?", shiftId);
    }

    @Test
    void headCanCreateDraftAndAssignDoctorInsideOwnDepartment() throws Exception {
        mvc.perform(get("/api/head/schedules")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/head/schedules").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/head/schedules/options?departmentId=" + departmentId)
                        .header("Authorization", "Bearer " + headToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.staff[0].id").isNumber())
                .andExpect(jsonPath("$.staff[0].roles[0]").value("DOCTOR"))
                .andExpect(jsonPath("$.staff[0].specialties[0].id").value(specialtyId))
                .andExpect(jsonPath("$.rooms[0].id").value(roomId))
                .andExpect(jsonPath("$.specialties[0].id").value(specialtyId))
                .andExpect(jsonPath("$.departments[0].validFrom").isNotEmpty());
        mvc.perform(get("/api/head/schedules/options?departmentId=" + otherDepartmentId)
                        .header("Authorization", "Bearer " + headToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEPARTMENT_SCOPE"));

        LocalDateTime shortenedTermEnd = periodStart.plusDays(2).atStartOfDay().minusHours(7);
        jdbc.update("UPDATE dbo.DepartmentHeadAssignment SET valid_to = ? WHERE staff_id = ? AND department_id = ?",
                shortenedTermEnd, headStaffId, departmentId);
        mvc.perform(post("/api/head/schedules")
                        .header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + departmentId + ",\"periodStart\":\"" + periodStart + "\",\"periodEnd\":\"" + periodEnd + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HEAD_TERM_OUTSIDE_PERIOD"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Nhiệm kỳ trưởng khoa hiện tại")));
        jdbc.update("UPDATE dbo.DepartmentHeadAssignment SET valid_to = NULL WHERE staff_id = ? AND department_id = ?",
                headStaffId, departmentId);

        String scheduleJson = mvc.perform(post("/api/head/schedules")
                        .header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":" + departmentId + ",\"periodStart\":\"" + periodStart + "\",\"periodEnd\":\"" + periodEnd + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        scheduleId = json.readTree(scheduleJson).get("id").asLong();
        mvc.perform(get("/api/head/schedules").header("Authorization", "Bearer " + headToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(scheduleId));

        String assignment = "{\"staffId\":" + doctorStaffId + ",\"roomId\":" + roomId
                + ",\"specialtyId\":" + specialtyId + ",\"shiftId\":" + shiftId
                + ",\"workDate\":\"" + periodStart + "\",\"capacity\":4}";
        String assignmentPath = "/api/head/schedules/" + scheduleId + "/assignments";
        mvc.perform(post(assignmentPath).header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignment.replace("\"staffId\":" + doctorStaffId, "\"staffId\":" + headStaffId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STAFF_ROLE"));
        mvc.perform(post(assignmentPath).header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignment.replace("\"staffId\":" + doctorStaffId, "\"staffId\":" + outsideDoctorStaffId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STAFF_OUTSIDE_DEPARTMENT"));
        mvc.perform(post(assignmentPath).header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignment.replace("\"roomId\":" + roomId, "\"roomId\":" + otherRoomId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ROOM"));
        mvc.perform(post(assignmentPath).header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignment.replace("\"specialtyId\":" + specialtyId, "\"specialtyId\":999999999")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SPECIALTY"));
        mvc.perform(post(assignmentPath).header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignment.replace("\"shiftId\":" + shiftId, "\"shiftId\":999999999")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SHIFT"));
        mvc.perform(post(assignmentPath).header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignment.replace(periodStart.toString(), periodEnd.plusDays(1).toString())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DATE_OUTSIDE_SCHEDULE"));
        mvc.perform(post(assignmentPath).header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignment.replace(periodStart.toString(), periodEnd.toString())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WORK_DAY"));
        mvc.perform(post(assignmentPath).header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignment.replace("\"capacity\":4", "\"capacity\":0")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/head/schedules/" + scheduleId + "/assignments")
                        .header("Authorization", "Bearer " + headToken).contentType(MediaType.APPLICATION_JSON).content(assignment))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].capacity").value(4));

        mvc.perform(post("/api/head/schedules/" + scheduleId + "/assignments")
                        .header("Authorization", "Bearer " + headToken).contentType(MediaType.APPLICATION_JSON).content(assignment))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STAFF_TIME_CONFLICT"));

        mvc.perform(get("/api/head/schedules/" + scheduleId + "/assignments").header("Authorization", "Bearer " + headToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].workDate").value(periodStart.toString()));
        long assignmentId = jdbc.queryForObject("SELECT id FROM dbo.WorkAssignment WHERE schedule_id = ?", Long.class, scheduleId);
        mvc.perform(delete("/api/head/schedules/" + scheduleId + "/assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + headToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        jdbc.update("UPDATE dbo.DepartmentHeadAssignment SET valid_from = ? WHERE staff_id = ? AND department_id = ?",
                periodStart.atStartOfDay().minusHours(7), headStaffId, departmentId);
        mvc.perform(post(assignmentPath).header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON).content(assignment))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEPARTMENT_SCOPE"));
    }

    private long insertStaff(String code, String name) {
        return jdbc.queryForObject("INSERT INTO dbo.Staff (code, full_name, sex, is_active) OUTPUT INSERTED.id VALUES (?, ?, 'UNKNOWN', 1)", Long.class, code, name);
    }
}
