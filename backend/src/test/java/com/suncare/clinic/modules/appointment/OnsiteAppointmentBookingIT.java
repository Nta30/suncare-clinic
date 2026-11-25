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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @MockitoSpyBean private com.suncare.clinic.modules.encounter.repository.EncounterRepository encounterRepository;
    @MockitoSpyBean private com.suncare.clinic.modules.encounter.repository.EncounterHistoryRepository historyRepository;

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
    private String doctorTwoToken;

    @BeforeEach
    void setUp() throws Exception {
        reset(bookingRepository);
        reset(encounterRepository);
        reset(historyRepository);
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
        doctorTwoToken = login("p16.doctor.b." + prefix.toLowerCase());
    }

    @AfterEach
    void cleanUp() {
        reset(bookingRepository);
        reset(encounterRepository);
        reset(historyRepository);
        if (prefix == null) return;

        jdbc.update("DELETE FROM dbo.AuditEvent WHERE (action = 'ENCOUNTER_STARTED' OR action LIKE 'PATIENT_HISTORY_%' OR action LIKE 'PATIENT_ALLERGY_%') AND actor_id IN (?, ?)",
                accountIdForStaff(doctorOneId), accountIdForStaff(doctorTwoId));
        jdbc.update("""
                DELETE FROM dbo.EncounterHistorySnapshot WHERE encounter_id IN (
                    SELECT e.id FROM dbo.Encounter e JOIN dbo.Appointment a ON a.id = e.appointment_id
                    WHERE a.patient_id IN (?, ?))
                """, patientOneId, patientTwoId);
        jdbc.update("DELETE FROM dbo.PatientHistory WHERE patient_id IN (?, ?)", patientOneId, patientTwoId);
        jdbc.update("DELETE FROM dbo.PatientAllergy WHERE patient_id IN (?, ?)", patientOneId, patientTwoId);
        jdbc.update("DELETE FROM dbo.AuditEvent WHERE entity_type = 'Allergen' AND entity_id IN (SELECT id FROM dbo.Allergen WHERE code LIKE ?)", prefix + "%");
        jdbc.update("DELETE FROM dbo.Allergen WHERE code LIKE ?", prefix + "%");

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
                DELETE FROM dbo.PrevisitResponse
                WHERE appointment_id IN (
                    SELECT id FROM dbo.[Appointment] WHERE patient_id IN (?, ?)
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
    void usesCatalogForMultipleAllergiesAndRequiresNoteForUnlistedSubstance() throws Exception {
        long encounterId = bookEncounterForStart("allergen-catalog");
        String path = "/api/doctor/encounters/" + encounterId + "/allergies";
        long first = jdbc.queryForObject("INSERT INTO dbo.Allergen (code, name, is_active) OUTPUT INSERTED.id VALUES (?, ?, 1)", Long.class, prefix + "ALG1", prefix + " Test allergen one");
        long second = jdbc.queryForObject("INSERT INTO dbo.Allergen (code, name, is_active) OUTPUT INSERTED.id VALUES (?, ?, 1)", Long.class, prefix + "ALG2", prefix + " Test allergen two");
        for (long id : java.util.List.of(first, second)) {
            mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(java.util.Map.of("allergenId", id, "severity", "MILD", "isActive", true))))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("allergenId", first, "severity", "SEVERE", "isActive", true))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PATIENT_ALLERGEN_EXISTS"));
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"severity\":\"UNKNOWN\",\"isActive\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ALLERGEN_NOTE_REQUIRED"));
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"otherNote\":\"P18 unlisted substance\",\"severity\":\"UNKNOWN\",\"isActive\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allergies.length()").value(3));
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("allergenId", second, "otherNote", "P18 mixed source", "severity", "MILD", "isActive", true))))
                .andExpect(status().isBadRequest());
        String started = mvc.perform(startRequest(encounterId).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(started).get("allergyText").asText().contains(prefix + "ALG1"));
        jdbc.update("UPDATE dbo.Allergen SET name = ?, is_active = 0 WHERE id = ?", prefix + " Renamed catalog entry", first);
        mvc.perform(startRequest(encounterId).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allergyText").value(json.readTree(started).get("allergyText").asText()));
        long inactive = jdbc.queryForObject("INSERT INTO dbo.Allergen (code, name, is_active) OUTPUT INSERTED.id VALUES (?, ?, 0)", Long.class, prefix + "ALG3", prefix + " Inactive test allergen");
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("allergenId", inactive, "severity", "MILD", "isActive", true))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managesUniqueAllergenCodesAndNamesWithPermissionAndVersionChecks() throws Exception {
        String path = "/api/admin/allergens";
        mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isForbidden());
        grantRole(accountIdForStaff(doctorOneId), "SYSTEM_ADMIN");
        String code = prefix + "CAT";
        String name = prefix + " Controlled test substance";
        String response = mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("code", code, "name", name, "isActive", true))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var saved = java.util.stream.StreamSupport.stream(json.readTree(response).spliterator(), false)
                .filter(item -> code.equals(item.get("code").asText())).findFirst().orElseThrow();
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("code", prefix + "OTHER", "name", name.toLowerCase(), "isActive", true))))
                .andExpect(status().isConflict());
        String update = json.writeValueAsString(java.util.Map.of("code", code, "name", name, "isActive", false, "version", saved.get("version").asText()));
        mvc.perform(put(path + "/" + saved.get("id").asLong()).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk());
        mvc.perform(put(path + "/" + saved.get("id").asLong()).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict());
        mvc.perform(post(path).header("Authorization", "Bearer " + patientOneToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void paginatesAndFiltersEncounterWorklistOnServerWithoutLeakingOtherDoctors() throws Exception {
        jdbc.update("UPDATE dbo.WorkAssignment SET capacity = 20 WHERE id = ?", assignmentOneId);
        for (int i = 0; i < 12; i++) {
            String suffix = "PAGE" + i;
            insertExistingAppointment(assignmentOneId, doctorOneId, patientOneId, suffix);
            jdbc.update("""
                    INSERT INTO dbo.Encounter (code, appointment_id, status)
                    SELECT ?, id, ? FROM dbo.Appointment WHERE code = ?
                    """, prefix + "ENC" + i, i % 2 == 0 ? "PLANNED" : "IN_PROGRESS", prefix + suffix);
        }
        insertExistingAppointment(assignmentTwoId, doctorTwoId, patientTwoId, "OTHERDOC");
        jdbc.update("INSERT INTO dbo.Encounter (code, appointment_id, status) SELECT ?, id, 'PLANNED' FROM dbo.Appointment WHERE code = ?",
                prefix + "OTHERENC", prefix + "OTHERDOC");
        String path = "/api/doctor/encounters?date=" + workDate;
        String first = mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.items.length()").value(10)).andExpect(jsonPath("$.totalPages").value(2))
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(get(path + "&page=2").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(2)).andReturn().getResponse().getContentAsString();
        var firstIds = new java.util.HashSet<Long>();
        for (var item : json.readTree(first).get("items")) firstIds.add(item.get("encounterId").asLong());
        for (var item : json.readTree(second).get("items")) assertFalse(firstIds.contains(item.get("encounterId").asLong()));
        mvc.perform(get(path + "&page=99").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2));
        mvc.perform(get(path + "&status=IN_PROGRESS&pageSize=20").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.items.length()").value(6)).andExpect(jsonPath("$.items[0].status").value("IN_PROGRESS"));
        mvc.perform(get("/api/doctor/encounters?date=" + workDate.plusDays(1)).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void searchesEncounterWorklistByVietnameseNameAndIdentifiersWithLiteralWildcards() throws Exception {
        long encounterId = bookEncounterForStart("search");
        jdbc.update("UPDATE dbo.Patient SET full_name = N'Đặng Thị Ánh' WHERE id = ?", patientOneId);
        String path = "/api/doctor/encounters";
        for (String term : java.util.List.of("anh DANG", "Đặng Ánh", prefix + "PATOne",
                jdbc.queryForObject("SELECT code FROM dbo.Encounter WHERE id = ?", String.class, encounterId),
                jdbc.queryForObject("SELECT a.code FROM dbo.Appointment a JOIN dbo.Encounter e ON e.appointment_id = a.id WHERE e.id = ?", String.class, encounterId))) {
            mvc.perform(get(path).param("date", workDate.toString()).param("search", term)
                    .header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].encounterId").value(encounterId));
        }
        for (String term : java.util.List.of("%", "_", "[", "' OR 1=1--")) {
            mvc.perform(get(path).param("date", workDate.toString()).param("search", term)
                    .header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
        mvc.perform(get(path).param("date", workDate.toString()).param("search", "anh")
                .param("status", "COMPLETED").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void validatesWorklistPagingStatusAndSearchParameters() throws Exception {
        String path = "/api/doctor/encounters?date=" + workDate;
        for (String query : java.util.List.of("&page=0", "&page=-1", "&pageSize=500", "&status=CHECKED_IN", "&search=" + "a".repeat(101))) {
            mvc.perform(get(path + query).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(path).header("Authorization", "Bearer " + patientOneToken)).andExpect(status().isForbidden());
    }

    @Test
    void managesHistoryAndAllergyThroughEncounterAndKeepsStartedSnapshotImmutable() throws Exception {
        long encounterId = bookEncounterForStart("history-ui");
        String base = "/api/doctor/encounters/" + encounterId;
        mvc.perform(get("/api/doctor/encounters?date=" + workDate).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].encounterId").value(encounterId));
        mvc.perform(get("/api/doctor/encounters?date=" + workDate).header("Authorization", "Bearer " + doctorTwoToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(base).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true)).andExpect(jsonPath("$.snapshot").isEmpty());
        String created = mvc.perform(post(base + "/histories").header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"historyType\":\"FAMILY\",\"description\":\" P18 test history \",\"isActive\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.histories[0].description").value("P18 test history"))
                .andReturn().getResponse().getContentAsString();
        var history = json.readTree(created).get("histories").get(0);
        long historyId = history.get("id").asLong();
        String version = history.get("version").asText();
        String allergyBody = mvc.perform(post(base + "/allergies").header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"otherNote\":\"P18 test allergen\",\"reaction\":\"Test rash\",\"severity\":\"SEVERE\",\"isActive\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var allergy = json.readTree(allergyBody).get("allergies").get(0);
        mvc.perform(startRequest(encounterId).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk());
        String update = json.writeValueAsString(java.util.Map.of("historyType", "PERSONAL", "description", "P18 changed history", "isActive", false, "version", version));
        mvc.perform(put(base + "/histories/" + historyId).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.histories[0].isActive").value(false))
                .andExpect(jsonPath("$.snapshot.historyText").value("FAMILY: P18 test history"));
        mvc.perform(put(base + "/histories/" + historyId).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict());
        mvc.perform(put(base + "/allergies/" + allergy.get("id").asLong()).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                        "otherNote", "P18 changed allergen", "reaction", "", "severity", "MILD", "isActive", false,
                        "version", allergy.get("version").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allergies[0].isActive").value(false))
                .andExpect(jsonPath("$.snapshot.allergyText").value("P18 test allergen [SEVERE]: Test rash"));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent WHERE entity_type = 'Encounter' AND entity_id = ? AND (action LIKE 'PATIENT_HISTORY_%' OR action LIKE 'PATIENT_ALLERGY_%')", Integer.class, encounterId));
    }

    @Test
    void historyEndpointsRejectWrongOwnerRoleForeignPatientRecordsInvalidInputAndClosedEncounter() throws Exception {
        long encounterId = bookEncounterForStart("history-auth");
        String base = "/api/doctor/encounters/" + encounterId;
        String body = "{\"historyType\":\"PERSONAL\",\"description\":\"P18 valid\",\"isActive\":true}";
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base).header("Authorization", "Bearer " + patientOneToken)).andExpect(status().isForbidden());
        mvc.perform(get(base).header("Authorization", "Bearer " + doctorTwoToken)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/histories").header("Authorization", "Bearer " + doctorTwoToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/allergies").header("Authorization", "Bearer " + patientOneToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/histories").header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"historyType\":\"BAD\",\"description\":\" \",\"isActive\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/allergies").header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"otherNote\":\"Test\",\"severity\":\"INVALID\",\"isActive\":true}"))
                .andExpect(status().isBadRequest());
        long foreignId = jdbc.queryForObject("""
                INSERT INTO dbo.PatientHistory (patient_id, history_type, description, is_active, recorded_by, recorded_at)
                OUTPUT INSERTED.id VALUES (?, 'PERSONAL', 'P18 foreign patient history', 1, ?, SYSUTCDATETIME())
                """, Long.class, patientTwoId, accountIdForStaff(doctorOneId));
        String foreignVersion = java.util.Base64.getEncoder().encodeToString(jdbc.queryForObject("SELECT version FROM dbo.PatientHistory WHERE id = ?", byte[].class, foreignId));
        mvc.perform(put(base + "/histories/" + foreignId).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                        "historyType", "PERSONAL", "description", "P18 overwrite attempt", "isActive", true, "version", foreignVersion))))
                .andExpect(status().isConflict());
        assertEquals("P18 foreign patient history", jdbc.queryForObject("SELECT description FROM dbo.PatientHistory WHERE id = ?", String.class, foreignId));
        mvc.perform(put(base + "/histories/" + foreignId).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        jdbc.update("UPDATE dbo.Encounter SET status = 'CANCELLED' WHERE id = ?", encounterId);
        mvc.perform(post(base + "/histories").header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(get(base).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void historyMutationAndAuditRollBackTogether() throws Exception {
        long encounterId = bookEncounterForStart("history-rollback");
        doThrow(new IllegalStateException("P18 history audit rollback probe")).when(historyRepository)
                .audit(anyLong(), anyLong(), anyString(), anyLong());
        assertThrows(Exception.class, () -> mvc.perform(post("/api/doctor/encounters/" + encounterId + "/allergies")
                .header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"otherNote\":\"P18 rollback allergen\",\"severity\":\"UNKNOWN\",\"isActive\":true}")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.PatientAllergy WHERE patient_id = ?", Integer.class, patientOneId));
    }

    @Test
    void twoConcurrentHistoryEditsWithSameVersionAllowOnlyOneWriter() throws Exception {
        long encounterId = bookEncounterForStart("history-race");
        String base = "/api/doctor/encounters/" + encounterId + "/histories";
        String created = mvc.perform(post(base).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"historyType\":\"SURGERY\",\"description\":\"P18 initial\",\"isActive\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var item = json.readTree(created).get("histories").get(0);
        String body = json.writeValueAsString(java.util.Map.of("historyType", "SURGERY", "description", "P18 concurrent edit", "isActive", true, "version", item.get("version").asText()));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.Callable<Integer> request = () -> {
            start.await();
            return mvc.perform(put(base + "/" + item.get("id").asLong()).header("Authorization", "Bearer " + doctorToken)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        };
        try {
            Future<Integer> first = executor.submit(request);
            Future<Integer> second = executor.submit(request);
            start.countDown();
            var results = new java.util.ArrayList<>(java.util.List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS), second.get(20, java.util.concurrent.TimeUnit.SECONDS)));
            java.util.Collections.sort(results);
            assertEquals(java.util.List.of(200, 409), results);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void startsEncounterOnceAndPreservesHistorySnapshotOnRetry() throws Exception {
        long encounterId = bookEncounterForStart("snapshot");
        long actorId = accountIdForStaff(doctorOneId);
        jdbc.update("""
                INSERT INTO dbo.PatientHistory (patient_id, history_type, description, recorded_by, recorded_at, is_active)
                VALUES (?, 'PERSONAL', N'Tiền sử giả lập P18', ?, SYSUTCDATETIME(), 1)
                """, patientOneId, actorId);
        jdbc.update("""
                INSERT INTO dbo.PatientAllergy (patient_id, substance, other_note, reaction, severity, recorded_by, is_active)
                VALUES (?, 'P18 test substance', 'P18 test substance', 'P18 test reaction', 'MILD', ?, 1)
                """, patientOneId, actorId);
        jdbc.update("""
                INSERT INTO dbo.PatientHistory (patient_id, history_type, description, recorded_by, recorded_at, is_active)
                VALUES (?, 'FAMILY', 'P18 inactive history', ?, SYSUTCDATETIME(), 0)
                """, patientOneId, actorId);
        mvc.perform(startRequest(encounterId).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.historyText").value("PERSONAL: Tiền sử giả lập P18"))
                .andExpect(jsonPath("$.allergyText").value("P18 test substance [MILD]: P18 test reaction"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty());
        jdbc.update("UPDATE dbo.PatientHistory SET description = 'P18 changed' WHERE patient_id = ?", patientOneId);
        mvc.perform(startRequest(encounterId).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.historyText").value("PERSONAL: Tiền sử giả lập P18"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterEvent WHERE encounter_id = ? AND to_status = 'IN_PROGRESS'", Integer.class, encounterId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent WHERE action = 'ENCOUNTER_STARTED' AND entity_id = ?", Integer.class, encounterId));
    }

    @Test
    void startEncounterRejectsWrongOwnerRoleAndInvalidState() throws Exception {
        long encounterId = bookEncounterForStart("auth");
        mvc.perform(startRequest(encounterId).content("{\"presenceConfirmed\":false}")
                .header("Authorization", "Bearer " + doctorToken)).andExpect(status().isBadRequest());
        mvc.perform(startRequest(encounterId).content("{}")
                .header("Authorization", "Bearer " + doctorToken)).andExpect(status().isBadRequest());
        mvc.perform(startRequest(encounterId)).andExpect(status().isUnauthorized());
        mvc.perform(startRequest(encounterId).header("Authorization", "Bearer " + patientOneToken)).andExpect(status().isForbidden());
        mvc.perform(startRequest(encounterId).header("Authorization", "Bearer " + doctorTwoToken)).andExpect(status().isNotFound());
        jdbc.update("UPDATE dbo.Encounter SET status = 'CANCELLED' WHERE id = ?", encounterId);
        mvc.perform(startRequest(encounterId).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterHistorySnapshot WHERE encounter_id = ?", Integer.class, encounterId));
    }

    @Test
    void startEncounterRollsBackSnapshotWhenLaterStepFails() throws Exception {
        long encounterId = bookEncounterForStart("rollback");
        doThrow(new IllegalStateException("P18 rollback probe")).when(encounterRepository).start(anyLong(), anyLong());
        assertThrows(Exception.class, () -> mvc.perform(startRequest(encounterId)
                .header("Authorization", "Bearer " + doctorToken)));
        assertEquals("PLANNED", jdbc.queryForObject("SELECT status FROM dbo.Encounter WHERE id = ?", String.class, encounterId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterHistorySnapshot WHERE encounter_id = ?", Integer.class, encounterId));
    }

    @Test
    void concurrentStartRequestsCreateOneSnapshotAndOneEvent() throws Exception {
        long encounterId = bookEncounterForStart("race");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.Callable<Integer> call = () -> {
            start.await();
            return mvc.perform(startRequest(encounterId)
                    .header("Authorization", "Bearer " + doctorToken)).andReturn().getResponse().getStatus();
        };
        try {
            Future<Integer> first = executor.submit(call);
            Future<Integer> second = executor.submit(call);
            start.countDown();
            assertEquals(200, first.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(200, second.get(20, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterHistorySnapshot WHERE encounter_id = ?", Integer.class, encounterId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterEvent WHERE encounter_id = ? AND to_status = 'IN_PROGRESS'", Integer.class, encounterId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder startRequest(long encounterId) {
        return post("/api/doctor/encounters/" + encounterId + "/start")
                .contentType(MediaType.APPLICATION_JSON).content("{\"presenceConfirmed\":true}");
    }

    private long bookEncounterForStart(String suffix) throws Exception {
        String body = mvc.perform(book(patientOneToken, prefix + "-p18-" + suffix, doctorOneId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("encounterId").asLong();
    }

    @Test
    void doctorTimetableIncludesEmptyShiftsAndProtectsOwnershipAndDateRange() throws Exception {
        String url = "/api/doctor/appointments/timetable?from=" + workDate + "&to=" + workDate;
        mvc.perform(get(url).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].assignmentId").value(assignmentOneId))
                .andExpect(jsonPath("$[0].visitMode").value("ONSITE"))
                .andExpect(jsonPath("$[0].roomCode").isNotEmpty())
                .andExpect(jsonPath("$[0].patientName").doesNotExist());
        mvc.perform(get(url).header("Authorization", "Bearer " + patientOneToken))
                .andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/doctor/appointments/timetable?from=" + workDate + "&to=" + workDate.minusDays(1))
                .header("Authorization", "Bearer " + doctorToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/doctor/appointments/timetable?from=" + workDate + "&to=" + workDate.plusDays(31))
                .header("Authorization", "Bearer " + doctorToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/doctor/appointments/timetable/" + assignmentTwoId + "/slots")
                .header("Authorization", "Bearer " + doctorToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/doctor/appointments/timetable/" + assignmentOneId + "/slots")
                .header("Authorization", "Bearer " + doctorToken)).andExpect(status().isNotFound());
        jdbc.update("UPDATE dbo.WorkSchedule SET status = 'DRAFT', published_at = NULL, published_by = NULL WHERE department_id = ?", departmentId);
        mvc.perform(get(url).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlineTimetableShowsSlotsAndOnlyAssignedDoctorCanReadBasicPatient() throws Exception {
        // Roll back the online fixture before the shared cleanup removes its parent assignments.
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.support.JdbcTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(tx -> {
            try {
                jdbc.update("UPDATE dbo.WorkAssignment SET mode = 'ONLINE', capacity = NULL WHERE id = ?", assignmentOneId);
                jdbc.update("UPDATE dbo.Room SET room_type = 'ONLINE' WHERE id = (SELECT room_id FROM dbo.WorkAssignment WHERE id = ?)", assignmentOneId);
                long slotId = jdbc.queryForObject("""
                        INSERT INTO dbo.OnlineSlot (assignment_id, starts_at, ends_at, is_open)
                        OUTPUT INSERTED.id SELECT id, starts_at, DATEADD(MINUTE, 20, starts_at), 1
                        FROM dbo.WorkAssignment WHERE id = ?
                        """, Long.class, assignmentOneId);
                long priceId = jdbc.queryForObject("SELECT TOP 1 id FROM dbo.ServicePrice WHERE service_id = ? AND doctor_id = ?", Long.class, serviceId, doctorOneId);
                jdbc.update("UPDATE dbo.ServicePrice SET visit_mode = 'ONLINE' WHERE id = ?", priceId);
                jdbc.update("""
                        INSERT INTO dbo.OnlineSlot (assignment_id, starts_at, ends_at, is_open)
                        SELECT assignment_id, ends_at, DATEADD(MINUTE, 20, ends_at), 1 FROM dbo.OnlineSlot WHERE id = ?
                        """, slotId);
                long holdId = jdbc.queryForObject("""
                        INSERT INTO dbo.BookingHold (code, patient_id, slot_id, price_id, quoted_amount, status,
                            expires_at, confirmed_at, idempotency_key)
                        OUTPUT INSERTED.id VALUES (?, ?, ?, ?, 500000, 'CONFIRMED',
                            DATEADD(MINUTE, 5, SYSUTCDATETIME()), SYSUTCDATETIME(), ?)
                        """, Long.class, prefix + "HOLD", patientOneId, slotId, priceId, prefix + "ONLINE");
                jdbc.update("""
                        INSERT INTO dbo.Appointment (code, patient_id, doctor_id, department_id, specialty_id,
                            visit_mode, online_slot_id, hold_id, price_id, examination_amount, status, booked_at)
                        VALUES (?, ?, ?, ?, ?, 'ONLINE', ?, ?, ?, 500000, 'BOOKED', SYSUTCDATETIME())
                        """, prefix + "ONLINE", patientOneId, doctorOneId, departmentId, specialtyId, slotId, holdId, priceId);
                String slotsUrl = "/api/doctor/appointments/timetable/" + assignmentOneId + "/slots";
                String patientUrl = "/api/doctor/appointments/timetable/slots/" + slotId + "/patient";
                mvc.perform(get(slotsUrl).header("Authorization", "Bearer " + doctorToken))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                        .andExpect(jsonPath("$[1].appointmentId").isEmpty())
                        .andExpect(jsonPath("$[0].patientName").isNotEmpty())
                        .andExpect(jsonPath("$[0].patientCode").doesNotExist());
                mvc.perform(get(patientUrl).header("Authorization", "Bearer " + doctorToken))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.patientCode").isNotEmpty())
                        .andExpect(jsonPath("$.identityNumber").doesNotExist());
                mvc.perform(get(slotsUrl).header("Authorization", "Bearer " + doctorTwoToken)).andExpect(status().isNotFound());
                mvc.perform(get(patientUrl).header("Authorization", "Bearer " + doctorTwoToken)).andExpect(status().isNotFound());
                mvc.perform(get(patientUrl).header("Authorization", "Bearer " + patientOneToken)).andExpect(status().isForbidden());
                jdbc.update("UPDATE dbo.Appointment SET status = 'CANCELLED', cancelled_at = SYSUTCDATETIME() WHERE online_slot_id = ?", slotId);
                mvc.perform(get(slotsUrl).header("Authorization", "Bearer " + doctorToken))
                        .andExpect(status().isOk()).andExpect(jsonPath("$[0].appointmentId").isEmpty());
                mvc.perform(get(patientUrl).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isNotFound());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                tx.setRollbackOnly();
            }
        });
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
    void patientAndDoctorSchedulesOnlyExposeAppointmentsWithinTheirOwnScope() throws Exception {
        String symptoms = "Ho khan và đau họng.";
        String patientOneBooking = mvc.perform(bookWithSymptoms(patientOneToken, prefix + "-list-one", doctorOneId, symptoms))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String patientTwoBooking = mvc.perform(book(patientTwoToken, prefix + "-list-two", doctorTwoId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long patientOneAppointmentId = json.readTree(patientOneBooking).get("appointmentId").asLong();
        long patientTwoAppointmentId = json.readTree(patientTwoBooking).get("appointmentId").asLong();

        var patientOneAppointments = json.readTree(mvc.perform(get("/api/patient/appointments")
                        .header("Authorization", "Bearer " + patientOneToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var patientTwoAppointments = json.readTree(mvc.perform(get("/api/patient/appointments")
                        .header("Authorization", "Bearer " + patientTwoToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var doctorOneAppointments = json.readTree(mvc.perform(get("/api/doctor/appointments")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var doctorTwoAppointments = json.readTree(mvc.perform(get("/api/doctor/appointments")
                        .header("Authorization", "Bearer " + doctorTwoToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertEquals(1, patientOneAppointments.size());
        assertEquals(patientOneAppointmentId, patientOneAppointments.get(0).get("appointmentId").asLong());
        assertTrue(patientOneAppointments.get(0).has("specialtyName"));
        assertFalse(patientOneAppointments.get(0).has("symptoms"));
        assertFalse(patientOneAppointments.get(0).has("examinationAmount"));
        assertEquals(1, patientTwoAppointments.size());
        assertEquals(patientTwoAppointmentId, patientTwoAppointments.get(0).get("appointmentId").asLong());
        assertTrue(patientOneAppointments.get(0).get("startsAt").asText().endsWith("+07:00"));

        assertEquals(1, doctorOneAppointments.size());
        assertEquals(patientOneAppointmentId, doctorOneAppointments.get(0).get("appointmentId").asLong());
        assertEquals("P16 Patient One", doctorOneAppointments.get(0).get("patientName").asText());
        assertEquals(1, doctorTwoAppointments.size());
        assertEquals(patientTwoAppointmentId, doctorTwoAppointments.get(0).get("appointmentId").asLong());
        assertEquals("P16 Patient Two", doctorTwoAppointments.get(0).get("patientName").asText());

        var patientOneDetail = json.readTree(mvc.perform(get("/api/patient/appointments/" + patientOneAppointmentId)
                        .header("Authorization", "Bearer " + patientOneToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("P16 Patient One", patientOneDetail.get("patientName").asText());
        assertEquals("P16 Doctor A", patientOneDetail.get("doctorName").asText());
        assertEquals(symptoms, patientOneDetail.get("symptoms").asText());
        assertEquals("500000", patientOneDetail.get("examinationAmount").asText());
        assertEquals("BOOKED", patientOneDetail.get("appointmentStatus").asText());

        var doctorOneDetail = json.readTree(mvc.perform(get("/api/doctor/appointments/" + patientOneAppointmentId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("P16 Patient One", doctorOneDetail.get("patientName").asText());
        assertEquals(symptoms, doctorOneDetail.get("symptoms").asText());

        mvc.perform(get("/api/patient/appointments/" + patientTwoAppointmentId)
                        .header("Authorization", "Bearer " + patientOneToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/doctor/appointments/" + patientOneAppointmentId)
                        .header("Authorization", "Bearer " + doctorTwoToken))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/doctor/appointments").header("Authorization", "Bearer " + patientOneToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patient/appointments").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patient/appointments/" + patientOneAppointmentId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/patient/appointments/" + patientOneAppointmentId)
                        .header("Authorization", "Bearer " + patientOneToken))
                .andExpect(status().isForbidden());
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bookWithSymptoms(
            String token, String key, Long doctorId, String symptoms) throws Exception {
        var request = post("/api/appointments/onsite/bookings")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new OnsiteAppointmentBookingRequest(
                        specialtyId, workDate, shiftId, doctorId, symptoms)));
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
