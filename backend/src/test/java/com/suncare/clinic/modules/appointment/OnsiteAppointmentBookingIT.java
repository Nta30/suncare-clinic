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

@SpringBootTest(properties = {"suncare.billing.facility-name=P20 Test Clinic", "suncare.billing.facility-address=P20 fictional address"})
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
    @MockitoSpyBean private com.suncare.clinic.modules.encounter.repository.EncounterClinicalRepository clinicalRepository;
    @MockitoSpyBean private com.suncare.clinic.modules.billing.repository.InvoiceRepository invoiceRepository;
    @MockitoSpyBean private com.suncare.clinic.modules.billing.repository.CashPaymentRepository cashPaymentRepository;
    @MockitoSpyBean private com.suncare.clinic.modules.encounter.repository.SymptomRepository symptomRepository;
    @MockitoSpyBean private com.suncare.clinic.modules.prescription.repository.PrescriptionRepository prescriptionRepository;

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
    private long diagnosisConceptId;
    private String patientOneToken;
    private String patientTwoToken;
    private String doctorToken;
    private String doctorTwoToken;

    @BeforeEach
    void setUp() throws Exception {
        reset(bookingRepository);
        reset(encounterRepository);
        reset(historyRepository);
        reset(clinicalRepository);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        prefix = "P16" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        diagnosisConceptId = jdbc.queryForObject("INSERT INTO dbo.DiagnosisConcept (code_system,code_version,code,name,is_active,is_selectable,primary_allowed) OUTPUT INSERTED.id VALUES ('TEST','test',?,N'Test diagnosis',1,1,1)", Long.class, prefix);

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

        Integer activeShift = jdbc.query("""
                SELECT TOP 1 id FROM dbo.ShiftDefinition
                WHERE code = 'MORNING' AND is_active = 1 ORDER BY id
                """, (rs, n) -> rs.getInt(1)).stream().findFirst().orElse(null);
        if (activeShift == null) {
            Integer anyMorning = jdbc.query("""
                    SELECT TOP 1 id FROM dbo.ShiftDefinition WHERE code = 'MORNING'
                    """, (rs, n) -> rs.getInt(1)).stream().findFirst().orElse(null);
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
        reset(clinicalRepository);
        reset(invoiceRepository);
        reset(cashPaymentRepository);
        reset(symptomRepository);
        reset(prescriptionRepository);
        if (prefix == null) return;

        jdbc.update("DELETE FROM dbo.AuditEvent WHERE action='ENCOUNTER_COMPLETED' AND actor_id IN (?,?)", accountIdForStaff(doctorOneId), accountIdForStaff(doctorTwoId));
        jdbc.update("DELETE FROM dbo.ClinicalOrder WHERE code=?", prefix + "CLS");

        jdbc.update("DELETE FROM dbo.CommandReceipt WHERE resource_type='Prescription' AND account_id IN (?,?)", accountIdForStaff(doctorOneId),accountIdForStaff(doctorTwoId));
        jdbc.update("DELETE FROM dbo.AuditEvent WHERE entity_type='Prescription' AND actor_id IN (?,?)",accountIdForStaff(doctorOneId),accountIdForStaff(doctorTwoId));
        jdbc.update("DELETE FROM dbo.PrescriptionItem WHERE prescription_id IN (SELECT p.id FROM dbo.Prescription p JOIN dbo.Encounter e ON e.id=p.encounter_id JOIN dbo.Appointment a ON a.id=e.appointment_id WHERE a.patient_id IN (?,?))",patientOneId,patientTwoId);
        jdbc.update("DELETE p FROM dbo.Prescription p JOIN dbo.Encounter e ON e.id=p.encounter_id JOIN dbo.Appointment a ON a.id=e.appointment_id WHERE a.patient_id IN (?,?)",patientOneId,patientTwoId);
        jdbc.update("DELETE FROM dbo.Drug WHERE code LIKE ?",prefix+"RX%");

        jdbc.update("DELETE FROM dbo.EncounterSymptom WHERE encounter_id IN (SELECT e.id FROM dbo.Encounter e JOIN dbo.Appointment a ON a.id=e.appointment_id WHERE a.patient_id IN (?,?))", patientOneId, patientTwoId);
        jdbc.update("DELETE FROM dbo.Symptom WHERE name LIKE ?", "%" + prefix + "%");

        jdbc.update("DELETE FROM dbo.AuditEvent WHERE entity_type='Payment' AND entity_id IN (SELECT p.id FROM dbo.Payment p JOIN dbo.Invoice i ON i.id=p.invoice_id JOIN dbo.Encounter e ON e.id=i.encounter_id JOIN dbo.Appointment a ON a.id=e.appointment_id WHERE a.patient_id IN (?,?))", patientOneId, patientTwoId);
        jdbc.update("DELETE FROM dbo.PaymentAllocation WHERE payment_id IN (SELECT p.id FROM dbo.Payment p JOIN dbo.Invoice i ON i.id=p.invoice_id JOIN dbo.Encounter e ON e.id=i.encounter_id JOIN dbo.Appointment a ON a.id=e.appointment_id WHERE a.patient_id IN (?,?))", patientOneId, patientTwoId);
        jdbc.update("DELETE p FROM dbo.Payment p JOIN dbo.Invoice i ON i.id=p.invoice_id JOIN dbo.Encounter e ON e.id=i.encounter_id JOIN dbo.Appointment a ON a.id=e.appointment_id WHERE a.patient_id IN (?,?)", patientOneId, patientTwoId);
        jdbc.update("DELETE FROM dbo.CommandReceipt WHERE command_type IN ('ONSITE_EXAM_INVOICE_CREATE','ONSITE_EXAM_INVOICE_ISSUE') AND account_id IN (?,?)", accountIdForStaff(doctorOneId), accountIdForStaff(doctorTwoId));
        jdbc.update("DELETE FROM dbo.AuditEvent WHERE entity_type='Invoice' AND actor_id IN (?,?)", accountIdForStaff(doctorOneId), accountIdForStaff(doctorTwoId));
        jdbc.update("DELETE FROM dbo.InvoiceLine WHERE invoice_id IN (SELECT id FROM dbo.Invoice WHERE encounter_id IN (SELECT e.id FROM dbo.Encounter e JOIN dbo.Appointment a ON a.id=e.appointment_id WHERE a.patient_id IN (?,?)))", patientOneId,patientTwoId);
        jdbc.update("DELETE FROM dbo.Invoice WHERE encounter_id IN (SELECT e.id FROM dbo.Encounter e JOIN dbo.Appointment a ON a.id=e.appointment_id WHERE a.patient_id IN (?,?))", patientOneId,patientTwoId);

        jdbc.update("DELETE FROM dbo.CommandReceipt WHERE command_type IN ('ENCOUNTER_VITAL_SIGN','ENCOUNTER_DIAGNOSIS_CREATE') AND account_id IN (?, ?)", accountIdForStaff(doctorOneId), accountIdForStaff(doctorTwoId));
        jdbc.update("DELETE FROM dbo.AuditEvent WHERE action IN ('ENCOUNTER_NOTES_UPDATED','VITAL_SIGN_RECORDED','DIAGNOSIS_CREATED','DIAGNOSIS_UPDATED') AND actor_id IN (?, ?)", accountIdForStaff(doctorOneId), accountIdForStaff(doctorTwoId));
        jdbc.update("DELETE FROM dbo.VitalSign WHERE encounter_id IN (SELECT e.id FROM dbo.Encounter e JOIN dbo.Appointment a ON a.id=e.appointment_id WHERE a.patient_id IN (?, ?))", patientOneId, patientTwoId);
        jdbc.update("DELETE FROM dbo.Diagnosis WHERE encounter_id IN (SELECT e.id FROM dbo.Encounter e JOIN dbo.Appointment a ON a.id=e.appointment_id WHERE a.patient_id IN (?, ?))", patientOneId, patientTwoId);
        jdbc.update("DELETE FROM dbo.DiagnosisConcept WHERE id=?", diagnosisConceptId);

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
    void symptomSeedSearchesVietnameseAndUnaccentedTerms() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Symptom", Integer.class) >= 250);
        assertEquals(400, jdbc.queryForObject("SELECT max_length FROM sys.columns WHERE object_id=OBJECT_ID('dbo.Symptom') AND name='normalized_name'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT normalized_name FROM dbo.Symptom GROUP BY normalized_name HAVING COUNT(*)>1) duplicates", Integer.class));
        for (String query : java.util.List.of("ho", "sot", "đau", "dau bung")) {
            var response = json.readTree(mvc.perform(get("/api/doctor/symptoms").param("search", query)
                    .header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertTrue(response.size() > 0 && response.size() <= 20, query);
            String expected = switch (query) { case "ho" -> "Ho"; case "sot" -> "Sốt"; case "dau bung" -> "Đau bụng"; default -> null; };
            if (expected != null) assertEquals(expected, response.get(0).get("name").asText(), query);
            for (var symptom : response) {
                String normalized = java.text.Normalizer.normalize(symptom.get("name").asText(), java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).replace('đ', 'd');
                assertTrue(normalized.contains(query.replace('đ', 'd')), query);
            }
        }
        String accented = mvc.perform(get("/api/doctor/symptoms").param("search", "ĐAU BỤNG").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String plain = mvc.perform(get("/api/doctor/symptoms").param("search", "dau bung").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(plain, accented);
    }

    @Test
    void symptomSeedReplayPreservesExistingRowsAndSupportsFullLengthIndexKeys() {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.support.JdbcTransactionManager(jdbc.getDataSource()));
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Symptom", Integer.class);
        transaction.executeWithoutResult(status -> {
            var original = jdbc.queryForMap("SELECT id,name,created_at FROM dbo.Symptom WHERE name=N'Ho'");
            jdbc.update("UPDATE dbo.Symptom SET is_active=0 WHERE id=?", original.get("id"));
            String longName = prefix + "đ".repeat(200 - prefix.length());
            jdbc.update("INSERT dbo.Symptom(name) VALUES (?)", longName);
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                        new org.springframework.core.io.ClassPathResource("db/migration/V20__seed_outpatient_symptom_catalog.sql"));
                return null;
            });
            assertEquals(before + 1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Symptom", Integer.class));
            assertEquals(original, jdbc.queryForMap("SELECT id,name,created_at FROM dbo.Symptom WHERE id=?", original.get("id")));
            assertFalse(jdbc.queryForObject("SELECT is_active FROM dbo.Symptom WHERE id=?", Boolean.class, original.get("id")));
            assertEquals(400, jdbc.queryForObject("SELECT DATALENGTH(normalized_name) FROM dbo.Symptom WHERE name=?", Integer.class, longName));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT dbo.Symptom(name) VALUES (?)", longName.replace('đ', 'd')));
            status.setRollbackOnly();
        });
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Symptom", Integer.class));
    }

    @Test
    void anamnesisSearchPersistsStructuredAndFreeSymptomsWithoutChangingExamination() throws Exception {
        long encounter = startedEncounter("anamnesis");
        String path = "/api/doctor/encounters/" + encounter + "/clinical";
        String name = "Đau đầu " + prefix;
        long symptomId = jdbc.queryForObject("INSERT dbo.Symptom(name) OUTPUT INSERTED.id VALUES (?)", Long.class, name);
        mvc.perform(get("/api/doctor/symptoms").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        for (String query : java.util.List.of("đau đầu " + prefix, "DAU DAU " + prefix))
            mvc.perform(get("/api/doctor/symptoms").param("search", query).header("Authorization", "Bearer " + doctorToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].symptomId").value(symptomId));
        mvc.perform(get("/api/doctor/symptoms").param("search", "%").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        var before = json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken)).andReturn().getResponse().getContentAsString());
        var body = new java.util.HashMap<String, Object>();
        body.put("version", before.get("notes").get("version").asText());
        body.put("chiefComplaint", "Lý do chính");
        body.put("examinationNote", "Kết quả khám thực thể riêng");
        body.put("historyOfPresentIllness", "Khởi phát từ hôm qua; diễn biến và điều trị trước đó.");
        body.put("symptoms", java.util.List.of(java.util.Map.of("symptomId", symptomId, "name", "Client không được đổi tên danh mục"), java.util.Map.of("name", "  Tự nhập   tại lượt khám  ")));
        var saved = json.readTree(mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.historyOfPresentIllness").value(body.get("historyOfPresentIllness")))
                .andExpect(jsonPath("$.notes.examinationNote").value("Kết quả khám thực thể riêng"))
                .andExpect(jsonPath("$.notes.symptoms[0].name").value(name))
                .andExpect(jsonPath("$.notes.symptoms[1].symptomId").isEmpty())
                .andExpect(jsonPath("$.notes.symptoms[1].name").value("Tự nhập tại lượt khám"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Symptom WHERE name LIKE ?", Integer.class, "%" + prefix + "%"));
        mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.symptoms.length()").value(2));
        // An older client omitting the new fields must not erase them.
        var legacy = java.util.Map.of("chiefComplaint", "Lý do đã cập nhật", "examinationNote", "Kết quả khám thực thể riêng", "version", saved.get("notes").get("version").asText());
        var preserved = json.readTree(mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(legacy))).andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.symptoms.length()").value(2)).andExpect(jsonPath("$.notes.historyOfPresentIllness").value(body.get("historyOfPresentIllness")))
                .andReturn().getResponse().getContentAsString());
        jdbc.update("UPDATE dbo.Symptom SET name=?,is_active=0 WHERE id=?", "Đã đổi tên " + prefix, symptomId);
        body.put("version", preserved.get("notes").get("version").asText());
        mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes.symptoms[0].name").value(name));
        body.put("version", json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken)).andReturn().getResponse().getContentAsString()).get("notes").get("version").asText());
        body.put("symptoms", java.util.List.of());
        body.put("historyOfPresentIllness", "");
        mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes.symptoms.length()").value(0)).andExpect(jsonPath("$.notes.historyOfPresentIllness").isEmpty());
    }

    @Test
    void anamnesisRejectsDuplicateBlankInvalidAndUnauthorizedChanges() throws Exception {
        long encounter = startedEncounter("anamnesis-validation");
        String path = "/api/doctor/encounters/" + encounter + "/clinical";
        var data = json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken)).andReturn().getResponse().getContentAsString());
        var body = new java.util.HashMap<String, Object>();
        body.put("version", data.get("notes").get("version").asText());
        body.put("historyOfPresentIllness", "Không được lưu khi triệu chứng không hợp lệ");
        for (var selections : java.util.List.of(
                java.util.List.of(java.util.Map.of("name", "  ")),
                java.util.List.of(java.util.Map.of("name", "Đau  đầu"), java.util.Map.of("name", "DAU ĐẦU")),
                java.util.List.of(java.util.Map.of("name", "Đau\u00a0đầu"), java.util.Map.of("name", "dau dau")),
                java.util.List.of(java.util.Map.of("name", "Không có mã này", "symptomId", Long.MAX_VALUE)))) {
            body.put("symptoms", selections);
            mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        body.put("symptoms", java.util.List.of(java.util.Map.of("name", "Hợp lệ nhưng sai quyền")));
        mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + patientOneToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + doctorTwoToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/doctor/symptoms").param("search", "đau")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/doctor/symptoms").param("search", "đau").header("Authorization", "Bearer " + patientOneToken)).andExpect(status().isForbidden());
        jdbc.update("UPDATE dbo.Encounter SET status='COMPLETED',completed_at=SYSUTCDATETIME() WHERE id=?", encounter);
        mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterSymptom WHERE encounter_id=?", Integer.class, encounter));
        assertEquals(null, jdbc.queryForObject("SELECT history_of_present_illness FROM dbo.Encounter WHERE id=?", String.class, encounter));
    }

    @Test
    void anamnesisRollsBackAndSerializesConcurrentVersionedReplacement() throws Exception {
        long encounter = startedEncounter("anamnesis-rollback");
        String path = "/api/doctor/encounters/" + encounter + "/clinical";
        var data = json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken)).andReturn().getResponse().getContentAsString());
        String version = data.get("notes").get("version").asText();
        var body = java.util.Map.of("version", version, "examinationNote", "Khám thực thể không ghi đè", "historyOfPresentIllness", "Nhánh kiểm tra rollback", "symptoms", java.util.List.of(java.util.Map.of("name", "Triệu chứng rollback")));
        doThrow(new IllegalStateException("Anamnesis audit rollback probe")).when(clinicalRepository).audit(anyLong(), anyLong(), org.mockito.ArgumentMatchers.eq("ENCOUNTER_NOTES_UPDATED"), anyLong());
        assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterSymptom WHERE encounter_id=?", Integer.class, encounter));
        assertEquals(null, jdbc.queryForObject("SELECT history_of_present_illness FROM dbo.Encounter WHERE id=?", String.class, encounter));
        reset(clinicalRepository);
        var sessions = java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        org.mockito.Mockito.doAnswer(call -> { sessions.add(jdbc.queryForObject("SELECT @@SPID", Integer.class)); barrier.await(10, java.util.concurrent.TimeUnit.SECONDS); return call.callRealMethod(); })
                .when(encounterRepository).lockAppointment(anyLong(), anyLong());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var requests = new java.util.ArrayList<Future<Integer>>();
            for (int index = 0; index < 2; index++) {
                final int branch = index;
                requests.add(executor.submit(() -> {
                    var request = java.util.Map.of("version", version, "examinationNote", "Khám thực thể không ghi đè", "historyOfPresentIllness", "Nhánh " + branch, "symptoms", java.util.List.of(java.util.Map.of("name", "Triệu chứng nhánh " + branch)));
                    return mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andReturn().getResponse().getStatus();
                }));
            }
            var codes = new java.util.ArrayList<>(java.util.List.of(requests.get(0).get(25, java.util.concurrent.TimeUnit.SECONDS), requests.get(1).get(25, java.util.concurrent.TimeUnit.SECONDS)));
            codes.sort(Integer::compareTo);
            assertEquals(java.util.List.of(200, 409), codes);
            assertEquals(2, sessions.size());
        } finally { executor.shutdownNow(); reset(encounterRepository); }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterSymptom WHERE encounter_id=?", Integer.class, encounter));
        assertEquals("Khám thực thể không ghi đè", jdbc.queryForObject("SELECT examination_note FROM dbo.Encounter WHERE id=?", String.class, encounter));
        String history = jdbc.queryForObject("SELECT history_of_present_illness FROM dbo.Encounter WHERE id=?", String.class, encounter);
        String symptom = jdbc.queryForObject("SELECT name_snapshot FROM dbo.EncounterSymptom WHERE encounter_id=?", String.class, encounter);
        assertEquals(history.substring(history.length() - 1), symptom.substring(symptom.length() - 1));
    }

    @Test
    void recordsClinicalVitalsNotesAndDiagnosisWithRetryAndVersionProtection() throws Exception {
        long encounterId = startedEncounter("p19-basic");
        String path = "/api/doctor/encounters/" + encounterId + "/clinical";
        String vital = "{\"measuredAt\":\"2026-11-25T10:05:00+07:00\",\"temperatureC\":36.75,\"pulseBpm\":75,\"spo2Percent\":98.5}";
        String clinicalBody = mvc.perform(post(path + "/vitals").header("Authorization", "Bearer " + doctorToken)
                .header("Idempotency-Key", prefix + "VITAL").contentType(MediaType.APPLICATION_JSON).content(vital))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vitals.length()").value(1))
                .andExpect(jsonPath("$.vitals[0].measuredAt").value("2026-11-25T03:05:00Z"))
                .andExpect(jsonPath("$.replayed").value(false)).andReturn().getResponse().getContentAsString();
        mvc.perform(post(path + "/vitals").header("Authorization", "Bearer " + doctorToken)
                .header("Idempotency-Key", prefix + "VITAL").contentType(MediaType.APPLICATION_JSON).content(vital))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true)).andExpect(jsonPath("$.vitals.length()").value(1));
        mvc.perform(post(path + "/vitals").header("Authorization", "Bearer " + doctorToken)
                .header("Idempotency-Key", prefix + "VITAL").contentType(MediaType.APPLICATION_JSON).content(vital.replace("36.75", "37.5")))
                .andExpect(status().isConflict());
        String version = json.readTree(clinicalBody).get("notes").get("version").asText();
        String notes = json.writeValueAsString(java.util.Map.of("chiefComplaint", " P19 test complaint ", "examinationNote", "P19 test note", "version", version));
        mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON).content(notes))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes.chiefComplaint").value("P19 test complaint"));
        mvc.perform(put(path + "/notes").header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON).content(notes))
                .andExpect(status().isConflict());
        String preliminary = "{\"description\":\"P19 test diagnosis\",\"diagnosisType\":\"PRELIMINARY\",\"isPrimary\":true}";
        String diagnosisBody = mvc.perform(post(path + "/diagnoses").header("Authorization", "Bearer " + doctorToken)
                .header("Idempotency-Key", prefix + "DIAG").contentType(MediaType.APPLICATION_JSON).content(preliminary))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post(path + "/diagnoses").header("Authorization", "Bearer " + doctorToken)
                .header("Idempotency-Key", prefix + "DIAG").contentType(MediaType.APPLICATION_JSON).content(preliminary))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diagnoses.length()").value(1)).andExpect(jsonPath("$.replayed").value(true));
        var diagnosis = json.readTree(diagnosisBody).get("diagnoses").get(0);
        String finalBody = json.writeValueAsString(java.util.Map.of("diagnosisConceptId", diagnosisConceptId, "description", "P19 final diagnosis", "diagnosisType", "FINAL", "isPrimary", true, "version", diagnosis.get("version").asText()));
        mvc.perform(put(path + "/diagnoses/" + diagnosis.get("id").asLong()).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(finalBody)).andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnoses[0].diagnosisType").value("FINAL"));
        mvc.perform(put(path + "/diagnoses/" + diagnosis.get("id").asLong()).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(finalBody)).andExpect(status().isConflict());
        assertEquals("IN_PROGRESS", jdbc.queryForObject("SELECT status FROM dbo.Encounter WHERE id=?", String.class, encounterId));
    }

    @Test
    void clinicalEndpointsValidateInputStateAndDoctorOwnership() throws Exception {
        long encounterId = bookEncounterForStart("p19-guard");
        String path = "/api/doctor/encounters/" + encounterId + "/clinical";
        String vital = "{\"measuredAt\":\"2026-11-25T10:05:00+07:00\",\"pulseBpm\":75}";
        mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(false));
        mvc.perform(post(path + "/vitals").header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "GUARD")
                .contentType(MediaType.APPLICATION_JSON).content(vital)).andExpect(status().isConflict());
        mvc.perform(startRequest(encounterId).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + doctorTwoToken)).andExpect(status().isNotFound());
        mvc.perform(get(path).header("Authorization", "Bearer " + patientOneToken)).andExpect(status().isForbidden());
        mvc.perform(post(path + "/vitals").header("Authorization", "Bearer " + doctorTwoToken).header("Idempotency-Key", prefix + "OTHER")
                .contentType(MediaType.APPLICATION_JSON).content(vital)).andExpect(status().isNotFound());
        for (String body : java.util.List.of("{}", "{\"measuredAt\":\"2026-11-25T10:05:00Z\"}",
                "{\"measuredAt\":\"2026-11-25T10:05:00Z\",\"spo2Percent\":101}",
                "{\"measuredAt\":\"2026-11-25T10:05:00Z\",\"temperatureC\":36.555}",
                "{\"measuredAt\":\"2026-11-25T10:05:00Z\",\"pulseBpm\":75.5}",
                "{\"measuredAt\":\"2026-11-25T10:05:00Z\",\"pulseBpm\":0}")) {
            mvc.perform(post(path + "/vitals").header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "BAD")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(path + "/vitals").header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(vital)).andExpect(status().isBadRequest());
        mvc.perform(post(path + "/diagnoses").header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "PAIR")
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"P19 test\",\"diagnosisType\":\"FINAL\",\"isPrimary\":false,\"codeSystem\":\"ICD-10\"}"))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE dbo.Encounter SET status='COMPLETED', completed_at=SYSUTCDATETIME() WHERE id=?", encounterId);
        mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(false));
        mvc.perform(post(path + "/vitals").header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "CLOSED")
                .contentType(MediaType.APPLICATION_JSON).content(vital)).andExpect(status().isConflict());
    }

    @Test
    void clinicalWriteRollsBackVitalAndReceiptWhenAuditFails() throws Exception {
        long encounterId = startedEncounter("p19-rollback");
        doThrow(new IllegalStateException("P19 audit rollback probe")).when(clinicalRepository).audit(anyLong(), anyLong(), anyString(), anyLong());
        assertThrows(Exception.class, () -> mvc.perform(post("/api/doctor/encounters/" + encounterId + "/clinical/vitals")
                .header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "ROLLBACK")
                .contentType(MediaType.APPLICATION_JSON).content("{\"measuredAt\":\"2026-11-25T10:05:00Z\",\"weightKg\":60.5}")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.VitalSign WHERE encounter_id=?", Integer.class, encounterId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.CommandReceipt WHERE command_type='ENCOUNTER_VITAL_SIGN' AND idempotency_key=?", Integer.class, prefix + "ROLLBACK"));
    }

    @Test
    void concurrentPrimaryFinalDiagnosesAllowOneAndRejectCrossEncounterUpdate() throws Exception {
        long encounterId = startedEncounter("p19-primary");
        String path = "/api/doctor/encounters/" + encounterId + "/clinical/diagnoses";
        String diagnosis = json.writeValueAsString(java.util.Map.of("diagnosisConceptId", diagnosisConceptId, "description", "P19 final test", "diagnosisType", "FINAL", "isPrimary", true));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.List<Future<Integer>> requests = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final String key = prefix + "FINAL" + i;
                requests.add(executor.submit(() -> { start.await(); return mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(diagnosis)).andReturn().getResponse().getStatus(); }));
            }
            start.countDown();
            var results = new java.util.ArrayList<>(java.util.List.of(requests.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS), requests.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS)));
            java.util.Collections.sort(results);
            assertEquals(java.util.List.of(200, 409), results);
        } finally { executor.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Diagnosis WHERE encounter_id=? AND diagnosis_type='FINAL' AND is_primary=1", Integer.class, encounterId));
        String created = mvc.perform(get("/api/doctor/encounters/" + encounterId + "/clinical").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var item = json.readTree(created).get("diagnoses").get(0);
        String secondBooking = mvc.perform(book(patientTwoToken, prefix + "P19SECOND", doctorOneId)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long secondId = json.readTree(secondBooking).get("encounterId").asLong();
        mvc.perform(startRequest(secondId).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk());
        mvc.perform(put("/api/doctor/encounters/" + secondId + "/clinical/diagnoses/" + item.get("id").asLong())
                .header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("diagnosisConceptId", diagnosisConceptId, "description", "P19 cross attempt", "diagnosisType", "FINAL", "isPrimary", true, "version", item.get("version").asText()))))
                .andExpect(status().isConflict());
        assertEquals("P19 final test", jdbc.queryForObject("SELECT description FROM dbo.Diagnosis WHERE id=?", String.class, item.get("id").asLong()));
    }

    @Test
    void catalogControlsFinalCodesAndPreservesSnapshots() throws Exception {
        long id = startedEncounter("catalog");
        String path = "/api/doctor/encounters/" + id + "/clinical/diagnoses";
        String body = json.writeValueAsString(java.util.Map.of("diagnosisConceptId", diagnosisConceptId, "description", "Clinical detail", "diagnosisType", "FINAL", "isPrimary", true));
        mvc.perform(get("/api/doctor/diagnosis-concepts").param("search", prefix).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(diagnosisConceptId));
        mvc.perform(get("/api/doctor/diagnosis-concepts").param("search", prefix).header("Authorization", "Bearer " + patientOneToken)).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "NOCODE")
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Final\",\"diagnosisType\":\"FINAL\",\"isPrimary\":true}"))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE dbo.DiagnosisConcept SET is_active=0 WHERE id=?", diagnosisConceptId);
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "INACTIVE").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        jdbc.update("UPDATE dbo.DiagnosisConcept SET is_active=1,is_selectable=0 WHERE id=?", diagnosisConceptId);
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "CATEGORY").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        jdbc.update("UPDATE dbo.DiagnosisConcept SET is_selectable=1,primary_allowed=0 WHERE id=?", diagnosisConceptId);
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "SECONDARY").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        jdbc.update("UPDATE dbo.DiagnosisConcept SET primary_allowed=1 WHERE id=?", diagnosisConceptId);
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "VALID").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diagnoses[0].diagnosisCode").value(prefix))
                .andExpect(jsonPath("$.diagnoses[0].codeSystem").value("TEST")).andExpect(jsonPath("$.diagnoses[0].codeVersion").value("test"))
                .andExpect(jsonPath("$.diagnoses[0].diagnosisName").value("Test diagnosis"));
        jdbc.update("UPDATE dbo.DiagnosisConcept SET name='Changed catalog name' WHERE id=?", diagnosisConceptId);
        assertEquals("Test diagnosis", jdbc.queryForObject("SELECT diagnosis_name_snapshot FROM dbo.Diagnosis WHERE encounter_id=?", String.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Diagnosis WHERE encounter_id=?", Integer.class, id));
    }

    @Test
    void previousVisitsArePaginatedSamePatientCompletedAndReadOnlyAcrossDoctors() throws Exception {
        long contextId = bookEncounterForStart("history-context");
        long previousId = 0;
        for (int i = 0; i < 12; i++) previousId = previousEncounter(patientOneId, doctorTwoId, "HIST" + i, "COMPLETED");
        long wrongPatientId = previousEncounter(patientTwoId, doctorTwoId, "PRIVATE", "COMPLETED");
        long unfinishedId = previousEncounter(patientOneId, doctorTwoId, "UNFINISHED", "IN_PROGRESS");
        String path = "/api/doctor/encounters/" + contextId + "/history";
        mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12)).andExpect(jsonPath("$.items.length()").value(10)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(path).param("page", "2").header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get(path + "/" + previousId).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.encounter.encounterId").value(previousId)).andExpect(jsonPath("$.clinical.editable").value(false))
                .andExpect(jsonPath("$.prescriptions.length()").value(0)).andExpect(jsonPath("$.results.length()").value(0));
        for (long excluded : new long[]{contextId, wrongPatientId, unfinishedId}) {
            mvc.perform(get(path + "/" + excluded).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isNotFound());
        }
        mvc.perform(get(path).header("Authorization", "Bearer " + doctorTwoToken)).andExpect(status().isNotFound());
        mvc.perform(get(path).header("Authorization", "Bearer " + patientOneToken)).andExpect(status().isForbidden());
        mvc.perform(get(path).param("page", "0").header("Authorization", "Bearer " + doctorToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/doctor/encounters/" + previousId + "/clinical").header("Authorization", "Bearer " + doctorToken)).andExpect(status().isNotFound());
    }

    @Test
    void historyReturnsIssuedPrescriptionAndLatestPublishedResultWithoutDrafts() throws Exception {
        long contextId = bookEncounterForStart("published-history");
        var transaction = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.support.JdbcTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(tx -> {
            try {
                long previousId = previousEncounter(patientOneId, doctorTwoId, "PUBLISHED", "COMPLETED");
                long actorId = accountIdForStaff(doctorTwoId);
                long rxId = jdbc.queryForObject("INSERT dbo.Prescription(code,encounter_id,status,prescribed_by,issued_at,instruction) OUTPUT inserted.id VALUES (?,?,'ISSUED',?,SYSUTCDATETIME(),'Published instruction')",Long.class,prefix+"RX",previousId,actorId);
                jdbc.update("INSERT dbo.Prescription(code,encounter_id,status,prescribed_by,instruction) VALUES (?,?,'DRAFT',?,'PRIVATE DRAFT')",prefix+"RXD",previousId,actorId);
                long drugId = jdbc.queryForObject("INSERT dbo.Drug(code,name,unit,is_active) OUTPUT inserted.id VALUES (?,'Current name','tablet',1)",Long.class,prefix+"DRUG");
                jdbc.update("INSERT dbo.PrescriptionItem(prescription_id,line_no,drug_id,drug_name,strength,unit,dose,route,frequency,duration_days,quantity) VALUES (?,1,?,'Issued snapshot','100 mg','tablet','1 tablet','oral','daily',3,3)",rxId,drugId);
                long orderId = jdbc.queryForObject("INSERT dbo.ClinicalOrder(code,encounter_id,service_id,performing_department_id,ordered_by,indication,status) OUTPUT inserted.id VALUES (?,?,?,?,?,'History test','PENDING_PAYMENT')",Long.class,prefix+"ORDER",previousId,serviceId,departmentId,actorId);
                for (int version = 1; version <= 2; version++) jdbc.update("INSERT dbo.ResultVersion(order_id,version_no,status,description,conclusion,authored_by,confirmed_by,confirmed_at,published_by,published_at) VALUES (?,?,'PUBLISHED',?,'Published conclusion',?,?,SYSUTCDATETIME(),?,SYSUTCDATETIME())",orderId,version,"Published "+version,actorId,actorId,actorId);
                jdbc.update("INSERT dbo.ResultVersion(order_id,version_no,status,description,authored_by) VALUES (?,3,'DRAFT','PRIVATE RESULT DRAFT',?)",orderId,actorId);
                long resultId = jdbc.queryForObject("SELECT id FROM dbo.ResultVersion WHERE order_id=? AND version_no=2",Long.class,orderId);
                jdbc.update("INSERT dbo.ResultValue(result_version_id,line_no,analyte_name,numeric_value,unit,sort_order) VALUES (?,1,'Published analyte',12.5,'unit',1)",resultId);
                mvc.perform(get("/api/doctor/encounters/"+contextId+"/history/"+previousId).header("Authorization","Bearer "+doctorToken)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.prescriptions.length()").value(1)).andExpect(jsonPath("$.prescriptions[0].items[0].drugName").value("Issued snapshot"))
                        .andExpect(jsonPath("$.results.length()").value(1)).andExpect(jsonPath("$.results[0].versionNo").value(2))
                        .andExpect(jsonPath("$.results[0].values[0].numericValue").value(12.5));
            } catch (Exception ex) { throw new RuntimeException(ex); }
            finally { tx.setRollbackOnly(); }
        });
    }

    @Test
    void clinicalDetailRetainsCatalogPrimaryRestrictionWhenEditingSecondaryDiagnosis() throws Exception {
        long id = startedEncounter("secondary-edit");
        jdbc.update("UPDATE dbo.DiagnosisConcept SET primary_allowed=0 WHERE id=?", diagnosisConceptId);
        String body = json.writeValueAsString(java.util.Map.of("diagnosisConceptId", diagnosisConceptId,
                "description", "Secondary diagnosis", "diagnosisType", "FINAL", "isPrimary", false));
        mvc.perform(post("/api/doctor/encounters/" + id + "/clinical/diagnoses")
                .header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "SECONDARYEDIT")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diagnoses[0].primaryAllowed").value(false));
        mvc.perform(get("/api/doctor/encounters/" + id + "/clinical").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diagnoses[0].primaryAllowed").value(false));
    }

    @Test
    void diagnosisLookupRanksNamesSupportsEnglishCompactCodesGroupsAndRealPagination() throws Exception {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.support.JdbcTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(tx -> {
            try {
                jdbc.update("INSERT dbo.DiagnosisConcept(code_system,code_version,code,name,english_name,is_active,is_selectable,primary_allowed) VALUES ('TEST',?,'Z99',?,N'Test group',1,0,1)", prefix, prefix + " Group");
                jdbc.update("INSERT dbo.DiagnosisConcept(code_system,code_version,code,name,english_name,is_active,is_selectable,primary_allowed) VALUES ('TEST',?,'Z99.Z9',?,?,1,1,0)", prefix, prefix + " Răng", prefix + " Dental bleeding example");
                jdbc.update("INSERT dbo.DiagnosisConcept(code_system,code_version,code,name,english_name,is_active,is_selectable,primary_allowed) VALUES ('TEST',?,'Z99.Z8',?,N'Test',1,1,1)", prefix, prefix + " Răng extra");
                jdbc.update("INSERT dbo.DiagnosisConcept(code_system,code_version,code,name,is_active,is_selectable,primary_allowed) VALUES ('TEST',?,'Z99.Z7',?,1,1,1)", prefix, prefix + " Trắng");
                for (int i = 0; i < 20; i++) jdbc.update("INSERT dbo.DiagnosisConcept(code_system,code_version,code,name,is_active,is_selectable,primary_allowed) VALUES ('TEST',?,?,?,1,1,1)", prefix, "Y00.X" + i, prefix + " Răng variant " + i);
                String path = "/api/doctor/diagnosis-concepts/search";
                mvc.perform(get(path).param("search", prefix + " rang").header("Authorization", "Bearer " + doctorToken))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(22))
                        .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items.length()").value(20))
                        .andExpect(jsonPath("$.items[0].code").value("Z99.Z9"))
                        .andExpect(jsonPath("$.items[0].categoryName").value(prefix + " Group"));
                mvc.perform(get(path).param("search", prefix + " rang").param("page", "2").header("Authorization", "Bearer " + doctorToken))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
                mvc.perform(get(path).param("search", prefix + " bleeding").header("Authorization", "Bearer " + doctorToken))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.items[0].englishName").value(prefix + " Dental bleeding example"));
                mvc.perform(get(path).param("search", "z99z9").header("Authorization", "Bearer " + doctorToken))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].code").value("Z99.Z9"));
                mvc.perform(get(path).param("search", prefix).param("groups", "true").header("Authorization", "Bearer " + doctorToken))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.items[0].code").value("Z99"));
                mvc.perform(get(path).param("search", prefix + "%").header("Authorization", "Bearer " + doctorToken))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
                mvc.perform(get(path).header("Authorization", "Bearer " + patientOneToken)).andExpect(status().isForbidden());
                mvc.perform(get(path)).andExpect(status().isUnauthorized());
                mvc.perform(get(path).param("page", "0").header("Authorization", "Bearer " + doctorToken)).andExpect(status().isBadRequest());
            } catch (Exception ex) { throw new RuntimeException(ex); }
            finally { tx.setRollbackOnly(); }
        });
    }

    private String cashierToken() {
        long accountId = accountIdForStaff(doctorTwoId);
        jdbc.update("""
                INSERT dbo.AccountRole(account_id,role_id,granted_at,granted_by)
                SELECT ?,id,SYSUTCDATETIME(),NULL FROM dbo.Role WHERE code='CASHIER'
                """, accountId);
        return doctorTwoToken;
    }

    private long cashInvoice(boolean issue) throws Exception { return cashInvoiceFor(issue, "cash"); }

    private long cashInvoiceFor(boolean issue, String suffix) throws Exception {
        long encounterId = startedEncounter(suffix);
        String path = "/api/doctor/encounters/" + encounterId + "/invoices";
        var draft = json.readTree(mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken)
                .header("Idempotency-Key", prefix + suffix + "draft")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long id = draft.get("id").asLong();
        if (issue) mvc.perform(post(path + "/" + id + "/issue").header("Authorization", "Bearer " + doctorToken)
                .header("Idempotency-Key", prefix + suffix + "issue").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("version", draft.get("version").asText()))))
                .andExpect(status().isOk());
        return id;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder cashRequest(
            long invoiceId, String token, String key, String body) {
        var request = post("/api/cashier/invoices/" + invoiceId + "/cash-payments")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        return request;
    }

    @Test
    void cashLookupByExactCodeAndMoneyStringPreservesContract() throws Exception {
        long id = cashInvoice(true);
        String token = cashierToken();
        String code = jdbc.queryForObject("SELECT code FROM dbo.Invoice WHERE id=?", String.class, id);
        String path = "/api/cashier/invoices";
        mvc.perform(get(path).param("code", "  " + code + "  ").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].invoice.id").value(id)).andExpect(jsonPath("$[0].outstanding").value(500000));
        mvc.perform(get(path).param("code", code.substring(0, 10)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(path).param("code", "bad%code").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("code", code)).andExpect(status().isUnauthorized());
        for (String wrong : java.util.List.of(patientOneToken, doctorToken))
            mvc.perform(get(path).param("code", code).header("Authorization", "Bearer " + wrong)).andExpect(status().isForbidden());
        mvc.perform(cashRequest(id, token, prefix + "stringcash", "{\"amount\":\"500000\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(500000));
    }

    @Test
    void cashierWorkspaceSearchesPagesProtectsIdentityAndUpdatesStatistics() throws Exception {
        String token = cashierToken();
        String path = "/api/cashier/invoices/search";
        var baseline = json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("statistics");
        jdbc.update("UPDATE dbo.WorkAssignment SET capacity=30 WHERE id=?", assignmentOneId);
        String name = "Nguyễn Thị Ánh " + prefix;
        String identity = "999999999999";
        jdbc.update("UPDATE dbo.Patient SET full_name=?,phone='0999999999',identity_number=? WHERE id=?", name, identity, patientOneId);
        var ids = new java.util.ArrayList<Long>();
        for (int index = 0; index < 11; index++) ids.add(cashInvoiceFor(true, "workspace" + index));
        cashInvoiceFor(false, "workspace-draft");
        jdbc.update("UPDATE dbo.Invoice SET issued_at='2026-01-01T00:00:00' WHERE patient_id=? AND status='ISSUED'", patientOneId);
        String patientCode = jdbc.queryForObject("SELECT code FROM dbo.Patient WHERE id=?", String.class, patientOneId);
        for (String term : java.util.List.of(name, "nguyen thi anh " + prefix.toLowerCase(), identity, "0999999999", patientCode)) {
            var result = mvc.perform(get(path).param("search", term).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(11))
                    .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items.length()").value(10))
                    .andExpect(jsonPath("$.items[0].id").value(ids.getLast()))
                    .andExpect(jsonPath("$.items[0].issuedAt").value("2026-01-01T00:00:00Z"))
                    .andExpect(jsonPath("$.items[0].identityNumber").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            assertFalse(result.contains(identity));
        }
        mvc.perform(get(path).param("search", prefix).param("page", "2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(path).param("search", "%_").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        long id = ids.getFirst();
        String code = jdbc.queryForObject("SELECT code FROM dbo.Invoice WHERE id=?", String.class, id);
        mvc.perform(get(path).param("search", code).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(cashRequest(id, token, prefix + "workspace-full", "{\"amount\":500000}")).andExpect(status().isOk());
        mvc.perform(get(path).param("search", prefix).param("status", "PAID").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.statistics.pending").value(baseline.get("pending").asLong() + 10))
                .andExpect(jsonPath("$.statistics.paid").value(baseline.get("paid").asLong() + 1))
                .andExpect(jsonPath("$.statistics.receivedToday").value(new BigDecimal(baseline.get("receivedToday").asText()).add(new BigDecimal("500000")).toPlainString()));
        mvc.perform(get(path).param("search", prefix).param("status", "ALL").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(11));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String wrong : java.util.List.of(doctorToken, patientOneToken)) {
            mvc.perform(get(path).header("Authorization", "Bearer " + wrong)).andExpect(status().isForbidden());
            mvc.perform(get("/api/cashier/invoices/" + id + "/detail").header("Authorization", "Bearer " + wrong)).andExpect(status().isForbidden());
        }
        for (String[] invalid : java.util.List.of(new String[]{"status", "DRAFT"}, new String[]{"page", "0"},
                new String[]{"pageSize", "999"}, new String[]{"search", "x".repeat(101)}))
            mvc.perform(get(path).param(invalid[0], invalid[1]).header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    }

    @Test
    void cashierStatisticsUseVietnameseDayBoundaries() throws Exception {
        long id = cashInvoice(true);
        String token = cashierToken();
        String path = "/api/cashier/invoices/search";
        var baseline = json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString()).get("statistics").get("receivedToday").asText();
        mvc.perform(cashRequest(id, token, prefix + "day-full", "{\"amount\":500000}")).andExpect(status().isOk());
        var start = java.time.LocalDate.now(BUSINESS_ZONE).atStartOfDay(BUSINESS_ZONE).toInstant();
        for (int index = 0; index < 3; index++) {
            var point = index == 0 ? start.minusSeconds(1) : index == 1 ? start : start.plusSeconds(86400);
            jdbc.update("UPDATE dbo.Payment SET paid_at=? WHERE invoice_id=?", java.time.LocalDateTime.ofInstant(point, java.time.ZoneOffset.UTC), id);
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.statistics.receivedToday").value(new BigDecimal(baseline)
                            .add(index == 1 ? new BigDecimal("500000") : BigDecimal.ZERO).toPlainString()));
        }
    }

    @Test
    void cashRequiresFullPaymentAndRetryDoesNotCollectTwice() throws Exception {
        long id = cashInvoice(true);
        String token = cashierToken();
        mvc.perform(cashRequest(id, token, prefix + "partial", "{\"amount\":200000}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FULL_PAYMENT_REQUIRED"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Payment WHERE invoice_id=?", Integer.class, id));
        var payment = json.readTree(mvc.perform(cashRequest(id, token, prefix + "full", "{\"amount\":500000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(500000))
                .andExpect(jsonPath("$.allocations[0].amount").value(500000))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(cashRequest(id, token, prefix + "full", "{\"amount\":500000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.id").value(payment.get("id").asLong()));
        mvc.perform(cashRequest(id, token, prefix + "second", "{\"amount\":500000}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/cashier/invoices/" + id + "/detail").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.canCollect").value(false)).andExpect(jsonPath("$.invoice.status").value("ISSUED"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Payment WHERE invoice_id=?", Integer.class, id));
    }
    @Test
    void cashRejectsInvalidInputUnauthorizedAndRevokedCashier() throws Exception {
        long id = cashInvoice(true);
        String token = cashierToken();
        mvc.perform(cashRequest(id, null, prefix + "noauth", "{\"amount\":1}" )).andExpect(status().isUnauthorized());
        for (String wrong : java.util.List.of(patientOneToken, doctorToken)) {
            mvc.perform(cashRequest(id, wrong, prefix + "role", "{\"amount\":1}" )).andExpect(status().isForbidden());
            mvc.perform(get("/api/cashier/invoices/" + id).header("Authorization", "Bearer " + wrong)).andExpect(status().isForbidden());
        }
        for (String body : java.util.List.of("{}", "{\"amount\":0}", "{\"amount\":-1}", "{\"amount\":1.5}",
                "{\"amount\":10000000000000000000}", "{\"amount\":1,\"confirmedBy\":1}", "{\"amount\":1,\"method\":\"MOMO\"}"))
            mvc.perform(cashRequest(id, token, prefix + "bad", body)).andExpect(status().isBadRequest());
        mvc.perform(cashRequest(id, token, null, "{\"amount\":1}" )).andExpect(status().isBadRequest());
        mvc.perform(cashRequest(id, token, "bad key", "{\"amount\":1}" )).andExpect(status().isBadRequest());
        mvc.perform(cashRequest(id, token, prefix + "over", "{\"amount\":500001}" )).andExpect(status().isConflict());
        mvc.perform(cashRequest(Long.MAX_VALUE, token, prefix + "missing", "{\"amount\":1}" )).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id=? AND role_id=(SELECT id FROM dbo.Role WHERE code='CASHIER')", accountIdForStaff(doctorTwoId));
        mvc.perform(cashRequest(id, token, prefix + "revoked", "{\"amount\":1}" )).andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Payment WHERE invoice_id=?", Integer.class, id));
    }

    @Test
    void cashRejectsDraftVoidCancelledLineAndReusedKey() throws Exception {
        long id = cashInvoice(false);
        String token = cashierToken();
        mvc.perform(cashRequest(id, token, prefix + "draftcash", "{\"amount\":1}" )).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_ISSUED"));
        jdbc.update("UPDATE dbo.Invoice SET status='VOID' WHERE id=?", id);
        mvc.perform(cashRequest(id, token, prefix + "voidcash", "{\"amount\":1}" )).andExpect(status().isConflict());
        jdbc.update("UPDATE dbo.Invoice SET status='ISSUED',issued_at=SYSUTCDATETIME() WHERE id=?", id);
        mvc.perform(cashRequest(id, token, prefix + "cash", "{\"amount\":500000}" )).andExpect(status().isOk());
        mvc.perform(cashRequest(id, token, prefix + "cash", "{\"amount\":2}" )).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        jdbc.update("INSERT dbo.AccountRole(account_id,role_id,granted_at,granted_by) SELECT ?,id,SYSUTCDATETIME(),NULL FROM dbo.Role WHERE code='CASHIER'", accountIdForStaff(doctorOneId));
        mvc.perform(cashRequest(id, doctorToken, prefix + "cash", "{\"amount\":500000}" )).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        jdbc.update("UPDATE dbo.InvoiceLine SET status='CANCELLED',cancelled_at=SYSUTCDATETIME(),cancel_reason='ORDER_CANCELLED' WHERE invoice_id=?", id);
        mvc.perform(get("/api/cashier/invoices/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outstanding").value(0));
        mvc.perform(cashRequest(id, token, prefix + "cancelled", "{\"amount\":1}" )).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Payment WHERE invoice_id=?", Integer.class, id));
    }

    @Test
    void cashRejectsOnlineHoldInvoiceWithoutRecordingPayment() throws Exception {
        long id = cashInvoice(true);
        String token = cashierToken();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.support.JdbcTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(tx -> {
            try {
                jdbc.update("UPDATE dbo.WorkAssignment SET mode='ONLINE',capacity=NULL WHERE id=?", assignmentOneId);
                jdbc.update("UPDATE dbo.Room SET room_type='ONLINE' WHERE id=(SELECT room_id FROM dbo.WorkAssignment WHERE id=?)", assignmentOneId);
                long slotId = jdbc.queryForObject("""
                        INSERT dbo.OnlineSlot(assignment_id,starts_at,ends_at,is_open)
                        OUTPUT INSERTED.id SELECT id,starts_at,DATEADD(MINUTE,20,starts_at),1
                        FROM dbo.WorkAssignment WHERE id=?
                        """, Long.class, assignmentOneId);
                long priceId = jdbc.queryForObject("SELECT TOP 1 id FROM dbo.ServicePrice WHERE service_id=? AND doctor_id=?", Long.class, serviceId, doctorOneId);
                long holdId = jdbc.queryForObject("""
                        INSERT dbo.BookingHold(code,patient_id,slot_id,price_id,quoted_amount,status,expires_at,idempotency_key)
                        OUTPUT INSERTED.id VALUES (?,?,?,?,500000,'ACTIVE',DATEADD(MINUTE,5,SYSUTCDATETIME()),?)
                        """, Long.class, prefix + "HOLD", patientOneId, slotId, priceId, prefix + "HOLD");
                jdbc.update("UPDATE dbo.Invoice SET encounter_id=NULL,hold_id=? WHERE id=?", holdId, id);
                mvc.perform(cashRequest(id, token, prefix + "holdcash", "{\"amount\":1}"))
                        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ONLINE_HOLD_CASH_NOT_ALLOWED"));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Payment WHERE invoice_id=?", Integer.class, id));
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { tx.setRollbackOnly(); }
        });
    }

    @Test
    void cashRollbackAllocationAndAuditFailures() throws Exception {
        long id = cashInvoice(true);
        String token = cashierToken();
        doThrow(new IllegalStateException("P21 allocation failure")).when(cashPaymentRepository)
                .allocate(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
        assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(cashRequest(id, token, prefix + "rollback", "{\"amount\":500000}")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Payment WHERE invoice_id=?", Integer.class, id));
        reset(cashPaymentRepository);
        doThrow(new IllegalStateException("P21 audit failure")).when(cashPaymentRepository).audit(anyLong(), anyLong());
        assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(cashRequest(id, token, prefix + "rollback", "{\"amount\":500000}")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Payment WHERE invoice_id=?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.PaymentAllocation pa JOIN dbo.InvoiceLine l ON l.id=pa.invoice_line_id WHERE l.invoice_id=?", Integer.class, id));
        reset(cashPaymentRepository);
        mvc.perform(cashRequest(id, token, prefix + "rollback", "{\"amount\":500000}" )).andExpect(status().isOk());
    }

    @Test
    void cashConcurrentLastBalanceAndSameKeyUseTwoSqlSessions() throws Exception {
        long id = cashInvoice(true);
        long secondId = cashInvoiceFor(true, "cash-race-second");
        String token = cashierToken();
        var sessions = java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        var bothConnections = new java.util.concurrent.CyclicBarrier(2);
        org.mockito.Mockito.doAnswer(call -> {
            sessions.add(jdbc.queryForObject("SELECT @@SPID", Integer.class));
            bothConnections.await(10, java.util.concurrent.TimeUnit.SECONDS);
            return call.callRealMethod();
        }).when(cashPaymentRepository).lockSource(org.mockito.ArgumentMatchers.any());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Same-key concurrent retry: one real receipt, two successful responses.
            // Different keys compete to settle a second invoice in full.
            for (int phase = 0; phase < 2; phase++) {
                sessions.clear();
                CountDownLatch start = new CountDownLatch(1);
                final int step = phase;
                var requests = new java.util.ArrayList<Future<Integer>>();
                for (int i = 0; i < 2; i++) {
                    final int index = i;
                    requests.add(executor.submit(() -> {
                        start.await();
                        String key = prefix + "race" + step + (step == 0 ? "same" : index);
                        return mvc.perform(cashRequest(step == 0 ? id : secondId, token, key, "{\"amount\":500000}"))
                                .andReturn().getResponse().getStatus();
                    }));
                }
                start.countDown();
                var codes = new java.util.ArrayList<>(java.util.List.of(requests.get(0).get(25, java.util.concurrent.TimeUnit.SECONDS),
                        requests.get(1).get(25, java.util.concurrent.TimeUnit.SECONDS)));
                codes.sort(Integer::compareTo);
                assertEquals(step == 0 ? java.util.List.of(200, 200) : java.util.List.of(200, 409), codes);
                assertEquals(2, sessions.size());
            }
        } finally { executor.shutdownNow(); }
        assertEquals(new BigDecimal("500000"), jdbc.queryForObject("SELECT SUM(amount) FROM dbo.Payment WHERE invoice_id=?", BigDecimal.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Payment WHERE invoice_id=?", Integer.class, id));
        assertEquals(new BigDecimal("500000"), jdbc.queryForObject("SELECT SUM(amount) FROM dbo.Payment WHERE invoice_id=?", BigDecimal.class, secondId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Payment WHERE invoice_id=?", Integer.class, secondId));
    }

    @Test
    void billingSummaryUsesBookedFeeBeforeInvoiceAndSeparatesPaymentFromIssue() throws Exception {
        long encounterId = startedEncounter("billing-summary");
        String base = "/api/doctor/encounters/" + encounterId;
        String summary = base + "/billing-summary";
        jdbc.update("UPDATE dbo.ServicePrice SET amount=900000 WHERE service_id=?", serviceId);
        mvc.perform(get(summary).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.examinationAmount").value("500000"))
                .andExpect(jsonPath("$.invoicedAmount").value("0"))
                .andExpect(jsonPath("$.unbilledAmount").value("500000"))
                .andExpect(jsonPath("$.receivedAmount").value("0"))
                .andExpect(jsonPath("$.costs[0].unitPrice").value("500000"))
                .andExpect(jsonPath("$.costs[0].quantity").value(1))
                .andExpect(jsonPath("$.invoices.length()").value(0))
                .andExpect(jsonPath("$.outstandingAmount").doesNotExist());
        var draft = json.readTree(mvc.perform(post(base + "/invoices").header("Authorization", "Bearer " + doctorToken)
                .header("Idempotency-Key", prefix + "summary-draft")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        mvc.perform(get(summary).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicedAmount").value("500000"))
                .andExpect(jsonPath("$.unbilledAmount").value("0"))
                .andExpect(jsonPath("$.invoices[0].paymentStatus").value("NOT_DUE"));
        long id = draft.get("id").asLong();
        mvc.perform(post(base + "/invoices/" + id + "/issue").header("Authorization", "Bearer " + doctorToken)
                .header("Idempotency-Key", prefix + "summary-issue").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("version", draft.get("version").asText())))).andExpect(status().isOk());
        String serviceSnapshot = jdbc.queryForObject("SELECT service_name FROM dbo.InvoiceLine WHERE invoice_id=?", String.class, id);
        jdbc.update("UPDATE dbo.Service SET name=N'P20 changed after issue' WHERE id=?", serviceId);
        mvc.perform(get(summary).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoices[0].invoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.invoices[0].paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.costs[0].serviceName").value(serviceSnapshot));
        String cashier = cashierToken();
        mvc.perform(cashRequest(id, cashier, prefix + "summary-partial", "{\"amount\":200000}")).andExpect(status().isConflict());
        // Legacy partial receipt fixture: new requests cannot create partial payments.
        long legacyPayment = cashPaymentRepository.insert(id, accountIdForStaff(doctorTwoId), prefix + "legacy-partial", new BigDecimal("200000"));
        cashPaymentRepository.allocate(legacyPayment, jdbc.queryForObject("SELECT id FROM dbo.InvoiceLine WHERE invoice_id=?", Long.class, id), new BigDecimal("200000"));
        mvc.perform(cashRequest(id, cashier, prefix + "legacy-partial", "{\"amount\":200000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        mvc.perform(get(summary).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedAmount").value("200000"))
                .andExpect(jsonPath("$.invoices[0].paymentStatus").value("PARTIAL"));
        mvc.perform(cashRequest(id, cashier, prefix + "summary-rest", "{\"amount\":300000}")).andExpect(status().isOk());
        mvc.perform(get(summary).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedAmount").value("500000"))
                .andExpect(jsonPath("$.invoices[0].invoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.invoices[0].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.invoices[0].outstanding").doesNotExist());
        assertEquals("IN_PROGRESS", jdbc.queryForObject("SELECT status FROM dbo.Encounter WHERE id=?", String.class, encounterId));
    }

    @Test
    void billingSummaryChecksOwnershipRolesAndVoidInvoiceDoesNotConsumeFee() throws Exception {
        long id = cashInvoice(false);
        long encounterId = jdbc.queryForObject("SELECT encounter_id FROM dbo.Invoice WHERE id=?", Long.class, id);
        String path = "/api/doctor/encounters/" + encounterId + "/billing-summary";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + patientOneToken)).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", "Bearer " + doctorTwoToken)).andExpect(status().isNotFound());
        mvc.perform(cashRequest(id, doctorToken, prefix + "summary-forbidden", "{\"amount\":500000}")).andExpect(status().isForbidden());
        jdbc.update("UPDATE dbo.Invoice SET status='VOID' WHERE id=?", id);
        mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.examinationInvoiced").value(false))
                .andExpect(jsonPath("$.invoicedAmount").value("0"))
                .andExpect(jsonPath("$.unbilledAmount").value("500000"))
                .andExpect(jsonPath("$.costs.length()").value(1))
                .andExpect(jsonPath("$.invoices[0].paymentStatus").value("NOT_APPLICABLE"));
        jdbc.update("UPDATE dbo.Invoice SET status='ISSUED',issued_at=SYSUTCDATETIME() WHERE id=?", id);
        jdbc.update("UPDATE dbo.InvoiceLine SET status='CANCELLED',cancelled_at=SYSUTCDATETIME(),cancel_reason='APPOINTMENT_CANCELLED' WHERE invoice_id=?", id);
        mvc.perform(get(path).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoices[0].paymentStatus").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.receivedAmount").value("0"));
    }

    @Test
    void invoiceDraftAndIssuePreserveBookedAmountSnapshotsAndRetry() throws Exception {
        long encounterId=startedEncounter("invoice");
        String path="/api/doctor/encounters/"+encounterId+"/invoices";
        jdbc.update("UPDATE dbo.ServicePrice SET amount=900000 WHERE service_id=?",serviceId);
        var draft=json.readTree(mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"draft"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalAmount").value(500000)).andExpect(jsonPath("$.lines[0].unitPrice").value(500000))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"draft"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true)).andExpect(jsonPath("$.id").value(draft.get("id").asLong()));
        mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"duplicate"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAMINATION_ALREADY_INVOICED"));
        jdbc.update("UPDATE dbo.Patient SET full_name=N'P20 name at issue' WHERE id=?",patientOneId);
        jdbc.update("UPDATE dbo.Service SET name=N'P20 exam at issue' WHERE id=?",serviceId);
        String body=json.writeValueAsString(java.util.Map.of("version",draft.get("version").asText()));
        String issue=path+"/"+draft.get("id").asLong()+"/issue";
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"issue").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.patientName").value("P20 name at issue"))
                .andExpect(jsonPath("$.facilityName").value("P20 Test Clinic"))
                .andExpect(jsonPath("$.issuedAt").isNotEmpty()).andExpect(jsonPath("$.lines[0].serviceName").value("P20 exam at issue"));
        jdbc.update("UPDATE dbo.Patient SET full_name=N'P20 later name' WHERE id=?",patientOneId);
        jdbc.update("UPDATE dbo.Service SET name=N'P20 later service' WHERE id=?",serviceId);
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"issue").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true)).andExpect(jsonPath("$.patientName").value("P20 name at issue"));
        mvc.perform(get(path).header("Authorization","Bearer "+doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalAmount").value(500000)).andExpect(jsonPath("$[0].lines[0].serviceName").value("P20 exam at issue"));
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"newissue").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVOICE_NOT_DRAFT"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Diagnosis WHERE encounter_id=?",Integer.class,encounterId));
    }

    @Test
    void invoiceRejectsWrongOwnerInvalidVersionAndReusedKey() throws Exception {
        long encounterId=bookEncounterForStart("invoice-validation");
        String path="/api/doctor/encounters/"+encounterId+"/invoices";
        mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"planned"))
                .andExpect(status().isConflict());
        mvc.perform(startRequest(encounterId).header("Authorization","Bearer "+doctorToken)).andExpect(status().isOk());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization","Bearer "+patientOneToken)).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization","Bearer "+doctorTwoToken).header("Idempotency-Key",prefix+"owner"))
                .andExpect(status().isNotFound());
        mvc.perform(post(path).header("Authorization","Bearer "+doctorToken)).andExpect(status().isBadRequest());
        var draft=json.readTree(mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"draft"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String issue=path+"/"+draft.get("id").asLong()+"/issue";
        mvc.perform(get(path).header("Authorization","Bearer "+doctorTwoToken)).andExpect(status().isNotFound());
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorTwoToken).header("Idempotency-Key",prefix+"issue").contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"AAAAAAAAAAA=\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"issue").contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_VERSION"));
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"issue").contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"AAAAAAAAAAA=\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVOICE_CHANGED"));
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"issue").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        long other=startedEncounter("invoice-other");
        mvc.perform(post("/api/doctor/encounters/"+other+"/invoices").header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"draft"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Invoice WHERE encounter_id=?",Integer.class,other));
    }

    @Test
    void invoiceCreationAndIssueRollbackAllSteps() throws Exception {
        long encounterId=startedEncounter("invoice-rollback");
        String path="/api/doctor/encounters/"+encounterId+"/invoices";
        org.mockito.Mockito.doThrow(new IllegalStateException("P20 test insert failure")).when(invoiceRepository).insertExamination(anyLong(),org.mockito.ArgumentMatchers.any());
        assertThrows(jakarta.servlet.ServletException.class,()->mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"draft")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Invoice WHERE encounter_id=?",Integer.class,encounterId));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.CommandReceipt WHERE idempotency_key=?",Integer.class,prefix+"draft"));
        reset(invoiceRepository);
        var draft=json.readTree(mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"draft"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.mockito.Mockito.doThrow(new IllegalStateException("P20 test audit failure")).when(invoiceRepository).audit(anyLong(),anyLong(),org.mockito.ArgumentMatchers.eq("INVOICE_ISSUED"));
        assertThrows(jakarta.servlet.ServletException.class,()->mvc.perform(post(path+"/"+draft.get("id").asLong()+"/issue").header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"issue")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("version",draft.get("version").asText())))));
        mvc.perform(get(path).header("Authorization","Bearer "+doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DRAFT")).andExpect(jsonPath("$[0].issuedAt").isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.CommandReceipt WHERE idempotency_key=?",Integer.class,prefix+"issue"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent WHERE entity_type='Invoice' AND entity_id=? AND action='INVOICE_ISSUED'",Integer.class,draft.get("id").asLong()));
    }

    @Test
    void invoiceConcurrentCreationAndIssueUseTwoSqlSessions() throws Exception {
        long encounterId=startedEncounter("invoice-race");
        String path="/api/doctor/encounters/"+encounterId+"/invoices";
        var sessions=java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        org.mockito.Mockito.doAnswer(call->{sessions.add(jdbc.queryForObject("SELECT @@SPID",Integer.class)); return call.callRealMethod();})
                .when(encounterRepository).lockAppointment(anyLong(),anyLong());
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            for(int phase=0;phase<2;phase++) {
                CountDownLatch start=new CountDownLatch(1);
                final int step=phase;
                var drafts=json.readTree(mvc.perform(get(path).header("Authorization","Bearer "+doctorToken)).andReturn().getResponse().getContentAsString());
                String url=phase==0?path:path+"/"+drafts.get(0).get("id").asLong()+"/issue";
                String body=phase==0?"":json.writeValueAsString(java.util.Map.of("version",drafts.get(0).get("version").asText()));
                sessions.clear();
                var requests=new java.util.ArrayList<Future<Integer>>();
                for(int i=0;i<2;i++) { final int index=i; requests.add(executor.submit(()->{
                    start.await(); return mvc.perform(post(url).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"race"+step+index)
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
                })); }
                start.countDown();
                var codes=new java.util.ArrayList<>(java.util.List.of(requests.get(0).get(25,java.util.concurrent.TimeUnit.SECONDS),requests.get(1).get(25,java.util.concurrent.TimeUnit.SECONDS)));
                codes.sort(Integer::compareTo);
                assertEquals(java.util.List.of(200,409),codes);
                assertEquals(2,sessions.size());
            }
        } finally {executor.shutdownNow();}
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Invoice WHERE encounter_id=? AND status='ISSUED'",Integer.class,encounterId));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.InvoiceLine l JOIN dbo.Invoice i ON i.id=l.invoice_id WHERE i.encounter_id=?",Integer.class,encounterId));
    }

    @Test
    void invoiceMissingFacilityFailsWithoutWriting() throws Exception {
        long encounterId=startedEncounter("invoice-config");
        var service=new com.suncare.clinic.modules.billing.service.InvoiceService(invoiceRepository,encounterRepository,cashPaymentRepository,"","");
        var transaction=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.support.JdbcTransactionManager(jdbc.getDataSource()));
        var error=assertThrows(com.suncare.clinic.common.exception.ApiException.class,()->transaction.execute(tx->
                service.create(new CurrentAccountResponse(accountIdForStaff(doctorOneId),"fixture","STAFF",null,doctorOneId,java.util.List.of("DOCTOR"),java.util.List.of()),encounterId,prefix+"config")));
        assertEquals("BILLING_FACILITY_NOT_CONFIGURED",error.getCode());
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,error.getStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Invoice WHERE encounter_id=?",Integer.class,encounterId));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.CommandReceipt WHERE idempotency_key=?",Integer.class,prefix+"config"));
    }

    @Test
    void invoiceRejectsMismatchedTotalAndClientSuppliedMoney() throws Exception {
        long encounterId=startedEncounter("invoice-total");
        String path="/api/doctor/encounters/"+encounterId+"/invoices";
        var draft=json.readTree(mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"draft"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String issue=path+"/"+draft.get("id").asLong()+"/issue";
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"money").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("version",draft.get("version").asText(),"totalAmount",1))))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE dbo.Invoice SET total_amount=1 WHERE id=?",draft.get("id").asLong());
        var current=json.readTree(mvc.perform(get(path).header("Authorization","Bearer "+doctorToken)).andReturn().getResponse().getContentAsString()).get(0);
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"invalid").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("version",current.get("version").asText()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVOICE_INVALID"));
        assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM dbo.Invoice WHERE id=?",String.class,draft.get("id").asLong()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.CommandReceipt WHERE idempotency_key=?",Integer.class,prefix+"invalid"));
    }

    private long previousEncounter(long patientId, long doctorId, String suffix, String state) {
        insertExistingAppointment(assignmentTwoId, doctorId, patientId, suffix);
        long appointmentId = jdbc.queryForObject("SELECT id FROM dbo.Appointment WHERE code=?", Long.class, prefix + suffix);
        return jdbc.queryForObject("INSERT INTO dbo.Encounter(code,appointment_id,status,started_at,completed_at) OUTPUT INSERTED.id VALUES (?,?,?,SYSUTCDATETIME(),CASE WHEN ?='COMPLETED' THEN SYSUTCDATETIME() ELSE NULL END)", Long.class,
                prefix + "E" + suffix, appointmentId, state, state);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder completeRequest(long encounterId) {
        return post("/api/doctor/encounters/" + encounterId + "/complete")
                .header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"completionConfirmed\":true}");
    }

    private void completionDiagnosis(long encounterId) throws Exception {
        mvc.perform(post("/api/doctor/encounters/" + encounterId + "/clinical/diagnoses")
                .header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "final")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                        "diagnosisConceptId", diagnosisConceptId, "description", "Chẩn đoán test P23",
                        "diagnosisType", "FINAL", "isPrimary", true))))
                .andExpect(status().isOk());
    }

    private long completionReady() throws Exception {
        long invoiceId = cashInvoice(true);
        long encounterId = jdbc.queryForObject("SELECT encounter_id FROM dbo.Invoice WHERE id=?", Long.class, invoiceId);
        completionDiagnosis(encounterId);
        mvc.perform(cashRequest(invoiceId, cashierToken(), prefix + "paid", "{\"amount\":500000}"))
                .andExpect(status().isOk());
        return encounterId;
    }

    @Test
    void completionWithoutPrescriptionIsAtomicAndRetryDoesNotDuplicateEvents() throws Exception {
        long encounterId = completionReady();
        var first = json.readTree(mvc.perform(completeRequest(encounterId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.replayed").value(false))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(completeRequest(encounterId)).andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.completedAt").value(first.get("completedAt").asText()));
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT a.status FROM dbo.Appointment a JOIN dbo.Encounter e ON e.appointment_id=a.id WHERE e.id=?", String.class, encounterId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterEvent WHERE encounter_id=? AND to_status='COMPLETED'", Integer.class, encounterId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AppointmentEvent WHERE appointment_id=(SELECT appointment_id FROM dbo.Encounter WHERE id=?) AND event_type='COMPLETED'", Integer.class, encounterId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent WHERE entity_type='Encounter' AND entity_id=? AND action='ENCOUNTER_COMPLETED'", Integer.class, encounterId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Prescription WHERE encounter_id=?", Integer.class, encounterId));
        mvc.perform(post("/api/doctor/encounters/" + encounterId + "/clinical/diagnoses")
                .header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "after")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                        "diagnosisConceptId", diagnosisConceptId, "description", "After completion", "diagnosisType", "FINAL", "isPrimary", false))))
                .andExpect(status().isConflict());
    }

    @Test
    void completionRejectsMissingFinalUnpaidPartialAllocationDraftAndCls() throws Exception {
        long invoiceId = cashInvoice(true);
        long encounterId = jdbc.queryForObject("SELECT encounter_id FROM dbo.Invoice WHERE id=?", Long.class, invoiceId);
        mvc.perform(completeRequest(encounterId)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FINAL_DIAGNOSIS_REQUIRED"));
        completionDiagnosis(encounterId);
        mvc.perform(completeRequest(encounterId)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENCOUNTER_OUTSTANDING"));
        mvc.perform(cashRequest(invoiceId, cashierToken(), prefix + "paid", "{\"amount\":500000}" )).andExpect(status().isOk());
        jdbc.update("UPDATE dbo.PaymentAllocation SET amount=200000 WHERE invoice_line_id IN (SELECT id FROM dbo.InvoiceLine WHERE invoice_id=?)", invoiceId);
        mvc.perform(completeRequest(encounterId)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENCOUNTER_OUTSTANDING"));
        jdbc.update("UPDATE dbo.PaymentAllocation SET amount=500000 WHERE invoice_line_id IN (SELECT id FROM dbo.InvoiceLine WHERE invoice_id=?)", invoiceId);
        var draft = prescriptionCreate(encounterId, prefix + "draft");
        mvc.perform(completeRequest(encounterId)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRAFT_PRESCRIPTION_EXISTS"));
        mvc.perform(delete(prescriptionPath(encounterId) + "/" + draft.get("id").asLong())
                .header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "discard")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("version", draft.get("version").asText()))))
                .andExpect(status().isOk());
        jdbc.update("INSERT dbo.ClinicalOrder(code,encounter_id,service_id,performing_department_id,ordered_by,indication,status) VALUES (?,?,?,?,?,N'Fixture CLS','PENDING_PAYMENT')",
                prefix + "CLS", encounterId, serviceId, departmentId, accountIdForStaff(doctorOneId));
        mvc.perform(completeRequest(encounterId)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENCOUNTER_CLS_NOT_SUPPORTED"));
        assertEquals("IN_PROGRESS", jdbc.queryForObject("SELECT status FROM dbo.Encounter WHERE id=?", String.class, encounterId));
        jdbc.update("DELETE dbo.ClinicalOrder WHERE encounter_id=?", encounterId);
        mvc.perform(completeRequest(encounterId)).andExpect(status().isOk());
    }

    @Test
    void completionChecksOwnershipInputStateAndRoleOnEveryRetry() throws Exception {
        long encounterId = bookEncounterForStart("plannedcomplete");
        String path = "/api/doctor/encounters/" + encounterId + "/complete";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"completionConfirmed\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", "Bearer " + patientOneToken).contentType(MediaType.APPLICATION_JSON).content("{\"completionConfirmed\":true}")).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", "Bearer " + doctorTwoToken).contentType(MediaType.APPLICATION_JSON).content("{\"completionConfirmed\":true}")).andExpect(status().isNotFound());
        mvc.perform(completeRequest(encounterId)).andExpect(status().isConflict());
        for (String body : java.util.List.of("{}", "{\"completionConfirmed\":false}", "{\"completionConfirmed\":true,\"status\":\"COMPLETED\"}"))
            mvc.perform(post(path).header("Authorization", "Bearer " + doctorToken).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(completeRequest(Long.MAX_VALUE)).andExpect(status().isNotFound());
        jdbc.update("DELETE dbo.AccountRole WHERE account_id=?", accountIdForStaff(doctorOneId));
        mvc.perform(completeRequest(encounterId)).andExpect(status().isForbidden());
    }

    @Test
    void completionRollsBackBothStatesAndEventsWhenAuditFails() throws Exception {
        long encounterId = completionReady();
        doThrow(new IllegalStateException("P23 audit failure")).when(encounterRepository).auditCompletion(anyLong(), anyLong());
        assertThrows(Exception.class, () -> mvc.perform(completeRequest(encounterId)));
        assertEquals("IN_PROGRESS", jdbc.queryForObject("SELECT status FROM dbo.Encounter WHERE id=?", String.class, encounterId));
        assertEquals("BOOKED", jdbc.queryForObject("SELECT a.status FROM dbo.Appointment a JOIN dbo.Encounter e ON e.appointment_id=a.id WHERE e.id=?", String.class, encounterId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterEvent WHERE encounter_id=? AND to_status='COMPLETED'", Integer.class, encounterId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AppointmentEvent WHERE appointment_id=(SELECT appointment_id FROM dbo.Encounter WHERE id=?) AND event_type='COMPLETED'", Integer.class, encounterId));
        reset(encounterRepository);
        mvc.perform(completeRequest(encounterId)).andExpect(status().isOk());
    }

    @Test
    void completionRequiresIssuedExaminationAndAllowsIssuedPrescription() throws Exception {
        long encounterId = startedEncounter("nofee");
        completionDiagnosis(encounterId);
        mvc.perform(completeRequest(encounterId)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENCOUNTER_OUTSTANDING"));
        String invoices = "/api/doctor/encounters/" + encounterId + "/invoices";
        var draft = json.readTree(mvc.perform(post(invoices).header("Authorization", "Bearer " + doctorToken)
                .header("Idempotency-Key", prefix + "fee")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(completeRequest(encounterId)).andExpect(status().isConflict());
        mvc.perform(post(invoices + "/" + draft.get("id").asLong() + "/issue").header("Authorization", "Bearer " + doctorToken)
                .header("Idempotency-Key", prefix + "feeissue").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("version", draft.get("version").asText())))).andExpect(status().isOk());
        mvc.perform(cashRequest(draft.get("id").asLong(), cashierToken(), prefix + "paid", "{\"amount\":500000}")).andExpect(status().isOk());
        var rx = prescriptionCreate(encounterId, prefix + "rx");
        mvc.perform(post(prescriptionPath(encounterId) + "/" + rx.get("id").asLong() + "/issue")
                .header("Authorization", "Bearer " + doctorToken).header("Idempotency-Key", prefix + "rxissue")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("version", rx.get("version").asText()))))
                .andExpect(status().isOk());
        mvc.perform(completeRequest(encounterId)).andExpect(status().isOk());
        mvc.perform(post("/api/doctor/encounters/" + encounterId + "/complete")
                .header("Authorization", "Bearer " + doctorTwoToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"completionConfirmed\":true}")).andExpect(status().isNotFound());
        jdbc.update("UPDATE dbo.Account SET status='LOCKED' WHERE id=?", accountIdForStaff(doctorOneId));
        mvc.perform(completeRequest(encounterId)).andExpect(status().isUnauthorized());
    }

    @Test
    void completionConcurrentRetryUsesIndependentSqlSessions() throws Exception {
        long encounterId = completionReady();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var sessions = java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        org.mockito.Mockito.doAnswer(invocation -> {
            sessions.add(jdbc.queryForObject("SELECT @@SPID", Integer.class));
            ready.countDown();
            if (!go.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("P23 barrier timeout");
            return invocation.callRealMethod();
        }).when(encounterRepository).appointmentId(encounterId);
        try {
            var first = pool.submit(() -> mvc.perform(completeRequest(encounterId)).andReturn());
            var second = pool.submit(() -> mvc.perform(completeRequest(encounterId)).andReturn());
            assertTrue(ready.await(15, java.util.concurrent.TimeUnit.SECONDS));
            go.countDown();
            var a = first.get(30, java.util.concurrent.TimeUnit.SECONDS).getResponse();
            var b = second.get(30, java.util.concurrent.TimeUnit.SECONDS).getResponse();
            assertEquals(200, a.getStatus());
            assertEquals(200, b.getStatus());
            assertEquals(2, sessions.size());
            assertTrue(json.readTree(a.getContentAsString()).get("replayed").asBoolean()
                    != json.readTree(b.getContentAsString()).get("replayed").asBoolean());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.EncounterEvent WHERE encounter_id=? AND to_status='COMPLETED'", Integer.class, encounterId));
        } finally {
            go.countDown();
            pool.shutdownNow();
            reset(encounterRepository);
        }
    }

    private long startedEncounter(String suffix) throws Exception {
        long id = bookEncounterForStart(suffix);
        mvc.perform(startRequest(id).header("Authorization", "Bearer " + doctorToken)).andExpect(status().isOk());
        return id;
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


    private long prescriptionDrug(String suffix,boolean active) {
        assertEquals("SuncareClinicMigrationTest",jdbc.queryForObject("SELECT DB_NAME()",String.class));
        return jdbc.queryForObject("""
                INSERT dbo.Drug(code,name,ingredient,strength,dosage_form,unit,is_active,dose_unit,dispensing_unit)
                OUTPUT INSERTED.id VALUES (?,N'Thuốc giả P22',N'Hoạt chất gốc',N'100 mg',N'Viên nén',N'viên',?,N'viên',N'viên')
                """,Long.class,prefix+"RX"+suffix,active);
    }

    private String prescriptionPath(long encounterId) { return "/api/doctor/encounters/"+encounterId+"/prescriptions"; }

    private String prescriptionCreateBody(long drug) {
        var body=(tools.jackson.databind.node.ObjectNode)json.readTree(prescriptionBody("",drug));
        body.remove("version");
        return json.writeValueAsString(body);
    }

    private tools.jackson.databind.JsonNode prescriptionCreate(long encounterId,String key) throws Exception {
        var existing=jdbc.query("SELECT id FROM dbo.Drug WHERE code LIKE ? AND is_active=1 ORDER BY id",(rs,n)->rs.getLong(1),prefix+"RX%");
        long drug=existing.isEmpty()?prescriptionDrug("CREATE",true):existing.get(0);
        return json.readTree(mvc.perform(post(prescriptionPath(encounterId)).header("Authorization","Bearer "+doctorToken)
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(prescriptionCreateBody(drug))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String prescriptionBody(String version,long drugId) {
        return json.writeValueAsString(java.util.Map.of("version",version,"instruction"," Dặn dò giả ",
                "items",java.util.List.of(java.util.Map.of("drugId",drugId,"dose"," 1 ","route","Uống",
                "frequency","2 lần/ngày","durationDays",5,"quantity",new BigDecimal("10.125"),"instruction","Sau ăn"))));
    }

    private tools.jackson.databind.JsonNode prescriptionSave(long encounterId,tools.jackson.databind.JsonNode draft,long drug,String key) throws Exception {
        return json.readTree(mvc.perform(put(prescriptionPath(encounterId)+"/"+draft.get("id").asLong())
                .header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
                .content(prescriptionBody(draft.get("version").asText(),drug))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void prescriptionDraftIssueSnapshotsAndRetryAreImmutable() throws Exception {
        long encounter=startedEncounter("rx-snapshot");
        long drug=prescriptionDrug("ONE",true);
        var draft=prescriptionCreate(encounter,prefix+"create");
        long id=draft.get("id").asLong();
        var saved=prescriptionSave(encounter,draft,drug,prefix+"save");
        assertEquals("1",saved.get("items").get(0).get("dose").asText());
        assertEquals(new BigDecimal("10.125"),saved.get("items").get(0).get("quantity").decimalValue());
        assertEquals("Dặn dò giả",saved.get("instruction").asText());
        String base=prescriptionPath(encounter);
        String version=json.writeValueAsString(java.util.Map.of("version",saved.get("version").asText()));
        jdbc.update("UPDATE dbo.Drug SET name=N'Tên mới',ingredient=N'Hoạt chất mới',strength=N'200 mg',unit=N'gói' WHERE id=?",drug);
        var issued=json.readTree(mvc.perform(post(base+"/"+id+"/issue").header("Authorization","Bearer "+doctorToken)
                .header("Idempotency-Key",prefix+"issue").contentType(MediaType.APPLICATION_JSON).content(version))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ISSUED")).andReturn().getResponse().getContentAsString());
        assertTrue(issued.get("issuedAt").asText().endsWith("Z"));
        assertEquals("Thuốc giả P22",issued.get("items").get(0).get("drugName").asText());
        assertEquals("100 mg",issued.get("items").get(0).get("strength").asText());
        assertEquals("viên",issued.get("items").get(0).get("unit").asText());
        assertEquals("viên",issued.get("items").get(0).get("doseUnit").asText());
        assertEquals("viên",issued.get("items").get(0).get("dispensingUnit").asText());
        jdbc.update("UPDATE dbo.Drug SET is_active=0 WHERE id=?",drug);
        mvc.perform(post(base+"/"+id+"/issue").header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"issue")
                .contentType(MediaType.APPLICATION_JSON).content(version)).andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        mvc.perform(put(base+"/"+id).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"save2")
                .contentType(MediaType.APPLICATION_JSON).content(prescriptionBody(issued.get("version").asText(),drug)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRESCRIPTION_IMMUTABLE"));
        mvc.perform(delete(base+"/"+id).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"delete")
                .contentType(MediaType.APPLICATION_JSON).content(version)).andExpect(status().isConflict());
        mvc.perform(post(base).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"new").contentType(MediaType.APPLICATION_JSON).content(prescriptionCreateBody(drug)))
                .andExpect(status().isConflict());
        mvc.perform(get(base).header("Authorization","Bearer "+doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[0].ingredient").value("Hoạt chất gốc"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent WHERE entity_type='Prescription' AND entity_id=? AND action='PRESCRIPTION_ISSUED'",Integer.class,id));
        assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT status FROM dbo.Encounter WHERE id=?",String.class,encounter));
    }

    @Test
    void prescriptionValidationAndInactiveDrugDoNotWrite() throws Exception {
        long encounter=startedEncounter("rx-validation");
        long drug=prescriptionDrug("VALID",true);
        var draft=prescriptionCreate(encounter,prefix+"create");
        String base=prescriptionPath(encounter)+"/"+draft.get("id").asLong();
        // Legacy empty draft is still guarded on issue; public POST now requires items.
        jdbc.update("DELETE FROM dbo.PrescriptionItem WHERE prescription_id=?",draft.get("id").asLong());
        String version=json.writeValueAsString(java.util.Map.of("version",draft.get("version").asText()));
        mvc.perform(post(base+"/issue").header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"empty")
                .contentType(MediaType.APPLICATION_JSON).content(version))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMPTY_PRESCRIPTION"));
        String body=prescriptionBody(draft.get("version").asText(),drug);
        for(String invalid:java.util.List.of(body.replace("10.125","0"),body.replace("10.125","1.2345"),
                body.replace("10.125","1000000000"),body.replace("\"durationDays\":5","\"durationDays\":0"),
                body.replace(" 1 "," "),body.replace(" 1 ","0"),body.replace(" 1 ","-1"),body.replace(" 1 ","abc"),body.replace(" 1 ","1/0"),body.replace("Uống"," "),body.replace("2 lần/ngày"," "))) {
            mvc.perform(put(base).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"bad")
                    .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(put(base).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"unknown")
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"drugId\":", "\"drugName\":\"forged\",\"drugId\":")))
                .andExpect(status().isBadRequest());
        long inactive=prescriptionDrug("OFF",false);
        mvc.perform(put(base).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"inactive")
                .contentType(MediaType.APPLICATION_JSON).content(prescriptionBody(draft.get("version").asText(),inactive)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRUG_UNAVAILABLE"));
        mvc.perform(put(base).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"missing")
                .contentType(MediaType.APPLICATION_JSON).content(prescriptionBody(draft.get("version").asText(),Long.MAX_VALUE)))
                .andExpect(status().isConflict());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.PrescriptionItem WHERE prescription_id=?",Integer.class,draft.get("id").asLong()));
        assertEquals(draft.get("version").asText(),json.readTree(mvc.perform(get(prescriptionPath(encounter)).header("Authorization","Bearer "+doctorToken)).andReturn().getResponse().getContentAsString()).get(0).get("version").asText());
    }

    @Test
    void prescriptionStaleVersionKeyConflictsAndDraftDiscard() throws Exception {
        long encounter=startedEncounter("rx-version");
        long drug=prescriptionDrug("VER",true);
        var draft=prescriptionCreate(encounter,prefix+"create");
        assertEquals(draft.get("id").asLong(),prescriptionCreate(encounter,prefix+"create").get("id").asLong());
        var saved=prescriptionSave(encounter,draft,drug,prefix+"save");
        var replay=prescriptionSave(encounter,draft,drug,prefix+"save");
        assertTrue(replay.get("replayed").asBoolean());
        String path=prescriptionPath(encounter)+"/"+draft.get("id").asLong();
        mvc.perform(put(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"stale")
                .contentType(MediaType.APPLICATION_JSON).content(prescriptionBody(draft.get("version").asText(),drug)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRESCRIPTION_CHANGED"));
        mvc.perform(put(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"save")
                .contentType(MediaType.APPLICATION_JSON).content(prescriptionBody(saved.get("version").asText(),drug)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        String v=json.writeValueAsString(java.util.Map.of("version",saved.get("version").asText()));
        for(int index=0;index<2;index++) mvc.perform(delete(path).header("Authorization","Bearer "+doctorToken)
                .header("Idempotency-Key",prefix+"discard").contentType(MediaType.APPLICATION_JSON).content(v))
                .andExpect(status().isOk()).andExpect(jsonPath("$.discarded").value(true)).andExpect(jsonPath("$.replayed").value(index==1));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.PrescriptionItem WHERE prescription_id=?",Integer.class,draft.get("id").asLong()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Prescription WHERE encounter_id=?",Integer.class,encounter));
        var next=prescriptionCreate(encounter,prefix+"next");
        assertTrue(next.get("id").asLong()!=draft.get("id").asLong());
        mvc.perform(post(prescriptionPath(encounter)).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"create").contentType(MediaType.APPLICATION_JSON).content(prescriptionCreateBody(drug)))
                .andExpect(status().isNotFound());
    }

    @Test
    void prescriptionOwnershipStateAndRevokedRoleAreEnforced() throws Exception {
        long encounter=bookEncounterForStart("rx-auth");
        String base=prescriptionPath(encounter);
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base).header("Authorization","Bearer "+patientOneToken)).andExpect(status().isForbidden());
        long plannedDrug=prescriptionDrug("PLANNED",true);
        mvc.perform(post(base).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"planned").contentType(MediaType.APPLICATION_JSON).content(prescriptionCreateBody(plannedDrug)))
                .andExpect(status().isConflict());
        long started=startedEncounter("rx-active");
        var draft=prescriptionCreate(started,prefix+"create");
        long drug=prescriptionDrug("AUTH",true);
        String path=prescriptionPath(started)+"/"+draft.get("id").asLong();
        String version=json.writeValueAsString(java.util.Map.of("version",draft.get("version").asText()));
        mvc.perform(get(prescriptionPath(started)).header("Authorization","Bearer "+doctorTwoToken)).andExpect(status().isNotFound());
        mvc.perform(put(path).header("Authorization","Bearer "+doctorTwoToken).header("Idempotency-Key",prefix+"other")
                .contentType(MediaType.APPLICATION_JSON).content(prescriptionBody(draft.get("version").asText(),drug))).andExpect(status().isNotFound());
        mvc.perform(post(path+"/issue").header("Authorization","Bearer "+doctorTwoToken).header("Idempotency-Key",prefix+"otherissue")
                .contentType(MediaType.APPLICATION_JSON).content(version)).andExpect(status().isNotFound());
        mvc.perform(delete(path).header("Authorization","Bearer "+doctorTwoToken).header("Idempotency-Key",prefix+"otherdelete")
                .contentType(MediaType.APPLICATION_JSON).content(version)).andExpect(status().isNotFound());
        jdbc.update("UPDATE dbo.Encounter SET status='WAITING_CLS' WHERE id=?",started);
        mvc.perform(put(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"waiting")
                .contentType(MediaType.APPLICATION_JSON).content(prescriptionBody(draft.get("version").asText(),drug))).andExpect(status().isConflict());
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id=?",accountIdForStaff(doctorOneId));
        mvc.perform(get(prescriptionPath(started)).header("Authorization","Bearer "+doctorToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/doctor/drugs").header("Authorization","Bearer "+doctorToken)).andExpect(status().isForbidden());
    }

    @Test
    void prescriptionRollbackCoversItemsAuditAndReceipts() throws Exception {
        long encounter=startedEncounter("rx-rollback");
        long drug=prescriptionDrug("ROLL",true);
        String base=prescriptionPath(encounter);
        org.mockito.Mockito.doThrow(new IllegalStateException("Test audit failure")).when(prescriptionRepository).audit(anyLong(),anyLong(),anyString());
        assertThrows(Exception.class,()->prescriptionCreate(encounter,prefix+"create"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Prescription WHERE encounter_id=?",Integer.class,encounter));
        reset(prescriptionRepository);
        var draft=prescriptionCreate(encounter,prefix+"create");
        var saved=prescriptionSave(encounter,draft,drug,prefix+"save");
        long id=saved.get("id").asLong();
        org.mockito.Mockito.doThrow(new IllegalStateException("Test receipt failure")).when(prescriptionRepository)
                .receipt(anyLong(),anyString(),anyString(),anyString(),anyLong());
        assertThrows(Exception.class,()->prescriptionSave(encounter,saved,drug,prefix+"fail-save"));
        String version=json.writeValueAsString(java.util.Map.of("version",saved.get("version").asText()));
        assertThrows(Exception.class,()->mvc.perform(post(base+"/"+id+"/issue").header("Authorization","Bearer "+doctorToken)
                .header("Idempotency-Key",prefix+"fail-issue").contentType(MediaType.APPLICATION_JSON).content(version)));
        assertThrows(Exception.class,()->mvc.perform(delete(base+"/"+id).header("Authorization","Bearer "+doctorToken)
                .header("Idempotency-Key",prefix+"fail-delete").contentType(MediaType.APPLICATION_JSON).content(version)));
        assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM dbo.Prescription WHERE id=?",String.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.PrescriptionItem WHERE prescription_id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.CommandReceipt WHERE idempotency_key IN (?,?,?)",Integer.class,prefix+"fail-save",prefix+"fail-issue",prefix+"fail-delete"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent WHERE entity_type='Prescription' AND entity_id=?",Integer.class,id));
        assertEquals(saved.get("version").asText(),java.util.Base64.getEncoder().encodeToString(jdbc.queryForObject("SELECT version FROM dbo.Prescription WHERE id=?",byte[].class,id)));
    }

    @Test
    void prescriptionConcurrentCommandsUseTwoIndependentSqlSessions() throws Exception {
        long encounter=startedEncounter("rx-race");
        long drug=prescriptionDrug("RACE",true);
        String base=prescriptionPath(encounter);
        var sessions=java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        org.mockito.Mockito.doAnswer(call->{
            sessions.add(jdbc.queryForObject("SELECT @@SPID",Integer.class));
            barrier.await(15,java.util.concurrent.TimeUnit.SECONDS);
            return call.callRealMethod();
        }).when(encounterRepository).lockAppointment(anyLong(),anyLong());
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var createTasks=new java.util.ArrayList<Future<MvcResult>>();
            for(int i=0;i<2;i++) createTasks.add(executor.submit(()->mvc.perform(post(base).header("Authorization","Bearer "+doctorToken)
                    .header("Idempotency-Key",prefix+"same-create").contentType(MediaType.APPLICATION_JSON).content(prescriptionCreateBody(drug))).andReturn()));
            for(var task:createTasks) assertEquals(200,task.get(25,java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus());
            assertEquals(2,sessions.size());
            reset(encounterRepository);
            var draft=json.readTree(createTasks.getFirst().get().getResponse().getContentAsString());
            var saved=prescriptionSave(encounter,draft,drug,prefix+"save");
            for(boolean sameKey:java.util.List.of(false,true)) {
                // Hai request khác key tranh phiên bản save; cùng key issue phải replay một lần.
                sessions.clear();
                org.mockito.Mockito.doAnswer(call->{
                    sessions.add(jdbc.queryForObject("SELECT @@SPID",Integer.class));
                    barrier.await(15,java.util.concurrent.TimeUnit.SECONDS);
                    return call.callRealMethod();
                }).when(encounterRepository).lockAppointment(anyLong(),anyLong());
                String path=base+"/"+saved.get("id").asLong()+(sameKey?"/issue":"");
                String body=sameKey?json.writeValueAsString(java.util.Map.of("version",saved.get("version").asText())):prescriptionBody(saved.get("version").asText(),drug);
                var tasks=new java.util.ArrayList<Future<Integer>>();
                for(int i=0;i<2;i++) {
                    final int index=i;
                    tasks.add(executor.submit(()->mvc.perform((sameKey?post(path):put(path)).header("Authorization","Bearer "+doctorToken)
                            .header("Idempotency-Key",prefix+(sameKey?"same-issue":"save-race"+index))
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus()));
                }
                var codes=new java.util.ArrayList<>(java.util.List.of(tasks.get(0).get(25,java.util.concurrent.TimeUnit.SECONDS),tasks.get(1).get(25,java.util.concurrent.TimeUnit.SECONDS)));
                codes.sort(Integer::compareTo);
                assertEquals(sameKey?java.util.List.of(200,200):java.util.List.of(200,409),codes);
                assertEquals(2,sessions.size());
                reset(encounterRepository);
                saved=json.readTree(mvc.perform(get(base).header("Authorization","Bearer "+doctorToken)).andReturn().getResponse().getContentAsString()).get(0);
            }
        } finally { reset(encounterRepository); executor.shutdownNow(); }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Prescription WHERE encounter_id=? AND status='ISSUED'",Integer.class,encounter));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.PrescriptionItem WHERE prescription_id IN (SELECT id FROM dbo.Prescription WHERE encounter_id=?)",Integer.class,encounter));
    }

    @Test
    void prescriptionIssueChecksActiveDrugVersionAndReadOnlyEncounter() throws Exception {
        long encounter=startedEncounter("rx-issue-guards");
        long drug=prescriptionDrug("GUARD",true);
        long inactive=prescriptionDrug("HIDDEN",false);
        mvc.perform(get("/api/doctor/drugs").header("Authorization","Bearer "+doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == "+drug+")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == "+inactive+")]").isEmpty());
        mvc.perform(get("/api/doctor/drugs").header("Authorization","Bearer "+patientOneToken)).andExpect(status().isForbidden());
        var draft=prescriptionCreate(encounter,prefix+"create");
        var saved=prescriptionSave(encounter,draft,drug,prefix+"save");
        long id=saved.get("id").asLong();
        String base=prescriptionPath(encounter);
        String issue=base+"/"+id+"/issue";
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"invalid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_VERSION"));
        String version=json.writeValueAsString(java.util.Map.of("version",saved.get("version").asText()));
        jdbc.update("UPDATE dbo.Drug SET is_active=0 WHERE id=?",drug);
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"inactive")
                .contentType(MediaType.APPLICATION_JSON).content(version))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRUG_UNAVAILABLE"));
        jdbc.update("UPDATE dbo.Drug SET is_active=1 WHERE id=?",drug);
        jdbc.update("UPDATE dbo.Encounter SET status='COMPLETED',completed_at=SYSUTCDATETIME() WHERE id=?",encounter);
        mvc.perform(post(issue).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"completed")
                .contentType(MediaType.APPLICATION_JSON).content(version)).andExpect(status().isConflict());
        mvc.perform(get(base).header("Authorization","Bearer "+doctorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.CommandReceipt WHERE idempotency_key IN (?,?,?)",Integer.class,prefix+"invalid",prefix+"inactive",prefix+"completed"));
    }

    @Test
    void prescriptionDifferentKeysCompeteForSingleDraftAndIssuedDocument() throws Exception {
        long encounter=startedEncounter("rx-different-keys");
        long drug=prescriptionDrug("DIFF",true);
        String base=prescriptionPath(encounter);
        var sessions=java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            for(int phase=0;phase<2;phase++) {
                String path=base;
                String body=prescriptionCreateBody(drug);
                if(phase==1) {
                    var current=json.readTree(mvc.perform(get(base).header("Authorization","Bearer "+doctorToken)).andReturn().getResponse().getContentAsString()).get(0);
                    current=prescriptionSave(encounter,current,drug,prefix+"save");
                    path=base+"/"+current.get("id").asLong()+"/issue";
                    body=json.writeValueAsString(java.util.Map.of("version",current.get("version").asText()));
                }
                final String url=path;
                final String payload=body;
                final int step=phase;
                sessions.clear();
                org.mockito.Mockito.doAnswer(call->{
                    sessions.add(jdbc.queryForObject("SELECT @@SPID",Integer.class));
                    barrier.await(15,java.util.concurrent.TimeUnit.SECONDS);
                    return call.callRealMethod();
                }).when(encounterRepository).lockAppointment(anyLong(),anyLong());
                var tasks=new java.util.ArrayList<Future<Integer>>();
                for(int index=0;index<2;index++) {
                    final int item=index;
                    tasks.add(executor.submit(()->mvc.perform(post(url).header("Authorization","Bearer "+doctorToken)
                            .header("Idempotency-Key",prefix+"diff"+step+item).contentType(MediaType.APPLICATION_JSON).content(payload))
                            .andReturn().getResponse().getStatus()));
                }
                var codes=new java.util.ArrayList<>(java.util.List.of(tasks.get(0).get(25,java.util.concurrent.TimeUnit.SECONDS),tasks.get(1).get(25,java.util.concurrent.TimeUnit.SECONDS)));
                codes.sort(Integer::compareTo);
                assertEquals(java.util.List.of(200,409),codes);
                assertEquals(2,sessions.size());
                reset(encounterRepository);
            }
        } finally { reset(encounterRepository); executor.shutdownNow(); }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Prescription WHERE encounter_id=? AND status='ISSUED'",Integer.class,encounter));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent WHERE entity_type='Prescription' AND action='PRESCRIPTION_ISSUED' AND actor_id=?",Integer.class,accountIdForStaff(doctorOneId)));
    }
    @Test
    void prescriptionAtomicCreateRejectsInvalidDataAndSavesMultipleDrugs() throws Exception {
        long encounter=startedEncounter("rx-atomic");
        long first=prescriptionDrug("ATOM1",true);
        long second=prescriptionDrug("ATOM2",true);
        String path=prescriptionPath(encounter);
        String one=prescriptionCreateBody(first);
        for(String invalid:java.util.List.of("{}", "{\"items\":[]}", one.replace(" 1 ","0"),
                one.replace(" 1 ","-1"),one.replace(" 1 ","1/0"),one.replace(" 1 ","abc"))) {
            mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"invalid-create")
                    .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Prescription WHERE encounter_id=?",Integer.class,encounter));
        }
        mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"no-body"))
                .andExpect(status().isBadRequest());
        long inactive=prescriptionDrug("ATOMOFF",false);
        mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"inactive-create")
                .contentType(MediaType.APPLICATION_JSON).content(prescriptionCreateBody(inactive))).andExpect(status().isConflict());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Prescription WHERE encounter_id=?",Integer.class,encounter));
        String body=json.writeValueAsString(java.util.Map.of("instruction","Hai thuốc giả", "items",java.util.List.of(
                json.readTree(one).get("items").get(0),json.readTree(prescriptionCreateBody(second)).get("items").get(0))));
        var created=json.readTree(mvc.perform(post(path).header("Authorization","Bearer "+doctorToken)
                .header("Idempotency-Key",prefix+"atomic").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(2,created.get("items").size());
        long id=created.get("id").asLong();
        mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"atomic")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        mvc.perform(put(path+"/"+id).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"empty-save")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                        "version",created.get("version").asText(),"items",java.util.List.of())))).andExpect(status().isBadRequest());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.PrescriptionItem WHERE prescription_id=?",Integer.class,id));
        assertEquals(created.get("version").asText(),java.util.Base64.getEncoder().encodeToString(
                jdbc.queryForObject("SELECT version FROM dbo.Prescription WHERE id=?",byte[].class,id)));
        jdbc.update("UPDATE dbo.PrescriptionItem SET dose=N'0 viên' WHERE prescription_id=? AND line_no=1",id);
        mvc.perform(post(path+"/"+id+"/issue").header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"invalid-issue")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                        "version",created.get("version").asText())))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRESCRIPTION_INVALID"));
    }

    @Test
    void prescriptionSeparatesUnitsAndPreservesThemOnIssue() throws Exception {
        long encounter=startedEncounter("rx-units");
        long drug=prescriptionDrug("UNITS",true);
        String path=prescriptionPath(encounter);
        jdbc.update("UPDATE dbo.Drug SET dose_unit=NULL WHERE id=?",drug);
        mvc.perform(post(path).header("Authorization","Bearer "+doctorToken).header("Idempotency-Key",prefix+"no-unit")
                .contentType(MediaType.APPLICATION_JSON).content(prescriptionCreateBody(drug)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRUG_UNITS_REQUIRED"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Prescription WHERE encounter_id=?",Integer.class,encounter));
        jdbc.update("UPDATE dbo.Drug SET dose_unit=N'mL',dispensing_unit=N'chai',unit=N'chai' WHERE id=?",drug);
        String body=prescriptionCreateBody(drug).replace("\"dose\":\" 1 \"","\"dose\":\"5\"").replace("10.125","1");
        var saved=json.readTree(mvc.perform(post(path).header("Authorization","Bearer "+doctorToken)
                .header("Idempotency-Key",prefix+"units-create").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("mL",saved.get("items").get(0).get("doseUnit").asText());
        assertEquals("chai",saved.get("items").get(0).get("dispensingUnit").asText());
        long id=saved.get("id").asLong();
        String version=json.writeValueAsString(java.util.Map.of("version",saved.get("version").asText()));
        jdbc.update("UPDATE dbo.PrescriptionItem SET dose_unit=NULL,dispensing_unit=NULL WHERE prescription_id=?",id);
        mvc.perform(post(path+"/"+id+"/issue").header("Authorization","Bearer "+doctorToken)
                .header("Idempotency-Key",prefix+"legacy-unit-issue").contentType(MediaType.APPLICATION_JSON).content(version))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRESCRIPTION_INVALID"));
        saved=prescriptionSave(encounter,saved,drug,prefix+"review-units");
        jdbc.update("UPDATE dbo.Drug SET dose_unit=N'g',dispensing_unit=N'tuýp',unit=N'tuýp' WHERE id=?",drug);
        version=json.writeValueAsString(java.util.Map.of("version",saved.get("version").asText()));
        mvc.perform(post(path+"/"+id+"/issue").header("Authorization","Bearer "+doctorToken)
                .header("Idempotency-Key",prefix+"units-issue").contentType(MediaType.APPLICATION_JSON).content(version))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].doseUnit").value("mL"))
                .andExpect(jsonPath("$.items[0].dispensingUnit").value("chai"))
                .andExpect(jsonPath("$.items[0].unit").value("chai"));
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
