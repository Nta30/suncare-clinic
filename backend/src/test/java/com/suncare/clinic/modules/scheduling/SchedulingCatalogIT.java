package com.suncare.clinic.modules.scheduling;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.suncare.clinic.modules.clinical.dto.ClinicalServiceCreateRequest;
import com.suncare.clinic.modules.clinical.dto.ConsultationPriceCreateRequest;
import com.suncare.clinic.modules.clinical.dto.PriceEndRequest;
import com.suncare.clinic.modules.scheduling.dto.ShiftDefinitionCreateRequest;
import com.suncare.clinic.modules.scheduling.dto.ShiftDefinitionUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class SchedulingCatalogIT {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private MockMvc mvc;
    private long adminStaffId;
    private long adminAccountId;
    private long doctorStaffId;
    private long doctorAccountId;
    private long secondDoctorStaffId;
    private long secondDoctorAccountId;
    private long clinicalDeptId;
    private long specialtyId;
    private String adminToken;
    private String doctorToken;
    private String prefix;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        prefix = "P12" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        // 1. Admin account (with SYSTEM_ADMIN -> CATALOG_MANAGE)
        adminStaffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P12 Admin', 'UNKNOWN', 1)
                """, Long.class, prefix + "A");
        String adminLogin = "p12.admin." + prefix.toLowerCase();
        adminAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, adminLogin, new BCryptPasswordEncoder().encode("test-admin-password-123"), adminStaffId);
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'SYSTEM_ADMIN'
                """, adminAccountId);

        // 2. Doctor account (DOCTOR only, no CATALOG_MANAGE)
        doctorStaffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P12 Doctor 1', 'MALE', 1)
                """, Long.class, prefix + "D1");
        String doctorLogin = "p12.doc1." + prefix.toLowerCase();
        doctorAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, doctorLogin, new BCryptPasswordEncoder().encode("test-doctor-password-123"), doctorStaffId);
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'DOCTOR'
                """, doctorAccountId);

        // 3. Second Doctor account
        secondDoctorStaffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P12 Doctor 2', 'FEMALE', 1)
                """, Long.class, prefix + "D2");
        String doc2Login = "p12.doc2." + prefix.toLowerCase();
        secondDoctorAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, doc2Login, new BCryptPasswordEncoder().encode("test-doctor-password-123"), secondDoctorStaffId);
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'DOCTOR'
                """, secondDoctorAccountId);

        // Department & Specialty
        clinicalDeptId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Khoa Khám Bệnh P12', 'CLINICAL', 1)
                """, Long.class, prefix + "DEP");
        specialtyId = jdbc.queryForObject("""
                INSERT INTO dbo.Specialty (code, name, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Chuyên Khoa P12', 1)
                """, Long.class, prefix + "SPEC");

        // Login tokens
        String adminRes = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"" + adminLogin + "\",\"password\":\"test-admin-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        adminToken = json.readTree(adminRes).get("accessToken").asText();

        String docRes = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"" + doctorLogin + "\",\"password\":\"test-doctor-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        doctorToken = json.readTree(docRes).get("accessToken").asText();
    }

    @AfterEach
    void cleanUp() {
        // Clean prices
        jdbc.update("DELETE FROM dbo.ServicePrice WHERE service_id IN (SELECT id FROM dbo.[Service] WHERE code LIKE ?)", prefix + "%");
        // Clean services
        jdbc.update("DELETE FROM dbo.[Service] WHERE code LIKE ?", prefix + "%");
        // Clean specialty & dept
        jdbc.update("DELETE FROM dbo.Specialty WHERE id = ?", specialtyId);
        jdbc.update("DELETE FROM dbo.Department WHERE id = ?", clinicalDeptId);
        // Clean accounts, tokens, roles, staff, audit
        jdbc.update("DELETE FROM dbo.AuditEvent WHERE actor_id IN (?, ?, ?)", adminAccountId, doctorAccountId, secondDoctorAccountId);
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id IN (?, ?, ?)", adminAccountId, doctorAccountId, secondDoctorAccountId);
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id IN (?, ?, ?)", adminAccountId, doctorAccountId, secondDoctorAccountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE id IN (?, ?, ?)", adminAccountId, doctorAccountId, secondDoctorAccountId);
        jdbc.update("DELETE FROM dbo.Staff WHERE id IN (?, ?, ?)", adminStaffId, doctorStaffId, secondDoctorStaffId);
    }

    @Test
    void managesShiftDefinitionsAndValidatesConstraints() throws Exception {
        // 1. Doctor without CATALOG_MANAGE cannot access
        mvc.perform(get("/api/admin/scheduling/shifts").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());

        // 2. Initialize default shifts if empty
        mvc.perform(post("/api/admin/scheduling/shifts/init-defaults").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 3. List shifts
        String listRes = mvc.perform(get("/api/admin/scheduling/shifts").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode shifts = json.readTree(listRes);
        assertTrue(shifts.size() >= 2);

        JsonNode morningShift = null;
        for (JsonNode s : shifts) {
            if ("MORNING".equals(s.get("code").asText())) {
                morningShift = s;
                break;
            }
        }
        assertTrue(morningShift != null);
        long morningId = morningShift.get("id").asLong();
        String morningVersion = morningShift.get("version").asText();

        // 4. Update shift: Invalid time range (end <= start) -> 400
        ShiftDefinitionUpdateRequest invalidTimeReq = new ShiftDefinitionUpdateRequest(
                "Ca sáng cập nhật", LocalTime.of(12, 0), LocalTime.of(8, 0), true, morningVersion
        );
        mvc.perform(put("/api/admin/scheduling/shifts/" + morningId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(invalidTimeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));

        // 5. Valid update
        ShiftDefinitionUpdateRequest validUpdateReq = new ShiftDefinitionUpdateRequest(
                "Ca sáng mùa hè", LocalTime.of(7, 0), LocalTime.of(11, 30), true, morningVersion
        );
        String updateRes = mvc.perform(put("/api/admin/scheduling/shifts/" + morningId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(validUpdateReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode updatedShift = json.readTree(updateRes);
        assertEquals("Ca sáng mùa hè", updatedShift.get("name").asText());
        assertEquals("07:00:00", updatedShift.get("localStart").asText());

        // 6. Optimistic concurrency conflict: retry with old version -> 409
        mvc.perform(put("/api/admin/scheduling/shifts/" + morningId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(validUpdateReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }

    @Test
    void managesConsultationPricesAndValidatesConstraints() throws Exception {
        // 1. Create EXAMINATION service
        ClinicalServiceCreateRequest examReq = new ClinicalServiceCreateRequest(
                prefix + "EXM", "Khám chuyên khoa Tim Mạch", "EXAMINATION", null, specialtyId, true
        );
        String examRes = mvc.perform(post("/api/admin/services").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(examReq)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long serviceId = json.readTree(examRes).get("id").asLong();

        // 2. Doctor list returns active doctors
        String docListRes = mvc.perform(get("/api/admin/services/doctors").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode doctors = json.readTree(docListRes);
        assertTrue(doctors.size() >= 2);

        // 3. Amount <= 0 -> 400
        ConsultationPriceCreateRequest badAmountReq = new ConsultationPriceCreateRequest(
                doctorStaffId, "ONSITE", BigDecimal.ZERO, LocalDateTime.of(2026, 1, 1, 0, 0), null
        );
        mvc.perform(post("/api/admin/services/" + serviceId + "/consultation-prices")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(badAmountReq)))
                .andExpect(status().isBadRequest());

        // 4. Invalid visit mode -> 400
        ConsultationPriceCreateRequest badModeReq = new ConsultationPriceCreateRequest(
                doctorStaffId, "TELEPHONE", new BigDecimal("200000"), LocalDateTime.of(2026, 1, 1, 0, 0), null
        );
        mvc.perform(post("/api/admin/services/" + serviceId + "/consultation-prices")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(badModeReq)))
                .andExpect(status().isBadRequest());

        // 5. Invalid period (valid_to <= valid_from) -> 400
        ConsultationPriceCreateRequest badPeriodReq = new ConsultationPriceCreateRequest(
                doctorStaffId, "ONSITE", new BigDecimal("200000"),
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 5, 1, 0, 0)
        );
        mvc.perform(post("/api/admin/services/" + serviceId + "/consultation-prices")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(badPeriodReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));

        // 6. Valid price 1: Doctor 1, ONSITE [2026-01-01, 2026-12-31] -> 201 Created
        ConsultationPriceCreateRequest price1Req = new ConsultationPriceCreateRequest(
                doctorStaffId, "ONSITE", new BigDecimal("200000"),
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 12, 31, 23, 59, 59)
        );
        String p1Res = mvc.perform(post("/api/admin/services/" + serviceId + "/consultation-prices")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(price1Req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode p1List = json.readTree(p1Res);
        assertEquals(1, p1List.size());
        assertEquals("P12 Doctor 1", p1List.get(0).get("doctorName").asText());
        assertEquals(200000, p1List.get(0).get("amount").asLong());

        // 7. Overlapping price: Same Doctor 1, Same ONSITE [2026-06-01, 2027-06-01] -> 409 Conflict
        ConsultationPriceCreateRequest overlapReq = new ConsultationPriceCreateRequest(
                doctorStaffId, "ONSITE", new BigDecimal("250000"),
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2027, 6, 1, 0, 0)
        );
        mvc.perform(post("/api/admin/services/" + serviceId + "/consultation-prices")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(overlapReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_OVERLAP"));

        // 8. Same Doctor 1, DIFFERENT visit mode ONLINE [2026-01-01, 2026-12-31] -> 201 Created!
        ConsultationPriceCreateRequest onlineReq = new ConsultationPriceCreateRequest(
                doctorStaffId, "ONLINE", new BigDecimal("150000"),
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 12, 31, 23, 59, 59)
        );
        mvc.perform(post("/api/admin/services/" + serviceId + "/consultation-prices")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(onlineReq)))
                .andExpect(status().isCreated());

        // 9. DIFFERENT Doctor 2, Same ONSITE [2026-01-01, 2026-12-31] -> 201 Created!
        ConsultationPriceCreateRequest doc2Req = new ConsultationPriceCreateRequest(
                secondDoctorStaffId, "ONSITE", new BigDecimal("220000"),
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 12, 31, 23, 59, 59)
        );
        mvc.perform(post("/api/admin/services/" + serviceId + "/consultation-prices")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(doc2Req)))
                .andExpect(status().isCreated());

        // 10. List all consultation prices across services
        String allPricesRes = mvc.perform(get("/api/admin/services/consultation-prices")
                .header("Authorization", "Bearer " + adminToken)
                .param("serviceId", String.valueOf(serviceId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode allPrices = json.readTree(allPricesRes);
        assertEquals(3, allPrices.size());

        // 11. End price early
        long p1Id = p1List.get(0).get("id").asLong();
        String p1Version = p1List.get(0).get("version").asText();
        PriceEndRequest endReq = new PriceEndRequest(
                LocalDateTime.of(2026, 11, 15, 0, 0), p1Version
        );
        String endRes = mvc.perform(put("/api/admin/services/" + serviceId + "/consultation-prices/" + p1Id + "/end")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(endReq)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode endedList = json.readTree(endRes);
        assertEquals("2026-11-15T00:00:00Z", endedList.get(0).get("validTo").asText());
    }
}
