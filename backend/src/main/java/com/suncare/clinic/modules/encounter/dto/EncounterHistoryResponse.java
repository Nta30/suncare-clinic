package com.suncare.clinic.modules.encounter.dto;

import java.util.List;

public record EncounterHistoryResponse(EncounterWorkItemResponse encounter, boolean editable,
        List<HistoryItem> histories, List<AllergyItem> allergies, Snapshot snapshot,
        List<AllergenResponse> allergenOptions) {
    public record HistoryItem(long id, String historyType, String description, boolean isActive, String version) {}
    public record AllergyItem(long id, String substance, Long allergenId, String allergenCode, String otherNote,
            String reaction, String severity, boolean isActive, String version) {}
    public record Snapshot(String historyText, String allergyText, String recordedAt) {}
}
