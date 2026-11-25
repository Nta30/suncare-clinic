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
import java.time.LocalDate;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import com.suncare.clinic.modules.appointment.dto.DoctorWorkShiftResponse;
import com.suncare.clinic.modules.appointment.dto.DoctorOnlineSlotResponse;
import com.suncare.clinic.modules.appointment.dto.DoctorSlotPatientResponse;

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

    @GetMapping("/timetable")
    public List<DoctorWorkShiftResponse> timetable(@AuthenticationPrincipal CurrentAccountResponse account,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.doctorShifts(account, from, to);
    }

    @GetMapping("/timetable/{assignmentId}/slots")
    public List<DoctorOnlineSlotResponse> slots(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long assignmentId) {
        return service.doctorSlots(account, assignmentId);
    }

    @GetMapping("/timetable/slots/{slotId}/patient")
    public DoctorSlotPatientResponse patient(@AuthenticationPrincipal CurrentAccountResponse account,
            @PathVariable long slotId) {
        return service.slotPatient(account, slotId);
    }
}
