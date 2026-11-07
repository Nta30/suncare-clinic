package com.suncare.clinic.modules.appointment;

import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentBookingRequest;
import com.suncare.clinic.modules.appointment.repository.OnsiteAppointmentBookingRepository;
import com.suncare.clinic.modules.appointment.service.OnsiteAppointmentBookingService;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class OnsiteAppointmentBookingIT {
    private static final String PASSWORD = "p16-test-password-123";
    private static final String COMMAND_TYPE = "ONSITE_APPOINTMENT_BOOKING";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Saigon");

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private OnsiteAppointmentBookingService bookingService;
    @MockitoSpyBean private OnsiteAppointmentBookingRepository bookingRepository;

    private MockMvc mvc;
    private String prefix;
    private long departmentId;
    private long specialtyId;
    private long shiftId;
    private boolean createdShift;
    private LocalDate workDate;
    private LocalTime shiftStart;
    private LocalTime shiftEnd;
    private long patientOneId;
    private long patientTwoId;
    private long accountOneId;
    private long accountTwoId;
    private long doctorOneId;
    private long doctorTwoId;
    private long assignmentOneId;
    private long assignmentTwoId;
    private long serviceId;
    private String patientOneToken;
    private String patientTwoToken;
    private String doctorToken;

    @BeforeEach
    void setUp() throws Exception {
        reset(bookingRepository);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        prefix = "P16" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        departmentId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Khoa P16 test', 'CLINICAL', 1)
                """, Long.class, prefix + "DEP");
        specialtyId = jdbc.queryForObject("""
                INSERT INTO dbo.Specialty (code, name, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Chuyên khoa P16 test', 1)
                """, Long.class, prefix + "SPEC");
        jdbc.update("INSERT INTO dbo.DepartmentSpecialty (department_id, specialty_id) VALUES (?, ?)",
                departmentId, specialtyId);

        patientOneId = createPatient("One");
        patientTwoId = createPatient("Two");
        accountOneId = accountIdFor(patientOneId);
        accountTwoId = accountIdFor(patientTwoId);

        Integer activeShift = jdbc.queryForObject("""
                SELECT TOP 1 id FROM dbo.ShiftDefinition
                WHERE code = 'MORNING' AND is_active = 1 ORDER BY id
                """, Integer.class);
        if (activeShift == null) {
            Integer anyMorning = jdbc.queryForObject("""
                    SELECT TOP 1 id FROM dbo.ShiftDefinition WHERE code = 'MORNING'
                    """, Integer.class);
            if (anyMorning != null) throw new IllegalStateException("P16 test needs an active MORNING shift.");
            shiftId = jdbc.queryForObject("""
                    INSERT INTO dbo.ShiftDefinition (code, name, local_start, local_end, is_active)
                    OUTPUT INSERTED.id VALUES ('MORNING', 'Ca sáng giả lập P16', '08:00', '12:00', 1)
                    """, Long.class);
            createdShift = true;
        } else {
            shiftId = activeShift;
        }
        ShiftTimes shiftTimes = jdbc.queryForObject("""
                SELECT local_start, local_end FROM dbo.ShiftDefinition WHERE id = ?
                """, (rs, rowNum) -> new ShiftTimes(rs.getTime("local_start").toLocalTime(),
                rs.getTime("local_end").toLocalTime()), shiftId);
        shiftStart = shiftTimes.start();
        shiftEnd = shiftTimes.end();
        workDate = LocalDate.now(BUSINESS_ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        doctorOneId = createDoctor("A");
        doctorTwoId = createDoctor("B");
        long roomOneId = createRoom("A");
        long roomTwoId = createRoom("B");
        long scheduleId = jdbc.queryForObject("""
                INSERT INTO dbo.WorkSchedule (department_id, period_start, period_end, status, published_at, published_by)
                OUTPUT INSERTED.id VALUES (?, ?, ?, 'PUBLISHED', SYSUTCDATETIME(), ?)
                """, Long.class, departmentId, workDate, workDate, accountOneId);
        assignmentOneId = createAssignment(scheduleId, doctorOneId, roomOneId, 2);
        assignmentTwoId = createAssignment(scheduleId, doctorTwoId, roomTwoId, 2);
        serviceId = jdbc.queryForObject("""
                INSERT INTO dbo.[Service] (code, name, service_type, specialty_id, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Khám P16 test', 'EXAMINATION', ?, 1)
                """, Long.class, prefix + "EXAM", specialtyId);
        createPrice(doctorOneId, new BigDecimal("500000"));
        createPrice(doctorTwoId, new BigDecimal("700000"));

        patientOneToken = login("p16.patient.one." + prefix.toLowerCase());
        patientTwoToken = login("p16.patient.two." + prefix.toLowerCase());
        doctorToken = login("p16.doctor.a." + prefix.toLowerCase());
    }

    @AfterEach
    void cleanUp() {
        reset(bookingRepository);
        if (prefix == null) return;

        jdbc.update("""
                DELETE FROM dbo.CommandReceipt
                WHERE command_type = ? AND idempotency_key LIKE ?
                """, COMMAND_TYPE, prefix + "%");
        jdbc.update("""
                DELETE FROM dbo.AuditEvent
                WHERE action = 'ONSITE_APPOINTMENT_BOOKED' AND actor_id IN (?, ?)
                """, accountOneId, accountTwoId);
        jdbc.update("""
                DELETE FROM dbo.AppointmentEvent
                WHERE appointment_id IN (
                    SELECT id FROM dbo.[Appointment] WHERE patient_id IN (?, ?)
                )
                """, patientOneId, patientTwoId);
        jdbc.update("""
                DELETE FROM dbo.EncounterEvent
                WHERE encounter_id IN (
                    SELECT encounter.id FROM dbo.Encounter encounter
                    JOIN dbo.[Appointment] appointment ON appointment.id = encounter.appointment_id
                    WHERE appointment.patient_id IN (?, ?)
                )
                """, patientOneId, patientTwoId);
        jdbc.update("""
                DELETE FROM dbo.Encounter
                WHERE appointment_id IN (
                    SELECT id FROM dbo.[Appointment] WHERE patient_id IN (?, ?)
                )
                """, patientOneId, patientTwoId);
        jdbc.update("DELETE FROM dbo.[Appointment] WHERE patient_id IN (?, ?)", patientOneId, patientTwoId);
        jdbc.update("DELETE FROM dbo.WorkAssignment WHERE schedule_id IN (SELECT id FROM dbo.WorkSchedule WHERE department_id = ?)", departmentId);
        jdbc.update("DELETE FROM dbo.WorkSchedule WHERE department_id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.ServicePrice WHERE service_id = ?", serviceId);
        jdbc.update("DELETE FROM dbo.[Service] WHERE id = ?", serviceId);
        jdbc.update("DELETE FROM dbo.StaffSpecialty WHERE staff_id IN (?, ?)", doctorOneId, doctorTwoId);
        jdbc.update("DELETE FROM dbo.StaffDepartment WHERE staff_id IN (?, ?)", doctorOneId, doctorTwoId);
        jdbc.update("DELETE FROM dbo.Room WHERE code LIKE ?", prefix + "ROOM%");
        jdbc.update("DELETE FROM dbo.DepartmentSpecialty WHERE department_id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id IN (?, ?, ?, ?)",
                accountOneId, accountTwoId, accountIdForStaff(doctorOneId), accountIdForStaff(doctorTwoId));
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id IN (?, ?, ?, ?)",
                accountOneId, accountTwoId, accountIdForStaff(doctorOneId), accountIdForStaff(doctorTwoId));
        jdbc.update("DELETE FROM dbo.[Account] WHERE id IN (?, ?, ?, ?)",
                accountOneId, accountTwoId, accountIdForStaff(doctorOneId), accountIdForStaff(doctorTwoId));
        jdbc.update("DELETE FROM dbo.Staff WHERE id IN (?, ?)", doctorOneId, doctorTwoId);
        jdbc.update("DELETE FROM dbo.Department WHERE id = ?", departmentId);
        jdbc.update("DELETE FROM dbo.Specialty WHERE id = ?", specialtyId);
        jdbc.update("DELETE FROM dbo.Patient WHERE id IN (?, ?)", patientOneId, patientTwoId);
        if (createdShift) jdbc.update("DELETE FROM dbo.ShiftDefinition WHERE id = ?", shiftId);
    }

    @Test
    void booksSelectedDoctorAndMakesRetryIdempotentWithoutLeakingAnotherPatientsBooking() throws Exception {
        String key = prefix + "-selected";
        String createdBody = mvc.perform(book(patientOneToken, key, doctorTwoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doctorName").value("P16 Doctor B"))
                .andExpect(jsonPath("$.examinationAmount").value("700000"))
                .andExpect(jsonPath("$.appointmentStatus").value("BOOKED"))
                .andExpect(jsonPath("$.encounterStatus").value("PLANNED"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn().getResponse().getContentAsString();
        var created = json.readTree(createdBody);

        String replayedBody = mvc.perform(book(patientOneToken, key, doctorTwoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andReturn().getResponse().getContentAsString();
        var replayed = json.readTree(replayedBody);
        assertEquals(created.get("appointmentId").asLong(), replayed.get("appointmentId").asLong());
        assertEquals(created.get("encounterId").asLong(), replayed.get("encounterId").asLong());

        mvc.perform(book(patientOneToken, key, doctorOneId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        MvcResult otherPatient = mvc.perform(book(patientTwoToken, key, doctorTwoId))
                .andExpect(status().isConflict()).andReturn();
        assertFalse(otherPatient.getResponse().getContentAsString().contains(created.get("appointmentCode").asText()));
        assertEquals(1, countAppointmentsForPatients());
        assertEquals(1, countEncountersForPatients());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AppointmentEvent WHERE appointment_id = ?",
                Integer.class, created.get("appointmentId").asLong()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterEvent WHERE encounter_id = ?",
                Integer.class, created.get("encounterId").asLong()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.AuditEvent
                WHERE action = 'ONSITE_APPOINTMENT_BOOKED' AND entity_id = ?
                """, Integer.class, created.get("appointmentId").asLong()));
    }

    @Test
    void automaticallySelectsLowestBookingToCapacityRatio() throws Exception {
        insertExistingAppointment(assignmentOneId, doctorOneId, patientOneId, "BASEA");
        String selectedBody = mvc.perform(book(patientTwoToken, prefix + "-balance", null))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertEquals("P16 Doctor B", json.readTree(selectedBody).get("doctorName").asText());
    }

    @Test
    void breaksEqualBookingToCapacityRatiosByDoctorCode() throws Exception {
        insertExistingAppointment(assignmentOneId, doctorOneId, patientTwoId, "TIEA");
        insertExistingAppointment(assignmentTwoId, doctorTwoId, patientOneId, "TIEB");
        String tieBody = mvc.perform(book(patientOneToken, prefix + "-tie", null))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertEquals("P16 Doctor A", json.readTree(tieBody).get("doctorName").asText());
    }

    @Test
    void rejectsInvalidDatesUnavailableDoctorsAndNonPatientAccounts() throws Exception {
        mvc.perform(book(patientOneToken, prefix + "-sunday", null, workDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORK_DATE"));
        mvc.perform(book(patientOneToken, prefix + "-missing-doctor", 999999L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_UNAVAILABLE"));
        mvc.perform(book(doctorToken, prefix + "-doctor-role", doctorOneId))
                .andExpect(status().isForbidden());
        mvc.perform(book(null, prefix + "-anonymous", doctorOneId))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/appointments/onsite/bookings").header("Authorization", "Bearer " + patientOneToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                                new OnsiteAppointmentBookingRequest(specialtyId, workDate, shiftId, doctorOneId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void twoPatientsCompetingForTheLastPlaceCreateOnlyOneAppointmentAndEncounter() throws Exception {
        jdbc.update("UPDATE dbo.WorkAssignment SET capacity = 1 WHERE id = ?", assignmentOneId);
        jdbc.update("UPDATE dbo.Staff SET is_active = 0 WHERE id = ?", doctorTwoId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return postBookingStatus(patientOneToken, prefix + "-race-one", doctorOneId);
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return postBookingStatus(patientTwoToken, prefix + "-race-two", doctorOneId);
            });
            start.countDown();
            int firstStatus = first.get();
            int secondStatus = second.get();
            assertTrue((firstStatus == 201 && secondStatus == 409) || (firstStatus == 409 && secondStatus == 201),
                    "Expected one booking and one full-capacity conflict, received " + firstStatus + " and " + secondStatus);
            assertEquals(1, countAppointmentsForPatients());
            assertEquals(1, countEncountersForPatients());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rollsBackAppointmentAndEventsWhenEncounterInsertFails() throws Exception {
        doThrow(new IllegalStateException("P16 rollback probe"))
                .when(bookingRepository).insertEncounter(anyString(), anyLong(), anyLong());

        CurrentAccountResponse patient = new CurrentAccountResponse(accountOneId, "p16-test", "PATIENT",
                patientOneId, null, java.util.List.of("PATIENT"), java.util.List.of());
        assertThrows(IllegalStateException.class, () -> bookingService.book(patient,
                new OnsiteAppointmentBookingRequest(specialtyId, workDate, shiftId, doctorOneId), prefix + "-rollback"));
        assertEquals(0, countAppointmentsForPatients());
        assertEquals(0, countEncountersForPatients());
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.AppointmentEvent event
                JOIN dbo.[Appointment] appointment ON appointment.id = event.appointment_id
                WHERE appointment.patient_id IN (?, ?)
                """, Integer.class, patientOneId, patientTwoId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.CommandReceipt
                WHERE command_type = ? AND idempotency_key LIKE ?
                """, Integer.class, COMMAND_TYPE, prefix + "%"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder book(
            String token, String key, Long doctorId) throws Exception {
        return book(token, key, doctorId, workDate);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder book(
            String token, String key, Long doctorId, LocalDate date) throws Exception {
        var request = post("/api/appointments/onsite/bookings")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new OnsiteAppointmentBookingRequest(specialtyId, date, shiftId, doctorId)));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return request;
    }

    private int postBookingStatus(String token, String key, Long doctorId) throws Exception {
        return mvc.perform(book(token, key, doctorId)).andReturn().getResponse().getStatus();
    }

    private long createPatient(String suffix) {
        long patientId = jdbc.queryForObject("""
                INSERT INTO dbo.Patient (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, ?, 'UNKNOWN', 1)
                """, Long.class, prefix + "PAT" + suffix, "P16 Patient " + suffix);
        long accountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, patient_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'PATIENT', ?, 'ACTIVE')
                """, Long.class, "p16.patient." + suffix.toLowerCase() + "." + prefix.toLowerCase(),
                new BCryptPasswordEncoder().encode(PASSWORD), patientId);
        grantRole(accountId, "PATIENT");
        return patientId;
    }

    private long createDoctor(String suffix) {
        long staffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, ?, 'UNKNOWN', 1)
                """, Long.class, prefix + "DOC" + suffix, "P16 Doctor " + suffix);
        long accountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, "p16.doctor." + suffix.toLowerCase() + "." + prefix.toLowerCase(),
                new BCryptPasswordEncoder().encode(PASSWORD), staffId);
        grantRole(accountId, "DOCTOR");
        LocalDateTime departmentStart = LocalDate.now(BUSINESS_ZONE).minusDays(60).atStartOfDay().minusHours(7);
        jdbc.update("""
                INSERT INTO dbo.StaffDepartment (staff_id, department_id, valid_from, is_primary)
                VALUES (?, ?, ?, 1)
                """, staffId, departmentId, departmentStart);
        jdbc.update("INSERT INTO dbo.StaffSpecialty (staff_id, specialty_id, is_active) VALUES (?, ?, 1)",
                staffId, specialtyId);
        return staffId;
    }

    private long createRoom(String suffix) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Room (code, name, room_type, department_id, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Phòng khám P16 test', 'CONSULTATION', ?, 1)
                """, Long.class, prefix + "ROOM" + suffix, departmentId);
    }

    private long createAssignment(long scheduleId, long staffId, long roomId, int capacity) {
        LocalDateTime startsAt = workDate.atTime(shiftStart).minusHours(7);
        LocalDateTime endsAt = workDate.atTime(shiftEnd).minusHours(7);
        return jdbc.queryForObject("""
                INSERT INTO dbo.WorkAssignment (schedule_id, staff_id, room_id, specialty_id, work_date, shift_id,
                                                mode, starts_at, ends_at, capacity)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, 'ONSITE', ?, ?, ?)
                """, Long.class, scheduleId, staffId, roomId, specialtyId, workDate, shiftId,
                startsAt, endsAt, capacity);
    }

    private void createPrice(long doctorId, BigDecimal amount) {
        jdbc.update("""
                INSERT INTO dbo.ServicePrice (service_id, doctor_id, visit_mode, amount, valid_from, valid_to, created_by)
                VALUES (?, ?, 'ONSITE', ?, ?, NULL, ?)
                """, serviceId, doctorId, amount,
                workDate.atTime(shiftStart).minusHours(7).minusDays(30), accountOneId);
    }

    private void grantRole(long accountId, String roleCode) {
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = ?
                """, accountId, roleCode);
    }

    private void insertExistingAppointment(long assignmentId, long doctorId, long patientId, String suffix) {
        long priceId = jdbc.queryForObject("""
                SELECT TOP 1 id FROM dbo.ServicePrice WHERE service_id = ? AND doctor_id = ?
                """, Long.class, serviceId, doctorId);
        jdbc.update("""
                INSERT INTO dbo.[Appointment] (code, patient_id, doctor_id, department_id, specialty_id,
                                               visit_mode, onsite_assignment_id, price_id, examination_amount,
                                               status, booked_at)
                VALUES (?, ?, ?, ?, ?, 'ONSITE', ?, ?, 500000, 'BOOKED', SYSUTCDATETIME())
                """, prefix + suffix, patientId, doctorId, departmentId, specialtyId, assignmentId, priceId);
    }

    private long accountIdFor(long patientId) {
        return jdbc.queryForObject("SELECT id FROM dbo.[Account] WHERE patient_id = ?", Long.class, patientId);
    }

    private long accountIdForStaff(long staffId) {
        return jdbc.queryForObject("SELECT id FROM dbo.[Account] WHERE staff_id = ?", Long.class, staffId);
    }

    private int countAppointmentsForPatients() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[Appointment] WHERE patient_id IN (?, ?)",
                Integer.class, patientOneId, patientTwoId);
    }

    private int countEncountersForPatients() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Encounter encounter
                JOIN dbo.[Appointment] appointment ON appointment.id = encounter.appointment_id
                WHERE appointment.patient_id IN (?, ?)
                """, Integer.class, patientOneId, patientTwoId);
    }

    private String login(String loginName) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private record ShiftTimes(LocalTime start, LocalTime end) { }
}
