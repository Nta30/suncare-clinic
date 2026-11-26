package com.suncare.clinic.modules.encounter.controller;

import com.suncare.clinic.modules.encounter.dto.DiagnosisConceptResponse;
import com.suncare.clinic.modules.encounter.dto.DiagnosisConceptSearchResponse;
import com.suncare.clinic.modules.encounter.service.EncounterClinicalService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/doctor/diagnosis-concepts")
public class DiagnosisConceptController {
    private final EncounterClinicalService service;
    public DiagnosisConceptController(EncounterClinicalService service) {this.service=service;}
    @GetMapping
    public List<DiagnosisConceptResponse> search(@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="1") int page) {return service.searchConcepts(search,page);}

    @GetMapping("/search")
    public DiagnosisConceptSearchResponse searchPage(@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="false") boolean groups) {
        return service.searchConceptPage(search,page,groups);
    }
}
