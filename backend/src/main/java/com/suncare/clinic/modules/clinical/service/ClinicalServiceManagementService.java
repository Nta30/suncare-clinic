package com.suncare.clinic.modules.clinical.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.clinical.dto.ClinicalServiceCreateRequest;
import com.suncare.clinic.modules.clinical.dto.ClinicalServiceResponse;
import com.suncare.clinic.modules.clinical.dto.ClinicalServiceUpdateRequest;
import com.suncare.clinic.modules.clinical.dto.EffectivePriceResponse;
import com.suncare.clinic.modules.clinical.dto.PriceEndRequest;
import com.suncare.clinic.modules.clinical.dto.RoomServiceMappingResponse;
import com.suncare.clinic.modules.clinical.dto.ServicePriceCreateRequest;
import com.suncare.clinic.modules.clinical.dto.ConsultationPriceCreateRequest;
import com.suncare.clinic.modules.clinical.dto.ConsultationPriceResponse;
import com.suncare.clinic.modules.clinical.dto.DoctorOptionResponse;
import com.suncare.clinic.modules.clinical.dto.ServicePriceResponse;
import com.suncare.clinic.modules.clinical.repository.ClinicalServiceRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
public class ClinicalServiceManagementService {
    private static final Set<String> ALLOWED_SERVICE_TYPES = Set.of("EXAMINATION", "CLS");
    private static final Set<String> ALLOWED_RESULT_TYPES = Set.of("LAB", "IMAGING");

    private final ClinicalServiceRepository repository;

    public ClinicalServiceManagementService(ClinicalServiceRepository repository) {
        this.repository = repository;
    }

    public List<ClinicalServiceResponse> listServices(CurrentAccountResponse account, String type, Boolean active) {
        requireCatalogManage(account);
        return repository.listServices(type, active);
    }

    public ClinicalServiceResponse getService(CurrentAccountResponse account, long id) {
        requireCatalogManage(account);
        ClinicalServiceResponse service = repository.findServiceById(id);
        if (service == null) {
            throw notFound("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
        }
        return service;
    }

    @Transactional
    public ClinicalServiceResponse createService(CurrentAccountResponse account, ClinicalServiceCreateRequest request) {
        requireCatalogManage(account);
        validateServiceTypes(request.serviceType(), request.resultType());
        if (request.specialtyId() != null && !repository.activeSpecialty(request.specialtyId())) {
            throw bad("INVALID_SPECIALTY", "Chuyên khoa không tồn tại hoặc đã ngừng hoạt động.");
        }

        try {
            long id = repository.createService(request);
            repository.audit(account.accountId(), "SERVICE_CREATED", "Service", id, "code=" + request.code());
            return repository.findServiceById(id);
        } catch (DuplicateKeyException error) {
            throw conflict("DUPLICATE_CODE", "Mã dịch vụ đã tồn tại trong hệ thống.");
        }
    }

    @Transactional
    public ClinicalServiceResponse updateService(CurrentAccountResponse account, long id, ClinicalServiceUpdateRequest request) {
        requireCatalogManage(account);
        ClinicalServiceRepository.ServiceIdentity service = repository.lockService(id);
        if (service == null) {
            throw notFound("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
        }

        validateServiceTypes(service.serviceType(), request.resultType());
        if (request.specialtyId() != null && !repository.activeSpecialty(request.specialtyId())) {
            throw bad("INVALID_SPECIALTY", "Chuyên khoa không tồn tại hoặc đã ngừng hoạt động.");
        }

        byte[] versionBytes = decodeVersion(request.version());
        int updated = repository.updateService(id, request, versionBytes);
        if (updated != 1) {
            throw conflict("CONCURRENT_UPDATE", "Dữ liệu dịch vụ đã được cập nhật bởi thao tác khác. Vui lòng tải lại.");
        }

        repository.audit(account.accountId(), "SERVICE_UPDATED", "Service", id, "name=" + request.name());
        return repository.findServiceById(id);
    }

    public List<RoomServiceMappingResponse> listRooms(CurrentAccountResponse account, long serviceId) {
        requireCatalogManage(account);
        ClinicalServiceRepository.ServiceIdentity service = repository.lockService(serviceId);
        if (service == null) {
            throw notFound("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
        }
        if (!"CLS".equalsIgnoreCase(service.serviceType())) {
            throw bad("INVALID_SERVICE_TYPE", "Chỉ dịch vụ cận lâm sàng (CLS) mới có phòng thực hiện.");
        }
        return repository.listLabRoomsWithSupport(serviceId);
    }

    @Transactional
    public List<RoomServiceMappingResponse> toggleRoom(CurrentAccountResponse account, long serviceId, long roomId, boolean active) {
        requireCatalogManage(account);
        ClinicalServiceRepository.ServiceIdentity service = repository.lockService(serviceId);
        if (service == null) {
            throw notFound("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
        }
        if (!"CLS".equalsIgnoreCase(service.serviceType())) {
            throw bad("INVALID_SERVICE_TYPE", "Chỉ dịch vụ cận lâm sàng (CLS) mới có phòng thực hiện.");
        }

        ClinicalServiceRepository.RoomIdentity room = repository.lockRoom(roomId);
        if (room == null) {
            throw notFound("ROOM_NOT_FOUND", "Không tìm thấy phòng.");
        }
        if (!room.isActive()) {
            throw bad("ROOM_INACTIVE", "Phòng đã ngừng hoạt động.");
        }
        if (!"LAB".equalsIgnoreCase(room.roomType())) {
            throw bad("INVALID_ROOM_TYPE", "Phòng thực hiện CLS phải là phòng loại xét nghiệm/chẩn đoán hình ảnh (LAB).");
        }

        repository.upsertRoomService(roomId, serviceId, active);
        repository.audit(account.accountId(), active ? "ROOM_SERVICE_ASSIGNED" : "ROOM_SERVICE_REMOVED",
                "RoomService", serviceId, "room_id=" + roomId);

        return repository.listLabRoomsWithSupport(serviceId);
    }

    public List<ServicePriceResponse> listPrices(CurrentAccountResponse account, long serviceId) {
        requireCatalogManage(account);
        ClinicalServiceRepository.ServiceIdentity service = repository.lockService(serviceId);
        if (service == null) {
            throw notFound("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
        }
        if (!"CLS".equalsIgnoreCase(service.serviceType())) {
            throw bad("INVALID_SERVICE_TYPE", "Giá dịch vụ khám bệnh được cấu hình theo bác sĩ và hình thức khám (P12).");
        }
        return repository.listPrices(serviceId);
    }

    @Transactional
    public List<ServicePriceResponse> createClsPrice(CurrentAccountResponse account, long serviceId, ServicePriceCreateRequest request) {
        requireCatalogManage(account);
        ClinicalServiceRepository.ServiceIdentity service = repository.lockService(serviceId);
        if (service == null) {
            throw notFound("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
        }
        if (!"CLS".equalsIgnoreCase(service.serviceType())) {
            throw bad("INVALID_SERVICE_TYPE", "Giá dịch vụ khám bệnh được cấu hình theo bác sĩ và hình thức khám (P12).");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw bad("INVALID_AMOUNT", "Giá dịch vụ phải lớn hơn 0.");
        }
        if (request.validFrom() == null) {
            throw bad("INVALID_PERIOD", "Thời điểm bắt đầu không được để trống.");
        }
        if (request.validTo() != null && !request.validTo().isAfter(request.validFrom())) {
            throw bad("INVALID_PERIOD", "Thời điểm kết thúc phải sau thời điểm bắt đầu.");
        }

        if (repository.hasPriceOverlap(serviceId, null, null, request.validFrom(), request.validTo(), null)) {
            throw conflict("PRICE_OVERLAP", "Khoảng thời gian hiệu lực của giá bị chồng lấn với giá đã có.");
        }

        long priceId = repository.insertPrice(serviceId, null, null, request.amount(), request.validFrom(), request.validTo(), account.accountId());
        repository.audit(account.accountId(), "SERVICE_PRICE_CREATED", "ServicePrice", priceId, "amount=" + request.amount());

        return repository.listPrices(serviceId);
    }

    @Transactional
    public List<ServicePriceResponse> endPrice(CurrentAccountResponse account, long serviceId, long priceId, PriceEndRequest request) {
        requireCatalogManage(account);
        ClinicalServiceRepository.ServiceIdentity service = repository.lockService(serviceId);
        if (service == null) {
            throw notFound("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
        }
        ClinicalServiceRepository.PriceIdentity price = repository.lockPrice(priceId);
        if (price == null || price.serviceId() != serviceId) {
            throw notFound("PRICE_NOT_FOUND", "Không tìm thấy thông tin giá.");
        }
        if (request.validTo() == null || !request.validTo().isAfter(price.validFrom())) {
            throw bad("INVALID_PERIOD", "Thời điểm kết thúc phải sau thời điểm bắt đầu.");
        }
        if (price.validTo() != null && !request.validTo().isBefore(price.validTo())) {
            throw bad("INVALID_PERIOD", "Chỉ được kết thúc mức giá sớm hơn thời điểm đã lưu.");
        }
        if (!request.validTo().isAfter(LocalDateTime.now(Clock.systemUTC()))) {
            throw bad("INVALID_PERIOD", "Không được kết thúc mức giá trong quá khứ.");
        }

        if (repository.hasPriceOverlap(serviceId, price.doctorId(), price.visitMode(), price.validFrom(), request.validTo(), priceId)) {
            throw conflict("PRICE_OVERLAP", "Thời điểm kết thúc làm chồng lấn với khoảng giá khác.");
        }

        byte[] versionBytes = decodeVersion(request.version());
        int updated = repository.endPrice(priceId, request.validTo(), versionBytes);
        if (updated != 1) {
            throw conflict("CONCURRENT_UPDATE", "Thông tin giá đã thay đổi bởi thao tác khác. Vui lòng tải lại.");
        }

        repository.audit(account.accountId(), "SERVICE_PRICE_ENDED", "ServicePrice", priceId, "valid_to=" + request.validTo());
        return repository.listPrices(serviceId);
    }

    public List<ConsultationPriceResponse> listConsultationPrices(
            CurrentAccountResponse account, Long serviceId, Long doctorId, String visitMode, Boolean activeOnly) {
        requireCatalogManage(account);
        if (serviceId != null) {
            ClinicalServiceRepository.ServiceIdentity service = repository.lockService(serviceId);
            if (service == null) {
                throw notFound("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
            }
            if (!"EXAMINATION".equalsIgnoreCase(service.serviceType())) {
                throw bad("INVALID_SERVICE_TYPE", "Dịch vụ phải là loại EXAMINATION (khám bệnh).");
            }
        }
        return repository.listConsultationPrices(serviceId, doctorId, visitMode, activeOnly);
    }

    @Transactional
    public List<ConsultationPriceResponse> createConsultationPrice(
            CurrentAccountResponse account, long serviceId, ConsultationPriceCreateRequest request) {
        requireCatalogManage(account);
        ClinicalServiceRepository.ServiceIdentity service = repository.lockService(serviceId);
        if (service == null) {
            throw notFound("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
        }
        if (!"EXAMINATION".equalsIgnoreCase(service.serviceType())) {
            throw bad("INVALID_SERVICE_TYPE", "Giá khám theo bác sĩ chỉ áp dụng cho dịch vụ loại EXAMINATION.");
        }
        if (!service.isActive()) {
            throw bad("SERVICE_INACTIVE", "Không thể thêm giá cho dịch vụ đã ngưng hoạt động.");
        }

        if (!repository.isDoctorActive(request.doctorId())) {
            throw bad("INVALID_DOCTOR", "Bác sĩ không tồn tại, đã ngưng hoạt động hoặc không có vai trò Bác sĩ.");
        }

        String visitMode = request.visitMode().trim().toUpperCase();
        if (!"ONSITE".equals(visitMode) && !"ONLINE".equals(visitMode)) {
            throw bad("INVALID_VISIT_MODE", "Hình thức khám chỉ có thể là ONSITE hoặc ONLINE.");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw bad("INVALID_AMOUNT", "Giá dịch vụ phải lớn hơn 0.");
        }

        if (request.validTo() != null && !request.validTo().isAfter(request.validFrom())) {
            throw bad("INVALID_PERIOD", "Thời điểm kết thúc phải sau thời điểm bắt đầu.");
        }

        if (repository.hasPriceOverlap(serviceId, request.doctorId(), visitMode, request.validFrom(), request.validTo(), null)) {
            throw conflict("PRICE_OVERLAP", "Khoảng thời gian hiệu lực bị chồng lấn với mức giá đã có của bác sĩ này.");
        }

        long priceId = repository.insertPrice(serviceId, request.doctorId(), visitMode, request.amount(),
                request.validFrom(), request.validTo(), account.accountId());

        repository.audit(account.accountId(), "CONSULTATION_PRICE_CREATED", "ServicePrice", priceId,
                "doctor_id=" + request.doctorId() + ", mode=" + visitMode + ", amount=" + request.amount());

        return repository.listConsultationPrices(serviceId, request.doctorId(), visitMode, null);
    }

    @Transactional
    public List<ConsultationPriceResponse> endConsultationPrice(
            CurrentAccountResponse account, long serviceId, long priceId, PriceEndRequest request) {
        requireCatalogManage(account);
        ClinicalServiceRepository.ServiceIdentity service = repository.lockService(serviceId);
        if (service == null) {
            throw notFound("SERVICE_NOT_FOUND", "Không tìm thấy dịch vụ.");
        }
        ClinicalServiceRepository.PriceIdentity price = repository.lockPrice(priceId);
        if (price == null || price.serviceId() != serviceId) {
            throw notFound("PRICE_NOT_FOUND", "Không tìm thấy thông tin giá.");
        }
        if (price.doctorId() == null || price.visitMode() == null) {
            throw bad("INVALID_PRICE_TYPE", "Mức giá này không phải là giá khám theo bác sĩ.");
        }
        if (request.validTo() == null || !request.validTo().isAfter(price.validFrom())) {
            throw bad("INVALID_PERIOD", "Thời điểm kết thúc phải sau thời điểm bắt đầu.");
        }
        if (price.validTo() != null && !request.validTo().isBefore(price.validTo())) {
            throw bad("INVALID_PERIOD", "Chỉ được kết thúc mức giá sớm hơn thời điểm đã lưu.");
        }
        if (!request.validTo().isAfter(LocalDateTime.now(Clock.systemUTC()))) {
            throw bad("INVALID_PERIOD", "Không được kết thúc mức giá trong quá khứ.");
        }

        if (repository.hasPriceOverlap(serviceId, price.doctorId(), price.visitMode(), price.validFrom(), request.validTo(), priceId)) {
            throw conflict("PRICE_OVERLAP", "Thời điểm kết thúc làm chồng lấn với khoảng giá khác.");
        }

        byte[] versionBytes = decodeVersion(request.version());
        int updated = repository.endPrice(priceId, request.validTo(), versionBytes);
        if (updated != 1) {
            throw conflict("CONCURRENT_UPDATE", "Thông tin giá đã thay đổi bởi thao tác khác. Vui lòng tải lại.");
        }

        repository.audit(account.accountId(), "CONSULTATION_PRICE_ENDED", "ServicePrice", priceId, "valid_to=" + request.validTo());
        return repository.listConsultationPrices(serviceId, price.doctorId(), price.visitMode(), null);
    }

    public List<DoctorOptionResponse> listActiveDoctors(CurrentAccountResponse account) {
        requireCatalogManage(account);
        return repository.listActiveDoctors();
    }

    public EffectivePriceResponse getEffectivePrice(long serviceId, Long doctorId, String visitMode, LocalDateTime targetTime) {
        EffectivePriceResponse price = repository.findEffectivePrice(serviceId, doctorId, visitMode, targetTime);
        if (price == null) {
            throw notFound("PRICE_NOT_FOUND", "Không tìm thấy giá có hiệu lực tại thời điểm này.");
        }
        return price;
    }

    private void validateServiceTypes(String serviceType, String resultType) {
        if (serviceType == null || !ALLOWED_SERVICE_TYPES.contains(serviceType.trim().toUpperCase())) {
            throw bad("INVALID_SERVICE_TYPE", "Loại dịch vụ phải là EXAMINATION hoặc CLS.");
        }
        String normalizedType = serviceType.trim().toUpperCase();
        if ("EXAMINATION".equals(normalizedType)) {
            if (resultType != null && !resultType.isBlank()) {
                throw bad("INVALID_RESULT_TYPE", "Dịch vụ khám bệnh không có loại kết quả.");
            }
        } else if ("CLS".equals(normalizedType)) {
            if (resultType != null && !resultType.isBlank() && !ALLOWED_RESULT_TYPES.contains(resultType.trim().toUpperCase())) {
                throw bad("INVALID_RESULT_TYPE", "Loại kết quả CLS phải là LAB, IMAGING hoặc để trống.");
            }
        }
    }

    private void requireCatalogManage(CurrentAccountResponse account) {
        if (account == null || account.permissions() == null || !account.permissions().contains("CATALOG_MANAGE")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Bạn không có quyền quản lý danh mục dịch vụ.");
        }
    }

    private byte[] decodeVersion(String version) {
        try {
            return Base64.getDecoder().decode(version);
        } catch (IllegalArgumentException e) {
            throw bad("INVALID_VERSION", "Phiên bản dữ liệu không hợp lệ.");
        }
    }

    private ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
