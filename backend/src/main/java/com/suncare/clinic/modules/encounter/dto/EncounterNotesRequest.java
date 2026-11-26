package com.suncare.clinic.modules.encounter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EncounterNotesRequest(@Size(max = 2000) String chiefComplaint,
        @Size(max = 20000) String examinationNote, @Size(max = 20000) String treatmentPlan,
        @Size(max = 2000) String followupNote, @NotBlank String version) {}
