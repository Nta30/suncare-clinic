package com.suncare.clinic.modules.clinical;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.account.repository.AccountAuthRepository;
import com.suncare.clinic.modules.account.service.AdminBootstrapService;
import com.suncare.clinic.modules.clinical.repository.ClinicalOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit integration checks against the approved demo catalog; never creates clinical fixtures. */
@SpringBootTest(properties = "spring.flyway.enabled=false")
class ClinicalCatalogSeedIT {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WebApplicationContext context;
    @Autowired private AccountAuthRepository accounts;
    @Autowired private AdminBootstrapService bootstrap;
    @Autowired private ClinicalOrderRepository orders;
    @Autowired private ObjectMapper json;
    @Value("${suncare.seed.verify-database:NOT_CONFIGURED}") private String expectedDatabase;

    @Test
    void bootstrapApprovedTestAdmin() {
        // Run this method explicitly only after owner authorization, with credentials in environment.
        assertEquals("SuncareClinicMigrationTest", jdbc.queryForObject("SELECT DB_NAME()", String.class));
        bootstrap.bootstrap(System.getenv("SUNCARE_ADMIN_LOGIN"), System.getenv("SUNCARE_ADMIN_PASSWORD"),
                System.getenv("SUNCARE_ADMIN_NAME"));
        Long firstId = accounts.findByLogin(System.getenv("SUNCARE_ADMIN_LOGIN")).id();
        bootstrap.bootstrap(System.getenv("SUNCARE_ADMIN_LOGIN"), System.getenv("SUNCARE_ADMIN_PASSWORD"),
                System.getenv("SUNCARE_ADMIN_NAME"));
        assertEquals(firstId, accounts.findByLogin(System.getenv("SUNCARE_ADMIN_LOGIN")).id());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Staff WHERE code='DEV_ADMIN'", Integer.class));
        assertTrue(accounts.roles(firstId).contains("SYSTEM_ADMIN"));
        assertTrue(accounts.permissions(firstId).contains("CATALOG_MANAGE"));
    }

    @Test
    void seededCatalogAvailableThroughExistingReadApis() throws Exception {
        String database = jdbc.queryForObject("SELECT DB_NAME()", String.class);
        assertTrue(expectedDatabase.equals("SuncareClinicMigrationTest") || expectedDatabase.equals("SuncareClinic"));
        assertEquals(expectedDatabase, database);
        long adminId = jdbc.queryForObject("""
                SELECT MIN(a.id) FROM dbo.Account a JOIN dbo.AccountRole ar ON ar.account_id=a.id
                JOIN dbo.Role r ON r.id=ar.role_id WHERE a.status='ACTIVE' AND r.code='SYSTEM_ADMIN'
                """, Long.class);
        var account = accounts.findById(adminId);
        var principal = new CurrentAccountResponse(account.id(), account.loginName(), account.accountType(),
                account.patientId(), account.staffId(), accounts.roles(adminId), accounts.permissions(adminId));
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        principal.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        principal.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var response = mvc.perform(get("/api/admin/services").param("type", "CLS").param("active", "true")
                .with(authentication(auth))).andExpect(status().isOk()).andReturn();
        var services = json.readTree(response.getResponse().getContentAsString());
        assertEquals(32, services.size());
        int roomCount = 0;
        int multipleRooms = 0;
        for (var service : services) {
            long id = service.path("id").asLong();
            String code = service.path("code").asText();
            assertTrue(new BigDecimal(service.path("currentPrice").asText()).signum() > 0, code);
            var priceResponse = mvc.perform(get("/api/admin/services/{id}/effective-price", id)
                    .with(authentication(auth))).andExpect(status().isOk()).andReturn();
            var price = json.readTree(priceResponse.getResponse().getContentAsString());
            assertEquals(id, price.path("serviceId").asLong());
            assertEquals(0, new BigDecimal(service.path("currentPrice").asText())
                    .compareTo(new BigDecimal(price.path("amount").asText())), code);
            var roomsResponse = mvc.perform(get("/api/admin/services/{id}/rooms", id)
                    .with(authentication(auth))).andExpect(status().isOk()).andReturn();
            var rooms = json.readTree(roomsResponse.getResponse().getContentAsString());
            int supported = 0;
            for (var room : rooms) {
                if (!room.path("isSupported").asBoolean()) continue;
                supported++;
                assertTrue(room.path("departmentId").asLong() > 0, code);
                assertFalse(room.path("departmentName").asText().isBlank(), code);
                String roomCode = room.path("roomCode").asText();
                assertNotEquals("CLS-XN-LM-01", roomCode);
                if (code.startsWith("CLS-XQ-")) assertEquals("CLS-CDHA-XQ-01", roomCode);
                else if (code.startsWith("CLS-SA-")) assertEquals("CLS-CDHA-SA-01", roomCode);
                else assertTrue(roomCode.startsWith("CLS-XN-"), code);
            }
            assertTrue(supported > 0, code);
            roomCount += supported;
            if (supported > 1) multipleRooms++;
            // Exercise the doctor's actual options SQL without fabricating an Encounter/account.
            var option = orders.options(code).stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
            assertEquals(id, option.serviceId());
            assertEquals(supported, option.rooms().size());
            assertEquals(0, new BigDecimal(option.amount()).compareTo(new BigDecimal(price.path("amount").asText())));
        }
        assertEquals(36, roomCount);
        assertEquals(4, multipleRooms);
        mvc.perform(get("/api/admin/services")).andExpect(status().isUnauthorized());
    }
}
