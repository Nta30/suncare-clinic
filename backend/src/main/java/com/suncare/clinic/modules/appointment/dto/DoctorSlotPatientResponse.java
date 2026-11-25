package com.suncare.clinic.modules.appointment.dto;

import java.time.LocalDate;

public record DoctorSlotPatientResponse(String patientCode, String fullName, LocalDate birthDate,
        String sex, String phone) {}
