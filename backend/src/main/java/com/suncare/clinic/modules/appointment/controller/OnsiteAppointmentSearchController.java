package com.suncare.clinic.modules.appointment.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentAvailabilityResponse;
import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentOptionsResponse;
import com.suncare.clinic.modules.appointment.service.OnsiteAppointmentSearchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments/onsite")
public class OnsiteAppointmentSearchController {
    private final OnsiteAppointmentSearchService service;

    public OnsiteAppointmentSearchController(OnsiteAppointmentSearchService service) {
        this.service = service;
    }

    @GetMapping("/options")
    public OnsiteAppointmentOptionsResponse options(@AuthenticationPrincipal CurrentAccountResponse account) {
        return service.options(account);
    }

    @GetMapping("/availability")
    public List<OnsiteAppointmentAvailabilityResponse> availability(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long shiftId,
            @RequestParam(required = false) String doctorName) {
        return service.availability(account, specialtyId, date, dateFrom, dateTo, shiftId, doctorName);
    }
}
