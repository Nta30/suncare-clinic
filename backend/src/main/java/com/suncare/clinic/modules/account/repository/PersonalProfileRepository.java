package com.suncare.clinic.modules.account.repository;

import com.suncare.clinic.modules.account.dto.PersonalProfileRequest;
import com.suncare.clinic.modules.account.dto.PersonalProfileResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Base64;
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
                NULL AS professional_license, NULL AS qualification
                """ : """
                NULL AS identity_number, NULL AS emergency_name, NULL AS emergency_phone,
                p.professional_license, p.qualification
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
                        rs.getString("qualification"), Base64.getEncoder().encodeToString(rs.getBytes("version"))),
                accountId, accountType);
        return rows.isEmpty() ? null : rows.getFirst();
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
