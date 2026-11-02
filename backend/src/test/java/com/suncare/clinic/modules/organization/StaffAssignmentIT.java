package com.suncare.clinic.modules.organization;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.organization.dto.HeadAssignmentRequest;
import com.suncare.clinic.modules.organization.dto.StaffAssignmentRequest;
import com.suncare.clinic.modules.organization.dto.StaffSpecialtyRequest;
import com.suncare.clinic.modules.organization.service.StaffAssignmentService;
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
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("migrationtest")
class StaffAssignmentIT {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StaffAssignmentService service;
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper json;

    @Test
    void protectsPeriodsRoleAndHistory() throws Exception {
        String code = "P10" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        long staffId = jdbc.queryForObject("INSERT INTO dbo.Staff(code, full_name, sex, is_active) OUTPUT INSERTED.id VALUES (?, 'P10 Staff', 'UNKNOWN', 1)", Long.class, code + "S");
        long accountId = jdbc.queryForObject("INSERT INTO dbo.[Account](login_name, password_hash, account_type, staff_id, status) OUTPUT INSERTED.id VALUES (?, ?, 'STAFF', ?, 'ACTIVE')",
                Long.class, code.toLowerCase(), new BCryptPasswordEncoder().encode("p10-local-test-password"), staffId);
        jdbc.update("INSERT INTO dbo.AccountRole(account_id, role_id, granted_by, granted_at) SELECT ?, id, NULL, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'SYSTEM_ADMIN'", accountId);
        long departmentId = jdbc.queryForObject("INSERT INTO dbo.Department(code, name, department_type, is_active) OUTPUT INSERTED.id VALUES (?, 'P10 Department', 'CLINICAL', 1)", Long.class, code + "D");
        long specialtyId = jdbc.queryForObject("INSERT INTO dbo.Specialty(code, name, is_active) OUTPUT INSERTED.id VALUES (?, 'P10 Specialty', 1)", Long.class, code + "P");
        jdbc.update("INSERT INTO dbo.DepartmentSpecialty(department_id, specialty_id, is_active) VALUES (?, ?, 1)", departmentId, specialtyId);
        CurrentAccountResponse admin = new CurrentAccountResponse(accountId, code, "STAFF", null, staffId,
                List.of("SYSTEM_ADMIN"), List.of("ACCOUNT_MANAGE"));
        LocalDateTime from = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 1, 0, 0);
        try {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mvc.perform(get("/api/admin/staff-assignments")).andExpect(status().isUnauthorized());
            String loginBody = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"loginName\":\"" + code.toLowerCase() + "\",\"password\":\"p10-local-test-password\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String token = json.readTree(loginBody).get("accessToken").asText();
            mvc.perform(get("/api/admin/staff-assignments").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.departments").isArray())
                    .andExpect(jsonPath("$.specialties").isArray()).andExpect(jsonPath("$.heads").isArray());
            CurrentAccountResponse noPermission = new CurrentAccountResponse(accountId, code, "STAFF", null, staffId,
                    List.of("DOCTOR"), List.of());
            assertThrows(ApiException.class, () -> service.assignDepartment(noPermission,
                    new StaffAssignmentRequest(staffId, departmentId, from, end, true)));
            service.assignDepartment(admin, new StaffAssignmentRequest(staffId, departmentId, from, end, true));
            String assignmentBody = mvc.perform(get("/api/admin/staff-assignments")
                    .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String actualFrom = null;
            for (var row : json.readTree(assignmentBody).get("departments")) {
                if (row.get("staffId").asLong() == staffId) actualFrom = row.get("validFrom").asText();
            }
            assertEquals("2026-11-01T00:00:00", actualFrom);
            assertThrows(ApiException.class, () -> service.assignDepartment(admin,
                    new StaffAssignmentRequest(staffId, departmentId, from.plusDays(1), end.plusDays(1), true)));
            assertThrows(ApiException.class, () -> service.assignHead(admin,
                    new HeadAssignmentRequest(staffId, departmentId, from, end)));
            jdbc.update("INSERT INTO dbo.AccountRole(account_id, role_id, granted_by, granted_at) SELECT ?, id, ?, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'DEPARTMENT_HEAD'", accountId, accountId);
            jdbc.update("INSERT INTO dbo.AccountRole(account_id, role_id, granted_by, granted_at) SELECT ?, id, ?, SYSUTCDATETIME() FROM dbo.[Role] WHERE code = 'DOCTOR'", accountId, accountId);
            service.assignHead(admin, new HeadAssignmentRequest(staffId, departmentId, from, end));
            service.specialty(admin, new StaffSpecialtyRequest(staffId, specialtyId, true));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.DepartmentHeadAssignment WHERE staff_id = ?", Integer.class, staffId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.StaffSpecialty WHERE staff_id = ? AND is_active = 1", Integer.class, staffId));
            long departmentAssignmentId = jdbc.queryForObject("SELECT id FROM dbo.StaffDepartment WHERE staff_id = ?", Long.class, staffId);
            long headAssignmentId = jdbc.queryForObject("SELECT id FROM dbo.DepartmentHeadAssignment WHERE staff_id = ?", Long.class, staffId);
            assertThrows(ApiException.class, () -> service.endDepartment(admin, departmentAssignmentId, from.plusDays(10)));
            service.endHead(admin, headAssignmentId, from.plusDays(10));
            jdbc.update("UPDATE dbo.Staff SET is_active = 0 WHERE id = ?", staffId);
            service.endDepartment(admin, departmentAssignmentId, from.plusDays(10));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.StaffDepartment WHERE id = ? AND valid_to = ?",
                    Integer.class, departmentAssignmentId, from.plusDays(10)));
        } finally {
            jdbc.update("DELETE FROM dbo.StaffSpecialty WHERE staff_id = ?", staffId);
            jdbc.update("DELETE FROM dbo.DepartmentHeadAssignment WHERE staff_id = ?", staffId);
            jdbc.update("DELETE FROM dbo.StaffDepartment WHERE staff_id = ?", staffId);
            jdbc.update("DELETE FROM dbo.DepartmentSpecialty WHERE department_id = ?", departmentId);
            jdbc.update("DELETE FROM dbo.Specialty WHERE id = ?", specialtyId);
            jdbc.update("DELETE FROM dbo.Department WHERE id = ?", departmentId);
            jdbc.update("DELETE FROM dbo.AuditEvent WHERE actor_id = ?", accountId);
            jdbc.update("DELETE FROM dbo.AuthToken WHERE account_id = ?", accountId);
            jdbc.update("DELETE FROM dbo.AccountRole WHERE account_id = ?", accountId);
            jdbc.update("DELETE FROM dbo.[Account] WHERE id = ?", accountId);
            jdbc.update("DELETE FROM dbo.Staff WHERE id = ?", staffId);
        }
    }
}
