package com.suncare.clinic.modules.appointment;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.flyway.enabled=false")
class DrugUnitsMigrationIT extends OnsiteAppointmentBookingIT {
    @Autowired JdbcTemplate unitsJdbc;
    @Autowired DataSource unitsDataSource;
    @Autowired ObjectMapper unitsJson;

    @Test
    void unitsMigrationPreservesLegacyIssuedSnapshotsAndMatchesSource() throws Exception {
        assertEquals("SuncareClinicMigrationTest", unitsJdbc.queryForObject("SELECT DB_NAME()", String.class));
        var method = OnsiteAppointmentBookingIT.class.getDeclaredMethod("startedEncounter", String.class);
        method.setAccessible(true);
        long encounter = (long) method.invoke(this, "units-legacy");
        var prefixField = OnsiteAppointmentBookingIT.class.getDeclaredField("prefix");
        prefixField.setAccessible(true);
        String prefix = (String) prefixField.get(this);
        long actor = unitsJdbc.queryForObject("""
                SELECT ac.id FROM dbo.Encounter e JOIN dbo.Appointment a ON a.id=e.appointment_id
                JOIN dbo.Account ac ON ac.staff_id=a.doctor_id WHERE e.id=?
                """, Long.class, encounter);
        long drug = unitsJdbc.queryForObject("""
                INSERT dbo.Drug(code,name,unit,is_active) OUTPUT INSERTED.id VALUES (?,N'Legacy test',N'viên',1)
                """, Long.class, prefix + "RXLEGACY");
        long prescription = unitsJdbc.queryForObject("""
                INSERT dbo.Prescription(code,encounter_id,status,prescribed_by,issued_at)
                OUTPUT INSERTED.id VALUES (?,?,'ISSUED',?,SYSUTCDATETIME())
                """, Long.class, prefix + "RXLEGACY", encounter, actor);
        long item = unitsJdbc.queryForObject("""
                INSERT dbo.PrescriptionItem(prescription_id,line_no,drug_id,drug_name,unit,dose,route,frequency,duration_days,quantity)
                OUTPUT INSERTED.id VALUES (?,1,?,N'Legacy snapshot',N'viên',N'1 viên',N'Uống',N'2 lần/ngày',5,10)
                """, Long.class, prescription, drug);
        byte[] drugVersion = unitsJdbc.queryForObject("SELECT version FROM dbo.Drug WHERE id=?", byte[].class, drug);
        byte[] itemVersion = unitsJdbc.queryForObject("SELECT version FROM dbo.PrescriptionItem WHERE id=?", byte[].class, item);
        byte[] headerVersion = unitsJdbc.queryForObject("SELECT version FROM dbo.Prescription WHERE id=?", byte[].class, prescription);
        var flyway = Flyway.configure().dataSource(unitsDataSource).defaultSchema("dbo")
                .locations("classpath:db/migration").cleanDisabled(true).load();
        flyway.migrate();
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertArrayEquals(drugVersion, unitsJdbc.queryForObject("SELECT version FROM dbo.Drug WHERE id=?", byte[].class, drug));
        assertArrayEquals(itemVersion, unitsJdbc.queryForObject("SELECT version FROM dbo.PrescriptionItem WHERE id=?", byte[].class, item));
        assertArrayEquals(headerVersion, unitsJdbc.queryForObject("SELECT version FROM dbo.Prescription WHERE id=?", byte[].class, prescription));
        var old = unitsJdbc.queryForMap("SELECT unit,dose,dose_unit,dispensing_unit FROM dbo.PrescriptionItem WHERE id=?", item);
        assertEquals("viên", old.get("unit"));
        assertEquals("1 viên", old.get("dose"));
        assertNull(old.get("dose_unit"));
        assertNull(old.get("dispensing_unit"));
        assertNull(unitsJdbc.queryForObject("SELECT dose_unit FROM dbo.Drug WHERE id=?", String.class, drug));
        var rows = unitsJson.readTree(Files.readString(Path.of("scripts/data/outpatient-drug-units.json"))).get("records");
        assertEquals(400, rows.size());
        var confirmed = unitsJson.readTree(Files.readString(Path.of("scripts/data/outpatient-drug-units-confirmed.json"))).get("records");
        assertEquals(11, confirmed.size());
        var overrides = new java.util.HashMap<String, String>();
        for (var row : confirmed) overrides.put(row.get("code").asText(), row.get("dispensing_unit").asText());
        int ready = 0;
        for (var row : rows) {
            var saved = unitsJdbc.queryForMap("SELECT dose_unit,dispensing_unit,unit FROM dbo.Drug WHERE code=?", row.get("code").asText());
            assertEquals(row.get("dose_unit").asText(), saved.get("dose_unit"));
            assertEquals(row.get("dispensing_unit").isNull() ? overrides.get(row.get("code").asText()) : row.get("dispensing_unit").asText(), saved.get("dispensing_unit"));
            assertEquals(row.get("legacy_unit").asText(), saved.get("unit"));
            if (saved.get("dispensing_unit") != null) ready++;
        }
        assertEquals(400, ready);
    }
}
