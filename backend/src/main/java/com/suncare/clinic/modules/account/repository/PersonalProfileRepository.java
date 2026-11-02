package com.suncare.clinic.modules.account.repository;

import com.suncare.clinic.modules.account.dto.PersonalProfileRequest;
import com.suncare.clinic.modules.account.dto.PersonalProfileResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Base64;
import java.util.Arrays;
import java.util.List;

@Repository
public class PersonalProfileRepository {
    private final JdbcTemplate jdbc;

    public PersonalProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PersonalProfileResponse find(long accountId, String accountType) {
        boolean patient = "PATIENT".equals(accountType);
        String table = patient ? "dbo.Patient" : "dbo.Staff";
        String relation = patient ? "a.patient_id" : "a.staff_id";
        String extras = patient ? """
                p.identity_number, p.emergency_name, p.emergency_phone,
                NULL AS professional_license, NULL AS qualification,
                NULL AS department_names, NULL AS specialty_names
                """ : """
                NULL AS identity_number, NULL AS emergency_name, NULL AS emergency_phone,
                p.professional_license, p.qualification,
                (SELECT STRING_AGG(d.name, N'|')
                 FROM dbo.StaffDepartment sd JOIN dbo.Department d ON d.id = sd.department_id
                 WHERE sd.staff_id = p.id AND sd.valid_from <= SYSUTCDATETIME()
                   AND (sd.valid_to IS NULL OR sd.valid_to > SYSUTCDATETIME())) AS department_names,
                (SELECT STRING_AGG(sp.name, N'|')
                 FROM dbo.StaffSpecialty ss JOIN dbo.Specialty sp ON sp.id = ss.specialty_id AND sp.is_active = 1
                 WHERE ss.staff_id = p.id AND ss.is_active = 1
                   AND EXISTS (
                       SELECT 1 FROM dbo.StaffDepartment sd
                       JOIN dbo.DepartmentSpecialty ds ON ds.department_id = sd.department_id
                           AND ds.specialty_id = ss.specialty_id AND ds.is_active = 1
                       WHERE sd.staff_id = p.id AND sd.valid_from <= SYSUTCDATETIME()
                         AND (sd.valid_to IS NULL OR sd.valid_to > SYSUTCDATETIME())
                   )) AS specialty_names
                """;
        List<PersonalProfileResponse> rows = jdbc.query("""
                SELECT p.code, p.full_name, p.birth_date, p.sex, p.phone, p.email, p.address,
                       %s, p.version
                FROM dbo.[Account] a JOIN %s p ON p.id = %s
                WHERE a.id = ? AND a.account_type = ? AND a.status = 'ACTIVE'
                """.formatted(extras, table, relation), (rs, row) ->
                new PersonalProfileResponse(accountType, rs.getString("code"), rs.getString("full_name"),
                        rs.getObject("birth_date", java.time.LocalDate.class), rs.getString("sex"),
                        rs.getString("phone"), rs.getString("email"), rs.getString("address"),
                        rs.getString("identity_number"), rs.getString("emergency_name"),
                        rs.getString("emergency_phone"), rs.getString("professional_license"),
                        rs.getString("qualification"), names(rs.getString("department_names")),
                        names(rs.getString("specialty_names")), Base64.getEncoder().encodeToString(rs.getBytes("version"))),
                accountId, accountType);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<String> names(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\|")).map(String::trim).filter(name -> !name.isEmpty()).toList();
    }

    public int update(long accountId, String accountType, PersonalProfileRequest request, byte[] version) {
        boolean patient = "PATIENT".equals(accountType);
        String table = patient ? "dbo.Patient" : "dbo.Staff";
        String relation = patient ? "a.patient_id" : "a.staff_id";
        String extraSet = patient ? ", identity_number = ?, emergency_name = ?, emergency_phone = ?" : "";
        String sql = """
                UPDATE p SET full_name = ?, birth_date = ?, sex = ?, phone = ?, email = ?, address = ?,
                             updated_at = SYSUTCDATETIME()%s
                FROM %s p JOIN dbo.[Account] a ON p.id = %s
                WHERE a.id = ? AND a.account_type = ? AND a.status = 'ACTIVE' AND p.version = ?
                """.formatted(extraSet, table, relation);
        if (patient) {
            return jdbc.update(sql, request.fullName().trim(), request.birthDate(), request.sex(),
                    request.phone(), request.email(), request.address(), request.identityNumber(),
                    request.emergencyName(), request.emergencyPhone(), accountId, accountType, version);
        }
        return jdbc.update(sql, request.fullName().trim(), request.birthDate(), request.sex(),
                request.phone(), request.email(), request.address(), accountId, accountType, version);
    }
}
