package com.suncare.clinic.modules.account;

import com.suncare.clinic.modules.account.dto.PatientRegistrationRequest;
import com.suncare.clinic.modules.account.repository.PatientRegistrationRepository;
import com.suncare.clinic.modules.account.service.PatientRegistrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class PatientRegistrationIT {
    private static final String PASSWORD = "test-password-123";

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PatientRegistrationService service;
    @MockitoSpyBean private PatientRegistrationRepository repository;

    private MockMvc mockMvc;
    private String login;
    private String fullName;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        login = "p03." + suffix;
        fullName = "Test P03 " + suffix;
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("""
                DELETE FROM dbo.AccountRole WHERE account_id IN
                    (SELECT a.id FROM dbo.[Account] a JOIN dbo.Patient p ON p.id = a.patient_id
                     WHERE p.full_name = ?)
                """, fullName);
        jdbc.update("DELETE FROM dbo.[Account] WHERE patient_id IN (SELECT id FROM dbo.Patient WHERE full_name = ?)", fullName);
        jdbc.update("DELETE FROM dbo.Patient WHERE full_name = ?", fullName);
    }

    @Test
    void createsPatientAccountAndPatientRole() throws Exception {
        mockMvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNumber())
                .andExpect(jsonPath("$.patientCode").isString())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT a.account_type, a.staff_id, a.password_hash, r.code AS role_code, ar.granted_by
                FROM dbo.[Account] a
                JOIN dbo.Patient p ON p.id = a.patient_id
                JOIN dbo.AccountRole ar ON ar.account_id = a.id
                JOIN dbo.[Role] r ON r.id = ar.role_id
                WHERE a.login_name = ? AND p.full_name = ?
                """, login, fullName);
        assertEquals("PATIENT", row.get("account_type"));
        assertEquals(null, row.get("staff_id"));
        assertEquals("PATIENT", row.get("role_code"));
        assertEquals(null, row.get("granted_by"));
        assertTrue(new BCryptPasswordEncoder().matches(PASSWORD, (String) row.get("password_hash")));
    }

    @Test
    void rejectsDuplicateLoginWithoutSecondPatient() throws Exception {
        mockMvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOGIN_EXISTS"));
        assertEquals(1, countPatients());
    }

    @Test
    void rejectsClientRoleAndStaffId() throws Exception {
        String json = requestJson().replace("}", ",\"role\":\"DOCTOR\",\"staffId\":1}");
        mockMvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        assertEquals(0, countPatients());
    }

    @Test
    void savesEmergencyContactAndAcceptsEightCharacterPassword() throws Exception {
        String json = """
                {"loginName":"%s","password":"Abcd1234","fullName":"%s",
                 "identityNumber":"012345678901","emergencyName":"Nguoi than test",
                 "emergencyPhone":"0900000000"}
                """.formatted(login, fullName);
        mockMvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT p.identity_number, p.emergency_name, p.emergency_phone, a.password_hash
                FROM dbo.Patient p JOIN dbo.[Account] a ON a.patient_id = p.id
                WHERE a.login_name = ?
                """, login);
        assertEquals("012345678901", row.get("identity_number"));
        assertEquals("Nguoi than test", row.get("emergency_name"));
        assertEquals("0900000000", row.get("emergency_phone"));
        assertTrue(new BCryptPasswordEncoder().matches("Abcd1234", (String) row.get("password_hash")));
    }

    @Test
    void rejectsSevenCharacterPasswordWithoutCreatingPatient() throws Exception {
        String json = requestJson().replace(PASSWORD, "Abc1234");
        mockMvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        assertEquals(0, countPatients());
    }

    @Test
    void rollsBackPatientAndAccountWhenRoleGrantFails() {
        doThrow(new IllegalStateException("Test role failure"))
                .when(repository).grantPatientRole(anyLong(), anyLong());
        PatientRegistrationRequest request = new PatientRegistrationRequest(
                login, PASSWORD, fullName, null, null, null, null, null, null, null, null);

        assertThrows(IllegalStateException.class, () -> service.register(request));
        assertEquals(0, countPatients());
    }

    private String requestJson() {
        return "{\"loginName\":\"" + login + "\",\"password\":\"" + PASSWORD
                + "\",\"fullName\":\"" + fullName + "\"}";
    }

    private int countPatients() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Patient WHERE full_name = ?",
                Integer.class, fullName);
    }
}
