package com.suncare.clinic.modules.appointment.controller;

import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentBookingRequest;
import com.suncare.clinic.modules.appointment.dto.OnsiteAppointmentBookingResponse;
import com.suncare.clinic.modules.appointment.service.OnsiteAppointmentBookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments/onsite")
public class OnsiteAppointmentBookingController {
    private final OnsiteAppointmentBookingService service;

    public OnsiteAppointmentBookingController(OnsiteAppointmentBookingService service) {
        this.service = service;
    }

    @PostMapping("/bookings")
    public ResponseEntity<OnsiteAppointmentBookingResponse> book(
            @AuthenticationPrincipal CurrentAccountResponse account,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OnsiteAppointmentBookingRequest request) {
        var result = service.book(account, request, idempotencyKey);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.booking());
    }
}
