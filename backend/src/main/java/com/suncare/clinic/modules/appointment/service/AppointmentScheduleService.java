package com.suncare.clinic.modules.appointment.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.appointment.dto.AppointmentScheduleDetailResponse;
import com.suncare.clinic.modules.appointment.dto.AppointmentScheduleListItemResponse;
import com.suncare.clinic.modules.appointment.repository.AppointmentScheduleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import com.suncare.clinic.modules.appointment.dto.DoctorWorkShiftResponse;
import com.suncare.clinic.modules.appointment.dto.DoctorOnlineSlotResponse;
import com.suncare.clinic.modules.appointment.dto.DoctorSlotPatientResponse;

@Service
public class AppointmentScheduleService {
    private final AppointmentScheduleRepository repository;

    public AppointmentScheduleService(AppointmentScheduleRepository repository) {
        this.repository = repository;
    }

    public List<AppointmentScheduleListItemResponse> patientAppointments(CurrentAccountResponse account) {
        if (account.patientId() == null) {
            throw new AccessDeniedException("Tài khoản không gắn hồ sơ bệnh nhân.");
        }
        return repository.findForPatient(account.patientId());
    }

    public AppointmentScheduleDetailResponse patientAppointment(CurrentAccountResponse account, long appointmentId) {
        if (account.patientId() == null) {
            throw new AccessDeniedException("Tài khoản không gắn hồ sơ bệnh nhân.");
        }
        return repository.findDetailForPatient(account.patientId(), appointmentId)
                .orElseThrow(this::appointmentNotFound);
    }

    public List<AppointmentScheduleListItemResponse> doctorAppointments(CurrentAccountResponse account) {
        if (account.staffId() == null) {
            throw new AccessDeniedException("Tài khoản không gắn hồ sơ nhân viên.");
        }
        return repository.findAssignedToDoctor(account.staffId());
    }

    public AppointmentScheduleDetailResponse doctorAppointment(CurrentAccountResponse account, long appointmentId) {
        if (account.staffId() == null) {
            throw new AccessDeniedException("Tài khoản không gắn hồ sơ nhân viên.");
        }
        return repository.findDetailAssignedToDoctor(account.staffId(), appointmentId)
                .orElseThrow(this::appointmentNotFound);
    }

    private ApiException appointmentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "Không tìm thấy lịch khám.");
    }

    private long doctorId(CurrentAccountResponse account) {
        if (account.staffId() == null) throw new AccessDeniedException("Tài khoản không gắn hồ sơ bác sĩ.");
        return account.staffId();
    }

    public List<DoctorWorkShiftResponse> doctorShifts(CurrentAccountResponse account, LocalDate from, LocalDate to) {
        long staffId = doctorId(account);
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Khoảng ngày không hợp lệ hoặc vượt quá 31 ngày.");
        }
        return repository.findDoctorShifts(staffId, from, to);
    }

    public List<DoctorOnlineSlotResponse> doctorSlots(CurrentAccountResponse account, long assignmentId) {
        long staffId = doctorId(account);
        if (!repository.ownsPublishedOnlineShift(staffId, assignmentId)) throw appointmentNotFound();
        return repository.findDoctorSlots(staffId, assignmentId);
    }

    public DoctorSlotPatientResponse slotPatient(CurrentAccountResponse account, long slotId) {
        return repository.findSlotPatient(doctorId(account), slotId).orElseThrow(this::appointmentNotFound);
    }
}
