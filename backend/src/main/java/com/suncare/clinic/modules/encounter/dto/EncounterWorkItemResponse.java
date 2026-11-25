package com.suncare.clinic.modules.encounter.dto;

public record EncounterWorkItemResponse(long encounterId, String encounterCode, String status,
        String appointmentCode, String appointmentStatus, String patientCode, String patientName,
        String startsAt, String endsAt, String roomName, String specialtyName) {}
