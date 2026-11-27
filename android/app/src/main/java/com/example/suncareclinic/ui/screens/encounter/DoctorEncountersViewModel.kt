package com.example.suncareclinic.ui.screens.encounter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.suncareclinic.data.dto.EncounterHistoryResponseDto
import com.example.suncareclinic.data.dto.EncounterWorkItemDto
import com.example.suncareclinic.data.dto.PatientAllergyItemDto
import com.example.suncareclinic.data.dto.PatientAllergyRequestDto
import com.example.suncareclinic.data.dto.PatientHistoryItemDto
import com.example.suncareclinic.data.dto.PatientHistoryRequestDto
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ENCOUNTER_VN_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
private val ENCOUNTER_ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val definitiveCompletionErrors = setOf(400, 401, 403, 404, 409, 422)

enum class EncounterCompletionPhase { Idle, Sending, NeedsVerification }

enum class EncounterStatusFilter(val code: String, val label: String) {
    All("ALL", "Tất cả"),
    Pending("PENDING", "Chờ khám"),
    InProgress("IN_PROGRESS", "Đang khám"),
    WaitingCls("WAITING_CLS", "Chờ CLS"),
    Completed("COMPLETED", "Hoàn tất"),
    Cancelled("CANCELLED", "Đã hủy"),
}

enum class HistoryTypeOption(val code: String, val label: String) {
    Personal("PERSONAL", "Bệnh bản thân"),
    Family("FAMILY", "Tiền sử gia đình"),
    Surgery("SURGERY", "Phẫu thuật / thủ thuật"),
}

enum class AllergySeverityOption(val code: String, val label: String) {
    Unknown("UNKNOWN", "Chưa rõ"),
    Mild("MILD", "Nhẹ"),
    Moderate("MODERATE", "Trung bình"),
    Severe("SEVERE", "Nặng"),
}

data class HistoryFormState(
    val editingId: Long? = null,
    val historyType: String = HistoryTypeOption.Personal.code,
    val description: String = "",
    val isActive: Boolean = true,
    val version: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
)

data class AllergyFormState(
    val editingId: Long? = null,
    val allergenId: Long? = null,
    val otherNote: String = "",
    val reaction: String = "",
    val severity: String = AllergySeverityOption.Mild.code,
    val isActive: Boolean = true,
    val version: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
)

data class DoctorEncountersUiState(
    val selectedDate: String = LocalDate.now(ENCOUNTER_VN_ZONE).format(ENCOUNTER_ISO_DATE),
    val statusFilter: EncounterStatusFilter = EncounterStatusFilter.All,
    val searchQuery: String = "",
    val page: Int = 1,
    val totalPages: Int = 0,
    val totalElements: Long = 0,
    val worklistLoading: Boolean = true,
    val items: List<EncounterWorkItemDto> = emptyList(),
    val worklistError: String? = null,
    val selectedEncounterId: Long? = null,
    val detailLoading: Boolean = false,
    val detail: EncounterHistoryResponseDto? = null,
    val detailError: String? = null,
    val startBusy: Boolean = false,
    val actionBanner: String? = null,
    val actionError: String? = null,
    val historyForm: HistoryFormState? = null,
    val allergyForm: AllergyFormState? = null,
    val completionPhase: EncounterCompletionPhase = EncounterCompletionPhase.Idle,
) {
    val canCompleteEncounter: Boolean
        get() = detail?.editable == true && detail.encounter.status == "IN_PROGRESS"
}

class DoctorEncountersViewModel(
    private val repository: DoctorEncounterRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorEncountersUiState())
    val uiState: StateFlow<DoctorEncountersUiState> = _uiState.asStateFlow()

    private var worklistJob: Job? = null
    private var detailJob: Job? = null
    private var searchDebounceJob: Job? = null

    init {
        loadWorklist(page = 1)
    }

    fun shiftDate(days: Long) {
        val current = runCatching { LocalDate.parse(_uiState.value.selectedDate) }
            .getOrElse { LocalDate.now(ENCOUNTER_VN_ZONE) }
        val next = current.plusDays(days).format(ENCOUNTER_ISO_DATE)
        _uiState.update { it.copy(selectedDate = next, page = 1) }
        loadWorklist(page = 1)
    }

    fun selectToday() {
        val today = LocalDate.now(ENCOUNTER_VN_ZONE).format(ENCOUNTER_ISO_DATE)
        _uiState.update { it.copy(selectedDate = today, page = 1) }
        loadWorklist(page = 1)
    }

    fun selectStatusFilter(filter: EncounterStatusFilter) {
        _uiState.update { it.copy(statusFilter = filter, page = 1) }
        loadWorklist(page = 1)
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, page = 1) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(300)
            loadWorklist(page = 1)
        }
    }

    fun loadWorklist(page: Int = _uiState.value.page) {
        worklistJob?.cancel()
        val snapshot = _uiState.value
        worklistJob = viewModelScope.launch {
            _uiState.update { it.copy(worklistLoading = true, worklistError = null, page = page) }
            when (
                val result = repository.getWorklist(
                    date = snapshot.selectedDate,
                    status = snapshot.statusFilter.code,
                    search = snapshot.searchQuery,
                    page = page,
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            worklistLoading = false,
                            items = result.data.items,
                            page = result.data.page,
                            totalPages = result.data.totalPages,
                            totalElements = result.data.totalElements,
                            worklistError = null,
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
                            worklistLoading = false,
                            worklistError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun openEncounter(encounterId: Long) {
        if (_uiState.value.completionPhase != EncounterCompletionPhase.Idle) return
        _uiState.update {
            it.copy(
                selectedEncounterId = encounterId,
                detailLoading = true,
                detail = null,
                detailError = null,
                actionBanner = null,
                actionError = null,
                historyForm = null,
                allergyForm = null,
            )
        }
        loadDetail(encounterId)
    }

    fun closeEncounter() {
        if (_uiState.value.completionPhase != EncounterCompletionPhase.Idle) return
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedEncounterId = null,
                detailLoading = false,
                detail = null,
                detailError = null,
                actionBanner = null,
                actionError = null,
                historyForm = null,
                allergyForm = null,
            )
        }
        loadWorklist(_uiState.value.page)
    }

    fun reloadDetail() {
        val id = _uiState.value.selectedEncounterId ?: return
        loadDetail(id)
    }

    private fun loadDetail(encounterId: Long) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _uiState.update { it.copy(detailLoading = true, detailError = null) }
            val result = repository.getDetail(encounterId)
            ensureActive()
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            detailLoading = false,
                            detail = result.data,
                            detailError = null,
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
                            detailLoading = false,
                            detailError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun startSelectedEncounter() {
        val encounterId = _uiState.value.selectedEncounterId ?: return
        if (_uiState.value.startBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(startBusy = true, actionError = null, actionBanner = null) }
            when (val result = repository.startEncounter(encounterId)) {
                is ApiResult.Success -> {
                    val msg = if (result.data.replayed) {
                        "Lượt khám đã được bắt đầu trước đó và giữ nguyên snapshot ban đầu."
                    } else {
                        "Đã xác nhận có mặt, bắt đầu lượt khám và chốt snapshot tiền sử/dị ứng."
                    }
                    _uiState.update { it.copy(startBusy = false, actionBanner = msg, actionError = null) }
                    loadDetail(encounterId)
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    _uiState.update { it.copy(startBusy = false, actionError = result.message) }
                }
            }
        }
    }

    fun completeSelectedEncounter() {
        val current = _uiState.value
        val encounterId = current.selectedEncounterId ?: return
        if (current.completionPhase == EncounterCompletionPhase.Sending) return
        if (current.completionPhase == EncounterCompletionPhase.Idle &&
            (!current.canCompleteEncounter || current.detailLoading || current.startBusy ||
                current.historyForm != null || current.allergyForm != null)
        ) return

        _uiState.update {
            it.copy(completionPhase = EncounterCompletionPhase.Sending, actionBanner = null, actionError = null)
        }
        viewModelScope.launch {
            when (val result = repository.completeEncounter(encounterId)) {
                is ApiResult.Success -> {
                    detailJob?.cancel()
                    val time = formatClinDateTime(result.data.completedAt)
                    _uiState.update { state ->
                        val updatedDetail = state.detail?.let { detail ->
                            detail.copy(
                                encounter = detail.encounter.copy(
                                    status = result.data.status,
                                    appointmentStatus = result.data.status,
                                ),
                                editable = false,
                            )
                        }
                        state.copy(
                            detail = updatedDetail,
                            completionPhase = EncounterCompletionPhase.Idle,
                            actionBanner = "Đã hoàn tất lượt khám lúc $time.",
                        )
                    }
                    loadDetail(encounterId)
                    loadWorklist()
                }
                is ApiResult.Error -> {
                    val denied = result.status in setOf(401, 403, 404)
                    val rejected = result.status in definitiveCompletionErrors
                    _uiState.update {
                        it.copy(
                            completionPhase = if (rejected) EncounterCompletionPhase.Idle
                                else EncounterCompletionPhase.NeedsVerification,
                            detail = if (denied) null else it.detail,
                            detailError = if (denied) result.message else it.detailError,
                            actionError = if (rejected && !denied) result.message else null,
                        )
                    }
                    if (result.status == 401) onSessionExpired()
                }
            }
        }
    }

    fun openAddHistory() {
        _uiState.update {
            it.copy(
                historyForm = HistoryFormState(),
                actionError = null,
            )
        }
    }

    fun openEditHistory(item: PatientHistoryItemDto) {
        _uiState.update {
            it.copy(
                historyForm = HistoryFormState(
                    editingId = item.id,
                    historyType = item.historyType,
                    description = item.description,
                    isActive = item.isActive,
                    version = item.version,
                ),
                actionError = null,
            )
        }
    }

    fun updateHistoryForm(transform: (HistoryFormState) -> HistoryFormState) {
        _uiState.update { state ->
            val current = state.historyForm ?: return@update state
            state.copy(historyForm = transform(current).copy(error = null))
        }
    }

    fun closeHistoryForm() {
        _uiState.update { it.copy(historyForm = null) }
    }

    fun submitHistoryForm() {
        val encounterId = _uiState.value.selectedEncounterId ?: return
        val form = _uiState.value.historyForm ?: return
        if (form.saving) return
        val trimmed = form.description.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(historyForm = form.copy(error = "Vui lòng nhập nội dung tiền sử bệnh.")) }
            return
        }
        if (trimmed.length > 2000) {
            _uiState.update { it.copy(historyForm = form.copy(error = "Nội dung tối đa 2.000 ký tự.")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(historyForm = form.copy(saving = true, error = null, conflict = false)) }
            val request = PatientHistoryRequestDto(
                historyType = form.historyType,
                description = trimmed,
                isActive = form.isActive,
                version = form.version,
            )
            when (val result = repository.saveHistory(encounterId, form.editingId, request)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            detail = result.data,
                            historyForm = null,
                            actionBanner = if (form.editingId == null) {
                                "Đã thêm tiền sử bệnh."
                            } else {
                                "Đã cập nhật tiền sử bệnh."
                            },
                            actionError = null,
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
                            historyForm = form.copy(
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

    fun openAddAllergy() {
        val firstAllergenId = _uiState.value.detail?.allergenOptions
            ?.firstOrNull { it.isActive }?.id
        _uiState.update {
            it.copy(
                allergyForm = AllergyFormState(allergenId = firstAllergenId),
                actionError = null,
            )
        }
    }

    fun openEditAllergy(item: PatientAllergyItemDto) {
        _uiState.update {
            it.copy(
                allergyForm = AllergyFormState(
                    editingId = item.id,
                    allergenId = item.allergenId,
                    otherNote = item.otherNote.orEmpty(),
                    reaction = item.reaction.orEmpty(),
                    severity = item.severity,
                    isActive = item.isActive,
                    version = item.version,
                ),
                actionError = null,
            )
        }
    }

    fun updateAllergyForm(transform: (AllergyFormState) -> AllergyFormState) {
        _uiState.update { state ->
            val current = state.allergyForm ?: return@update state
            state.copy(allergyForm = transform(current).copy(error = null))
        }
    }

    fun closeAllergyForm() {
        _uiState.update { it.copy(allergyForm = null) }
    }

    fun submitAllergyForm() {
        val encounterId = _uiState.value.selectedEncounterId ?: return
        val form = _uiState.value.allergyForm ?: return
        if (form.saving) return
        val otherTrimmed = form.otherNote.trim().ifBlank { null }
        val reactionTrimmed = form.reaction.trim().ifBlank { null }
        if (form.allergenId == null && otherTrimmed == null) {
            _uiState.update {
                it.copy(allergyForm = form.copy(error = "Hãy chọn dị nguyên trong danh mục hoặc ghi rõ dị nguyên khác."))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(allergyForm = form.copy(saving = true, error = null, conflict = false)) }
            val request = PatientAllergyRequestDto(
                allergenId = form.allergenId,
                otherNote = otherTrimmed,
                reaction = reactionTrimmed,
                severity = form.severity,
                isActive = form.isActive,
                version = form.version,
            )
            when (val result = repository.saveAllergy(encounterId, form.editingId, request)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            detail = result.data,
                            allergyForm = null,
                            actionBanner = if (form.editingId == null) {
                                "Đã thêm thông tin dị ứng."
                            } else {
                                "Đã cập nhật thông tin dị ứng."
                            },
                            actionError = null,
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
                            allergyForm = form.copy(
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

    class Factory(
        private val repository: DoctorEncounterRepository,
        private val onSessionExpired: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DoctorEncountersViewModel(repository, onSessionExpired) as T
        }
    }
}
