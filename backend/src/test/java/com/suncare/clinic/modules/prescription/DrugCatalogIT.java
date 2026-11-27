package com.suncare.clinic.modules.prescription;

import com.suncare.clinic.modules.organization.repository.OrganizationCatalogRepository;
import org.junit.jupiter.api.*;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("migrationtest")
class DrugCatalogIT {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean OrganizationCatalogRepository audit;
    MockMvc mvc;
    String prefix;
    String adminToken;
    String doctorToken;
    long adminId;
    private final List<Long> accountIds = new ArrayList<>();
    private final List<Long> staffIds = new ArrayList<>();

    @BeforeEach
    void prepare() throws Exception {
        assertEquals("SuncareClinicMigrationTest", jdbc.queryForObject("SELECT DB_NAME()", String.class));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        prefix = "P22A" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        adminToken = account("SYSTEM_ADMIN");
        adminId = accountIds.getFirst();
        doctorToken = account("DOCTOR");
    }

    private String account(String role) throws Exception {
        long staffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P22 fictional staff', 'UNKNOWN', 1)
                """, Long.class, prefix + role);
        staffIds.add(staffId);
        String login = "p22a." + prefix.toLowerCase() + "." + role.toLowerCase();
        long id = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, login, new BCryptPasswordEncoder().encode("fictional-test-password-123"), staffId);
        accountIds.add(id);
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code=?
                """, id, role);
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("loginName", login, "password", "fictional-test-password-123"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("accessToken").asText();
    }

    @AfterEach
    void cleanup() {
        reset(audit);
        assertEquals("SuncareClinicMigrationTest", jdbc.queryForObject("SELECT DB_NAME()", String.class));
        if (prefix != null) jdbc.update("DELETE FROM dbo.Drug WHERE code LIKE ?", prefix + "%");
        for (long id : accountIds) {
            jdbc.update("DELETE FROM dbo.AuditEvent WHERE actor_id=?", id);
            jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id=?", id);
            jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id=?", id);
            jdbc.update("DELETE FROM dbo.[Account] WHERE id=?", id);
        }
        for (long id : staffIds) jdbc.update("DELETE FROM dbo.Staff WHERE id=?", id);
    }

    Map<String, Object> payload(String suffix) {
        var data = new LinkedHashMap<String, Object>();
        data.put("code", prefix + suffix);
        data.put("name", " Thuốc thử Đường Nguyễn ");
        data.put("ingredient", " Hoạt chất giả ");
        data.put("strength", " 100 mg ");
        data.put("dosageForm", " Viên nén ");
        data.put("doseUnit", "viên");
        data.put("dispensingUnit", "viên");
        data.put("isActive", true);
        return data;
    }

    JsonNode create(String suffix) throws Exception {
        String response = mvc.perform(post("/api/admin/drugs").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload(suffix))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (var item : json.readTree(response)) if (item.get("code").asText().equals(prefix + suffix)) return item;
        throw new AssertionError("Created drug absent");
    }

    @Test
    void createsUpdatesDeactivatesAndKeepsCodeAndVersion() throws Exception {
        var item = create("ONE");
        assertEquals("Thuốc thử Đường Nguyễn", item.get("name").asText());
        assertEquals("Hoạt chất giả", item.get("ingredient").asText());
        assertEquals("viên", item.get("doseUnit").asText());
        assertEquals("viên", item.get("dispensingUnit").asText());
        assertEquals(8, Base64.getDecoder().decode(item.get("version").asText()).length);
        var data = payload("ONE");
        data.put("name", "Thuốc đã sửa");
        data.put("ingredient", " ");
        data.put("isActive", false);
        data.put("version", item.get("version").asText());
        mvc.perform(put("/api/admin/drugs/" + item.get("id").asLong()).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andExpect(status().isOk());
        var saved = jdbc.queryForMap("SELECT name,ingredient,is_active,version FROM dbo.Drug WHERE id=?", item.get("id").asLong());
        assertEquals("Thuốc đã sửa", saved.get("name"));
        assertNull(saved.get("ingredient"));
        assertEquals(false, saved.get("is_active"));
        assertFalse(Arrays.equals(Base64.getDecoder().decode(item.get("version").asText()), (byte[]) saved.get("version")));
        mvc.perform(get("/api/admin/drugs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Thuốc đã sửa")));
        mvc.perform(put("/api/admin/drugs/" + item.get("id").asLong()).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRUG_CHANGED"));
        data.put("code", prefix + "OTHER");
        data.put("version", Base64.getEncoder().encodeToString((byte[]) saved.get("version")));
        mvc.perform(put("/api/admin/drugs/" + item.get("id").asLong()).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRUG_CODE_FIXED"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent WHERE actor_id=? AND entity_type='Drug'", Integer.class, adminId));
    }

    @Test
    void rejectsInvalidDuplicateAndUnknownFields() throws Exception {
        var item = create("TWO");
        mvc.perform(post("/api/admin/drugs").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload("TWO"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRUG_EXISTS"));
        var data = payload("BAD");
        data.put("name", " ");
        data.put("dispensingUnit", "");
        data.put("strength", "x".repeat(101));
        mvc.perform(post("/api/admin/drugs").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.dispensingUnit").exists());
        data = payload("BAD");
        data.put("price", 1000);
        mvc.perform(post("/api/admin/drugs").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andExpect(status().isBadRequest());
        data = payload("TWO");
        data.put("version", "bad");
        mvc.perform(put("/api/admin/drugs/" + item.get("id").asLong()).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_VERSION"));
        data.put("version", item.get("version").asText());
        mvc.perform(put("/api/admin/drugs/9223372036854775807").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Drug WHERE code LIKE ?", Integer.class, prefix + "%"));
    }

    @Test
    void enforcesPermissionForEveryOperationAndRevocation() throws Exception {
        var item = create("THREE");
        mvc.perform(get("/api/admin/drugs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/drugs").header("Authorization", "Bearer " + doctorToken)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/drugs").header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload("BAD")))).andExpect(status().isForbidden());
        var data = payload("THREE");
        data.put("version", item.get("version").asText());
        mvc.perform(put("/api/admin/drugs/" + item.get("id").asLong()).header("Authorization", "Bearer " + doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/drugs/" + item.get("id").asLong()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id=?", adminId);
        mvc.perform(get("/api/admin/drugs").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/drugs").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload("BAD")))).andExpect(status().isForbidden());
    }

    @Test
    void rollsBackCreateAndUpdateWhenAuditFails() throws Exception {
        var item = create("FOUR");
        doThrow(new IllegalStateException("Test audit failure")).when(audit).audit(anyLong(), anyString(), eq("Drug"), anyLong());
        assertThrows(Exception.class, () -> mvc.perform(post("/api/admin/drugs").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload("FAIL")))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Drug WHERE code=?", Integer.class, prefix + "FAIL"));
        var data = payload("FOUR");
        data.put("name", "Must rollback");
        data.put("isActive", false);
        data.put("version", item.get("version").asText());
        assertThrows(Exception.class, () -> mvc.perform(put("/api/admin/drugs/" + item.get("id").asLong())
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))));
        assertEquals(item.get("name").asText(), jdbc.queryForObject("SELECT name FROM dbo.Drug WHERE id=?", String.class, item.get("id").asLong()));
        assertEquals(true, jdbc.queryForObject("SELECT is_active FROM dbo.Drug WHERE id=?", Boolean.class, item.get("id").asLong()));
    }
}
