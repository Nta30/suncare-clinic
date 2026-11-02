package com.suncare.clinic.modules.account;

import com.suncare.clinic.modules.account.repository.StaffManagementRepository;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class StaffManagementIT {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @MockitoSpyBean private StaffManagementRepository repository;
    private MockMvc mvc;
    private long adminStaffId;
    private long adminAccountId;
    private String token;
    private String staffLogin;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String adminLogin = "p07.admin." + suffix;
        staffLogin = "p07.staff." + suffix;
        adminStaffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Test Admin', 'UNKNOWN', 1)
                """, Long.class, "P07A" + suffix);
        adminAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, adminLogin, new BCryptPasswordEncoder().encode("test-admin-password-123"), adminStaffId);
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'SYSTEM_ADMIN'
                """, adminAccountId);
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"" + adminLogin + "\",\"password\":\"test-admin-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("accessToken").asText();
    }

    @AfterEach
    void cleanUp() {
        reset(repository);
        jdbc.update("DELETE FROM dbo.AuditEvent WHERE actor_id = ?", adminAccountId);
        var staffIds = jdbc.queryForList("SELECT staff_id FROM dbo.[Account] WHERE login_name = ?", Long.class, staffLogin);
        for (long staffId : staffIds) {
            long accountId = jdbc.queryForObject("SELECT id FROM dbo.[Account] WHERE staff_id = ?", Long.class, staffId);
            jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id = ?", accountId);
            jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", accountId);
            jdbc.update("DELETE FROM dbo.[Account] WHERE id = ?", accountId);
            jdbc.update("DELETE FROM dbo.Staff WHERE id = ?", staffId);
        }
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id = ?", adminAccountId);
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", adminAccountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE id = ?", adminAccountId);
        jdbc.update("DELETE FROM dbo.Staff WHERE id = ?", adminStaffId);
    }

    @Test
    void createsStaffAccountAndTwoRolesWithAdminGrantor() throws Exception {
        String response = mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\",\"DEPARTMENT_HEAD\"]")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        long createdId = json.readTree(response).get("accountId").asLong();
        assertEquals("STAFF", jdbc.queryForObject("SELECT account_type FROM dbo.[Account] WHERE id = ?", String.class, createdId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AccountRole WHERE account_id = ? AND granted_by = ?",
                Integer.class, createdId, adminAccountId));
        String hash = jdbc.queryForObject("SELECT password_hash FROM dbo.[Account] WHERE id = ?", String.class, createdId);
        assertEquals(true, new BCryptPasswordEncoder().matches("test-staff-password-123", hash));
    }

    @Test
    void rejectsAnonymousDuplicateAndInvalidRole() throws Exception {
        mvc.perform(post("/api/admin/staff").contentType(MediaType.APPLICATION_JSON)
                .content(request("[\"DOCTOR\"]"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"PATIENT\"]")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\"]")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\"]")))
                .andExpect(status().isConflict());
    }

    @Test
    void rollsBackStaffAndAccountIfRoleGrantFails() {
        int staffBefore = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Staff WHERE full_name = 'Demo Staff'", Integer.class);
        doThrow(new IllegalStateException("test failure")).when(repository).grantRole(anyLong(), anyLong(), anyLong());
        assertThrows(Exception.class, () -> mvc.perform(post("/api/admin/staff")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(request("[\"DOCTOR\"]"))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[Account] WHERE login_name = ?", Integer.class, staffLogin));
        assertEquals(staffBefore, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Staff WHERE full_name = 'Demo Staff'", Integer.class));
    }

    @Test
    void revokedAdminPermissionCannotCreateStaffOnNextRequest() throws Exception {
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", adminAccountId);
        mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\"]")))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[Account] WHERE login_name = ?", Integer.class, staffLogin));
    }

    @Test
    void managesRolesAndLockWithImmediateEffect() throws Exception {
        String created = mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\"]")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("accountId").asLong();
        mvc.perform(get("/api/admin/staff").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String staffLoginBody = "{\"loginName\":\"" + staffLogin + "\",\"password\":\"test-staff-password-123\"}";
        String staffToken = json.readTree(mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(staffLoginBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
        mvc.perform(get("/api/admin/staff").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/staff/" + id + "/roles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCode\":\"DEPARTMENT_HEAD\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(2));
        mvc.perform(delete("/api/admin/staff/" + id + "/roles/DOCTOR")
                .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("DEPARTMENT_HEAD"));
        mvc.perform(patch("/api/admin/staff/" + id + "/status").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isUnauthorized());
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent WHERE actor_id = ?",
                Integer.class, adminAccountId));
    }

    @Test
    void protectsAdminAccountAndRejectsUnknownRole() throws Exception {
        mvc.perform(patch("/api/admin/staff/" + adminAccountId + "/status")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isForbidden());
        String created = mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\"]")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("accountId").asLong();
        mvc.perform(post("/api/admin/staff/" + id + "/roles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCode\":\"SYSTEM_ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/staff/" + id + "/roles/SYSTEM_ADMIN")
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    }

    @Test
    void roleGrantRollsBackWhenAuditFails() throws Exception {
        String created = mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\"]")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("accountId").asLong();
        doThrow(new IllegalStateException("test audit failure")).when(repository)
                .audit(adminAccountId, "STAFF_ROLE_GRANTED", id, "role=LAB_STAFF");
        assertThrows(Exception.class, () -> mvc.perform(post("/api/admin/staff/" + id + "/roles")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCode\":\"LAB_STAFF\"}")));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.AccountRole ar JOIN dbo.[Role] r ON r.id = ar.role_id
                WHERE ar.account_id = ? AND r.code = 'LAB_STAFF'
                """, Integer.class, id));
    }

    private String request(String roles) {
        return "{\"loginName\":\"" + staffLogin + "\",\"password\":\"test-staff-password-123\","
                + "\"fullName\":\"Demo Staff\",\"roles\":" + roles + "}";
    }
}
