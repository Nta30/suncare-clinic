package com.example.suncareclinic.ui.screens.encounter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.suncareclinic.data.dto.DiagnosisConceptDto
import com.example.suncareclinic.data.dto.DiagnosisItemDto
import com.example.suncareclinic.data.dto.DiagnosisRequestDto
import com.example.suncareclinic.data.dto.EncounterClinicalResponseDto
import com.example.suncareclinic.data.dto.EncounterNotesRequestDto
import com.example.suncareclinic.data.dto.InvoiceResponseDto
import com.example.suncareclinic.data.dto.PrescriptionResponseDto
import com.example.suncareclinic.data.dto.PreviousEncounterDetailDto
import com.example.suncareclinic.data.dto.PreviousEncounterSummaryDto
import com.example.suncareclinic.data.dto.VitalSignRequestDto
import com.example.suncareclinic.data.repository.ApiResult
import com.example.suncareclinic.data.repository.DoctorEncounterRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

data class NotesDraftState(
    val chiefComplaint: String = "",
    val historyOfPresentIllness: String = "",
    val examinationNote: String = "",
    val treatmentPlan: String = "",
    val followupNote: String = "",
    val version: String = "",
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
    val successBanner: String? = null,
)

data class VitalFormState(
    val temperatureC: String = "",
    val pulseBpm: String = "",
    val systolicMmhg: String = "",
    val diastolicMmhg: String = "",
    val respiratoryRate: String = "",
    val spo2Percent: String = "",
    val weightKg: String = "",
    val heightCm: String = "",
    val measuredAtIso: String = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val saving: Boolean = false,
    val error: String? = null,
)

data class DiagnosisFormState(
    val editingId: Long? = null,
    val diagnosisConceptId: Long? = null,
    val selectedCode: String? = null,
    val selectedName: String? = null,
    val primaryAllowed: Boolean = true,
    val autoFilledDescription: String? = null,
    val description: String = "",
    val diagnosisType: String = "PRELIMINARY",
    val isPrimary: Boolean = false,
    val version: String? = null,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val saving: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
    val icdQuery: String = "",
    val icdGroupsOnly: Boolean = false,
    val icdLoading: Boolean = false,
    val icdResults: List<DiagnosisConceptDto> = emptyList(),
    val icdPage: Int = 1,
    val icdTotalPages: Int = 0,
    val icdTotalElements: Int = 0,
    val icdError: String? = null,
    val icdShowResults: Boolean = false,
)

data class EncounterClinicalUiState(
    val clinicalLoaded: Boolean = false,
    val clinicalLoading: Boolean = false,
    val clinical: EncounterClinicalResponseDto? = null,
    val clinicalError: String? = null,
    val notesDraft: NotesDraftState = NotesDraftState(),
    val vitalForm: VitalFormState? = null,
    val diagnosisForm: DiagnosisFormState? = null,
    val clinicalBanner: String? = null,
    val historyLoaded: Boolean = false,
    val historyLoading: Boolean = false,
    val previousItems: List<PreviousEncounterSummaryDto> = emptyList(),
    val historyPage: Int = 1,
    val historyTotalPages: Int = 0,
    val historyTotalElements: Long = 0,
    val historyError: String? = null,
    val selectedPreviousId: Long? = null,
    val previousDetailLoading: Boolean = false,
    val previousDetail: PreviousEncounterDetailDto? = null,
    val previousDetailError: String? = null,
    val prescriptions: ApiResult<List<PrescriptionResponseDto>>? = null,
    val invoicesLoaded: Boolean = false,
    val invoicesLoading: Boolean = false,
    val invoices: List<InvoiceResponseDto> = emptyList(),
    val invoicesLoadError: String? = null,
    val invoiceActionBusy: String? = null,
    val invoiceActionBanner: String? = null,
    val invoiceActionError: String? = null,
    val invoiceActionConflict: Boolean = false,
    val confirmedIssueIds: Set<Long> = emptySet(),
) {
    val hasActiveExaminationInvoice: Boolean
        get() = invoices.any { inv ->
            !inv.status.equals("VOID", ignoreCase = true) &&
                inv.lines.any { line ->
                    line.lineType.equals("EXAMINATION", ignoreCase = true) &&
                        !line.status.equals("CANCELLED", ignoreCase = true)
                }
        }
}

class EncounterClinicalViewModel(
    private val encounterId: Long,
    private val repository: DoctorEncounterRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EncounterClinicalUiState())
    val uiState: StateFlow<EncounterClinicalUiState> = _uiState.asStateFlow()

    private var clinicalJob: Job? = null
    private var historyJob: Job? = null
    private var previousDetailJob: Job? = null
    private var icdSearchJob: Job? = null
    private var prescriptionsJob: Job? = null
    private var invoicesJob: Job? = null
    // Preserve Idempotency-Keys across retries/reloads until a command commits cleanly
    private val invoiceCommandKeys = mutableMapOf<String, String>()

    fun ensureClinicalLoaded() {
        val current = _uiState.value
        if (!current.clinicalLoaded && !current.clinicalLoading) {
            loadClinical(forceReplaceNotes = true)
        }
    }

    fun loadClinical(forceReplaceNotes: Boolean = false) {
        clinicalJob?.cancel()
        clinicalJob = viewModelScope.launch {
            _uiState.update { it.copy(clinicalLoading = true, clinicalError = null) }
            when (val result = repository.getClinical(encounterId)) {
                is ApiResult.Success -> {
                    val data = result.data
                    _uiState.update { state ->
                        val keepDraft = !forceReplaceNotes && state.notesDraft.dirty
                        val nextDraft = if (keepDraft) {
                            state.notesDraft
                        } else {
                            notesDraftFromDto(data)
                        }
                        state.copy(
                            clinicalLoaded = true,
                            clinicalLoading = false,
                            clinical = data,
                            clinicalError = null,
                            notesDraft = nextDraft,
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            clinicalLoaded = true,
                            clinicalLoading = false,
                            clinicalError = result.message,
                        )
                    }
                }
            }
        }
    }

    private fun notesDraftFromDto(data: EncounterClinicalResponseDto): NotesDraftState {
        val notes = data.notes
        return NotesDraftState(
            chiefComplaint = notes?.chiefComplaint.orEmpty(),
            historyOfPresentIllness = notes?.historyOfPresentIllness.orEmpty(),
            examinationNote = notes?.examinationNote.orEmpty(),
            treatmentPlan = notes?.treatmentPlan.orEmpty(),
            followupNote = notes?.followupNote.orEmpty(),
            version = notes?.version.orEmpty(),
            dirty = false,
        )
    }

    fun updateNotesDraft(transform: (NotesDraftState) -> NotesDraftState) {
        _uiState.update { state ->
            state.copy(
                notesDraft = transform(state.notesDraft).copy(
                    dirty = true,
                    error = null,
                    successBanner = null,
                ),
            )
        }
    }

    fun saveNotes() {
        val draft = _uiState.value.notesDraft
        if (draft.saving) return
        if (draft.version.isBlank()) {
            _uiState.update {
                it.copy(notesDraft = draft.copy(error = "Thiếu phiên bản ghi chú. Hãy tải lại dữ liệu lâm sàng."))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(notesDraft = draft.copy(saving = true, error = null, conflict = false, successBanner = null))
            }
            val request = EncounterNotesRequestDto(
                chiefComplaint = draft.chiefComplaint.trim().ifBlank { null },
                historyOfPresentIllness = draft.historyOfPresentIllness.trim().ifBlank { null },
                examinationNote = draft.examinationNote.trim().ifBlank { null },
                treatmentPlan = draft.treatmentPlan.trim().ifBlank { null },
                followupNote = draft.followupNote.trim().ifBlank { null },
                version = draft.version,
                symptoms = _uiState.value.clinical?.notes?.symptoms,
            )
            when (val result = repository.saveNotes(encounterId, request)) {
                is ApiResult.Success -> {
                    val updated = result.data
                    _uiState.update {
                        it.copy(
                            clinical = updated,
                            notesDraft = notesDraftFromDto(updated).copy(
                                successBanner = "Đã lưu ghi chú lâm sàng.",
                            ),
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            notesDraft = draft.copy(
                                saving = false,
                                error = result.message,
                                conflict = result.status == 409,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun openRecordVital() {
        _uiState.update {
            it.copy(
                vitalForm = VitalFormState(),
                clinicalBanner = null,
            )
        }
    }

    fun updateVitalForm(transform: (VitalFormState) -> VitalFormState) {
        _uiState.update { state ->
            val current = state.vitalForm ?: return@update state
            state.copy(
                vitalForm = transform(current).copy(
                    error = null,
                    idempotencyKey = UUID.randomUUID().toString(),
                ),
            )
        }
    }

    fun closeVitalForm() {
        _uiState.update { it.copy(vitalForm = null) }
    }

    fun submitVitalForm() {
        val form = _uiState.value.vitalForm ?: return
        if (form.saving) return
        val hasAnyValue = listOf(
            form.temperatureC,
            form.pulseBpm,
            form.systolicMmhg,
            form.diastolicMmhg,
            form.respiratoryRate,
            form.spo2Percent,
            form.weightKg,
            form.heightCm,
        ).any { it.trim().isNotBlank() }

        if (!hasAnyValue) {
            _uiState.update {
                it.copy(vitalForm = form.copy(error = "Vui lòng nhập ít nhất một chỉ số sinh hiệu."))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(vitalForm = form.copy(saving = true, error = null)) }
            val request = VitalSignRequestDto(
                measuredAt = form.measuredAtIso,
                temperatureC = form.temperatureC.trim().ifBlank { null },
                pulseBpm = form.pulseBpm.trim().ifBlank { null },
                systolicMmhg = form.systolicMmhg.trim().ifBlank { null },
                diastolicMmhg = form.diastolicMmhg.trim().ifBlank { null },
                respiratoryRate = form.respiratoryRate.trim().ifBlank { null },
                spo2Percent = form.spo2Percent.trim().ifBlank { null },
                weightKg = form.weightKg.trim().ifBlank { null },
                heightCm = form.heightCm.trim().ifBlank { null },
            )
            when (val result = repository.recordVitalSign(encounterId, form.idempotencyKey, request)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            clinical = result.data,
                            vitalForm = null,
                            clinicalBanner = if (result.data.replayed) {
                                "Chỉ số sinh hiệu đã được ghi nhận trước đó (chống trùng)."
                            } else {
                                "Đã ghi nhận chỉ số sinh hiệu mới."
                            },
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    // Keep idempotencyKey intact on network/5xx so doctor can safely retry
                    val nextKey = if (result.status in listOf(400, 409, 422)) {
                        UUID.randomUUID().toString()
                    } else {
                        form.idempotencyKey
                    }
                    _uiState.update {
                        it.copy(
                            vitalForm = form.copy(
                                saving = false,
                                error = result.message,
                                idempotencyKey = nextKey,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun openAddDiagnosis() {
        _uiState.update {
            it.copy(
                diagnosisForm = DiagnosisFormState(icdShowResults = true),
                clinicalBanner = null,
            )
        }
        searchIcdConcepts(query = "", page = 1, groupsOnly = false)
    }

    fun openEditDiagnosis(item: DiagnosisItemDto) {
        val allowed = item.primaryAllowed ?: true
        _uiState.update {
            it.copy(
                diagnosisForm = DiagnosisFormState(
                    editingId = item.id,
                    diagnosisConceptId = item.diagnosisConceptId,
                    selectedCode = item.diagnosisCode,
                    selectedName = item.diagnosisName,
                    primaryAllowed = allowed,
                    autoFilledDescription = if (item.description == item.diagnosisName) item.diagnosisName else null,
                    description = item.description,
                    diagnosisType = item.diagnosisType,
                    isPrimary = if (allowed) item.isPrimary else false,
                    version = item.version,
                    icdShowResults = false,
                ),
                clinicalBanner = null,
            )
        }
    }

    fun closeDiagnosisForm() {
        icdSearchJob?.cancel()
        _uiState.update { it.copy(diagnosisForm = null) }
    }

    fun updateDiagnosisForm(transform: (DiagnosisFormState) -> DiagnosisFormState) {
        _uiState.update { state ->
            val current = state.diagnosisForm ?: return@update state
            val updated = transform(current)
            val safePrimary = if (!updated.primaryAllowed) false else updated.isPrimary
            state.copy(
                diagnosisForm = updated.copy(
                    isPrimary = safePrimary,
                    error = null,
                    idempotencyKey = UUID.randomUUID().toString(),
                ),
            )
        }
    }

    fun updateIcdQuery(query: String) {
        _uiState.update { state ->
            val form = state.diagnosisForm ?: return@update state
            state.copy(diagnosisForm = form.copy(icdQuery = query, icdShowResults = true, icdError = null))
        }
        icdSearchJob?.cancel()
        val groupsOnly = _uiState.value.diagnosisForm?.icdGroupsOnly ?: false
        icdSearchJob = viewModelScope.launch {
            delay(300)
            searchIcdConcepts(query = query, page = 1, groupsOnly = groupsOnly)
        }
    }

    fun toggleIcdGroupsOnly(groupsOnly: Boolean) {
        val query = _uiState.value.diagnosisForm?.icdQuery.orEmpty()
        _uiState.update { state ->
            val form = state.diagnosisForm ?: return@update state
            state.copy(diagnosisForm = form.copy(icdGroupsOnly = groupsOnly, icdShowResults = true))
        }
        searchIcdConcepts(query = query, page = 1, groupsOnly = groupsOnly)
    }

    fun loadIcdPage(page: Int) {
        val form = _uiState.value.diagnosisForm ?: return
        searchIcdConcepts(query = form.icdQuery, page = page, groupsOnly = form.icdGroupsOnly)
    }

    private fun searchIcdConcepts(query: String, page: Int, groupsOnly: Boolean) {
        icdSearchJob?.cancel()
        icdSearchJob = viewModelScope.launch {
            _uiState.update { state ->
                val form = state.diagnosisForm ?: return@update state
                state.copy(diagnosisForm = form.copy(icdLoading = true, icdError = null, icdShowResults = true))
            }
            when (val result = repository.searchDiagnosisConcepts(search = query, page = page, groups = groupsOnly)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        val form = state.diagnosisForm ?: return@update state
                        state.copy(
                            diagnosisForm = form.copy(
                                icdLoading = false,
                                icdResults = result.data.items,
                                icdPage = result.data.page,
                                icdTotalPages = result.data.totalPages,
                                icdTotalElements = result.data.totalElements,
                                icdError = null,
                            ),
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    _uiState.update { state ->
                        val form = state.diagnosisForm ?: return@update state
                        state.copy(
                            diagnosisForm = form.copy(
                                icdLoading = false,
                                icdError = result.message,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun selectDiagnosisConcept(concept: DiagnosisConceptDto) {
        _uiState.update { state ->
            val form = state.diagnosisForm ?: return@update state
            val shouldAutofill = form.description.isBlank() ||
                (form.autoFilledDescription != null && form.description == form.autoFilledDescription)
            val nextDescription = if (shouldAutofill) concept.name else form.description
            val nextPrimary = if (concept.primaryAllowed) form.isPrimary else false
            state.copy(
                diagnosisForm = form.copy(
                    diagnosisConceptId = concept.id,
                    selectedCode = concept.code,
                    selectedName = concept.name,
                    primaryAllowed = concept.primaryAllowed,
                    autoFilledDescription = concept.name,
                    description = nextDescription,
                    isPrimary = nextPrimary,
                    icdShowResults = false,
                    error = null,
                    idempotencyKey = UUID.randomUUID().toString(),
                ),
            )
        }
    }

    fun clearDiagnosisConcept() {
        _uiState.update { state ->
            val form = state.diagnosisForm ?: return@update state
            state.copy(
                diagnosisForm = form.copy(
                    diagnosisConceptId = null,
                    selectedCode = null,
                    selectedName = null,
                    primaryAllowed = true,
                    autoFilledDescription = null,
                    icdShowResults = true,
                    error = null,
                    idempotencyKey = UUID.randomUUID().toString(),
                ),
            )
        }
    }

    fun submitDiagnosisForm() {
        val form = _uiState.value.diagnosisForm ?: return
        if (form.saving) return
        val desc = form.description.trim()
        if (desc.isBlank()) {
            _uiState.update { it.copy(diagnosisForm = form.copy(error = "Vui lòng nhập diễn giải chẩn đoán.")) }
            return
        }
        if (form.diagnosisType == "FINAL" && form.diagnosisConceptId == null) {
            _uiState.update {
                it.copy(
                    diagnosisForm = form.copy(
                        error = "Chẩn đoán xác định (FINAL) bắt buộc chọn mã từ danh mục ICD-10-VN.",
                    ),
                )
            }
            return
        }
        if (form.isPrimary && !form.primaryAllowed) {
            _uiState.update {
                it.copy(
                    diagnosisForm = form.copy(
                        error = "Mã ICD đã chọn không được phép dùng làm chẩn đoán chính.",
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(diagnosisForm = form.copy(saving = true, error = null, conflict = false)) }
            val request = DiagnosisRequestDto(
                diagnosisConceptId = form.diagnosisConceptId,
                description = desc,
                diagnosisType = form.diagnosisType,
                isPrimary = form.isPrimary,
                version = form.version,
            )
            when (val result = repository.saveDiagnosis(encounterId, form.editingId, form.idempotencyKey, request)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            clinical = result.data,
                            diagnosisForm = null,
                            clinicalBanner = if (form.editingId == null) {
                                "Đã thêm chẩn đoán."
                            } else {
                                "Đã cập nhật chẩn đoán."
                            },
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    val nextKey = if (result.status in listOf(400, 409, 422)) {
                        UUID.randomUUID().toString()
                    } else {
                        form.idempotencyKey
                    }
                    _uiState.update {
                        it.copy(
                            diagnosisForm = form.copy(
                                saving = false,
                                error = result.message,
                                conflict = result.status == 409,
                                idempotencyKey = nextKey,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun ensureHistoryLoaded() {
        val current = _uiState.value
        if (!current.historyLoaded && !current.historyLoading) {
            loadPreviousEncounters(page = 1)
        }
    }

    fun loadPreviousEncounters(page: Int = _uiState.value.historyPage) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _uiState.update { it.copy(historyLoading = true, historyError = null, historyPage = page) }
            when (val result = repository.getPreviousEncounters(encounterId, page)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            historyLoaded = true,
                            historyLoading = false,
                            previousItems = result.data.items,
                            historyPage = result.data.page,
                            historyTotalPages = result.data.totalPages,
                            historyTotalElements = result.data.totalElements,
                            historyError = null,
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            historyLoaded = true,
                            historyLoading = false,
                            historyError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun openPreviousEncounterDetail(previousId: Long) {
        previousDetailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedPreviousId = previousId,
                previousDetailLoading = true,
                previousDetail = null,
                previousDetailError = null,
            )
        }
        previousDetailJob = viewModelScope.launch {
            when (val result = repository.getPreviousEncounterDetail(encounterId, previousId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            previousDetailLoading = false,
                            previousDetail = result.data,
                            previousDetailError = null,
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            previousDetailLoading = false,
                            previousDetailError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun retryPreviousDetail() {
        val prevId = _uiState.value.selectedPreviousId ?: return
        openPreviousEncounterDetail(prevId)
    }

    fun closePreviousDetail() {
        previousDetailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedPreviousId = null,
                previousDetailLoading = false,
                previousDetail = null,
                previousDetailError = null,
            )
        }
    }

    fun loadPrescriptions() {
        if (prescriptionsJob?.isActive == true) return
        _uiState.update { it.copy(prescriptions = null) }
        prescriptionsJob = viewModelScope.launch {
            val result = repository.getPrescriptions(encounterId)
            ensureActive()
            if (result is ApiResult.Error && result.status == 401) onSessionExpired()
            _uiState.update { it.copy(prescriptions = result) }
        }
    }

    fun clearPrescriptions() {
        prescriptionsJob?.cancel()
        _uiState.update { it.copy(prescriptions = null) }
    }

    fun ensureInvoicesLoaded() {
        val current = _uiState.value
        if (!current.invoicesLoaded && !current.invoicesLoading) {
            loadInvoices()
        }
    }

    fun loadInvoices() {
        if (_uiState.value.invoiceActionBusy != null) return
        invoicesJob?.cancel()
        invoicesJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    invoicesLoading = true,
                    invoicesLoadError = null,
                    invoiceActionError = null,
                    invoiceActionConflict = false,
                )
            }
            when (val result = repository.getInvoices(encounterId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            invoicesLoaded = true,
                            invoicesLoading = false,
                            invoices = result.data.sortedBy { inv -> inv.id },
                            invoicesLoadError = null,
                            confirmedIssueIds = emptySet(),
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            invoicesLoaded = true,
                            invoicesLoading = false,
                            invoicesLoadError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun toggleConfirmIssue(invoiceId: Long, checked: Boolean) {
        _uiState.update { state ->
            val next = if (checked) state.confirmedIssueIds + invoiceId else state.confirmedIssueIds - invoiceId
            state.copy(confirmedIssueIds = next)
        }
    }

    fun createExaminationInvoice() {
        if (_uiState.value.invoiceActionBusy != null) return
        val signature = "create"
        val key = invoiceCommandKeys.getOrPut(signature) { UUID.randomUUID().toString() }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    invoiceActionBusy = "create",
                    invoiceActionBanner = null,
                    invoiceActionError = null,
                    invoiceActionConflict = false,
                )
            }
            when (val result = repository.createExaminationInvoice(encounterId, key)) {
                is ApiResult.Success -> {
                    invoiceCommandKeys.remove(signature)
                    val saved = result.data
                    val msg = if (saved.replayed) {
                        "Yêu cầu đã được lưu trước đó; đã lấy lại hóa đơn hiện có."
                    } else {
                        "Đã tạo hóa đơn nháp. Hãy kiểm tra thông tin trước khi phát hành."
                    }
                    _uiState.update { state ->
                        val merged = (state.invoices.filterNot { it.id == saved.id } + saved).sortedBy { it.id }
                        state.copy(
                            invoicesLoaded = true,
                            invoices = merged,
                            invoiceActionBusy = null,
                            invoiceActionBanner = msg,
                            invoiceActionError = null,
                            invoiceActionConflict = false,
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    if (result.status == 409) {
                        invoiceCommandKeys.remove(signature)
                    }
                    _uiState.update {
                        it.copy(
                            invoiceActionBusy = null,
                            invoiceActionError = result.message,
                            invoiceActionConflict = result.status == 409,
                        )
                    }
                }
            }
        }
    }

    fun issueInvoice(invoice: InvoiceResponseDto) {
        if (_uiState.value.invoiceActionBusy != null) return
        if (!_uiState.value.confirmedIssueIds.contains(invoice.id)) return
        val version = invoice.version?.trim().orEmpty()
        if (version.isBlank()) {
            _uiState.update {
                it.copy(invoiceActionError = "Thiếu phiên bản hóa đơn. Hãy tải lại danh sách hóa đơn.")
            }
            return
        }
        val signature = "issue:${invoice.id}:$version"
        val key = invoiceCommandKeys.getOrPut(signature) { UUID.randomUUID().toString() }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    invoiceActionBusy = "issue-${invoice.id}",
                    invoiceActionBanner = null,
                    invoiceActionError = null,
                    invoiceActionConflict = false,
                )
            }
            when (val result = repository.issueExaminationInvoice(encounterId, invoice.id, version, key)) {
                is ApiResult.Success -> {
                    invoiceCommandKeys.remove(signature)
                    val issued = result.data
                    val msg = if (issued.replayed) {
                        "Yêu cầu phát hành đã được lưu trước đó; đã lấy lại hóa đơn đã phát hành."
                    } else {
                        "Đã phát hành hóa đơn. Thu ngân sẽ tiếp nhận thanh toán theo hóa đơn này."
                    }
                    _uiState.update { state ->
                        val merged = (state.invoices.filterNot { it.id == issued.id } + issued).sortedBy { it.id }
                        state.copy(
                            invoicesLoaded = true,
                            invoices = merged,
                            invoiceActionBusy = null,
                            invoiceActionBanner = msg,
                            invoiceActionError = null,
                            invoiceActionConflict = false,
                            confirmedIssueIds = state.confirmedIssueIds - invoice.id,
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    if (result.status == 409) {
                        invoiceCommandKeys.remove(signature)
                    }
                    _uiState.update {
                        it.copy(
                            invoiceActionBusy = null,
                            invoiceActionError = result.message,
                            invoiceActionConflict = result.status == 409,
                        )
                    }
                }
            }
        }
    }

    class Factory(
        private val encounterId: Long,
        private val repository: DoctorEncounterRepository,
        private val onSessionExpired: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EncounterClinicalViewModel(encounterId, repository, onSessionExpired) as T
        }
    }
}
