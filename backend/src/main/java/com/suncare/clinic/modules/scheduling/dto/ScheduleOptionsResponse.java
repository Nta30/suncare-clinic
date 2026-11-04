package com.suncare.clinic.modules.scheduling.dto;

import java.util.List;

public record ScheduleOptionsResponse(
        List<DepartmentOption> departments,
        List<StaffOption> staff,
        List<RoomOption> rooms,
        List<SpecialtyOption> specialties,
        List<ShiftOption> shifts
) {
    public record DepartmentOption(long id, String code, String name, String validFrom, String validTo) { }
    public record StaffOption(long id, String code, String name, List<String> roles,
                              List<SpecialtyOption> specialties) { }
    public record RoomOption(long id, String code, String name, String roomType) { }
    public record SpecialtyOption(long id, String code, String name) { }
    public record ShiftOption(long id, String code, String name, String localStart, String localEnd) { }
}
