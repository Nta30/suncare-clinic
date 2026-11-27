package com.suncare.clinic.modules.prescription.dto;

public record DrugResponse(long id, String code, String name, String ingredient,
        String strength, String dosageForm, String unit, boolean isActive, String version,
        String doseUnit, String dispensingUnit) {}
