package com.suncare.clinic.modules.appointment.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentBookingRequest;
import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentBookingResponse;
import com.suncare.clinic.modules.appointment.repository.OnsiteAppointmentBookingRepository;
import com.suncare.clinic.modules.appointment.repository.OnsiteAppointmentBookingRepository.Candidate;
import com.suncare.clinic.modules.appointment.repository.OnsiteAppointmentBookingRepository.Receipt;
import com.suncare.clinic.modules.appointment.repository.OnsiteAppointmentSearchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class OnsiteAppointmentBookingService {
    private static final String COMMAND_TYPE = "ONSITE_APPOINTMENT_BOOKING";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Saigon");
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final OnsiteAppointmentBookingRepository repository;
    private final OnsiteAppointmentSearchRepository searchRepository;

    public OnsiteAppointmentBookingService(OnsiteAppointmentBookingRepository repository,
                                          OnsiteAppointmentSearchRepository searchRepository) {
        this.repository = repository;
        this.searchRepository = searchRepository;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingResult book(CurrentAccountResponse account, OnsiteAppointmentBookingRequest request,
                              String suppliedKey) {
        requirePatient(account);
        String idempotencyKey = normalizeKey(suppliedKey);
        String symptoms = normalizeSymptoms(request.symptoms());
        String requestHash = hashRequest(request, symptoms);

        requireLock(repository.acquireLock("ONSITE_COMMAND:" + idempotencyKey));
        // Acquire the balancing lock before reading the receipt to avoid serializable key-range lock cycles.
        requireLock(repository.acquireLock("ONSITE_SPECIALTY:" + request.specialtyId()
                + ":" + request.date() + ":" + request.shiftId()));
        Receipt existingReceipt = repository.findReceipt(COMMAND_TYPE, idempotencyKey).orElse(null);
        if (existingReceipt != null) {
            if (existingReceipt.accountId() != account.accountId()
                    || !existingReceipt.requestHash().equals(requestHash)) {
                throw conflict("IDEMPOTENCY_CONFLICT", "Khóa yêu cầu đã được dùng cho thao tác khác.");
            }
            OnsiteAppointmentBookingResponse previousBooking = repository
                    .bookingForPatient(existingReceipt.resourceId(), account.patientId(), true)
                    .orElseThrow(() -> conflict("BOOKING_NOT_AVAILABLE", "Không thể tải lại kết quả đặt lịch này."));
            return new BookingResult(previousBooking, true);
        }

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (request.date().isBefore(today) || request.date().getDayOfWeek().getValue() == 7) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORK_DATE",
                    "Ngày khám phải từ hôm nay trở đi và nằm trong khoảng thứ Hai đến thứ Bảy.");
        }
        if (!searchRepository.activeSpecialty(request.specialtyId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SPECIALTY", "Chuyên khoa không còn hoạt động.");
        }
        if (searchRepository.activeShift(request.shiftId()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHIFT", "Ca khám không còn hoạt động.");
        }

        List<Candidate> candidates = repository.findAvailableCandidates(
                request.specialtyId(), request.date(), request.shiftId(), request.doctorId());
        if (candidates.isEmpty()) {
            throw conflict("SCHEDULE_UNAVAILABLE", "Lịch khám vừa hết chỗ hoặc không còn phù hợp. Hãy tìm lại lịch khác.");
        }

        Candidate selected = candidates.stream().min(candidateOrder()).orElseThrow();
        String appointmentCode = newCode("APT", request.date());
        String encounterCode = newCode("ENC", request.date());
        String correlationId = UUID.randomUUID().toString();

        long appointmentId = repository.insertAppointment(appointmentCode, account.patientId(), selected);
        repository.insertAppointmentEvent(appointmentId, account.accountId());
        long encounterId = repository.insertEncounter(encounterCode, appointmentId, account.accountId());
        if (symptoms != null) repository.insertPrevisitResponse(appointmentId, account.accountId(), symptoms);
        repository.insertAudit(account.accountId(), appointmentId, correlationId);
        repository.insertReceipt(account.accountId(), COMMAND_TYPE, idempotencyKey, requestHash, appointmentId);

        OnsiteAppointmentBookingResponse booking = repository
                .bookingForPatient(appointmentId, account.patientId(), false)
                .orElseThrow(() -> new IllegalStateException("Đã tạo lịch nhưng không tải được thông tin xác nhận."));
        if (booking.encounterId() != encounterId) {
            throw new IllegalStateException("Encounter được tạo không khớp với lịch khám.");
        }
        return new BookingResult(booking, false);
    }

    private Comparator<Candidate> candidateOrder() {
        return (left, right) -> {
            BigInteger leftRatio = BigInteger.valueOf(left.bookedCount()).multiply(BigInteger.valueOf(right.capacity()));
            BigInteger rightRatio = BigInteger.valueOf(right.bookedCount()).multiply(BigInteger.valueOf(left.capacity()));
            int ratioOrder = leftRatio.compareTo(rightRatio);
            if (ratioOrder != 0) return ratioOrder;
            int codeOrder = left.doctorCode().compareToIgnoreCase(right.doctorCode());
            if (codeOrder != 0) return codeOrder;
            return Long.compare(left.assignmentId(), right.assignmentId());
        };
    }

    private String normalizeKey(String suppliedKey) {
        if (suppliedKey == null || suppliedKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Thiếu khóa chống đặt trùng cho yêu cầu này.");
        }
        String key = suppliedKey.trim();
        if (key.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Khóa yêu cầu không được dài quá 100 ký tự.");
        }
        return key;
    }

    private String normalizeSymptoms(String symptoms) {
        if (symptoms == null || symptoms.isBlank()) return null;
        String normalized = symptoms.trim();
        if (normalized.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SYMPTOMS",
                    "Triệu chứng không được dài quá 2.000 ký tự.");
        }
        return normalized;
    }

    private String hashRequest(OnsiteAppointmentBookingRequest request, String symptoms) {
        String canonicalRequest = request.specialtyId() + "|" + request.date() + "|" + request.shiftId()
                + "|" + (request.doctorId() == null ? "" : request.doctorId())
                + "|" + (symptoms == null ? "" : symptoms);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Không tìm thấy thuật toán SHA-256.", exception);
        }
    }

    private void requireLock(int result) {
        if (result < 0) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_BUSY",
                    "Lịch đang được xử lý. Hãy thử lại với cùng khóa yêu cầu.");
        }
    }

    private String newCode(String prefix, LocalDate workDate) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return prefix + workDate.format(CODE_DATE) + suffix;
    }

    private void requirePatient(CurrentAccountResponse account) {
        if (account == null || !"PATIENT".equals(account.accountType()) || account.patientId() == null
                || account.roles() == null || !account.roles().contains("PATIENT")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PATIENT_REQUIRED", "Chỉ tài khoản bệnh nhân được đặt lịch khám.");
        }
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public record BookingResult(OnsiteAppointmentBookingResponse booking, boolean replayed) { }
}
