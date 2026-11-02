package com.suncare.clinic.modules.account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.suncare.clinic.modules.account.repository.PersonalProfileRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class PersonalProfileIT {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PersonalProfileRepository profiles;
    private MockMvc mvc;
    private long accountId;
    private long patientId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String login = "p06." + UUID.randomUUID().toString().replace("-", "");
        mvc.perform(post("/api/accounts/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"" + login + "\",\"password\":\"test-password-123\",\"fullName\":\"Test P06\"}"))
                .andExpect(status().isCreated());
        accountId = jdbc.queryForObject("SELECT id FROM dbo.[Account] WHERE login_name = ?", Long.class, login);
        patientId = jdbc.queryForObject("SELECT patient_id FROM dbo.[Account] WHERE id = ?", Long.class, accountId);
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"" + login + "\",\"password\":\"test-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("accessToken").asText();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id = ?", accountId);
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", accountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE id = ?", accountId);
        jdbc.update("DELETE FROM dbo.Patient WHERE id = ?", patientId);
    }

    @Test
    void readsAndUpdatesOnlyOwnProfileWithVersionCheck() throws Exception {
        mvc.perform(get("/api/accounts/profile")).andExpect(status().isUnauthorized());
        String original = mvc.perform(get("/api/accounts/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Test P06"))
                .andReturn().getResponse().getContentAsString();
        String version = json.readTree(original).get("version").asText();
        String request = "{\"fullName\":\"New Name\",\"sex\":\"UNKNOWN\",\"identityNumber\":\"123456\",\"version\":\"" + version + "\"}";
        mvc.perform(put("/api/accounts/profile").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("New Name"));
        mvc.perform(put("/api/accounts/profile").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
        assertEquals("123456", jdbc.queryForObject("SELECT identity_number FROM dbo.Patient WHERE id = ?", String.class, patientId));
        assertEquals("PATIENT", jdbc.queryForObject("SELECT account_type FROM dbo.[Account] WHERE id = ?", String.class, accountId));
    }

    @Test
    void rejectsInvalidInputAndLockedAccount() throws Exception {
        mvc.perform(put("/api/accounts/profile").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"\",\"sex\":\"INVALID\",\"version\":\"bad\"}"))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE dbo.[Account] SET status = 'LOCKED' WHERE id = ?", accountId);
        mvc.perform(get("/api/accounts/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readsStaffProfessionalDetailsWithoutPatientFields() {
        String code = "P06S" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long staffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, professional_license, qualification, is_active)
                OUTPUT INSERTED.id VALUES (?, ?, 'UNKNOWN', ?, ?, 1)
                """, Long.class, code, "Demo Doctor", "LICENSE-DEMO", "Bác sĩ chuyên khoa");
        long staffAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, 'test-hash', 'STAFF', ?, 'ACTIVE')
                """, Long.class, "p06.staff." + code, staffId);
        try {
            var result = profiles.find(staffAccountId, "STAFF");
            assertEquals("LICENSE-DEMO", result.professionalLicense());
            assertEquals("Bác sĩ chuyên khoa", result.qualification());
            assertEquals(null, result.identityNumber());
        } finally {
            jdbc.update("DELETE FROM dbo.[Account] WHERE id = ?", staffAccountId);
            jdbc.update("DELETE FROM dbo.Staff WHERE id = ?", staffId);
        }
    }
}
