package com.example.suncareclinic.data.repository

import com.example.suncareclinic.data.api.ApiClient
import com.example.suncareclinic.data.api.SuncareApi
import com.example.suncareclinic.data.dto.ApiErrorDto
import com.example.suncareclinic.data.dto.PersonalProfileRequestDto
import com.example.suncareclinic.data.dto.PersonalProfileResponseDto
import com.example.suncareclinic.data.local.TokenStorage
import retrofit2.Response
import java.io.IOException

class ProfileRepository(
    private val api: SuncareApi,
    private val tokenStorage: TokenStorage,
) {
    suspend fun getProfile(): ApiResult<PersonalProfileResponseDto> = safeCall {
        val response = api.getPersonalProfile()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về dữ liệu hồ sơ không hợp lệ.")
        } else {
            if (response.code() == 401) tokenStorage.clearToken()
            parseError(response)
        }
    }

    suspend fun updateProfile(request: PersonalProfileRequestDto): ApiResult<PersonalProfileResponseDto> = safeCall {
        val response = api.updatePersonalProfile(request)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về dữ liệu hồ sơ không hợp lệ.")
        } else {
            if (response.code() == 401) tokenStorage.clearToken()
            parseError(response)
        }
    }

    private suspend fun <T> safeCall(block: suspend () -> ApiResult<T>): ApiResult<T> {
        return try {
            block()
        } catch (_: IOException) {
            ApiResult.Error(
                code = "NETWORK_ERROR",
                message = "Không kết nối được máy chủ. Hãy kiểm tra mạng và thử lại.",
            )
        } catch (_: Exception) {
            ApiResult.Error(
                code = "UNEXPECTED_ERROR",
                message = "Có lỗi xảy ra khi tải hoặc lưu hồ sơ.",
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
            status == 409 -> "Hồ sơ đã được thay đổi ở nơi khác. Hãy tải lại trước khi lưu."
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
