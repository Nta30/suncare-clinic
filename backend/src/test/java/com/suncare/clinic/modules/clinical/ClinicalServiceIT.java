package com.suncare.clinic.modules.clinical;

import com.suncare.clinic.modules.clinical.dto.ClinicalServiceCreateRequest;
import com.suncare.clinic.modules.clinical.dto.ClinicalServiceUpdateRequest;
import com.suncare.clinic.modules.clinical.dto.PriceEndRequest;
import com.suncare.clinic.modules.clinical.dto.RoomServiceToggleRequest;
import com.suncare.clinic.modules.clinical.dto.ServicePriceCreateRequest;
import com.suncare.clinic.modules.clinical.repository.ClinicalServiceRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class ClinicalServiceIT {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @MockitoSpyBean private ClinicalServiceRepository repository;

    private MockMvc mvc;
    private long adminStaffId;
    private long adminAccountId;
    private long doctorStaffId;
    private long doctorAccountId;
    private long clinicalDeptId;
    private long labDeptId;
    private long labRoomId;
    private long consultationRoomId;
    private long specialtyId;
    private String adminToken;
    private String doctorToken;
    private String prefix;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        prefix = "P11" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        // 1. Admin
        adminStaffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P11 Admin', 'UNKNOWN', 1)
                """, Long.class, prefix + "A");
        String adminLogin = "p11.admin." + prefix.toLowerCase();
        adminAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, adminLogin, new BCryptPasswordEncoder().encode("test-admin-password-123"), adminStaffId);
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'SYSTEM_ADMIN'
                """, adminAccountId);

        // 2. Doctor (no CATALOG_MANAGE)
        doctorStaffId = jdbc.queryForObject("""
                INSERT INTO dbo.Staff (code, full_name, sex, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P11 Doctor', 'MALE', 1)
                """, Long.class, prefix + "D");
        String doctorLogin = "p11.doctor." + prefix.toLowerCase();
        doctorAccountId = jdbc.queryForObject("""
                INSERT INTO dbo.[Account] (login_name, password_hash, account_type, staff_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')
                """, Long.class, doctorLogin, new BCryptPasswordEncoder().encode("test-doctor-password-123"), doctorStaffId);
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'DOCTOR'
                """, doctorAccountId);

        // 3. Departments
        clinicalDeptId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Khoa Lâm Sàng P11', 'CLINICAL', 1)
                """, Long.class, prefix + "CD");
        labDeptId = jdbc.queryForObject("""
                INSERT INTO dbo.Department (code, name, department_type, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Khoa Xét Nghiệm P11', 'LAB', 1)
                """, Long.class, prefix + "LD");

        // 4. Rooms
        consultationRoomId = jdbc.queryForObject("""
                INSERT INTO dbo.Room (code, name, room_type, department_id, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Phòng Khám 101', 'CONSULTATION', ?, 1)
                """, Long.class, prefix + "R1", clinicalDeptId);
        labRoomId = jdbc.queryForObject("""
                INSERT INTO dbo.Room (code, name, room_type, department_id, is_active)
                OUTPUT INSERTED.id VALUES (?, 'Phòng X-Quang 201', 'LAB', ?, 1)
                """, Long.class, prefix + "R2", labDeptId);

        // 5. Specialty
        specialtyId = jdbc.queryForObject("""
                INSERT INTO dbo.Specialty (code, name, is_active)
                OUTPUT INSERTED.id VALUES (?, 'P11 Cardiology Specialty', 1)
                """, Long.class, prefix + "SP");

        // 6. Login tokens
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
        reset(repository);
        // Clean room services
        jdbc.update("DELETE FROM dbo.RoomService WHERE room_id IN (?, ?)", consultationRoomId, labRoomId);
        // Clean service prices
        jdbc.update("DELETE FROM dbo.ServicePrice WHERE service_id IN (SELECT id FROM dbo.[Service] WHERE code LIKE ?)", prefix + "%");
        // Clean services
        jdbc.update("DELETE FROM dbo.[Service] WHERE code LIKE ?", prefix + "%");
        // Clean rooms
        jdbc.update("DELETE FROM dbo.Room WHERE id IN (?, ?)", consultationRoomId, labRoomId);
        // Clean specialty
        jdbc.update("DELETE FROM dbo.Specialty WHERE id = ?", specialtyId);
        // Clean departments
        jdbc.update("DELETE FROM dbo.Department WHERE id IN (?, ?)", clinicalDeptId, labDeptId);
        // Clean accounts & tokens & audit
        jdbc.update("DELETE FROM dbo.AuditEvent WHERE actor_id IN (?, ?)", adminAccountId, doctorAccountId);
        jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id IN (?, ?)", adminAccountId, doctorAccountId);
        jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id IN (?, ?)", adminAccountId, doctorAccountId);
        jdbc.update("DELETE FROM dbo.[Account] WHERE id IN (?, ?)", adminAccountId, doctorAccountId);
        jdbc.update("DELETE FROM dbo.Staff WHERE id IN (?, ?)", adminStaffId, doctorStaffId);
    }

    @Test
    void createsListsAndFiltersServices() throws Exception {
        // 1. Unauthenticated -> 401
        mvc.perform(get("/api/admin/services")).andExpect(status().isUnauthorized());

        // 2. Doctor (without CATALOG_MANAGE) -> 403
        mvc.perform(get("/api/admin/services").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());

        // 3. EXAMINATION service with invalid result_type -> 400
        ClinicalServiceCreateRequest badExamReq = new ClinicalServiceCreateRequest(
                prefix + "EX1", "Khám Tim Mạch", "EXAMINATION", "LAB", specialtyId, true
        );
        mvc.perform(post("/api/admin/services").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(badExamReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESULT_TYPE"));

        // 4. Valid EXAMINATION service -> 201
        ClinicalServiceCreateRequest validExamReq = new ClinicalServiceCreateRequest(
                prefix + "EX1", "Khám Tim Mạch", "EXAMINATION", null, specialtyId, true
        );
        String createdExamStr = mvc.perform(post("/api/admin/services").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(validExamReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(prefix + "EX1"))
                .andExpect(jsonPath("$.serviceType").value("EXAMINATION"))
                .andReturn().getResponse().getContentAsString();
        JsonNode examNode = json.readTree(createdExamStr);
        long examId = examNode.get("id").asLong();

        // 5. Valid CLS service -> 201
        ClinicalServiceCreateRequest validClsReq = new ClinicalServiceCreateRequest(
                prefix + "CLS1", "Chụp X-Quang Ngực", "CLS", "IMAGING", null, true
        );
        String createdClsStr = mvc.perform(post("/api/admin/services").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(validClsReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(prefix + "CLS1"))
                .andExpect(jsonPath("$.resultType").value("IMAGING"))
                .andReturn().getResponse().getContentAsString();
        JsonNode clsNode = json.readTree(createdClsStr);
        long clsId = clsNode.get("id").asLong();

        // 6. Duplicate code -> 409
        mvc.perform(post("/api/admin/services").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(validExamReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));

        // 7. List and filter by type
        String listExamStr = mvc.perform(get("/api/admin/services?type=EXAMINATION").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode examList = json.readTree(listExamStr);
        assertTrue(examList.isArray());
        assertTrue(examList.size() >= 1);

        String listClsStr = mvc.perform(get("/api/admin/services?type=CLS").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode clsList = json.readTree(listClsStr);
        assertTrue(clsList.isArray());
        assertTrue(clsList.size() >= 1);
    }

    @Test
    void updatesServiceWithOptimisticLocking() throws Exception {
        // Create CLS service
        ClinicalServiceCreateRequest req = new ClinicalServiceCreateRequest(
                prefix + "UP1", "Siêu Âm Tim", "CLS", "IMAGING", null, true
        );
        String created = mvc.perform(post("/api/admin/services").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(created);
        long id = node.get("id").asLong();
        String initialVersion = node.get("version").asText();

        // Update successfully
        ClinicalServiceUpdateRequest updateReq = new ClinicalServiceUpdateRequest(
                "Sieu Am Tim Mau 4D", "IMAGING", specialtyId, true, initialVersion
        );
        String updated = mvc.perform(put("/api/admin/services/" + id).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode updatedNode = json.readTree(updated);
        assertEquals("Sieu Am Tim Mau 4D", updatedNode.get("name").asText());
        assertEquals("P11 Cardiology Specialty", updatedNode.get("specialtyName").asText());
        String newVersion = updatedNode.get("version").asText();

        // Stale update using old version -> 409
        ClinicalServiceUpdateRequest staleReq = new ClinicalServiceUpdateRequest(
                "Tên Cũ", "IMAGING", null, false, initialVersion
        );
        mvc.perform(put("/api/admin/services/" + id).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(staleReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }

    @Test
    void managesRoomServiceSupportAndValidatesConstraints() throws Exception {
        // 1. Create EXAMINATION service & CLS service
        ClinicalServiceCreateRequest examReq = new ClinicalServiceCreateRequest(
                prefix + "EXR", "Khám Nội", "EXAMINATION", null, null, true
        );
        String examStr = mvc.perform(post("/api/admin/services").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(examReq)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long examId = json.readTree(examStr).get("id").asLong();

        ClinicalServiceCreateRequest clsReq = new ClinicalServiceCreateRequest(
                prefix + "CLSR", "Nội Soi Tiêu Hóa", "CLS", "LAB", null, true
        );
        String clsStr = mvc.perform(post("/api/admin/services").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(clsReq)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long clsId = json.readTree(clsStr).get("id").asLong();

        // 2. Reject room linking for EXAMINATION service -> 400
        mvc.perform(put("/api/admin/services/" + examId + "/rooms/" + labRoomId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new RoomServiceToggleRequest(true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SERVICE_TYPE"));

        // 3. Reject linking CONSULTATION room for CLS service -> 400
        mvc.perform(put("/api/admin/services/" + clsId + "/rooms/" + consultationRoomId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new RoomServiceToggleRequest(true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROOM_TYPE"));

        // 4. Link LAB room for CLS service -> 200 OK
        String roomListStr = mvc.perform(put("/api/admin/services/" + clsId + "/rooms/" + labRoomId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new RoomServiceToggleRequest(true))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode roomList = json.readTree(roomListStr);
        boolean foundSupported = false;
        for (JsonNode item : roomList) {
            if (item.get("roomId").asLong() == labRoomId && item.get("isSupported").asBoolean()) {
                foundSupported = true;
            }
        }
        assertTrue(foundSupported, "Phòng LAB phải được đánh dấu hỗ trợ.");

        // 5. Disable support -> 200 OK
        String disabledListStr = mvc.perform(put("/api/admin/services/" + clsId + "/rooms/" + labRoomId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new RoomServiceToggleRequest(false))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode disabledList = json.readTree(disabledListStr);
        boolean stillSupported = false;
        for (JsonNode item : disabledList) {
            if (item.get("roomId").asLong() == labRoomId && item.get("isSupported").asBoolean()) {
                stillSupported = true;
            }
        }
        assertFalse(stillSupported, "Phòng LAB phải được cập nhật không còn hỗ trợ.");
    }

    @Test
    void managesClsPricesAndValidatesNonOverlappingValidity() throws Exception {
        // Create CLS service
        ClinicalServiceCreateRequest clsReq = new ClinicalServiceCreateRequest(
                prefix + "PRC", "Xét nghiệm máu toàn phần", "CLS", "LAB", null, true
        );
        String clsStr = mvc.perform(post("/api/admin/services").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(clsReq)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long clsId = json.readTree(clsStr).get("id").asLong();

        // 1. Amount <= 0 -> 400
        ServicePriceCreateRequest zeroAmountReq = new ServicePriceCreateRequest(
                BigDecimal.ZERO, LocalDateTime.of(2026, 1, 1, 0, 0), null
        );
        mvc.perform(post("/api/admin/services/" + clsId + "/prices").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(zeroAmountReq)))
                .andExpect(status().isBadRequest());

        // 2. valid_to <= valid_from -> 400
        ServicePriceCreateRequest badPeriodReq = new ServicePriceCreateRequest(
                new BigDecimal("150000"),
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 5, 1, 0, 0)
        );
        mvc.perform(post("/api/admin/services/" + clsId + "/prices").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(badPeriodReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));

        // 3. Create valid price: [2026-01-01, 2026-12-31]
        ServicePriceCreateRequest price1Req = new ServicePriceCreateRequest(
                new BigDecimal("150000"),
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 12, 31, 23, 59, 59)
        );
        String p1Str = mvc.perform(post("/api/admin/services/" + clsId + "/prices").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(price1Req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode p1List = json.readTree(p1Str);
        assertEquals(1, p1List.size());
        assertEquals(150000, p1List.get(0).get("amount").asLong());

        // 4. Overlapping price: [2026-06-01, 2027-06-01] -> 409 Conflict
        ServicePriceCreateRequest overlapReq = new ServicePriceCreateRequest(
                new BigDecimal("180000"),
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2027, 6, 1, 0, 0)
        );
        mvc.perform(post("/api/admin/services/" + clsId + "/prices").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(overlapReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_OVERLAP"));

        // 5. Non-overlapping open-ended price: [2027-01-01, NULL] -> 201 Created
        ServicePriceCreateRequest price2Req = new ServicePriceCreateRequest(
                new BigDecimal("200000"),
                LocalDateTime.of(2027, 1, 1, 0, 0),
                null
        );
        String p2Str = mvc.perform(post("/api/admin/services/" + clsId + "/prices").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(price2Req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode p2List = json.readTree(p2Str);
        assertEquals(2, p2List.size());
        long openEndedPriceId = p2List.get(0).get("id").asLong();
        String openEndedVersion = p2List.get(0).get("version").asText();

        // 6. Unified effective price query:
        // - At 2026-06-15 -> 150000
        mvc.perform(get("/api/admin/services/" + clsId + "/effective-price?targetTime=2026-06-15T10:00:00")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(150000));

        // - At 2027-03-01 -> 200000
        mvc.perform(get("/api/admin/services/" + clsId + "/effective-price?targetTime=2027-03-01T10:00:00")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(200000));

        // - At 2025-01-01 (before prices) -> 404
        mvc.perform(get("/api/admin/services/" + clsId + "/effective-price?targetTime=2025-01-01T10:00:00")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRICE_NOT_FOUND"));

        // 7. End open-ended price early: set valid_to = 2027-06-30T23:59:59
        PriceEndRequest endReq = new PriceEndRequest(
                LocalDateTime.of(2027, 6, 30, 23, 59, 59),
                openEndedVersion
        );
        String endedListStr = mvc.perform(put("/api/admin/services/" + clsId + "/prices/" + openEndedPriceId + "/end")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(endReq)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode endedList = json.readTree(endedListStr);
        assertNotNull(endedList.get(0).get("validTo").asText());

        // Now after 2027-07-01 -> 404
        mvc.perform(get("/api/admin/services/" + clsId + "/effective-price?targetTime=2027-07-01T10:00:00")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rollbacksTransactionWhenAuditFails() throws Exception {
        doThrow(new RuntimeException("Simulated audit failure"))
                .when(repository).audit(anyLong(), anyString(), eq("Service"), anyLong(), anyString());

        ClinicalServiceCreateRequest req = new ClinicalServiceCreateRequest(
                prefix + "ROLL", "Dịch vụ thử rollback", "EXAMINATION", null, null, true
        );

        assertThrows(Exception.class, () ->
                mvc.perform(post("/api/admin/services").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req))));

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[Service] WHERE code = ?", Integer.class, prefix + "ROLL");
        assertEquals(0, count, "Dịch vụ không được lưu khi audit thất bại (phải rollback).");
    }
}
