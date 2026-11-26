package com.suncare.clinic.modules.encounter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record EncounterNotesRequest(@Size(max = 2000) String chiefComplaint,
        @Size(max = 20000) String examinationNote, @Size(max = 20000) String treatmentPlan,
        @Size(max = 2000) String followupNote, @NotBlank String version,
        @Size(max = 20000) String historyOfPresentIllness,
        @Size(max = 50) List<@jakarta.validation.constraints.NotNull @Valid SymptomInput> symptoms) {
    public record SymptomInput(@Positive Long symptomId, @NotBlank @Size(max = 200) String name) {}
}
