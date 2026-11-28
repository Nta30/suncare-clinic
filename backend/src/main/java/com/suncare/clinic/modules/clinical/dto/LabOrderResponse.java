package com.suncare.clinic.modules.clinical.dto;

public record LabOrderResponse(long id, String code, long serviceId, String serviceName,
        String resultType, String status, String version, long patientId, String patientCode,
        String patientName, String birthDate, String sex, String doctorName, String indication,
        String orderedAt, String fullyPaidAt, String expiresAt, String startedAt,
        Long roomId, String roomName, Long acceptedBy, String staffName, String acceptedAt,
        boolean paid) { }
