package com.suncare.clinic.modules.account.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.account.dto.PersonalProfileRequest;
import com.suncare.clinic.modules.account.dto.PersonalProfileResponse;
import com.suncare.clinic.modules.account.repository.PersonalProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

@Service
public class PersonalProfileService {
    private final PersonalProfileRepository repository;

    public PersonalProfileService(PersonalProfileRepository repository) {
        this.repository = repository;
    }

    public PersonalProfileResponse get(CurrentAccountResponse account) {
        PersonalProfileResponse profile = repository.find(account.accountId(), account.accountType());
        if (profile == null) throw new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Không tìm thấy hồ sơ cá nhân.");
        return profile;
    }

    @Transactional
    public PersonalProfileResponse update(CurrentAccountResponse account, PersonalProfileRequest request) {
        if (!"PATIENT".equals(account.accountType()) &&
                (request.identityNumber() != null || request.emergencyName() != null || request.emergencyPhone() != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_FIELDS", "Thông tin bệnh nhân không áp dụng cho nhân viên.");
        }
        byte[] version;
        try {
            version = Base64.getDecoder().decode(request.version());
            if (version.length != 8) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERSION", "Phiên bản hồ sơ không hợp lệ.");
        }
        if (repository.update(account.accountId(), account.accountType(), request, version) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "PROFILE_CHANGED", "Hồ sơ đã thay đổi. Hãy tải lại trước khi lưu.");
        }
        return get(account);
    }
}
