package com.example.suncareclinic.data.dto

data class EncounterWorklistDto(
    val items: List<EncounterWorkItemDto> = emptyList(),
    val totalElements: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 10,
    val totalPages: Int = 0,
)

data class EncounterWorkItemDto(
    val encounterId: Long,
    val encounterCode: String,
    val status: String,
    val appointmentCode: String? = null,
    val appointmentStatus: String? = null,
    val patientCode: String? = null,
    val patientName: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val roomName: String? = null,
    val specialtyName: String? = null,
)

data class EncounterStartRequestDto(
    val presenceConfirmed: Boolean = true,
)

data class EncounterCompleteRequestDto(val completionConfirmed: Boolean = true)

data class EncounterCompleteResponseDto(
    val encounterId: Long,
    val status: String,
    val completedAt: String?,
    val replayed: Boolean = false,
)

data class EncounterStartResponseDto(
    val encounterId: Long,
    val encounterCode: String,
    val status: String,
    val startedAt: String? = null,
    val historyText: String? = null,
    val allergyText: String? = null,
    val replayed: Boolean = false,
)

data class EncounterHistoryResponseDto(
    val encounter: EncounterWorkItemDto,
    val editable: Boolean = false,
    val histories: List<PatientHistoryItemDto> = emptyList(),
    val allergies: List<PatientAllergyItemDto> = emptyList(),
    val snapshot: EncounterSnapshotDto? = null,
    val allergenOptions: List<AllergenOptionDto> = emptyList(),
)

data class PatientHistoryItemDto(
    val id: Long,
    val historyType: String,
    val description: String,
    val isActive: Boolean = true,
    val version: String? = null,
)

data class PatientAllergyItemDto(
    val id: Long,
    val substance: String? = null,
    val allergenId: Long? = null,
    val allergenCode: String? = null,
    val otherNote: String? = null,
    val reaction: String? = null,
    val severity: String,
    val isActive: Boolean = true,
    val version: String? = null,
)

data class EncounterSnapshotDto(
    val historyText: String? = null,
    val allergyText: String? = null,
    val recordedAt: String? = null,
)

data class AllergenOptionDto(
    val id: Long,
    val code: String,
    val name: String,
    val isActive: Boolean = true,
    val version: String? = null,
)

data class PatientHistoryRequestDto(
    val historyType: String,
    val description: String,
    val isActive: Boolean,
    val version: String? = null,
)

data class PatientAllergyRequestDto(
    val allergenId: Long? = null,
    val otherNote: String? = null,
    val reaction: String? = null,
    val severity: String,
    val isActive: Boolean,
    val version: String? = null,
)

// --- P19: Clinical (Vitals, Notes, Diagnoses, ICD-10-VN Lookup) ---

data class EncounterClinicalResponseDto(
    val notes: EncounterNotesDto? = null,
    val vitals: List<VitalSignDto> = emptyList(),
    val diagnoses: List<DiagnosisItemDto> = emptyList(),
    val editable: Boolean = false,
    val replayed: Boolean = false,
)

data class EncounterNotesDto(
    val chiefComplaint: String? = null,
    val examinationNote: String? = null,
    val treatmentPlan: String? = null,
    val followupNote: String? = null,
    val version: String? = null,
    val historyOfPresentIllness: String? = null,
    val symptoms: List<ClinicalSymptomDto> = emptyList(),
)

data class ClinicalSymptomDto(
    val symptomId: Long? = null,
    val name: String,
)

data class VitalSignDto(
    val id: Long,
    val measuredAt: String? = null,
    val temperatureC: String? = null,
    val pulseBpm: Int? = null,
    val systolicMmhg: Int? = null,
    val diastolicMmhg: Int? = null,
    val respiratoryRate: Int? = null,
    val spo2Percent: String? = null,
    val weightKg: String? = null,
    val heightCm: String? = null,
)

data class DiagnosisItemDto(
    val id: Long,
    val diagnosisConceptId: Long? = null,
    val codeSystem: String? = null,
    val codeVersion: String? = null,
    val diagnosisCode: String? = null,
    val diagnosisName: String? = null,
    val description: String,
    val diagnosisType: String,
    val isPrimary: Boolean = false,
    val version: String? = null,
    val primaryAllowed: Boolean? = null,
)

data class VitalSignRequestDto(
    val measuredAt: String,
    val temperatureC: String? = null,
    val pulseBpm: String? = null,
    val systolicMmhg: String? = null,
    val diastolicMmhg: String? = null,
    val respiratoryRate: String? = null,
    val spo2Percent: String? = null,
    val weightKg: String? = null,
    val heightCm: String? = null,
)

data class EncounterNotesRequestDto(
    val chiefComplaint: String? = null,
    val examinationNote: String? = null,
    val treatmentPlan: String? = null,
    val followupNote: String? = null,
    val version: String,
    val historyOfPresentIllness: String? = null,
    val symptoms: List<ClinicalSymptomDto>? = null,
)

data class DiagnosisRequestDto(
    val diagnosisConceptId: Long? = null,
    val description: String,
    val diagnosisType: String,
    val isPrimary: Boolean,
    val version: String? = null,
)

data class DiagnosisConceptDto(
    val id: Long,
    val codeSystem: String? = null,
    val codeVersion: String? = null,
    val code: String,
    val name: String,
    val primaryAllowed: Boolean = true,
    val englishName: String? = null,
    val categoryCode: String? = null,
    val categoryName: String? = null,
)

data class DiagnosisConceptSearchResponseDto(
    val items: List<DiagnosisConceptDto> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val totalElements: Int = 0,
    val totalPages: Int = 0,
)

// --- P41 (partial): Previous COMPLETED encounters (read-only for doctor) ---

data class PreviousEncounterSummaryDto(
    val encounterId: Long,
    val encounterCode: String,
    val completedAt: String? = null,
    val visitMode: String? = null,
    val doctorName: String? = null,
    val specialtyName: String? = null,
    val departmentName: String? = null,
    val primaryDiagnosis: String? = null,
)

data class PreviousEncounterPageDto(
    val items: List<PreviousEncounterSummaryDto> = emptyList(),
    val totalElements: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 10,
    val totalPages: Int = 0,
)

data class PreviousEncounterDetailDto(
    val encounter: PreviousEncounterSummaryDto,
    val clinical: EncounterClinicalResponseDto? = null,
    val snapshot: EncounterSnapshotDto? = null,
    val prescriptions: List<PreviousPrescriptionDto> = emptyList(),
    val results: List<PreviousClinicalResultDto> = emptyList(),
)

data class PreviousPrescriptionDto(
    val id: Long,
    val code: String? = null,
    val issuedAt: String? = null,
    val instruction: String? = null,
    val items: List<PreviousDrugItemDto> = emptyList(),
)

data class PreviousDrugItemDto(
    val drugName: String? = null,
    val strength: String? = null,
    val dose: String? = null,
    val route: String? = null,
    val frequency: String? = null,
    val durationDays: Int = 0,
    val quantity: String? = null,
    val unit: String? = null,
    val instruction: String? = null,
)

data class PreviousClinicalResultDto(
    val id: Long,
    val serviceName: String? = null,
    val versionNo: Int = 1,
    val publishedAt: String? = null,
    val description: String? = null,
    val conclusion: String? = null,
    val values: List<PreviousResultValueDto> = emptyList(),
)

data class PreviousResultValueDto(
    val analyteName: String? = null,
    val numericValue: String? = null,
    val textValue: String? = null,
    val unit: String? = null,
    val referenceText: String? = null,
    val abnormalFlag: String? = null,
)

// --- P20: Examination Invoice (DRAFT / ISSUED) ---

data class InvoiceResponseDto(
    val id: Long,
    val code: String,
    val encounterId: Long,
    val patientId: Long,
    val status: String,
    val patientName: String? = null,
    val patientAddress: String? = null,
    val facilityName: String? = null,
    val facilityAddress: String? = null,
    val totalAmount: String? = null,
    val currency: String? = "VND",
    val issuedAt: String? = null,
    val version: String? = null,
    val replayed: Boolean = false,
    val lines: List<InvoiceLineDto> = emptyList(),
)

data class InvoiceLineDto(
    val id: Long,
    val lineNo: Int,
    val lineType: String? = null,
    val serviceId: Long = 0,
    val priceId: Long = 0,
    val serviceName: String? = null,
    val unitName: String? = null,
    val quantity: String? = null,
    val unitPrice: String? = null,
    val amount: String? = null,
    val status: String? = null,
)

data class IssueInvoiceRequestDto(
    val version: String,
)
