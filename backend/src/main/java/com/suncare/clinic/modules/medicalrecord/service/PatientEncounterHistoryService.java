package com.suncare.clinic.modules.medicalrecord.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.encounter.repository.EncounterClinicalRepository;
import com.suncare.clinic.modules.encounter.repository.EncounterHistoryRepository;
import com.suncare.clinic.modules.medicalrecord.dto.PatientEncounterHistoryResponse.*;
import com.suncare.clinic.modules.medicalrecord.repository.PatientEncounterHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientEncounterHistoryService {
    private final PatientEncounterHistoryRepository repository;
    private final EncounterClinicalRepository clinical;
    private final EncounterHistoryRepository histories;
    public PatientEncounterHistoryService(PatientEncounterHistoryRepository repository, EncounterClinicalRepository clinical, EncounterHistoryRepository histories) {
        this.repository=repository; this.clinical=clinical; this.histories=histories;
    }
    @Transactional(readOnly=true)
    public Page list(CurrentAccountResponse account,long contextId,int page) {
        long patientId = patient(account,contextId);
        if(page<1) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION","Trang phải từ 1.");
        return repository.list(patientId,contextId,page);
    }
    @Transactional(readOnly=true)
    public Detail detail(CurrentAccountResponse account,long contextId,long previousId) {
        var summary=repository.find(patient(account,contextId),contextId,previousId);
        if(summary==null) throw notFound();
        return new Detail(summary,clinical.detail(previousId,false,false),histories.snapshot(previousId),
                repository.prescriptions(previousId),repository.results(previousId));
    }
    private long patient(CurrentAccountResponse account,long contextId) {
        if(account.staffId()==null) throw notFound();
        Long patientId=repository.contextPatient(contextId,account.staffId());
        if(patientId==null) throw notFound();
        return patientId;
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"ENCOUNTER_NOT_FOUND","Không tìm thấy lượt khám trong phạm vi được xem."); }
}
