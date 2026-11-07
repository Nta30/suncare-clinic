package com.suncare.clinic.modules.organization.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.organization.dto.RoomRequest;
import com.suncare.clinic.modules.organization.dto.RoomResponse;
import com.suncare.clinic.modules.organization.repository.OrganizationCatalogRepository;
import com.suncare.clinic.modules.organization.repository.RoomRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;

@Service
public class RoomService {
    private final RoomRepository rooms;
    private final OrganizationCatalogRepository audit;

    public RoomService(RoomRepository rooms, OrganizationCatalogRepository audit) {
        this.rooms = rooms;
        this.audit = audit;
    }

    public List<RoomResponse> list(CurrentAccountResponse account) {
        requireManager(account);
        return rooms.list();
    }

    @Transactional
    public List<RoomResponse> create(CurrentAccountResponse account, RoomRequest request) {
        requireManager(account);
        validateDepartment(request);
        try {
            long id = rooms.create(request);
            audit.audit(account.accountId(), "ROOM_CREATED", "Room", id,
                    "department_id=" + request.departmentId());
        } catch (DuplicateKeyException error) {
            throw duplicateCode();
        }
        return rooms.list();
    }

    @Transactional
    public List<RoomResponse> update(CurrentAccountResponse account, long id, RoomRequest request) {
        requireManager(account);
        var existing = rooms.find(id);
        if (existing == null) throw new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Không tìm thấy phòng.");
        if (existing.departmentId() != request.departmentId() || !existing.roomType().equals(request.roomType())) {
            throw new ApiException(HttpStatus.CONFLICT, "ROOM_ASSIGNMENT_FIXED",
                    "Không đổi khoa hoặc loại phòng đã tạo; hãy ngừng phòng cũ và tạo phòng mới.");
        }
        if (request.isActive()) validateDepartment(request);
        try {
            if (rooms.update(id, request, version(request.version())) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "ROOM_CHANGED", "Phòng đã thay đổi. Hãy tải lại trước khi lưu.");
            }
            audit.audit(account.accountId(), "ROOM_UPDATED", "Room", id);
        } catch (DuplicateKeyException error) {
            throw duplicateCode();
        }
        return rooms.list();
    }

    private void validateDepartment(RoomRequest request) {
        String type = rooms.activeDepartmentType(request.departmentId());
        String expectedDepartmentType = "LAB".equals(request.roomType()) ? "LAB" : "CLINICAL";
        if (!expectedDepartmentType.equals(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROOM_DEPARTMENT_MISMATCH",
                    "Loại phòng phải phù hợp với khoa đang hoạt động.");
        }
    }

    private void requireManager(CurrentAccountResponse account) {
        if (!account.permissions().contains("CATALOG_MANAGE")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Không có quyền quản lý danh mục.");
        }
    }

    private byte[] version(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length != 8) throw new IllegalArgumentException();
            return bytes;
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERSION", "Phiên bản dữ liệu không hợp lệ.");
        }
    }

    private ApiException duplicateCode() {
        return new ApiException(HttpStatus.CONFLICT, "ROOM_CODE_EXISTS", "Mã phòng đã được sử dụng.");
    }
}
