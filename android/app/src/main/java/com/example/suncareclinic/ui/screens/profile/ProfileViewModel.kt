package com.example.suncareclinic.ui.screens.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.suncareclinic.data.dto.PersonalProfileRequestDto
import com.example.suncareclinic.data.dto.PersonalProfileResponseDto
import com.example.suncareclinic.data.repository.ApiResult
import com.example.suncareclinic.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

@Immutable
data class ProfileFormState(
    val fullName: String = "",
    val birthDate: String = "",
    val sex: String = "UNKNOWN",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val identityNumber: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
    val version: String = "",
)

@Immutable
data class ProfileUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val editing: Boolean = false,
    val profile: PersonalProfileResponseDto? = null,
    val form: ProfileFormState = ProfileFormState(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val loadError: String? = null,
    val actionError: String? = null,
    val conflictError: Boolean = false,
    val successMessage: String? = null,
)

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    loadError = null,
                    actionError = null,
                    conflictError = false,
                    fieldErrors = emptyMap(),
                )
            }
            when (val result = repository.getProfile()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        loading = false,
                        editing = false,
                        profile = result.data,
                        form = result.data.toForm(),
                        loadError = null,
                        actionError = null,
                        conflictError = false,
                    )
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun startEditing() {
        val current = _uiState.value.profile ?: return
        _uiState.update {
            it.copy(
                editing = true,
                form = current.toForm(),
                fieldErrors = emptyMap(),
                actionError = null,
                conflictError = false,
                successMessage = null,
            )
        }
    }

    fun cancelEditing() {
        val current = _uiState.value.profile ?: return
        _uiState.update {
            it.copy(
                editing = false,
                form = current.toForm(),
                fieldErrors = emptyMap(),
                actionError = null,
                conflictError = false,
            )
        }
    }

    fun updateField(field: String, value: String) {
        _uiState.update { state ->
            val updated = when (field) {
                "fullName" -> state.form.copy(fullName = value)
                "birthDate" -> state.form.copy(birthDate = value)
                "sex" -> state.form.copy(sex = value)
                "phone" -> state.form.copy(phone = value)
                "email" -> state.form.copy(email = value)
                "address" -> state.form.copy(address = value)
                "identityNumber" -> state.form.copy(identityNumber = value)
                "emergencyName" -> state.form.copy(emergencyName = value)
                "emergencyPhone" -> state.form.copy(emergencyPhone = value)
                else -> state.form
            }
            state.copy(
                form = updated,
                fieldErrors = state.fieldErrors - field,
                actionError = null,
                successMessage = null,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        val current = state.profile ?: return
        if (state.saving) return

        val isPatient = current.accountType == "PATIENT"
        val errors = validate(state.form, isPatient)
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    fieldErrors = errors,
                    actionError = "Vui lòng kiểm tra các trường được đánh dấu.",
                    successMessage = null,
                )
            }
            return
        }

        // Backend từ chối nếu tài khoản STAFF gửi các trường dành riêng cho PATIENT.
        val request = PersonalProfileRequestDto(
            fullName = state.form.fullName.trim(),
            birthDate = state.form.birthDate.trim().ifBlank { null },
            sex = state.form.sex,
            phone = state.form.phone.trim().ifBlank { null },
            email = state.form.email.trim().ifBlank { null },
            address = state.form.address.trim().ifBlank { null },
            identityNumber = if (isPatient) state.form.identityNumber.trim().ifBlank { null } else null,
            emergencyName = if (isPatient) state.form.emergencyName.trim().ifBlank { null } else null,
            emergencyPhone = if (isPatient) state.form.emergencyPhone.trim().ifBlank { null } else null,
            version = state.form.version,
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    saving = true,
                    actionError = null,
                    conflictError = false,
                    fieldErrors = emptyMap(),
                    successMessage = null,
                )
            }
            when (val result = repository.updateProfile(request)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        saving = false,
                        editing = false,
                        profile = result.data,
                        form = result.data.toForm(),
                        successMessage = "Đã lưu hồ sơ cá nhân.",
                    )
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        onSessionExpired()
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            saving = false,
                            actionError = result.message,
                            conflictError = result.status == 409,
                            fieldErrors = result.fieldErrors,
                        )
                    }
                }
            }
        }
    }

    private fun PersonalProfileResponseDto.toForm(): ProfileFormState = ProfileFormState(
        fullName = fullName,
        birthDate = birthDate.orEmpty(),
        sex = sex,
        phone = phone.orEmpty(),
        email = email.orEmpty(),
        address = address.orEmpty(),
        identityNumber = identityNumber.orEmpty(),
        emergencyName = emergencyName.orEmpty(),
        emergencyPhone = emergencyPhone.orEmpty(),
        version = version,
    )

    private fun validate(form: ProfileFormState, isPatient: Boolean): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        val name = form.fullName.trim()
        if (name.isEmpty() || name.length > 150) {
            errors["fullName"] = "Nhập họ tên, tối đa 150 ký tự."
        }
        if (form.sex !in ALLOWED_SEX) {
            errors["sex"] = "Chọn giới tính hợp lệ."
        }
        val birthDate = form.birthDate.trim()
        if (birthDate.isNotEmpty()) {
            val parsed = try {
                LocalDate.parse(birthDate)
            } catch (_: DateTimeParseException) {
                null
            }
            val today = LocalDate.now(ZoneId.of("Asia/Saigon"))
            if (parsed == null) {
                errors["birthDate"] = "Nhập ngày sinh dạng YYYY-MM-DD (ví dụ 1998-05-20)."
            } else if (parsed.isAfter(today)) {
                errors["birthDate"] = "Ngày sinh không được ở tương lai."
            }
        }
        if (form.phone.trim().length > 20) errors["phone"] = "Tối đa 20 ký tự."
        val email = form.email.trim()
        if (email.length > 254 || (email.isNotEmpty() && !EMAIL_REGEX.matches(email))) {
            errors["email"] = "Email không đúng định dạng."
        }
        if (form.address.trim().length > 500) errors["address"] = "Tối đa 500 ký tự."
        if (isPatient) {
            if (form.identityNumber.trim().length > 30) errors["identityNumber"] = "Tối đa 30 ký tự."
            if (form.emergencyName.trim().length > 150) errors["emergencyName"] = "Tối đa 150 ký tự."
            if (form.emergencyPhone.trim().length > 20) errors["emergencyPhone"] = "Tối đa 20 ký tự."
        }
        return errors
    }

    class Factory(
        private val repository: ProfileRepository,
        private val onSessionExpired: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(repository, onSessionExpired) as T
        }
    }

    companion object {
        private val ALLOWED_SEX = setOf("UNKNOWN", "MALE", "FEMALE", "OTHER")
        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
