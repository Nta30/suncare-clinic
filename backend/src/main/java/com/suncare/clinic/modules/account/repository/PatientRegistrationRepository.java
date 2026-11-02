package com.suncare.clinic.modules.account.repository;

import com.suncare.clinic.modules.account.dto.PatientRegistrationRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PatientRegistrationRepository {
    private final JdbcTemplate jdbc;

    public PatientRegistrationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean loginExists(String loginName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.[Account] WHERE login_name = ?", Integer.class, loginName);
        return count != null && count > 0;
    }

    public long createPatient(String code, PatientRegistrationRequest request) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.Patient
                    (code, full_name, birth_date, sex, phone, email, address,
                     identity_number, emergency_name, emergency_phone, is_active)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, Long.class, code, request.fullName().trim(), request.birthDate(),
                request.sex() == null ? "UNKNOWN" : request.sex(),
                request.phone(), request.email(), request.address(),
                request.identityNumber(), request.emergencyName(), request.emergencyPhone());
    }

    public long createAccount(String loginName, String passwordHash, long patientId) {
        return jdbc.queryForObject("""
                INSERT INTO dbo.[Account]
                    (login_name, password_hash, account_type, patient_id, status)
                OUTPUT INSERTED.id
                VALUES (?, ?, 'PATIENT', ?, 'ACTIVE')
                """, Long.class, loginName, passwordHash, patientId);
    }

    public long findPatientRoleId() {
        return jdbc.queryForObject(
                "SELECT id FROM dbo.[Role] WHERE code = 'PATIENT' AND account_type = 'PATIENT'",
                Long.class);
    }

    public void grantPatientRole(long accountId, long roleId) {
        jdbc.update("""
                INSERT INTO dbo.AccountRole (account_id, role_id, granted_by, granted_at)
                VALUES (?, ?, NULL, SYSUTCDATETIME())
                """, accountId, roleId);
    }
}
