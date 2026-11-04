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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DEPARTMENT_HEAD\"]")))
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
                .header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(2));
        mvc.perform(delete("/api/admin/staff/" + id + "/roles/DEPARTMENT_HEAD")
                .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(1));
        mvc.perform(delete("/api/admin/staff/" + id + "/roles/DOCTOR")
                .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(patch("/api/admin/staff/" + id + "/status").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isUnauthorized());
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent WHERE actor_id = ?",
                Integer.class, adminAccountId));
    }

    @Test
    void requiresDoctorBeforeGrantingDepartmentHead() throws Exception {
        String created = mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"CASHIER\"]")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("accountId").asLong();

        mvc.perform(post("/api/admin/staff/" + id + "/roles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCode\":\"DEPARTMENT_HEAD\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.AccountRole ar JOIN dbo.[Role] r ON r.id = ar.role_id
                WHERE ar.account_id = ? AND r.code = 'DEPARTMENT_HEAD'
                """, Integer.class, id));

        mvc.perform(post("/api/admin/staff/" + id + "/roles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCode\":\"DOCTOR\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/admin/staff/" + id + "/roles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCode\":\"DEPARTMENT_HEAD\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void openHeadAssignmentKeepsDoctorRoleRequired() throws Exception {
        String created = mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\",\"DEPARTMENT_HEAD\"]")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long accountId = json.readTree(created).get("accountId").asLong();
        long staffId = jdbc.queryForObject("SELECT staff_id FROM dbo.[Account] WHERE id = ?", Long.class, accountId);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long departmentId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Head Role Test', 'CLINICAL', 1)
                """, Long.class, "P07H" + suffix);
        jdbc.update("""
                INSERT INTO dbo.DepartmentHeadAssignment (staff_id, department_id, valid_from, assigned_by)
                VALUES (?, ?, DATEADD(day, -1, SYSUTCDATETIME()), ?)
                """, staffId, departmentId, adminAccountId);

        try {
            mvc.perform(delete("/api/admin/staff/" + accountId + "/roles/DEPARTMENT_HEAD")
                    .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
            mvc.perform(delete("/api/admin/staff/" + accountId + "/roles/DOCTOR")
                    .header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
            jdbc.update("UPDATE dbo.DepartmentHeadAssignment SET valid_to = DATEADD(second, -1, SYSUTCDATETIME()) WHERE staff_id = ?", staffId);
            mvc.perform(delete("/api/admin/staff/" + accountId + "/roles/DOCTOR")
                    .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        } finally {
            jdbc.update("DELETE FROM dbo.DepartmentHeadAssignment WHERE staff_id = ?", staffId);
            jdbc.update("DELETE FROM dbo.Department WHERE id = ?", departmentId);
        }
    }

    @Test
    void legacyHeadRoleWithoutDoctorDoesNotGrantHeadAccess() throws Exception {
        String created = mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\",\"DEPARTMENT_HEAD\"]")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long accountId = json.readTree(created).get("accountId").asLong();
        jdbc.update("""
                DELETE ar FROM dbo.AccountRole ar JOIN dbo.[Role] r ON r.id = ar.role_id
                WHERE ar.account_id = ? AND r.code = 'DOCTOR'
                """, accountId);

        String loginBody = "{\"loginName\":\"" + staffLogin + "\",\"password\":\"test-staff-password-123\"}";
        String staffToken = json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
        var current = json.readTree(mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertEquals(0, current.get("roles").size());
        assertEquals(0, current.get("permissions").size());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AccountRole WHERE account_id = ?", Integer.class, accountId) > 0);
    }

    @Test
    void staffListIncludesCurrentDepartmentsAndSpecialties() throws Exception {
        String created = mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\"]")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long accountId = json.readTree(created).get("accountId").asLong();
        long staffId = jdbc.queryForObject("SELECT staff_id FROM dbo.[Account] WHERE id = ?", Long.class, accountId);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long departmentId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P07 Test Department', 'CLINICAL', 1)
                """, Long.class, "P07D" + suffix);
        long specialtyId = jdbc.queryForObject("""
                INSERT INTO dbo.Specialty (code, name, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P07 Test Specialty', 1)
                """, Long.class, "P07S" + suffix);
        try {
            jdbc.update("INSERT INTO dbo.DepartmentSpecialty (department_id, specialty_id, is_active) VALUES (?, ?, 1)", departmentId, specialtyId);
            jdbc.update("INSERT INTO dbo.StaffDepartment (staff_id, department_id, valid_from, is_primary) VALUES (?, ?, DATEADD(day, -1, SYSUTCDATETIME()), 1)", staffId, departmentId);
            jdbc.update("INSERT INTO dbo.StaffSpecialty (staff_id, specialty_id, is_active) VALUES (?, ?, 1)", staffId, specialtyId);

            String listed = mvc.perform(get("/api/admin/staff").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var rows = json.readTree(listed);
            var staff = java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                    .filter(row -> row.get("accountId").asLong() == accountId).findFirst().orElseThrow();
            assertEquals("P07 Test Department", staff.get("departments").get(0).asText());
            assertEquals("P07 Test Specialty", staff.get("specialties").get(0).asText());
        } finally {
            jdbc.update("DELETE FROM dbo.StaffSpecialty WHERE staff_id = ?", staffId);
            jdbc.update("DELETE FROM dbo.StaffDepartment WHERE staff_id = ?", staffId);
            jdbc.update("DELETE FROM dbo.DepartmentSpecialty WHERE department_id = ?", departmentId);
            jdbc.update("DELETE FROM dbo.Specialty WHERE id = ?", specialtyId);
            jdbc.update("DELETE FROM dbo.Department WHERE id = ?", departmentId);
        }
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

    @Test
    void updatesStaffProfileAndRejectsStaleVersionAndProtectedAdmin() throws Exception {
        String created = mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\"]")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("accountId").asLong();
        String listed = mvc.perform(get("/api/admin/staff").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var rows = json.readTree(listed);
        String version = null;
        for (var row : rows) if (row.get("accountId").asLong() == id) version = row.get("version").asText();
        String body = updateRequest(version);
        mvc.perform(put("/api/admin/staff/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"\",\"sex\":\"MALE\",\"version\":\"" + version + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/staff/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Nguyễn Văn Test"))
                .andExpect(jsonPath("$.professionalLicense").value("GP-TEST"));
        assertEquals("Nguyễn Văn Test", jdbc.queryForObject("""
                SELECT s.full_name FROM dbo.Staff s JOIN dbo.[Account] a ON a.staff_id = s.id WHERE a.id = ?
                """, String.class, id));
        mvc.perform(put("/api/admin/staff/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/staff/" + adminAccountId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/staff/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isUnauthorized());
    }

    @Test
    void profileUpdateRollsBackWhenAuditFails() throws Exception {
        String created = mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(request("[\"DOCTOR\"]")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("accountId").asLong();
        String listed = mvc.perform(get("/api/admin/staff").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String version = null;
        for (var row : json.readTree(listed)) if (row.get("accountId").asLong() == id) version = row.get("version").asText();
        doThrow(new IllegalStateException("test audit failure")).when(repository)
                .audit(adminAccountId, "STAFF_PROFILE_UPDATED", id, "profile fields updated");
        String body = updateRequest(version);
        assertThrows(Exception.class, () -> mvc.perform(put("/api/admin/staff/" + id)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body)));
        assertEquals("Demo Staff", jdbc.queryForObject("""
                SELECT s.full_name FROM dbo.Staff s JOIN dbo.[Account] a ON a.staff_id = s.id WHERE a.id = ?
                """, String.class, id));
    }

    private String updateRequest(String version) {
        return "{\"fullName\":\"Nguyễn Văn Test\",\"sex\":\"MALE\","
                + "\"phone\":\"0900000000\",\"professionalLicense\":\"GP-TEST\","
                + "\"version\":\"" + version + "\"}";
    }

    private String request(String roles) {
        return "{\"loginName\":\"" + staffLogin + "\",\"password\":\"test-staff-password-123\","
                + "\"fullName\":\"Demo Staff\",\"roles\":" + roles + "}";
    }
}
