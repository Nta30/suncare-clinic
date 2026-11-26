package com.suncare.clinic.modules.encounter.dto;

import java.util.List;

public record DiagnosisConceptSearchResponse(List<DiagnosisConceptResponse> items, int page, int pageSize,
        int totalElements, int totalPages) {}
