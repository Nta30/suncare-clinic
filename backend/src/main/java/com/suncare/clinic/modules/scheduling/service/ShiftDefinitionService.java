package com.suncare.clinic.modules.scheduling.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.scheduling.dto.ShiftDefinitionCreateRequest;
import com.suncare.clinic.modules.scheduling.dto.ShiftDefinitionResponse;
import com.suncare.clinic.modules.scheduling.dto.ShiftDefinitionUpdateRequest;
import com.suncare.clinic.modules.scheduling.repository.ShiftDefinitionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Base64;
import java.util.List;

@Service
public class ShiftDefinitionService {
    private final ShiftDefinitionRepository repository;

    public ShiftDefinitionService(ShiftDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<ShiftDefinitionResponse> listShifts(CurrentAccountResponse account) {
        requireCatalogManage(account);
        return repository.listAll();
    }

    public ShiftDefinitionResponse getShift(CurrentAccountResponse account, long id) {
        requireCatalogManage(account);
        ShiftDefinitionResponse shift = repository.findById(id);
        if (shift == null) {
            throw notFound("SHIFT_NOT_FOUND", "Không tìm thấy thông tin ca làm việc.");
        }
        return shift;
    }

    @Transactional
    public ShiftDefinitionResponse createShift(CurrentAccountResponse account, ShiftDefinitionCreateRequest request) {
        requireCatalogManage(account);
        String code = request.code().trim().toUpperCase();
        if (!"MORNING".equals(code) && !"AFTERNOON".equals(code)) {
            throw bad("INVALID_SHIFT_CODE", "Mã ca chỉ có thể là MORNING hoặc AFTERNOON.");
        }
        if (!request.localEnd().isAfter(request.localStart())) {
            throw bad("INVALID_TIME_RANGE", "Giờ kết thúc phải sau giờ bắt đầu.");
        }
        if (repository.existsByCode(code)) {
            throw conflict("SHIFT_CODE_EXISTS", "Ca làm việc với mã này đã tồn tại.");
        }

        boolean isActive = request.isActive() == null || request.isActive();
        long id = repository.insert(code, request.name().trim(), request.localStart(), request.localEnd(), isActive);
        repository.audit(account.accountId(), "SHIFT_DEFINITION_CREATED", "ShiftDefinition", id,
                "code=" + code + ", start=" + request.localStart() + ", end=" + request.localEnd());
        return repository.findById(id);
    }

    @Transactional
    public ShiftDefinitionResponse updateShift(CurrentAccountResponse account, long id, ShiftDefinitionUpdateRequest request) {
        requireCatalogManage(account);
        ShiftDefinitionRepository.ShiftDefinitionIdentity existing = repository.lockShift(id);
        if (existing == null) {
            throw notFound("SHIFT_NOT_FOUND", "Không tìm thấy thông tin ca làm việc.");
        }
        if (!request.localEnd().isAfter(request.localStart())) {
            throw bad("INVALID_TIME_RANGE", "Giờ kết thúc phải sau giờ bắt đầu.");
        }

        byte[] versionBytes = decodeVersion(request.version());
        int updated = repository.update(id, request.name().trim(), request.localStart(), request.localEnd(),
                request.isActive(), versionBytes);
        if (updated != 1) {
            throw conflict("CONCURRENT_UPDATE", "Thông tin ca làm việc đã thay đổi bởi thao tác khác. Vui lòng tải lại.");
        }

        repository.audit(account.accountId(), "SHIFT_DEFINITION_UPDATED", "ShiftDefinition", id,
                "name=" + request.name() + ", start=" + request.localStart() + ", end=" + request.localEnd() + ", active=" + request.isActive());
        return repository.findById(id);
    }

    @Transactional
    public List<ShiftDefinitionResponse> initDefaultShiftsIfEmpty(CurrentAccountResponse account) {
        requireCatalogManage(account);
        if (!repository.existsByCode("MORNING")) {
            long mId = repository.insert("MORNING", "Ca sáng", LocalTime.of(7, 30), LocalTime.of(11, 30), true);
            repository.audit(account.accountId(), "SHIFT_DEFINITION_INITIALIZED", "ShiftDefinition", mId, "code=MORNING");
        }
        if (!repository.existsByCode("AFTERNOON")) {
            long aId = repository.insert("AFTERNOON", "Ca chiều", LocalTime.of(13, 30), LocalTime.of(17, 30), true);
            repository.audit(account.accountId(), "SHIFT_DEFINITION_INITIALIZED", "ShiftDefinition", aId, "code=AFTERNOON");
        }
        return repository.listAll();
    }

    private void requireCatalogManage(CurrentAccountResponse account) {
        if (account == null || account.permissions() == null || !account.permissions().contains("CATALOG_MANAGE")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền quản lý danh mục lịch và ca khám.");
        }
    }

    private byte[] decodeVersion(String version) {
        try {
            return Base64.getDecoder().decode(version);
        } catch (IllegalArgumentException ex) {
            throw bad("INVALID_VERSION", "Phiên bản dữ liệu không hợp lệ.");
        }
    }

    private ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
