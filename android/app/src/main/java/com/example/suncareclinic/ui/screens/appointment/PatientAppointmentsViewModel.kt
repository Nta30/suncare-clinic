package com.example.suncareclinic.ui.screens.appointment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.suncareclinic.data.dto.AppointmentScheduleDetailDto
import com.example.suncareclinic.data.dto.AppointmentScheduleListItemDto
import com.example.suncareclinic.data.repository.ApiResult
import com.example.suncareclinic.data.repository.AppointmentScheduleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PatientAppointmentFilter(val code: String?, val label: String) {
    All(null, "Tất cả"),
    Booked("BOOKED", "Đã đặt"),
    Completed("COMPLETED", "Hoàn tất"),
    Cancelled("CANCELLED", "Đã hủy"),
    NoShow("NO_SHOW", "Vắng mặt"),
}

data class PatientAppointmentsUiState(
    val loading: Boolean = true,
    val items: List<AppointmentScheduleListItemDto> = emptyList(),
    val error: String? = null,
    val selectedFilter: PatientAppointmentFilter = PatientAppointmentFilter.All,
    val selectedAppointmentId: Long? = null,
    val detailLoading: Boolean = false,
    val detail: AppointmentScheduleDetailDto? = null,
    val detailError: String? = null,
) {
    val filteredItems: List<AppointmentScheduleListItemDto>
        get() = when (val code = selectedFilter.code) {
            null -> items
            else -> items.filter { it.appointmentStatus.equals(code, ignoreCase = true) }
        }
}

class PatientAppointmentsViewModel(
    private val repository: AppointmentScheduleRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientAppointmentsUiState())
    val uiState: StateFlow<PatientAppointmentsUiState> = _uiState.asStateFlow()

    private var listJob: Job? = null
    private var detailJob: Job? = null

    init {
        loadAppointments()
    }

    fun loadAppointments() {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val result = repository.getPatientAppointments()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            items = result.data,
                            error = null,
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
                            loading = false,
                            error = result.message,
                        )
                    }
                }
            }
        }
    }

    fun selectFilter(filter: PatientAppointmentFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun openDetail(appointmentId: Long) {
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
            when (val result = repository.getPatientAppointmentDetail(appointmentId)) {
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

    fun retryDetail() {
        val id = _uiState.value.selectedAppointmentId ?: return
        openDetail(id)
    }

    fun closeDetail() {
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

    class Factory(
        private val repository: AppointmentScheduleRepository,
        private val onSessionExpired: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PatientAppointmentsViewModel(repository, onSessionExpired) as T
        }
    }
}
