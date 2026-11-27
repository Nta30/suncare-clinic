package com.suncare.clinic.modules.prescription.controller;

import com.suncare.clinic.modules.prescription.dto.DrugResponse;
import com.suncare.clinic.modules.prescription.service.PrescriptionService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/doctor/drugs")
public class DoctorDrugController {
    private final PrescriptionService service;
    public DoctorDrugController(PrescriptionService service) { this.service=service; }
    @GetMapping
    public List<DrugResponse> list(@RequestParam(required=false) String search) { return service.activeDrugs(search); }
}
