package com.suncare.clinic.modules.appointment.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentAvailabilityResponse;
import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentOptionsResponse;
import com.suncare.clinic.modules.appointment.repository.OnsiteAppointmentSearchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class OnsiteAppointmentSearchService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Saigon");
    private final OnsiteAppointmentSearchRepository repository;

    public OnsiteAppointmentSearchService(OnsiteAppointmentSearchRepository repository) {
        this.repository = repository;
    }

    public OnsiteAppointmentOptionsResponse options(CurrentAccountResponse account) {
        requirePatient(account);
        return repository.options();
    }

    public List<OnsiteAppointmentAvailabilityResponse> availability(CurrentAccountResponse account, Long specialtyId,
                                                                     LocalDate date, LocalDate dateFrom, LocalDate dateTo,
                                                                     Long shiftId, String doctorName) {
        requirePatient(account);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (date != null && (dateFrom != null || dateTo != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "Chỉ dùng ngày đơn hoặc khoảng ngày, không dùng đồng thời.");
        }
        if (date != null && (date.isBefore(today) || date.getDayOfWeek().getValue() == 7)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORK_DATE",
                    "Ngày tra cứu phải từ hôm nay trở đi và nằm trong khoảng thứ Hai đến thứ Bảy.");
        }
        LocalDate effectiveFrom = date == null ? dateFrom : date;
        LocalDate effectiveTo = date == null ? dateTo : date;
        if ((effectiveFrom != null && effectiveFrom.isBefore(today))
                || (effectiveTo != null && effectiveTo.isBefore(today))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORK_DATE",
                    "Khoảng ngày tra cứu phải từ hôm nay trở đi.");
        }
        if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "Ngày bắt đầu phải nhỏ hơn hoặc bằng ngày kết thúc.");
        }
        if (specialtyId != null && !repository.activeSpecialty(specialtyId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SPECIALTY", "Chuyên khoa không còn hoạt động.");
        }
        if (shiftId != null && repository.activeShift(shiftId) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHIFT", "Ca khám không còn hoạt động.");
        }
        String normalizedDoctorName = doctorName == null ? null : doctorName.trim();
        if (normalizedDoctorName != null && normalizedDoctorName.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DOCTOR_SEARCH",
                    "Từ khóa tìm bác sĩ không được dài quá 100 ký tự.");
        }
        if (normalizedDoctorName != null && normalizedDoctorName.isEmpty()) normalizedDoctorName = null;
        return repository.availability(specialtyId, effectiveFrom, effectiveTo, shiftId, normalizedDoctorName, today);
    }

    private void requirePatient(CurrentAccountResponse account) {
        if (account == null || !"PATIENT".equals(account.accountType()) || account.patientId() == null
                || !account.roles().contains("PATIENT")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PATIENT_REQUIRED", "Chỉ tài khoản bệnh nhân được tra cứu lịch khám.");
        }
    }
}
