package com.suncare.clinic.modules.encounter.controller;

import com.suncare.clinic.modules.encounter.dto.EncounterClinicalResponse.Symptom;
import com.suncare.clinic.modules.encounter.service.EncounterClinicalService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor/symptoms")
public class SymptomController {
    private final EncounterClinicalService service;
    public SymptomController(EncounterClinicalService service) { this.service = service; }
    @GetMapping
    public List<Symptom> search(@RequestParam(required = false) String search) { return service.searchSymptoms(search); }
}
