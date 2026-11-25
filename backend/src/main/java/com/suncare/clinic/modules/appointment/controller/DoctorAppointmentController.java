package com.suncare.clinic.modules.appointment.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.appointment.dto.AppointmentScheduleDetailResponse;
import com.suncare.clinic.modules.appointment.dto.AppointmentScheduleListItemResponse;
import com.suncare.clinic.modules.appointment.service.AppointmentScheduleService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/doctor/appointments")
public class DoctorAppointmentController {
    private final AppointmentScheduleService service;

    public DoctorAppointmentController(AppointmentScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<AppointmentScheduleListItemResponse> assignedToMe(
            @AuthenticationPrincipal CurrentAccountResponse account) {
        return service.doctorAppointments(account);
    }

    @GetMapping("/{appointmentId}")
    public AppointmentScheduleDetailResponse detail(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long appointmentId) {
        return service.doctorAppointment(account, appointmentId);
    }
}
