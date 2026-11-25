package com.suncare.clinic.modules.encounter.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.encounter.dto.AllergenRequest;
import com.suncare.clinic.modules.encounter.dto.AllergenResponse;
import com.suncare.clinic.modules.encounter.repository.AllergenRepository;
import com.suncare.clinic.modules.organization.repository.OrganizationCatalogRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Base64;
import java.util.List;

@Service
public class AllergenService {
    private final AllergenRepository repository;
    private final OrganizationCatalogRepository audit;
    public AllergenService(AllergenRepository repository, OrganizationCatalogRepository audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public List<AllergenResponse> list() { return repository.list(false); }

    @Transactional
    public List<AllergenResponse> save(CurrentAccountResponse account, Long id, AllergenRequest request) {
        if (!account.permissions().contains("CATALOG_MANAGE")) throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Không có quyền quản lý danh mục.");
        try {
            long savedId;
            if (id == null) savedId = repository.create(request);
            else {
                var existing = repository.find(id);
                if (existing == null) throw new ApiException(HttpStatus.NOT_FOUND, "ALLERGEN_NOT_FOUND", "Không tìm thấy chất gây dị ứng.");
                if (!existing.code().equalsIgnoreCase(request.code())) throw new ApiException(HttpStatus.CONFLICT, "ALLERGEN_CODE_FIXED", "Không đổi mã đã tạo. Hãy ngừng áp dụng nếu không còn sử dụng.");
                byte[] version;
                try {
                    version = Base64.getDecoder().decode(request.version());
                    if (version.length != 8) throw new IllegalArgumentException();
                } catch (IllegalArgumentException | NullPointerException ex) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERSION", "Phiên bản không hợp lệ. Hãy tải lại.");
                }
                if (repository.update(id, request, version) != 1) throw new ApiException(HttpStatus.CONFLICT, "ALLERGEN_CHANGED", "Danh mục đã thay đổi. Hãy tải lại.");
                savedId = id;
            }
            audit.audit(account.accountId(), id == null ? "ALLERGEN_CREATED" : "ALLERGEN_UPDATED", "Allergen", savedId);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "ALLERGEN_EXISTS", "Mã hoặc tên chất gây dị ứng đã có trong danh mục.");
        }
        return repository.list(false);
    }
}
