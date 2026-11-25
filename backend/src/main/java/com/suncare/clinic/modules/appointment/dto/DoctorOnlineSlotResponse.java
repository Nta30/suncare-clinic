package com.suncare.clinic.modules.appointment.dto;

import java.time.OffsetDateTime;

public record DoctorOnlineSlotResponse(long slotId, OffsetDateTime startsAt, OffsetDateTime endsAt,
        boolean open, Long appointmentId, String appointmentStatus, String patientName) {}
