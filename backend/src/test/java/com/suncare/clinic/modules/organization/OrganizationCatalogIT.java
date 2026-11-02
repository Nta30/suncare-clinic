package com.suncare.clinic.modules.organization;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.suncare.clinic.modules.organization.repository.OrganizationCatalogRepository;

@SpringBootTest
@ActiveProfiles("migrationtest")
class OrganizationCatalogIT {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @MockitoSpyBean private OrganizationCatalogRepository repository;
    private MockMvc mvc;
    private long adminStaffId;
    private long adminAccountId;
    private String token;
    private String code;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        code = "P08" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        adminStaffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P08 Admin', 'UNKNOWN', 1)
                """, Long.class, code + "A");
        String login = "p08.admin." + code.toLowerCase();
        adminAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, login, new BCryptPasswordEncoder().encode("test-admin-password-123"), adminStaffId);
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'SYSTEM_ADMIN'
                """, adminAccountId);
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"" + login + "\",\"password\":\"test-admin-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("accessToken").asText();
    }

    @AfterEach
    void cleanUp() {
        reset(repository);
        jdbc.update("""
                DELETE FROM dbo.DepartmentSpecialty WHERE department_id IN
                (SELECT id FROM dbo.Department WHERE code LIKE ?)
                """, code + "%");
        jdbc.update("DELETE FROM dbo.Specialty WHERE code LIKE ?", code + "%");
        jdbc.update("DELETE FROM dbo.Department WHERE code LIKE ?", code + "%");
        jdbc.update("DELETE FROM dbo.AuditEvent WHERE actor_id = ?", adminAccountId);
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id = ?", adminAccountId);
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", adminAccountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE id = ?", adminAccountId);
        jdbc.update("DELETE FROM dbo.Staff WHERE id = ?", adminStaffId);
    }

    @Test
    void managesCatalogAndKeepsDisabledLinkHistory() throws Exception {
        mvc.perform(get("/api/admin/organization")).andExpect(status().isUnauthorized());
        String department = mvc.perform(post("/api/admin/organization/departments")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(departmentBody(code + "D", "Khoa thử", "CLINICAL", true, null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long departmentId = idFor(json.readTree(department).get("departments"), code + "D");
        mvc.perform(post("/api/admin/organization/departments")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(departmentBody(code + "D", "Trùng mã", "CLINICAL", true, null)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/organization/departments")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(departmentBody(code + "BAD", "Sai loại", "UNKNOWN", true, null)))
                .andExpect(status().isBadRequest());
        String specialty = mvc.perform(post("/api/admin/organization/specialties")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(specialtyBody(code + "S", "Chuyên khoa thử", true, null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long specialtyId = idFor(json.readTree(specialty).get("specialties"), code + "S");
        String linkPath = "/api/admin/organization/departments/" + departmentId + "/specialties/" + specialtyId;
        mvc.perform(put(linkPath).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(delete(linkPath).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.DepartmentSpecialty WHERE department_id = ? AND specialty_id = ? AND is_active = 0
                """, Integer.class, departmentId, specialtyId));
        mvc.perform(put(linkPath).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.DepartmentSpecialty WHERE department_id = ? AND specialty_id = ?",
                Integer.class, departmentId, specialtyId));

        String listed = mvc.perform(get("/api/admin/organization").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String version = versionFor(json.readTree(listed).get("departments"), code + "D");
        String update = departmentBody(code + "D", "Khoa đã sửa", "CLINICAL", false, version);
        mvc.perform(put("/api/admin/organization/departments/" + departmentId)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/organization/departments/" + departmentId)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict());
        mvc.perform(put(linkPath).header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        assertEquals("Khoa đã sửa", jdbc.queryForObject("SELECT name FROM dbo.Department WHERE id = ?", String.class, departmentId));
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", adminAccountId);
        mvc.perform(get("/api/admin/organization").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDepartmentRollsBackIfAuditFails() {
        doThrow(new IllegalStateException("test audit failure")).when(repository)
                .audit(eq(adminAccountId), eq("DEPARTMENT_CREATED"), eq("Department"), anyLong());
        assertThrows(Exception.class, () -> mvc.perform(post("/api/admin/organization/departments")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(departmentBody(code + "D", "Khoa thử", "CLINICAL", true, null))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Department WHERE code = ?",
                Integer.class, code + "D"));
    }

    private long idFor(tools.jackson.databind.JsonNode rows, String targetCode) {
        for (var row : rows) if (targetCode.equals(row.get("code").asText())) return row.get("id").asLong();
        throw new IllegalStateException("Missing test row");
    }

    private String versionFor(tools.jackson.databind.JsonNode rows, String targetCode) {
        for (var row : rows) if (targetCode.equals(row.get("code").asText())) return row.get("version").asText();
        throw new IllegalStateException("Missing test row");
    }

    private String departmentBody(String itemCode, String name, String type, boolean active, String version) {
        return "{\"code\":\"" + itemCode + "\",\"name\":\"" + name + "\",\"departmentType\":\"" + type
                + "\",\"isActive\":" + active + (version == null ? "" : ",\"version\":\"" + version + "\"") + "}";
    }

    private String specialtyBody(String itemCode, String name, boolean active, String version) {
        return "{\"code\":\"" + itemCode + "\",\"name\":\"" + name + "\",\"isActive\":" + active
                + (version == null ? "" : ",\"version\":\"" + version + "\"") + "}";
    }
}
