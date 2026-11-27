package com.example.suncareclinic.ui.screens.appointment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.suncareclinic.data.dto.AppointmentScheduleDetailDto
import com.example.suncareclinic.data.dto.AppointmentScheduleListItemDto
import com.example.suncareclinic.data.dto.DoctorOnlineSlotDto
import com.example.suncareclinic.data.dto.DoctorSlotPatientDto
import com.example.suncareclinic.data.dto.DoctorWorkShiftDto
import com.example.suncareclinic.data.repository.ApiResult
import com.example.suncareclinic.data.repository.AppointmentScheduleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DOCTOR_VIETNAM_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
private val ISO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

enum class DoctorScheduleSection(val label: String) {
    Appointments("Lịch hẹn được giao"),
    Timetable("Lịch trực công bố"),
}

enum class DoctorVisitModeFilter(val code: String?, val label: String) {
    All(null, "Mọi hình thức"),
    Onsite("ONSITE", "Trực tiếp"),
    Online("ONLINE", "Trực tuyến"),
}

enum class DoctorTimetableRange(val daysAhead: Long, val label: String) {
    Today(0, "Hôm nay"),
    Next7Days(6, "7 ngày tới"),
    Next14Days(13, "14 ngày tới"),
    Next30Days(29, "30 ngày tới"),
}

data class SlotGroupState(
    val loading: Boolean = false,
    val slots: List<DoctorOnlineSlotDto> = emptyList(),
    val error: String? = null,
    val loaded: Boolean = false,
)

data class DoctorAppointmentsUiState(
    val section: DoctorScheduleSection = DoctorScheduleSection.Appointments,
    val appointmentsLoading: Boolean = true,
    val appointments: List<AppointmentScheduleListItemDto> = emptyList(),
    val appointmentsError: String? = null,
    val statusFilter: PatientAppointmentFilter = PatientAppointmentFilter.All,
    val visitModeFilter: DoctorVisitModeFilter = DoctorVisitModeFilter.All,
    val timetableRange: DoctorTimetableRange = DoctorTimetableRange.Next7Days,
    val timetableFrom: String = LocalDate.now(DOCTOR_VIETNAM_ZONE).format(ISO_DATE_FORMATTER),
    val timetableTo: String = LocalDate.now(DOCTOR_VIETNAM_ZONE).plusDays(6).format(ISO_DATE_FORMATTER),
    val timetableLoading: Boolean = false,
    val timetableLoaded: Boolean = false,
    val shifts: List<DoctorWorkShiftDto> = emptyList(),
    val timetableError: String? = null,
    val expandedShiftIds: Set<Long> = emptySet(),
    val shiftSlots: Map<Long, SlotGroupState> = emptyMap(),
    val selectedAppointmentId: Long? = null,
    val detailLoading: Boolean = false,
    val detail: AppointmentScheduleDetailDto? = null,
    val detailError: String? = null,
    val selectedSlotId: Long? = null,
    val slotPatientLoading: Boolean = false,
    val slotPatient: DoctorSlotPatientDto? = null,
    val slotPatientError: String? = null,
) {
    val filteredAppointments: List<AppointmentScheduleListItemDto>
        get() = appointments.filter { item ->
            val matchStatus = statusFilter.code == null ||
                item.appointmentStatus.equals(statusFilter.code, ignoreCase = true)
            val matchMode = visitModeFilter.code == null ||
                item.visitMode.equals(visitModeFilter.code, ignoreCase = true)
            matchStatus && matchMode
        }
}

class DoctorAppointmentsViewModel(
    private val repository: AppointmentScheduleRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorAppointmentsUiState())
    val uiState: StateFlow<DoctorAppointmentsUiState> = _uiState.asStateFlow()

    private var appointmentsJob: Job? = null
    private var timetableJob: Job? = null
    private var detailJob: Job? = null
    private var slotPatientJob: Job? = null

    init {
        loadAppointments()
    }

    fun selectSection(section: DoctorScheduleSection) {
        _uiState.update { it.copy(section = section) }
        if (section == DoctorScheduleSection.Timetable && !_uiState.value.timetableLoaded && !_uiState.value.timetableLoading) {
            loadTimetable()
        }
    }

    fun refreshCurrentSection() {
        when (_uiState.value.section) {
            DoctorScheduleSection.Appointments -> loadAppointments()
            DoctorScheduleSection.Timetable -> loadTimetable()
        }
    }

    fun loadAppointments() {
        appointmentsJob?.cancel()
        appointmentsJob = viewModelScope.launch {
            _uiState.update { it.copy(appointmentsLoading = true, appointmentsError = null) }
            when (val result = repository.getDoctorAppointments()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            appointmentsLoading = false,
                            appointments = result.data,
                            appointmentsError = null,
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
                            appointmentsLoading = false,
                            appointmentsError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun selectStatusFilter(filter: PatientAppointmentFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
    }

    fun selectVisitModeFilter(filter: DoctorVisitModeFilter) {
        _uiState.update { it.copy(visitModeFilter = filter) }
    }

    fun selectTimetableRange(range: DoctorTimetableRange) {
        val today = LocalDate.now(DOCTOR_VIETNAM_ZONE)
        val from = today.format(ISO_DATE_FORMATTER)
        val to = today.plusDays(range.daysAhead).format(ISO_DATE_FORMATTER)
        _uiState.update {
            it.copy(
                timetableRange = range,
                timetableFrom = from,
                timetableTo = to,
                expandedShiftIds = emptySet(),
                shiftSlots = emptyMap(),
            )
        }
        loadTimetable()
    }

    fun loadTimetable() {
        timetableJob?.cancel()
        val from = _uiState.value.timetableFrom
        val to = _uiState.value.timetableTo
        timetableJob = viewModelScope.launch {
            _uiState.update { it.copy(timetableLoading = true, timetableError = null) }
            when (val result = repository.getDoctorTimetable(from = from, to = to)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            timetableLoading = false,
                            timetableLoaded = true,
                            shifts = result.data,
                            timetableError = null,
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
                            timetableLoading = false,
                            timetableLoaded = true,
                            timetableError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun toggleShiftSlots(assignmentId: Long) {
        val currentExpanded = _uiState.value.expandedShiftIds
        if (currentExpanded.contains(assignmentId)) {
            _uiState.update { it.copy(expandedShiftIds = currentExpanded - assignmentId) }
            return
        }
        _uiState.update { it.copy(expandedShiftIds = currentExpanded + assignmentId) }
        val existing = _uiState.value.shiftSlots[assignmentId]
        if (existing == null || (!existing.loading && !existing.loaded)) {
            loadShiftSlots(assignmentId)
        }
    }

    fun loadShiftSlots(assignmentId: Long) {
        viewModelScope.launch {
            _uiState.update { state ->
                val updated = state.shiftSlots.toMutableMap()
                updated[assignmentId] = (updated[assignmentId] ?: SlotGroupState()).copy(
                    loading = true,
                    error = null,
                )
                state.copy(shiftSlots = updated)
            }
            when (val result = repository.getDoctorTimetableSlots(assignmentId)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        val updated = state.shiftSlots.toMutableMap()
                        updated[assignmentId] = SlotGroupState(
                            loading = false,
                            slots = result.data,
                            error = null,
                            loaded = true,
                        )
                        state.copy(shiftSlots = updated)
                    }
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    _uiState.update { state ->
                        val updated = state.shiftSlots.toMutableMap()
                        updated[assignmentId] = SlotGroupState(
                            loading = false,
                            slots = emptyList(),
                            error = result.message,
                            loaded = false,
                        )
                        state.copy(shiftSlots = updated)
                    }
                }
            }
        }
    }

    fun openAppointmentDetail(appointmentId: Long) {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedAppointmentId = appointmentId,
                detailLoading = true,
                detail = null,
                detailError = null,
            )
        }
        detailJob = viewModelScope.launch {
            when (val result = repository.getDoctorAppointmentDetail(appointmentId)) {
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

    fun retryAppointmentDetail() {
        val id = _uiState.value.selectedAppointmentId ?: return
        openAppointmentDetail(id)
    }

    fun closeAppointmentDetail() {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedAppointmentId = null,
                detailLoading = false,
                detail = null,
                detailError = null,
            )
        }
    }

    fun openSlotPatient(slotId: Long) {
        slotPatientJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSlotId = slotId,
                slotPatientLoading = true,
                slotPatient = null,
                slotPatientError = null,
            )
        }
        slotPatientJob = viewModelScope.launch {
            when (val result = repository.getDoctorSlotPatient(slotId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            slotPatientLoading = false,
                            slotPatient = result.data,
                            slotPatientError = null,
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
                            slotPatientLoading = false,
                            slotPatientError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun retrySlotPatient() {
        val slotId = _uiState.value.selectedSlotId ?: return
        openSlotPatient(slotId)
    }

    fun closeSlotPatient() {
        slotPatientJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSlotId = null,
                slotPatientLoading = false,
                slotPatient = null,
                slotPatientError = null,
            )
        }
    }

    class Factory(
        private val repository: AppointmentScheduleRepository,
        private val onSessionExpired: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DoctorAppointmentsViewModel(repository, onSessionExpired) as T
        }
    }
}
