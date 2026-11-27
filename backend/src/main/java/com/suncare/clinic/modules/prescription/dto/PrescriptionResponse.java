package com.suncare.clinic.modules.prescription.dto;

import java.math.BigDecimal;
import java.util.List;

public record PrescriptionResponse(long id, String code, long encounterId, String status, long prescribedBy,
        String issuedAt, String instruction, String version, boolean replayed, List<Item> items) {
    public record Item(long id, int lineNo, long drugId, String drugName, String ingredient,
            String strength, String dosageForm, String unit, String dose, String route, String frequency,
            int durationDays, BigDecimal quantity, String instruction, String doseUnit, String dispensingUnit) {}
}
