package com.suncare.clinic.modules.encounter.dto;

public record EncounterStartResponse(long encounterId, String encounterCode, String status,
        String startedAt, String historyText, String allergyText, boolean replayed) {}
