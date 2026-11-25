package com.suncare.clinic.modules.encounter.dto;

import java.util.List;

public record EncounterWorklistResponse(List<EncounterWorkItemResponse> items, long totalElements,
        int page, int pageSize, int totalPages) {}
