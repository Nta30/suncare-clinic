package com.suncare.clinic.modules.appointment;

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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class OnsiteAppointmentSearchIT {
    private static final String PASSWORD = "p15-test-password-123";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Saigon");

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private MockMvc mvc;
    private String prefix;
    private String patientName;
    private long patientId;
    private long patientAccountId;
    private long departmentId;
    private long specialtyId;
    private long shiftId;
    private Doctor doctorOne;
    private long roomOneId;
    private boolean createdShift;
    private LocalTime shiftStart;
    private LocalTime shiftEnd;
    private LocalDate workDate;
    private String patientToken;
    private String doctorToken;
    private record Doctor(long staffId, long accountId) { }
    private record ShiftTimes(LocalTime start, LocalTime end) { }

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        prefix = "P15" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        patientName = "P15 Patient " + prefix;
        patientId = jdbc.queryForObject("""
                INSERT INTO dbo.Patient (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, ?, 'UNKNOWN', 1)
                """, Long.class, prefix + "PAT", patientName);
        String patientLogin = "p15.patient." + prefix.toLowerCase();
        patientAccountId = insertAccount(patientLogin, "PATIENT", patientId, null);
        grantRole(patientAccountId, "PATIENT");

        departmentId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Khoa P15 test', 'CLINICAL', 1)
                """, Long.class, prefix + "DEP");
        specialtyId = jdbc.queryForObject("""
                INSERT INTO dbo.Specialty (code, name, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Chuyên khoa P15 test', 1)
                """, Long.class, prefix + "SPEC");
        jdbc.update("INSERT INTO dbo.DepartmentSpecialty (department_id, specialty_id) VALUES (?, ?)", departmentId, specialtyId);

        Integer existingShiftId = jdbc.queryForObject("""
                SELECT TOP 1 id FROM dbo.ShiftDefinition WHERE code = 'MORNING' AND is_active = 1 ORDER BY id
                """, Integer.class);
        if (existingShiftId == null) {
            Integer anyMorningId = jdbc.queryForObject("SELECT TOP 1 id FROM dbo.ShiftDefinition WHERE code = 'MORNING'", Integer.class);
            if (anyMorningId != null) throw new IllegalStateException("P15 test needs an active MORNING shift.");
            shiftId = jdbc.queryForObject("""
                    INSERT INTO dbo.ShiftDefinition (code, name, local_start, local_end, is_active)
                    OUTPUT INSERTED.id VALUES ('MORNING', 'Ca sáng giả lập P15', '08:00', '12:00', 1)
                    """, Long.class);
            createdShift = true;
        } else {
            shiftId = existingShiftId;
        }
        ShiftTimes times = jdbc.queryForObject("SELECT local_start, local_end FROM dbo.ShiftDefinition WHERE id = ?",
                (rs, rowNum) -> new ShiftTimes(rs.getTime("local_start").toLocalTime(),
                        rs.getTime("local_end").toLocalTime()), shiftId);
        shiftStart = times.start();
        shiftEnd = times.end();
        workDate = LocalDate.now(BUSINESS_ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        doctorOne = createDoctor("A", true);
        Doctor doctorTwo = createDoctor("B", true);
        Doctor doctorNoPrice = createDoctor("C", true);
        Doctor doctorDraft = createDoctor("D", true);
        doctorToken = login("p15.doctor.a." + prefix.toLowerCase());

        roomOneId = createRoom("R1");
        long roomTwo = createRoom("R2");
        long roomThree = createRoom("R3");
        long roomDraft = createRoom("R4");
        long activeSchedule = createSchedule("PUBLISHED", patientAccountId);
        long draftSchedule = createSchedule("DRAFT", null);

        long priceOne = createExamPrice(doctorOne, "A", new BigDecimal("500000"), null);
        createExamPrice(doctorTwo, "B", new BigDecimal("800000"), null);
        createExamPrice(doctorNoPrice, "C", new BigDecimal("300000"), workDate.atTime(shiftStart).minusHours(8));
        long draftPrice = createExamPrice(doctorDraft, "D", new BigDecimal("100000"), null);

        long assignmentOne = createAssignment(activeSchedule, doctorOne.staffId(), roomOneId, 3);
        createAssignment(activeSchedule, doctorTwo.staffId(), roomTwo, 2);
        createAssignment(activeSchedule, doctorNoPrice.staffId(), roomThree, 4);
        long draftAssignment = createAssignment(draftSchedule, doctorDraft.staffId(), roomDraft, 6);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.ServicePrice WHERE id = ?", Integer.class, draftPrice));
        createBookedAppointment(assignmentOne, doctorOne, priceOne);

        patientToken = login(patientLogin);
    }

    @AfterEach
    void cleanUp() {
        if (prefix == null) return;
        jdbc.update("DELETE FROM dbo.[Appointment] WHERE code LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM dbo.WorkAssignment WHERE schedule_id IN (SELECT id FROM dbo.WorkSchedule WHERE department_id = ?)", departmentId);
        jdbc.update("DELETE FROM dbo.WorkSchedule WHERE department_id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.ServicePrice WHERE service_id IN (SELECT id FROM dbo.[Service] WHERE code LIKE ?)", prefix + "EXAM%");
        jdbc.update("DELETE FROM dbo.[Service] WHERE code LIKE ?", prefix + "EXAM%");
        jdbc.update("DELETE FROM dbo.StaffSpecialty WHERE staff_id IN (SELECT id FROM dbo.Staff WHERE code LIKE ?)", prefix + "DOC%");
        jdbc.update("DELETE FROM dbo.StaffDepartment WHERE staff_id IN (SELECT id FROM dbo.Staff WHERE code LIKE ?)", prefix + "DOC%");
        jdbc.update("DELETE FROM dbo.DepartmentSpecialty WHERE department_id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.Room WHERE code LIKE ?", prefix + "ROOM%");
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id IN (SELECT id FROM dbo.[Account] WHERE login_name LIKE ?)", "p15.%" + prefix.toLowerCase() + "%");
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id IN (SELECT id FROM dbo.[Account] WHERE login_name LIKE ?)", "p15.%" + prefix.toLowerCase() + "%");
        jdbc.update("DELETE FROM dbo.[Account] WHERE login_name LIKE ?", "p15.%" + prefix.toLowerCase() + "%");
        jdbc.update("DELETE FROM dbo.Staff WHERE code LIKE ?", prefix + "DOC%");
        jdbc.update("DELETE FROM dbo.Specialty WHERE id = ?", specialtyId);
        jdbc.update("DELETE FROM dbo.Department WHERE id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.Patient WHERE id = ?", patientId);
        if (createdShift) jdbc.update("DELETE FROM dbo.ShiftDefinition WHERE id = ?", shiftId);
    }

    @Test
    void returnsPublishedCapacityAndEffectiveFeeRangeWithoutDoctorNames() throws Exception {
        mvc.perform(get("/api/appointments/onsite/options").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialties[?(@.id == " + specialtyId + ")]").exists())
                .andExpect(jsonPath("$.shifts[?(@.id == " + shiftId + ")]").exists());

        String allFilters = mvc.perform(get("/api/appointments/onsite/availability")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("specialtyId", Long.toString(specialtyId))
                        .param("date", workDate.toString())
                        .param("shiftId", Long.toString(shiftId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var results = json.readTree(allFilters);
        assertEquals(2, results.size());
        assertEquals("P15 Doctor A", results.get(0).get("doctorName").asText());
        assertEquals(2, results.get(0).get("availablePlaces").asInt());
        assertEquals("500000", results.get(0).get("minimumFee").asText());
        assertEquals("P15 Doctor B", results.get(1).get("doctorName").asText());
        assertEquals(2, results.get(1).get("availablePlaces").asInt());
        assertEquals("800000", results.get(1).get("minimumFee").asText());
    }

    @Test
    void appliesOnlyFiltersThatWereProvided() throws Exception {
        String specialtyOnly = mvc.perform(get("/api/appointments/onsite/availability")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("specialtyId", Long.toString(specialtyId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var specialtyResults = json.readTree(specialtyOnly);
        assertEquals(2, specialtyResults.size());
        assertEquals(workDate.toString(), specialtyResults.get(0).get("date").asText());

        String dateOnly = mvc.perform(get("/api/appointments/onsite/availability")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("date", workDate.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(containsFixture(json.readTree(dateOnly)));

        String shiftOnly = mvc.perform(get("/api/appointments/onsite/availability")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("shiftId", Long.toString(shiftId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(containsFixture(json.readTree(shiftOnly)));

        String doctorOnly = mvc.perform(get("/api/appointments/onsite/availability")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("doctorName", "Doctor A"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var doctorResults = json.readTree(doctorOnly);
        assertEquals(1, doctorResults.size());
        assertEquals("P15 Doctor A", doctorResults.get(0).get("doctorName").asText());

        mvc.perform(get("/api/appointments/onsite/availability")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
    }

    @Test
    void filtersInclusiveDateRangeAcrossSundayAndRejectsInvalidRanges() throws Exception {
        LocalDate wednesday = workDate.plusDays(2);
        long wednesdaySchedule = createSchedule("PUBLISHED", patientAccountId, wednesday, wednesday);
        createAssignment(wednesdaySchedule, doctorOne.staffId(), roomOneId, 3, wednesday);

        LocalDate sunday = workDate.minusDays(1);
        String spanningRange = mvc.perform(rangeSearch(sunday, wednesday))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var rangeResults = json.readTree(spanningRange);
        int mondayResults = 0;
        int wednesdayResults = 0;
        for (var result : rangeResults) {
            if (workDate.toString().equals(result.path("date").asText())) mondayResults++;
            if (wednesday.toString().equals(result.path("date").asText())) wednesdayResults++;
        }
        assertEquals(3, rangeResults.size());
        assertEquals(2, mondayResults);
        assertEquals(1, wednesdayResults);

        String lowerBoundary = mvc.perform(rangeSearch(wednesday, wednesday))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, json.readTree(lowerBoundary).size());

        String upperBoundary = mvc.perform(rangeSearch(sunday, workDate))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(2, json.readTree(upperBoundary).size());

        mvc.perform(rangeSearch(wednesday, workDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        mvc.perform(rangeSearch(LocalDate.now(BUSINESS_ZONE).minusDays(1), wednesday))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORK_DATE"));
    }

    @Test
    void requiresPatientRoleAndRejectsPastOrSundayDates() throws Exception {
        mvc.perform(get("/api/appointments/onsite/options")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/onsite/options").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());

        mvc.perform(search(workDate.with(TemporalAdjusters.previous(DayOfWeek.MONDAY))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORK_DATE"));
        mvc.perform(search(workDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORK_DATE"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder search(LocalDate date) {
        return get("/api/appointments/onsite/availability")
                .header("Authorization", "Bearer " + patientToken)
                .param("specialtyId", Long.toString(specialtyId))
                .param("date", date.toString())
                .param("shiftId", Long.toString(shiftId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rangeSearch(LocalDate from, LocalDate to) {
        return get("/api/appointments/onsite/availability")
                .header("Authorization", "Bearer " + patientToken)
                .param("specialtyId", Long.toString(specialtyId))
                .param("dateFrom", from.toString())
                .param("dateTo", to.toString())
                .param("shiftId", Long.toString(shiftId));
    }

    private boolean containsFixture(Iterable<? extends tools.jackson.databind.JsonNode> results) {
        for (var result : results) {
            if (result.path("specialtyId").asLong() == specialtyId
                    && result.path("date").asText().equals(workDate.toString())) return true;
        }
        return false;
    }

    private long insertAccount(String login, String accountType, Long profileId, Long staffId) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, patient_id, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """, Long.class, login, new BCryptPasswordEncoder().encode(PASSWORD), accountType, profileId, staffId);
    }

    private void grantRole(long accountId, String roleCode) {
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = ?
                """, accountId, roleCode);
    }

    private Doctor createDoctor(String suffix, boolean active) {
        long staffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, ?, 'UNKNOWN', ?)
                """, Long.class, prefix + "DOC" + suffix, "P15 Doctor " + suffix, active);
        String login = "p15.doctor." + suffix.toLowerCase() + "." + prefix.toLowerCase();
        long accountId = insertAccount(login, "STAFF", null, staffId);
        grantRole(accountId, "DOCTOR");
        LocalDateTime departmentStart = LocalDate.now(BUSINESS_ZONE).minusDays(60).atStartOfDay().minusHours(7);
        jdbc.update("INSERT INTO dbo.StaffDepartment (staff_id, department_id, valid_from, is_primary) VALUES (?, ?, ?, 1)",
                staffId, departmentId, departmentStart);
        jdbc.update("INSERT INTO dbo.StaffSpecialty (staff_id, specialty_id, is_active) VALUES (?, ?, 1)", staffId, specialtyId);
        return new Doctor(staffId, accountId);
    }

    private long createRoom(String suffix) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Room (code, name, room_type, department_id, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Phòng khám P15 test', 'CONSULTATION', ?, 1)
                """, Long.class, prefix + "ROOM" + suffix, departmentId);
    }

    private long createSchedule(String status, Long publishedBy) {
        return createSchedule(status, publishedBy, workDate, workDate);
    }

    private long createSchedule(String status, Long publishedBy, LocalDate startDate, LocalDate endDate) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.WorkSchedule (department_id, period_start, period_end, status, published_at, published_by)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?)
                """, Long.class, departmentId, startDate, endDate, status,
                "PUBLISHED".equals(status) ? LocalDateTime.now() : null, publishedBy);
    }

    private long createExamPrice(Doctor doctor, String suffix, BigDecimal amount, LocalDateTime validTo) {
        long serviceId = jdbc.queryForObject("""
                INSERT INTO dbo.[Service] (code, name, service_type, specialty_id, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Khám P15 test', 'EXAMINATION', ?, 1)
                """, Long.class, prefix + "EXAM" + suffix, specialtyId);
        return jdbc.queryForObject("""
                INSERT INTO dbo.ServicePrice (service_id, doctor_id, visit_mode, amount, valid_from, valid_to, created_by)
                OUTPUT INSERTED.id VALUES (?, ?, 'ONSITE', ?, ?, ?, ?)
                """, Long.class, serviceId, doctor.staffId(), amount,
                workDate.atTime(shiftStart).minusHours(7).minusDays(30), validTo, patientAccountId);
    }

    private long createAssignment(long scheduleId, long staffId, long roomId, int capacity) {
        return createAssignment(scheduleId, staffId, roomId, capacity, workDate);
    }

    private long createAssignment(long scheduleId, long staffId, long roomId, int capacity, LocalDate workDate) {
        LocalDateTime startsAt = workDate.atTime(shiftStart).minusHours(7);
        LocalDateTime endsAt = workDate.atTime(shiftEnd).minusHours(7);
        return jdbc.queryForObject("""
                INSERT INTO dbo.WorkAssignment (schedule_id, staff_id, room_id, specialty_id, work_date, shift_id,
                                                mode, starts_at, ends_at, capacity)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, 'ONSITE', ?, ?, ?)
                """, Long.class, scheduleId, staffId, roomId, specialtyId, workDate, shiftId, startsAt, endsAt, capacity);
    }

    private void createBookedAppointment(long assignmentId, Doctor doctor, long priceId) {
        jdbc.update("""
                INSERT INTO dbo.[Appointment] (code, patient_id, doctor_id, department_id, specialty_id, visit_mode,
                                               onsite_assignment_id, price_id, examination_amount, status, booked_at)
                VALUES (?, ?, ?, ?, ?, 'ONSITE', ?, ?, 500000, 'BOOKED', SYSUTCDATETIME())
                """, prefix + "APT", patientId, doctor.staffId(), departmentId, specialtyId, assignmentId, priceId);
    }

    private String login(String loginName) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }
}
