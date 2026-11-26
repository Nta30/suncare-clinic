package com.suncare.clinic.modules.medicalrecord.dto;

import com.suncare.clinic.modules.encounter.dto.EncounterClinicalResponse;
import com.suncare.clinic.modules.encounter.dto.EncounterHistoryResponse;
import java.math.BigDecimal;
import java.util.List;

public final class PatientEncounterHistoryResponse {
    private PatientEncounterHistoryResponse() {}
    public record Summary(long encounterId, String encounterCode, String completedAt, String visitMode,
            String doctorName, String specialtyName, String departmentName, String primaryDiagnosis) {}
    public record Page(List<Summary> items, long totalElements, int page, int pageSize, int totalPages) {}
    public record Detail(Summary encounter, EncounterClinicalResponse clinical, EncounterHistoryResponse.Snapshot snapshot,
            List<Prescription> prescriptions, List<Result> results) {}
    public record Prescription(long id, String code, String issuedAt, String instruction, List<DrugItem> items) {}
    public record DrugItem(String drugName, String strength, String dose, String route, String frequency,
            int durationDays, BigDecimal quantity, String unit, String instruction) {}
    public record Result(long id, String serviceName, int versionNo, String publishedAt, String description,
            String conclusion, List<Value> values) {}
    public record Value(String analyteName, BigDecimal numericValue, String textValue, String unit,
            String referenceText, String abnormalFlag) {}
}
