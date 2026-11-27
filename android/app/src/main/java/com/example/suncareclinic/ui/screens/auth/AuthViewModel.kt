package com.example.suncareclinic.ui.screens.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.suncareclinic.data.dto.CurrentAccountDto
import com.example.suncareclinic.data.dto.PatientRegistrationRequestDto
import com.example.suncareclinic.data.repository.ApiResult
import com.example.suncareclinic.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

enum class AuthMode {
    Login,
    Register,
}

enum class HealthStatus {
    Checking,
    Online,
    Offline,
}

sealed interface SessionState {
    data object Checking : SessionState
    data object Guest : SessionState
    data class SignedIn(val account: CurrentAccountDto) : SessionState
}

@Immutable
data class AuthFormState(
    val loginName: String = "",
    val password: String = "",
    val fullName: String = "",
    val birthDate: String = "",
    val sex: String = "UNKNOWN",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val identityNumber: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
)

@Immutable
data class AuthUiState(
    val session: SessionState = SessionState.Checking,
    val mode: AuthMode = AuthMode.Login,
    val form: AuthFormState = AuthFormState(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val healthStatus: HealthStatus = HealthStatus.Checking,
    val logoutBusy: Boolean = false,
    val logoutError: String? = null,
)

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkHealth()
        restoreSession()
    }

    fun checkHealth() {
        viewModelScope.launch {
            _uiState.update { it.copy(healthStatus = HealthStatus.Checking) }
            val status = when (repository.checkHealth()) {
                is ApiResult.Success -> HealthStatus.Online
                is ApiResult.Error -> HealthStatus.Offline
            }
            _uiState.update { it.copy(healthStatus = status) }
        }
    }

    fun restoreSession() {
        if (!repository.hasSavedToken()) {
            _uiState.update { it.copy(session = SessionState.Guest) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(session = SessionState.Checking, errorMessage = null) }
            when (val result = repository.getCurrentAccount()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(session = SessionState.SignedIn(result.data), errorMessage = null)
                }

                is ApiResult.Error -> {
                    val expiredMessage = if (result.status == 401) {
                        "Phiên đăng nhập đã hết hạn hoặc máy chủ vừa khởi động lại. Hãy đăng nhập lại."
                    } else {
                        result.message
                    }
                    _uiState.update {
                        it.copy(
                            session = SessionState.Guest,
                            mode = AuthMode.Login,
                            errorMessage = expiredMessage,
                        )
                    }
                }
            }
        }
    }

    fun switchMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                fieldErrors = emptyMap(),
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun updateField(field: String, value: String) {
        _uiState.update { state ->
            val updatedForm = when (field) {
                "loginName" -> state.form.copy(loginName = value)
                "password" -> state.form.copy(password = value)
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
                form = updatedForm,
                fieldErrors = state.fieldErrors - field,
                errorMessage = null,
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.busy) return

        val validationErrors = validate(state.form, state.mode == AuthMode.Register)
        if (validationErrors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    fieldErrors = validationErrors,
                    errorMessage = "Vui lòng kiểm tra các trường được đánh dấu.",
                    successMessage = null,
                )
            }
            return
        }

        if (state.mode == AuthMode.Login) {
            performLogin(state.form.loginName, state.form.password)
        } else {
            performRegister(state.form)
        }
    }

    private fun performLogin(loginName: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorMessage = null, successMessage = null, fieldErrors = emptyMap()) }
            when (val result = repository.login(loginName, password)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        busy = false,
                        session = SessionState.SignedIn(result.data),
                        form = AuthFormState(),
                        errorMessage = null,
                        successMessage = null,
                    )
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        busy = false,
                        errorMessage = result.message,
                        fieldErrors = localizeFieldErrors(result.fieldErrors),
                    )
                }
            }
        }
    }

    private fun performRegister(form: AuthFormState) {
        val request = PatientRegistrationRequestDto(
            loginName = form.loginName.trim(),
            password = form.password,
            fullName = form.fullName.trim(),
            birthDate = form.birthDate.trim().ifBlank { null },
            sex = form.sex.ifBlank { null },
            phone = form.phone.trim().ifBlank { null },
            email = form.email.trim().ifBlank { null },
            address = form.address.trim().ifBlank { null },
            identityNumber = form.identityNumber.trim().ifBlank { null },
            emergencyName = form.emergencyName.trim().ifBlank { null },
            emergencyPhone = form.emergencyPhone.trim().ifBlank { null },
        )

        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorMessage = null, successMessage = null, fieldErrors = emptyMap()) }
            when (val result = repository.registerPatient(request)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        busy = false,
                        mode = AuthMode.Login,
                        form = AuthFormState(
                            loginName = request.loginName,
                            password = form.password,
                        ),
                        fieldErrors = emptyMap(),
                        errorMessage = null,
                        successMessage = "Tạo tài khoản thành công (${result.data.patientCode}). Tên đăng nhập và mật khẩu đã được điền sẵn; hãy chọn Đăng nhập.",
                    )
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        busy = false,
                        errorMessage = result.message,
                        fieldErrors = localizeFieldErrors(result.fieldErrors),
                    )
                }
            }
        }
    }

    fun refreshAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorMessage = null, successMessage = null) }
            when (val result = repository.getCurrentAccount()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        busy = false,
                        session = SessionState.SignedIn(result.data),
                        successMessage = "Đã cập nhật quyền hiện hành từ máy chủ.",
                    )
                }

                is ApiResult.Error -> {
                    if (result.status == 401) {
                        _uiState.update {
                            it.copy(
                                busy = false,
                                session = SessionState.Guest,
                                mode = AuthMode.Login,
                                errorMessage = "Phiên đăng nhập không còn hiệu lực hoặc tài khoản đã bị khóa.",
                            )
                        }
                    } else {
                        _uiState.update { it.copy(busy = false, errorMessage = result.message) }
                    }
                }
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(logoutBusy = true, logoutError = null) }
            when (val result = repository.logout()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            logoutBusy = false,
                            logoutError = null,
                            session = SessionState.Guest,
                            mode = AuthMode.Login,
                            form = AuthFormState(),
                            errorMessage = null,
                            successMessage = "Bạn đã đăng xuất an toàn.",
                        )
                    }
                    onSuccess()
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(logoutBusy = false, logoutError = result.message)
                }
            }
        }
    }

    fun clearLogoutError() {
        _uiState.update { it.copy(logoutError = null) }
    }

    fun handleSessionExpired(message: String = "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại để tiếp tục.") {
        repository.clearSession()
        _uiState.update {
            it.copy(
                session = SessionState.Guest,
                mode = AuthMode.Login,
                busy = false,
                logoutBusy = false,
                logoutError = null,
                successMessage = null,
                errorMessage = message,
            )
        }
    }

    private fun validate(form: AuthFormState, registering: Boolean): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        val login = form.loginName.trim()

        if (registering) {
            if (!LOGIN_REGEX.matches(login)) {
                errors["loginName"] = "Dùng 3–100 ký tự không dấu, không khoảng trắng (chữ, số, . _ @ -)."
            }
            if (form.password.length !in 8..72) {
                errors["password"] = "Mật khẩu cần từ 8 đến 72 ký tự."
            }
            val fullName = form.fullName.trim()
            if (fullName.isEmpty() || fullName.length > 150) {
                errors["fullName"] = "Họ và tên không được để trống và tối đa 150 ký tự."
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
                    errors["birthDate"] = "Nhập ngày sinh theo định dạng YYYY-MM-DD (ví dụ 1998-05-20)."
                } else if (parsed.isAfter(today)) {
                    errors["birthDate"] = "Ngày sinh không được ở tương lai."
                }
            }
            if (form.phone.trim().length > 20) errors["phone"] = "Tối đa 20 ký tự."
            val email = form.email.trim()
            if (email.length > 254 || (email.isNotEmpty() && !EMAIL_REGEX.matches(email))) {
                errors["email"] = "Email không đúng định dạng hoặc quá dài."
            }
            if (form.address.trim().length > 500) errors["address"] = "Tối đa 500 ký tự."
            if (form.identityNumber.trim().length > 30) errors["identityNumber"] = "Tối đa 30 ký tự."
            if (form.emergencyName.trim().length > 150) errors["emergencyName"] = "Tối đa 150 ký tự."
            if (form.emergencyPhone.trim().length > 20) errors["emergencyPhone"] = "Tối đa 20 ký tự."
        } else {
            if (login.isEmpty()) errors["loginName"] = "Vui lòng nhập tên đăng nhập."
            if (form.password.isEmpty()) errors["password"] = "Vui lòng nhập mật khẩu."
        }

        return errors
    }

    private fun localizeFieldErrors(serverErrors: Map<String, String>): Map<String, String> {
        if (serverErrors.isEmpty()) return emptyMap()
        return serverErrors.mapValues { (key, message) ->
            val label = FIELD_LABELS[key] ?: key
            if (message.isBlank()) "$label không hợp lệ." else message
        }
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(repository) as T
        }
    }

    companion object {
        private val LOGIN_REGEX = Regex("^[A-Za-z0-9._@-]{3,100}$")
        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        private val FIELD_LABELS = mapOf(
            "loginName" to "Tên đăng nhập",
            "password" to "Mật khẩu",
            "fullName" to "Họ và tên",
            "birthDate" to "Ngày sinh",
            "sex" to "Giới tính",
            "phone" to "Số điện thoại",
            "email" to "Email",
            "address" to "Địa chỉ",
            "identityNumber" to "Số giấy tờ tùy thân",
            "emergencyName" to "Người liên hệ khẩn cấp",
            "emergencyPhone" to "SĐT liên hệ khẩn cấp",
        )
    }
}
