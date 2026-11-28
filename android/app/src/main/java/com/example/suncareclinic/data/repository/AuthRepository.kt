package com.example.suncareclinic.data.repository

import com.example.suncareclinic.data.api.ApiClient
import com.example.suncareclinic.data.api.SuncareApi
import com.example.suncareclinic.data.dto.ApiErrorDto
import com.example.suncareclinic.data.dto.CurrentAccountDto
import com.example.suncareclinic.data.dto.HealthResponseDto
import com.example.suncareclinic.data.dto.LoginRequestDto
import com.example.suncareclinic.data.dto.PatientRegistrationRequestDto
import com.example.suncareclinic.data.dto.PatientRegistrationResponseDto
import com.example.suncareclinic.data.local.TokenStorage
import retrofit2.Response
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(
        val status: Int? = null,
        val code: String? = null,
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : ApiResult<Nothing>
}

class AuthRepository(
    private val api: SuncareApi,
    private val tokenStorage: TokenStorage,
) {
    fun hasSavedToken(): Boolean = tokenStorage.getToken() != null

    fun clearSession() {
        tokenStorage.clearToken()
    }

    suspend fun checkHealth(): ApiResult<HealthResponseDto> = safeCall {
        val response = api.checkHealth()
        if (response.isSuccessful) {
            val body = response.body()
            if (body?.status == "UP" && body.service == "suncare-clinic") {
                ApiResult.Success(body)
            } else {
                ApiResult.Error(
                    status = response.code(),
                    message = "Dữ liệu Health API không đúng định dạng mong đợi.",
                )
            }
        } else {
            parseError(response)
        }
    }

    suspend fun login(loginName: String, password: String): ApiResult<CurrentAccountDto> = safeCall {
        val loginResponse = api.login(LoginRequestDto(loginName = loginName.trim(), password = password))
        if (!loginResponse.isSuccessful) {
            return@safeCall parseError(loginResponse)
        }
        val token = loginResponse.body()?.accessToken
        if (token.isNullOrBlank()) {
            return@safeCall ApiResult.Error(message = "Máy chủ không trả về mã phiên hợp lệ.")
        }
        tokenStorage.saveToken(token)
        val meResult = getCurrentAccount()
        if (meResult is ApiResult.Error) {
            tokenStorage.clearToken()
        }
        meResult
    }

    suspend fun getCurrentAccount(): ApiResult<CurrentAccountDto> = safeCall {
        val response = api.getCurrentAccount()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Error(message = "Máy chủ trả về thông tin tài khoản không hợp lệ.")
            }
        } else {
            if (response.code() == 401) {
                tokenStorage.clearToken()
            }
            parseError(response)
        }
    }

    suspend fun registerPatient(request: PatientRegistrationRequestDto): ApiResult<PatientRegistrationResponseDto> =
        safeCall {
            val response = api.registerPatient(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(message = "Máy chủ trả về dữ liệu đăng ký không hợp lệ.")
                }
            } else {
                parseError(response)
            }
        }

    suspend fun logout(): ApiResult<Unit> = safeCall {
        val response = api.logout()
        if (response.isSuccessful || response.code() == 204 || response.code() == 401) {
            tokenStorage.clearToken()
            ApiResult.Success(Unit)
        } else {
            parseError(response)
        }
    }

    private suspend fun <T> safeCall(block: suspend () -> ApiResult<T>): ApiResult<T> {
        return try {
            block()
        } catch (_: IOException) {
            ApiResult.Error(
                code = "NETWORK_ERROR",
                message = "Không kết nối được máy chủ. Hãy kiểm tra Spring Boot đang chạy trên cổng 8080 rồi thử lại.",
            )
        } catch (_: Exception) {
            ApiResult.Error(
                code = "UNEXPECTED_ERROR",
                message = "Có lỗi xảy ra khi xử lý yêu cầu. Vui lòng thử lại.",
            )
        }
    }

    private fun parseError(response: Response<*>): ApiResult.Error {
        val rawBody = response.errorBody()?.string()
        val parsed = runCatching {
            if (rawBody.isNullOrBlank()) null else ApiClient.gson.fromJson(rawBody, ApiErrorDto::class.java)
        }.getOrNull()
        val status = response.code()
        val message = when {
            status == 502 || status == 503 ->
                "Không kết nối được backend. Hãy kiểm tra Spring Boot đang chạy trên cổng 8080."
            !parsed?.message.isNullOrBlank() -> parsed!!.message!!
            else -> "Yêu cầu thất bại (HTTP $status)."
        }
        return ApiResult.Error(
            status = status,
            code = parsed?.code,
            message = message,
            fieldErrors = parsed?.fieldErrors.orEmpty(),
        )
    }
}
