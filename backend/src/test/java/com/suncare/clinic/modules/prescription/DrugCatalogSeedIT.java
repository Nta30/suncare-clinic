package com.suncare.clinic.modules.prescription;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.flywaydb.core.Flyway;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.flyway.enabled=true")
class DrugCatalogSeedIT extends DrugCatalogIT {
    @Autowired Flyway flyway;

    private void executeSeed() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V21__seed_outpatient_drug_catalog.sql"));
        jdbc.execute((org.springframework.jdbc.core.StatementCallback<Void>) statement->{
            statement.execute(sql);
            // SQL Server may deliver a later THROW after an earlier update-count token.
            while(statement.getMoreResults() || statement.getUpdateCount()!=-1) { }
            return null;
        });
    }

    @Test
    void seedMatchesSourcedManifestAndAllFields() throws Exception {
        var manifest=json.readTree(Files.readString(Path.of("scripts/data/outpatient-drugs-openfda.json")));
        var rows=manifest.get("records");
        assertEquals(400,rows.size());
        assertEquals(400,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Drug WHERE code LIKE 'SCG-%'",Integer.class));
        var identities=new HashSet<String>();
        var scopes=new HashSet<String>();
        int combinations=0;
        for(var row:rows) {
            var saved=jdbc.queryForMap("SELECT * FROM dbo.Drug WHERE code=?",row.get("code").asText());
            for(String field:new String[]{"name","ingredient","strength","dosage_form","unit"})
                assertEquals(row.get(field).asText(),saved.get(field),row.get("code").asText()+":"+field);
            assertEquals(true,saved.get("is_active"));
            assertEquals(8,((byte[])saved.get("version")).length);
            assertTrue(identities.add(row.get("ingredient").asText()+"|"+row.get("strength").asText()+"|"+row.get("dosage_form").asText()+"|"+row.get("unit").asText()));
            for(var scope:row.get("specialty_scope")) scopes.add(scope.asText());
            if(row.get("source").get("active_ingredients").size()>1) combinations++;
        }
        assertEquals(9,scopes.size());
        assertEquals(26,combinations);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0,flyway.migrate().migrationsExecuted);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='21' AND success=1",Integer.class));
    }

    @Test
    void seedSearchesNameIngredientCodeAndEscapesWildcards() throws Exception {
        var item=create("SEARCH");
        String doctor="/api/doctor/drugs";
        String admin="/api/admin/drugs";
        for(String path:new String[]{doctor,admin}) {
            String token=path.equals(doctor)?doctorToken:adminToken;
            for(String term:new String[]{"duong nguyen","hoat chat gia",item.get("code").asText().toLowerCase()}) {
                mvc.perform(get(path).param("search",term).header("Authorization","Bearer "+token))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                        .andExpect(jsonPath("$[0].id").value(item.get("id").asLong()));
            }
            for(String literal:new String[]{"%","_","[","' OR 1=1 --"}) mvc.perform(get(path).param("search",literal)
                    .header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
            mvc.perform(get(path).param("search","a".repeat(101)).header("Authorization","Bearer "+token))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SEARCH"));
        }
        mvc.perform(get(doctor).param("search","amoxicillin").header("Authorization","Bearer "+doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.ingredient == 'Amoxicillin + Clavulanate potassium')]").isNotEmpty());
        String code=jdbc.queryForObject("SELECT TOP(1) code FROM dbo.Drug WHERE ingredient='Metformin hydrochloride' ORDER BY code",String.class);
        mvc.perform(get(doctor).param("search",code).header("Authorization","Bearer "+doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].ingredient").value("Metformin hydrochloride"));
        jdbc.update("UPDATE dbo.Drug SET is_active=0 WHERE id=?",item.get("id").asLong());
        mvc.perform(get(doctor).param("search",prefix).header("Authorization","Bearer "+doctorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(admin).param("search",prefix).header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].isActive").value(false));
        mvc.perform(get(doctor)).andExpect(status().isUnauthorized());
        mvc.perform(get(doctor).header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void seedRerunPreservesExistingInactiveEquivalentAndOtherRows() throws Exception {
        var original=jdbc.queryForMap("SELECT TOP(1) * FROM dbo.Drug WHERE code LIKE 'SCG-%' ORDER BY code");
        long id=((Number)original.get("id")).longValue();
        jdbc.update("UPDATE dbo.Drug SET code=?,name=N'Existing local name',is_active=0 WHERE id=?",prefix+"EXISTING",id);
        var preservedVersion=jdbc.queryForObject("SELECT version FROM dbo.Drug WHERE id=?",byte[].class,id);
        jdbc.update("INSERT dbo.Drug(code,name,ingredient,strength,dosage_form,unit,is_active) VALUES (?,N'Existing unrelated',N'Local ingredient',N'1 mg',N'Local form',N'viên',0)",prefix+"UNRELATED");
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Drug",Integer.class);
        executeSeed();
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Drug",Integer.class));
        var preserved=jdbc.queryForMap("SELECT name,is_active,version FROM dbo.Drug WHERE id=?",id);
        assertEquals("Existing local name",preserved.get("name"));
        assertEquals(false,preserved.get("is_active"));
        assertArrayEquals(preservedVersion,(byte[])preserved.get("version"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Drug WHERE code=?",Integer.class,original.get("code")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Drug WHERE code=?",Integer.class,prefix+"UNRELATED"));
    }

    @Test
    @Transactional
    void seedCodeCollisionFailsWithoutOverwriting() throws Exception {
        String code=jdbc.queryForObject("SELECT TOP(1) code FROM dbo.Drug WHERE code LIKE 'SCG-%' ORDER BY code",String.class);
        jdbc.update("UPDATE dbo.Drug SET ingredient=N'Existing conflicting ingredient' WHERE code=?",code);
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Drug",Integer.class);
        assertThrows(org.springframework.dao.DataAccessException.class,
                this::executeSeed);
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Drug",Integer.class));
        assertEquals("Existing conflicting ingredient",jdbc.queryForObject("SELECT ingredient FROM dbo.Drug WHERE code=?",String.class,code));
    }
}
