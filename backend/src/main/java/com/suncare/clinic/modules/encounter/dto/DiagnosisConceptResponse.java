package com.suncare.clinic.modules.encounter.dto;

public record DiagnosisConceptResponse(long id, String codeSystem, String codeVersion,
        String code, String name, boolean primaryAllowed, String englishName,
        String categoryCode, String categoryName) {}
