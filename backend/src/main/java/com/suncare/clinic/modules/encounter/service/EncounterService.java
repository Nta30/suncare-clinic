package com.suncare.clinic.modules.encounter.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterStartResponse;
import com.suncare.clinic.modules.encounter.repository.EncounterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncounterService {
    private final EncounterRepository repository;

    public EncounterService(EncounterRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EncounterStartResponse start(CurrentAccountResponse account, long encounterId) {
        if (account.staffId() == null) throw new AccessDeniedException("Tài khoản không gắn hồ sơ bác sĩ.");
        Long appointmentId = repository.appointmentId(encounterId);
        if (appointmentId == null) throw notFound();
        // Lock Appointment before Encounter, consistently with the booking/clinical command order.
        var appointment = repository.lockAppointment(appointmentId, account.staffId());
        if (appointment == null) throw notFound();
        String status = repository.lockStatus(encounterId);
        if (!"ONSITE".equals(appointment.visitMode())) {
            throw new ApiException(HttpStatus.CONFLICT, "ENCOUNTER_MODE_NOT_SUPPORTED", "Bước này chỉ hỗ trợ khám trực tiếp.");
        }
        if (!"BOOKED".equals(appointment.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_BOOKED", "Lịch hẹn không còn ở trạng thái đã đặt.");
        }
        if ("IN_PROGRESS".equals(status)) return repository.result(encounterId, true);
        if (!"PLANNED".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "ENCOUNTER_NOT_PLANNED", "Không thể bắt đầu lượt khám ở trạng thái hiện tại.");
        }
        repository.lockPatient(appointment.patientId());
        repository.insertSnapshot(encounterId, account.accountId(), repository.history(appointment.patientId()),
                repository.allergies(appointment.patientId()));
        repository.start(encounterId, account.accountId());
        return repository.result(encounterId, false);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ENCOUNTER_NOT_FOUND", "Không tìm thấy lượt khám.");
    }
}
