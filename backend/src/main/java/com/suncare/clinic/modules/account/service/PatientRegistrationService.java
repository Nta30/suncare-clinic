package com.suncare.clinic.modules.account.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.PatientRegistrationRequest;
import com.suncare.clinic.modules.account.dto.PatientRegistrationResponse;
import com.suncare.clinic.modules.account.repository.PatientRegistrationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class PatientRegistrationService {
    private final PatientRegistrationRepository repository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PatientRegistrationService(PatientRegistrationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PatientRegistrationResponse register(PatientRegistrationRequest request) {
        String loginName = request.loginName().trim().toLowerCase(Locale.ROOT);
        if (repository.loginExists(loginName)) {
            throw duplicateLogin();
        }

        String patientCode = "PAT-" + UUID.randomUUID().toString().replace("-", "");
        long patientId = repository.createPatient(patientCode, request);
        long accountId;
        try {
            accountId = repository.createAccount(
                    loginName, passwordEncoder.encode(request.password()), patientId);
        } catch (DuplicateKeyException exception) {
            throw duplicateLogin();
        }
        repository.grantPatientRole(accountId, repository.findPatientRoleId());
        return new PatientRegistrationResponse(accountId, patientCode);
    }

    private ApiException duplicateLogin() {
        return new ApiException(HttpStatus.CONFLICT, "LOGIN_EXISTS", "Tên đăng nhập đã được sử dụng.");
    }
}
