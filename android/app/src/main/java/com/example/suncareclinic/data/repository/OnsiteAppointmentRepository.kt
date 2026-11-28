package com.example.suncareclinic.data.repository

import com.example.suncareclinic.data.api.ApiClient
import com.example.suncareclinic.data.api.SuncareApi
import com.example.suncareclinic.data.dto.ApiErrorDto
import com.example.suncareclinic.data.dto.OnsiteAppointmentBookingRequestDto
import com.example.suncareclinic.data.dto.OnsiteAppointmentBookingResponseDto
import com.example.suncareclinic.data.dto.OnsiteAppointmentOptionsDto
import com.example.suncareclinic.data.dto.OnsiteAvailabilityDto
import com.example.suncareclinic.data.local.TokenStorage
import retrofit2.Response
import java.io.IOException

class OnsiteAppointmentRepository(
    private val api: SuncareApi,
    private val tokenStorage: TokenStorage,
) {
    suspend fun getOptions(): ApiResult<OnsiteAppointmentOptionsDto> = safeCall {
        val response = api.getOnsiteAppointmentOptions()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về danh mục lọc không hợp lệ.")
        } else {
            if (response.code() == 401) tokenStorage.clearToken()
            parseError(response)
        }
    }

    suspend fun searchAvailability(
        specialtyId: Long?,
        dateFrom: String?,
        dateTo: String?,
        shiftId: Long?,
        doctorName: String?,
    ): ApiResult<List<OnsiteAvailabilityDto>> = safeCall {
        val response = api.searchOnsiteAppointmentAvailability(
            specialtyId = specialtyId,
            dateFrom = dateFrom?.ifBlank { null },
            dateTo = dateTo?.ifBlank { null },
            shiftId = shiftId,
            doctorName = doctorName?.trim()?.ifBlank { null },
        )
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về danh sách lịch không hợp lệ.")
        } else {
            if (response.code() == 401) tokenStorage.clearToken()
            parseError(response)
        }
    }

    suspend fun bookOnsiteAppointment(
        request: OnsiteAppointmentBookingRequestDto,
        idempotencyKey: String,
    ): ApiResult<OnsiteAppointmentBookingResponseDto> = safeCall {
        val response = api.bookOnsiteAppointment(
            idempotencyKey = idempotencyKey,
            request = request,
        )
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về thông tin xác nhận đặt lịch không hợp lệ.")
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
                message = "Có lỗi xảy ra khi tra cứu lịch khám.",
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
