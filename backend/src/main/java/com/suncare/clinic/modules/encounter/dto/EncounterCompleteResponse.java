package com.suncare.clinic.modules.encounter.dto;

public record EncounterCompleteResponse(long encounterId, String status, String completedAt, boolean replayed) {}
