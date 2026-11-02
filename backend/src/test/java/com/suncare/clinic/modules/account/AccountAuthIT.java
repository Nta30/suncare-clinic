package com.suncare.clinic.modules.account;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.suncare.clinic.modules.account.repository.AccountAuthRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class AccountAuthIT {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @MockitoSpyBean private AccountAuthRepository repository;

    private MockMvc mvc;
    private String login;
    private long accountId;
    private long patientId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        login = "p04." + UUID.randomUUID().toString().replace("-", "");
        String request = "{\"loginName\":\"" + login + "\",\"password\":\"test-password-123\",\"fullName\":\"Test P04\"}";
        mvc.perform(post("/api/accounts/register").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        accountId = jdbc.queryForObject("SELECT id FROM dbo.[Account] WHERE login_name = ?", Long.class, login);
        patientId = jdbc.queryForObject("SELECT patient_id FROM dbo.[Account] WHERE id = ?", Long.class, accountId);
    }

    @AfterEach
    void cleanUp() {
        reset(repository);
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id = ?", accountId);
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", accountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE id = ?", accountId);
        jdbc.update("DELETE FROM dbo.Patient WHERE id = ?", patientId);
    }

    @Test
    void loginRollsBackTokenIfAccountUpdateFails() throws Exception {
        doThrow(new IllegalStateException("test failure")).when(repository).recordLogin(accountId);
        assertThrows(Exception.class, () -> mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"" + login + "\",\"password\":\"test-password-123\"}")));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuthToken WHERE account_id = ?", Integer.class, accountId);
        assertEquals(0, count);
    }

    @Test
    void loginMeAndLogout() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        String token = login();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.roles[0]").value("PATIENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongPasswordAndLockedAccount() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + login + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        String token = login();
        jdbc.update("UPDATE dbo.[Account] SET status = 'LOCKED' WHERE id = ?", accountId);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + login + "\",\"password\":\"test-password-123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedRoleIsGoneOnNextRequestAndForgedTokenFails() throws Exception {
        String token = login();
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", accountId);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles").isEmpty());
        String forged = token.substring(0, token.length() - 2) + "xx";
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + login + "\",\"password\":\"test-password-123\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertEquals("Bearer", body.get("tokenType").asText());
        return body.get("accessToken").asText();
    }
}
