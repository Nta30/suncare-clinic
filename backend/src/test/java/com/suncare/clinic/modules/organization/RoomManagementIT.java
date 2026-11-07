package com.suncare.clinic.modules.organization;

import com.suncare.clinic.modules.organization.repository.OrganizationCatalogRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class RoomManagementIT {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @MockitoSpyBean private OrganizationCatalogRepository audit;
    private MockMvc mvc;
    private long adminStaffId;
    private long adminAccountId;
    private long clinicalId;
    private long labId;
    private String token;
    private String code;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        code = "P09" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        adminStaffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P09 Admin', 'UNKNOWN', 1)
                """, Long.class, code + "A");
        String login = "p09.admin." + code.toLowerCase();
        adminAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, login, new BCryptPasswordEncoder().encode("test-admin-password-123"), adminStaffId);
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'SYSTEM_ADMIN'
                """, adminAccountId);
        clinicalId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Khoa thử P09', 'CLINICAL', 1)
                """, Long.class, code + "C");
        labId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Khoa CLS thử P09', 'LAB', 1)
                """, Long.class, code + "L");
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"" + login + "\",\"password\":\"test-admin-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("accessToken").asText();
    }

    @AfterEach
    void cleanUp() {
        reset(audit);
        jdbc.update("DELETE FROM dbo.Room WHERE code LIKE ?", code + "%");
        jdbc.update("DELETE FROM dbo.Department WHERE id IN (?, ?)", clinicalId, labId);
        jdbc.update("DELETE FROM dbo.AuditEvent WHERE actor_id = ?", adminAccountId);
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id = ?", adminAccountId);
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", adminAccountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE id = ?", adminAccountId);
        jdbc.update("DELETE FROM dbo.Staff WHERE id = ?", adminStaffId);
    }

    @Test
    void createsFiltersAndUpdatesRoomWithMatchingDepartment() throws Exception {
        mvc.perform(get("/api/admin/organization/rooms")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/organization/rooms").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody(code + "R", clinicalId, "LAB", true, null)))
                .andExpect(status().isBadRequest());
        String created = mvc.perform(post("/api/admin/organization/rooms").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody(code + "R", clinicalId, "CONSULTATION", true, null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = idFor(json.readTree(created), code + "R");
        String version = versionFor(json.readTree(created), code + "R");
        mvc.perform(post("/api/admin/organization/rooms").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody(code + "R", labId, "LAB", true, null)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/organization/rooms/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(roomBody(code + "R", labId, "LAB", true, version)))
                .andExpect(status().isConflict());
        String update = roomBody(code + "R", clinicalId, "CONSULTATION", false, version);
        String updated = mvc.perform(put("/api/admin/organization/rooms/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String onlineRoom = mvc.perform(post("/api/admin/organization/rooms").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(roomBody(code + "O", clinicalId, "ONLINE", true, null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals("ONLINE", roomTypeFor(json.readTree(onlineRoom), code + "O"));
        mvc.perform(post("/api/admin/organization/rooms").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(roomBody(code + "O2", labId, "ONLINE", true, null)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/organization/rooms/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("SELECT CAST(is_active AS INT) FROM dbo.Room WHERE id = ?", Integer.class, id));
        jdbc.update("UPDATE dbo.Department SET is_active = 0 WHERE id = ?", clinicalId);
        mvc.perform(put("/api/admin/organization/rooms/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roomBody(code + "R", clinicalId, "CONSULTATION", true,
                        versionFor(json.readTree(updated), code + "R"))))
                .andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", adminAccountId);
        mvc.perform(get("/api/admin/organization/rooms").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void creationRollsBackWhenAuditFails() {
        doThrow(new IllegalStateException("test audit failure")).when(audit)
                .audit(eq(adminAccountId), eq("ROOM_CREATED"), eq("Room"), anyLong(), eq("department_id=" + clinicalId));
        assertThrows(Exception.class, () -> mvc.perform(post("/api/admin/organization/rooms")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(roomBody(code + "R", clinicalId, "CONSULTATION", true, null))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Room WHERE code = ?", Integer.class, code + "R"));
    }

    private long idFor(tools.jackson.databind.JsonNode rows, String targetCode) {
        for (var row : rows) if (targetCode.equals(row.get("code").asText())) return row.get("id").asLong();
        throw new IllegalStateException("Missing test room");
    }

    private String versionFor(tools.jackson.databind.JsonNode rows, String targetCode) {
        for (var row : rows) if (targetCode.equals(row.get("code").asText())) return row.get("version").asText();
        throw new IllegalStateException("Missing test room");
    }

    private String roomTypeFor(tools.jackson.databind.JsonNode rows, String targetCode) {
        for (var row : rows) if (targetCode.equals(row.get("code").asText())) return row.get("roomType").asText();
        throw new IllegalStateException("Missing test room");
    }

    private String roomBody(String roomCode, long departmentId, String type, boolean active, String version) {
        return "{\"code\":\"" + roomCode + "\",\"name\":\"Phòng thử P09\",\"roomType\":\"" + type
                + "\",\"departmentId\":" + departmentId + ",\"isActive\":" + active
                + (version == null ? "" : ",\"version\":\"" + version + "\"") + "}";
    }
}
