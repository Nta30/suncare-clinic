package com.suncare.clinic.modules.appointment.dto;

import java.util.List;

public record OnsiteAppointmentOptionsResponse(
        List<SpecialtyOption> specialties,
        List<ShiftOption> shifts) {

    public record SpecialtyOption(long id, String code, String name) { }

    public record ShiftOption(long id, String code, String name, String startTime, String endTime) { }
}
