package com.suncare.clinic.modules.encounter.dto;

import java.math.BigDecimal;
import java.util.List;

public record EncounterClinicalResponse(Notes notes, List<Vital> vitals, List<Diagnosis> diagnoses,
        boolean editable, boolean replayed) {
    public record Notes(String chiefComplaint, String examinationNote, String treatmentPlan, String followupNote, String version) {}
    public record Vital(long id, String measuredAt, BigDecimal temperatureC, Integer pulseBpm,
            Integer systolicMmhg, Integer diastolicMmhg, Integer respiratoryRate,
            BigDecimal spo2Percent, BigDecimal weightKg, BigDecimal heightCm) {}
    public record Diagnosis(long id, Long diagnosisConceptId, String codeSystem, String codeVersion,
            String diagnosisCode, String diagnosisName, String description,
            String diagnosisType, boolean isPrimary, String version, Boolean primaryAllowed) {}
}
