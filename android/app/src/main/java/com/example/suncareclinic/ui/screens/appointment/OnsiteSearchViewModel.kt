package com.example.suncareclinic.ui.screens.appointment

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.suncareclinic.data.dto.OnsiteAppointmentBookingRequestDto
import com.example.suncareclinic.data.dto.OnsiteAppointmentBookingResponseDto
import com.example.suncareclinic.data.dto.OnsiteAppointmentOptionsDto
import com.example.suncareclinic.data.dto.OnsiteAvailabilityDto
import com.example.suncareclinic.data.repository.ApiResult
import com.example.suncareclinic.data.repository.OnsiteAppointmentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID

@Immutable
data class OnsiteSearchFilters(
    val specialtyId: Long? = null,
    val dateFrom: String = "",
    val dateTo: String = "",
    val shiftId: Long? = null,
    val doctorName: String = "",
) {
    fun hasAnyCriteria(): Boolean =
        specialtyId != null || dateFrom.isNotBlank() || dateTo.isNotBlank() || shiftId != null || doctorName.isNotBlank()
}

@Immutable
data class DoctorAvailabilityGroup(
    val doctorId: Long,
    val doctorName: String,
    val departmentName: String,
    val specialties: List<String>,
    val totalSlots: Int,
    val availableDates: List<String>,
    val itemsByDate: Map<String, List<OnsiteAvailabilityDto>>,
)

enum class SearchState {
    Idle,
    Loading,
    Ready,
    Error,
}

enum class DoctorAssignmentMode {
    SelectedDoctor,
    AutoAssign,
}

enum class BookingState {
    Idle,
    Submitting,
    Success,
    Error,
}

@Immutable
data class OnsiteSearchUiState(
    val optionsLoading: Boolean = true,
    val optionsError: String? = null,
    val options: OnsiteAppointmentOptionsDto? = null,
    val filters: OnsiteSearchFilters = OnsiteSearchFilters(),
    val validationError: String? = null,
    val searchState: SearchState = SearchState.Idle,
    val searchError: String? = null,
    val doctorGroups: List<DoctorAvailabilityGroup> = emptyList(),
    val totalMatchingSessions: Int = 0,
    val expandedDoctorId: Long? = null,
    val expandedDate: String? = null,
    val selectedSession: OnsiteAvailabilityDto? = null,
    val assignmentMode: DoctorAssignmentMode = DoctorAssignmentMode.SelectedDoctor,
    val symptoms: String = "",
    val symptomsError: String? = null,
    val bookingState: BookingState = BookingState.Idle,
    val bookingError: String? = null,
    val bookingErrorStatus: Int? = null,
    val bookingResult: OnsiteAppointmentBookingResponseDto? = null,
) {
    val autoAssignDisabled: Boolean
        get() = filters.doctorName.isNotBlank()

    val isBookingBusy: Boolean
        get() = bookingState == BookingState.Submitting
}

class OnsiteSearchViewModel(
    private val repository: OnsiteAppointmentRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnsiteSearchUiState())
    val uiState: StateFlow<OnsiteSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    // Giữ Idempotency-Key ổn định theo đúng chữ ký payload để retry an toàn khi lỗi mạng/server.
    private var lastPayloadSignature: String? = null
    private var currentIdempotencyKey: String? = null

    init {
        loadOptions()
    }

    fun loadOptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(optionsLoading = true, optionsError = null) }
            when (val result = repository.getOptions()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        optionsLoading = false,
                        optionsError = null,
                        options = result.data,
                    )
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            optionsLoading = false,
                            optionsError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun selectSpecialty(specialtyId: Long?) {
        if (_uiState.value.isBookingBusy) return
        val current = _uiState.value.filters
        applyFilters(current.copy(specialtyId = if (current.specialtyId == specialtyId) null else specialtyId))
    }

    fun selectShift(shiftId: Long?) {
        if (_uiState.value.isBookingBusy) return
        val current = _uiState.value.filters
        applyFilters(current.copy(shiftId = if (current.shiftId == shiftId) null else shiftId))
    }

    fun updateDateRange(dateFrom: String, dateTo: String) {
        if (_uiState.value.isBookingBusy) return
        applyFilters(_uiState.value.filters.copy(dateFrom = dateFrom.trim(), dateTo = dateTo.trim()))
    }

    fun applyQuickPresetDays(days: Long) {
        if (_uiState.value.isBookingBusy) return
        val today = LocalDate.now(BUSINESS_ZONE)
        val from = today.plusDays(1)
        val to = from.plusDays(days - 1)
        applyFilters(_uiState.value.filters.copy(dateFrom = from.toString(), dateTo = to.toString()))
    }

    fun updateDoctorName(name: String) {
        if (_uiState.value.isBookingBusy) return
        applyFilters(_uiState.value.filters.copy(doctorName = name), debounceMs = 300L)
    }

    fun clearFilters() {
        if (_uiState.value.isBookingBusy) return
        applyFilters(OnsiteSearchFilters())
    }

    fun retrySearch() {
        if (_uiState.value.isBookingBusy) return
        applyFilters(_uiState.value.filters)
    }

    fun toggleDoctorExpand(doctorId: Long) {
        if (_uiState.value.isBookingBusy) return
        _uiState.update { state ->
            if (state.expandedDoctorId == doctorId) {
                state.copy(expandedDoctorId = null, expandedDate = null)
            } else {
                val firstDate = state.doctorGroups.firstOrNull { it.doctorId == doctorId }?.availableDates?.firstOrNull()
                state.copy(expandedDoctorId = doctorId, expandedDate = firstDate)
            }
        }
    }

    fun selectDoctorDate(date: String) {
        if (_uiState.value.isBookingBusy) return
        _uiState.update { state ->
            val clearSelection = state.selectedSession?.date != date
            if (clearSelection) resetIdempotency()
            state.copy(
                expandedDate = date,
                selectedSession = if (clearSelection) null else state.selectedSession,
                bookingState = if (clearSelection) BookingState.Idle else state.bookingState,
                bookingError = if (clearSelection) null else state.bookingError,
                bookingErrorStatus = if (clearSelection) null else state.bookingErrorStatus,
                bookingResult = if (clearSelection) null else state.bookingResult,
            )
        }
    }

    fun toggleSelectSession(session: OnsiteAvailabilityDto) {
        if (_uiState.value.isBookingBusy) return
        resetIdempotency()
        _uiState.update { state ->
            val same = state.selectedSession != null &&
                state.selectedSession.doctorId == session.doctorId &&
                state.selectedSession.date == session.date &&
                state.selectedSession.shiftId == session.shiftId &&
                state.selectedSession.specialtyId == session.specialtyId
            state.copy(
                selectedSession = if (same) null else session,
                assignmentMode = DoctorAssignmentMode.SelectedDoctor,
                bookingState = BookingState.Idle,
                bookingError = null,
                bookingErrorStatus = null,
                bookingResult = null,
            )
        }
    }

    fun clearSelection() {
        if (_uiState.value.isBookingBusy) return
        resetIdempotency()
        _uiState.update {
            it.copy(
                selectedSession = null,
                assignmentMode = DoctorAssignmentMode.SelectedDoctor,
                bookingState = BookingState.Idle,
                bookingError = null,
                bookingErrorStatus = null,
                bookingResult = null,
            )
        }
    }

    fun selectAssignmentMode(mode: DoctorAssignmentMode) {
        val state = _uiState.value
        if (state.isBookingBusy) return
        if (mode == DoctorAssignmentMode.AutoAssign && state.autoAssignDisabled) return
        if (state.assignmentMode != mode) {
            resetIdempotency()
            _uiState.update {
                it.copy(
                    assignmentMode = mode,
                    bookingState = BookingState.Idle,
                    bookingError = null,
                    bookingErrorStatus = null,
                )
            }
        }
    }

    fun updateSymptoms(value: String) {
        if (_uiState.value.isBookingBusy) return
        val error = if (value.trim().length > MAX_SYMPTOMS_LENGTH) {
            "Triệu chứng không được dài quá 2.000 ký tự."
        } else {
            null
        }
        _uiState.update {
            it.copy(
                symptoms = value,
                symptomsError = error,
                bookingError = if (it.bookingState == BookingState.Error) null else it.bookingError,
            )
        }
    }

    fun submitBooking() {
        val state = _uiState.value
        val session = state.selectedSession ?: return
        if (state.isBookingBusy) return

        val normalizedSymptoms = state.symptoms.trim().ifBlank { null }
        if (normalizedSymptoms != null && normalizedSymptoms.length > MAX_SYMPTOMS_LENGTH) {
            _uiState.update {
                it.copy(symptomsError = "Triệu chứng không được dài quá 2.000 ký tự.")
            }
            return
        }

        val effectiveDoctorId = if (state.assignmentMode == DoctorAssignmentMode.AutoAssign && !state.autoAssignDisabled) {
            null
        } else {
            session.doctorId
        }

        val request = OnsiteAppointmentBookingRequestDto(
            specialtyId = session.specialtyId,
            date = session.date,
            shiftId = session.shiftId,
            doctorId = effectiveDoctorId,
            symptoms = normalizedSymptoms,
        )

        val signature = "${request.specialtyId}|${request.date}|${request.shiftId}|${request.doctorId ?: ""}|${request.symptoms ?: ""}"
        val idempotencyKey = if (signature == lastPayloadSignature && !currentIdempotencyKey.isNullOrBlank()) {
            currentIdempotencyKey!!
        } else {
            val newKey = "and-onsite-${UUID.randomUUID()}"
            lastPayloadSignature = signature
            currentIdempotencyKey = newKey
            newKey
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    bookingState = BookingState.Submitting,
                    bookingError = null,
                    bookingErrorStatus = null,
                    symptomsError = null,
                )
            }
            when (val result = repository.bookOnsiteAppointment(request, idempotencyKey)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        bookingState = BookingState.Success,
                        bookingResult = result.data,
                        bookingError = null,
                        bookingErrorStatus = null,
                    )
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    // Nếu 409 hoặc 400 (lỗi nghiệp vụ/hết chỗ), tạo khóa mới cho lần chọn khác; nếu lỗi mạng/5xx giữ nguyên khóa để retry an toàn.
                    if (result.status != null && result.status in 400..499) {
                        resetIdempotency()
                    }
                    _uiState.update {
                        it.copy(
                            bookingState = BookingState.Error,
                            bookingError = result.message,
                            bookingErrorStatus = result.status,
                        )
                    }
                }
            }
        }
    }

    fun startNewSearchAfterBooking() {
        resetIdempotency()
        _uiState.update {
            it.copy(
                selectedSession = null,
                assignmentMode = DoctorAssignmentMode.SelectedDoctor,
                symptoms = "",
                symptomsError = null,
                bookingState = BookingState.Idle,
                bookingError = null,
                bookingErrorStatus = null,
                bookingResult = null,
            )
        }
        applyFilters(_uiState.value.filters)
    }

    private fun resetIdempotency() {
        lastPayloadSignature = null
        currentIdempotencyKey = null
    }

    private fun applyFilters(nextFilters: OnsiteSearchFilters, debounceMs: Long = 0L) {
        searchJob?.cancel()
        resetIdempotency()

        val validationError = validateFilters(nextFilters)
        if (validationError != null) {
            _uiState.update {
                it.copy(
                    filters = nextFilters,
                    validationError = validationError,
                    searchState = SearchState.Idle,
                    searchError = null,
                    doctorGroups = emptyList(),
                    totalMatchingSessions = 0,
                    expandedDoctorId = null,
                    expandedDate = null,
                    selectedSession = null,
                    assignmentMode = DoctorAssignmentMode.SelectedDoctor,
                    bookingState = BookingState.Idle,
                    bookingError = null,
                    bookingErrorStatus = null,
                    bookingResult = null,
                )
            }
            return
        }

        if (!nextFilters.hasAnyCriteria()) {
            _uiState.update {
                it.copy(
                    filters = nextFilters,
                    validationError = null,
                    searchState = SearchState.Idle,
                    searchError = null,
                    doctorGroups = emptyList(),
                    totalMatchingSessions = 0,
                    expandedDoctorId = null,
                    expandedDate = null,
                    selectedSession = null,
                    assignmentMode = DoctorAssignmentMode.SelectedDoctor,
                    bookingState = BookingState.Idle,
                    bookingError = null,
                    bookingErrorStatus = null,
                    bookingResult = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                filters = nextFilters,
                validationError = null,
                searchState = SearchState.Loading,
                searchError = null,
                expandedDoctorId = null,
                expandedDate = null,
                selectedSession = null,
                assignmentMode = DoctorAssignmentMode.SelectedDoctor,
                bookingState = BookingState.Idle,
                bookingError = null,
                bookingErrorStatus = null,
                bookingResult = null,
            )
        }

        searchJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            when (
                val result = repository.searchAvailability(
                    specialtyId = nextFilters.specialtyId,
                    dateFrom = nextFilters.dateFrom.ifBlank { null },
                    dateTo = nextFilters.dateTo.ifBlank { null },
                    shiftId = nextFilters.shiftId,
                    doctorName = nextFilters.doctorName.trim().ifBlank { null },
                )
            ) {
                is ApiResult.Success -> {
                    val groups = groupByDoctor(result.data)
                    _uiState.update {
                        it.copy(
                            searchState = SearchState.Ready,
                            searchError = null,
                            doctorGroups = groups,
                            totalMatchingSessions = result.data.size,
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
                            searchState = SearchState.Error,
                            searchError = result.message,
                            doctorGroups = emptyList(),
                            totalMatchingSessions = 0,
                        )
                    }
                }
            }
        }
    }

    private fun validateFilters(filters: OnsiteSearchFilters): String? {
        val today = LocalDate.now(BUSINESS_ZONE)
        val from = if (filters.dateFrom.isNotBlank()) parseDate(filters.dateFrom) ?: return "Ngày bắt đầu không đúng định dạng YYYY-MM-DD." else null
        val to = if (filters.dateTo.isNotBlank()) parseDate(filters.dateTo) ?: return "Ngày kết thúc không đúng định dạng YYYY-MM-DD." else null
        if ((from != null && from.isBefore(today)) || (to != null && to.isBefore(today))) {
            return "Khoảng ngày khám phải từ hôm nay trở đi."
        }
        if (from != null && to != null && from.isAfter(to)) {
            return "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu."
        }
        if (filters.doctorName.trim().length > 100) {
            return "Từ khóa tìm bác sĩ không được dài quá 100 ký tự."
        }
        return null
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun groupByDoctor(items: List<OnsiteAvailabilityDto>): List<DoctorAvailabilityGroup> {
        return items.groupBy { it.doctorId }.map { (doctorId, sessions) ->
            val first = sessions.first()
            val byDate = sessions.groupBy { it.date }.toSortedMap()
            DoctorAvailabilityGroup(
                doctorId = doctorId,
                doctorName = first.doctorName,
                departmentName = first.departmentName,
                specialties = sessions.map { it.specialtyName }.distinct(),
                totalSlots = sessions.size,
                availableDates = byDate.keys.toList(),
                itemsByDate = byDate,
            )
        }
    }

    class Factory(
        private val repository: OnsiteAppointmentRepository,
        private val onSessionExpired: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnsiteSearchViewModel(repository, onSessionExpired) as T
        }
    }

    companion object {
        val BUSINESS_ZONE: ZoneId = ZoneId.of("Asia/Saigon")
        const val MAX_SYMPTOMS_LENGTH = 2000
    }
}
