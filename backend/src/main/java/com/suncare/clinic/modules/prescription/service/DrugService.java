package com.suncare.clinic.modules.prescription.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.organization.repository.OrganizationCatalogRepository;
import com.suncare.clinic.modules.prescription.dto.DrugRequest;
import com.suncare.clinic.modules.prescription.dto.DrugResponse;
import com.suncare.clinic.modules.prescription.repository.DrugRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Base64;
import java.util.List;

@Service
public class DrugService {
    private final DrugRepository repository;
    private final OrganizationCatalogRepository audit;

    public DrugService(DrugRepository repository, OrganizationCatalogRepository audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public List<DrugResponse> list() { return repository.list(); }

    public List<DrugResponse> list(String search) {
        validateSearch(search);
        return repository.list(search, false);
    }

    public static void validateSearch(String search) {
        if (search != null && search.length() > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH", "Từ khóa thuốc tối đa 100 ký tự.");
    }

    @Transactional
    public List<DrugResponse> save(CurrentAccountResponse account, Long id, DrugRequest request) {
        if (!account.permissions().contains("CATALOG_MANAGE"))
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Không có quyền quản lý danh mục.");
        long savedId;
        try {
            if (id == null) savedId = repository.create(request);
            else {
                byte[] version;
                try {
                    version = Base64.getDecoder().decode(request.version());
                    if (version.length != 8) throw new IllegalArgumentException();
                } catch (IllegalArgumentException | NullPointerException ex) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERSION", "Phiên bản không hợp lệ. Hãy tải lại danh mục.");
                }
                var existing = repository.find(id);
                if (existing == null)
                    throw new ApiException(HttpStatus.NOT_FOUND, "DRUG_NOT_FOUND", "Không tìm thấy thuốc.");
                if (!existing.code().equalsIgnoreCase(request.code()))
                    throw new ApiException(HttpStatus.CONFLICT, "DRUG_CODE_FIXED", "Không đổi mã thuốc đã tạo.");
                if (repository.update(id, request, version) != 1)
                    throw new ApiException(HttpStatus.CONFLICT, "DRUG_CHANGED", "Thuốc đã được cập nhật ở nơi khác. Hãy tải lại trước khi sửa.");
                savedId = id;
            }
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "DRUG_EXISTS", "Mã thuốc đã có trong danh mục.");
        }
        // Danh mục và audit cùng transaction; không cập nhật snapshot đơn thuốc đã có.
        audit.audit(account.accountId(), id == null ? "DRUG_CREATED" : "DRUG_UPDATED", "Drug", savedId);
        return repository.list();
    }
}
