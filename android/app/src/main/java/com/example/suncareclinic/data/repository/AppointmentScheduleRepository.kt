package com.example.suncareclinic.data.repository

import com.example.suncareclinic.data.api.ApiClient
import com.example.suncareclinic.data.api.SuncareApi
import com.example.suncareclinic.data.dto.ApiErrorDto
import com.example.suncareclinic.data.dto.AppointmentScheduleDetailDto
import com.example.suncareclinic.data.dto.AppointmentScheduleListItemDto
import com.example.suncareclinic.data.dto.DoctorOnlineSlotDto
import com.example.suncareclinic.data.dto.DoctorSlotPatientDto
import com.example.suncareclinic.data.dto.DoctorWorkShiftDto
import com.example.suncareclinic.data.local.TokenStorage
import retrofit2.Response
import java.io.IOException

class AppointmentScheduleRepository(
    private val api: SuncareApi,
    private val tokenStorage: TokenStorage,
) {
    suspend fun getPatientAppointments(): ApiResult<List<AppointmentScheduleListItemDto>> = safeCall {
        val response = api.getPatientAppointments()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về danh sách lịch khám không hợp lệ.")
        } else {
            if (response.code() == 401) tokenStorage.clearToken()
            parseError(response)
        }
    }

    suspend fun getPatientAppointmentDetail(
        appointmentId: Long,
    ): ApiResult<AppointmentScheduleDetailDto> = safeCall {
        val response = api.getPatientAppointmentDetail(appointmentId)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về chi tiết lịch khám không hợp lệ.")
        } else {
            if (response.code() == 401) tokenStorage.clearToken()
            parseError(response)
        }
    }

    suspend fun getDoctorAppointments(): ApiResult<List<AppointmentScheduleListItemDto>> = safeCall {
        val response = api.getDoctorAppointments()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về danh sách lịch hẹn bác sĩ không hợp lệ.")
        } else {
            if (response.code() == 401) tokenStorage.clearToken()
            parseError(response)
        }
    }

    suspend fun getDoctorAppointmentDetail(
        appointmentId: Long,
    ): ApiResult<AppointmentScheduleDetailDto> = safeCall {
        val response = api.getDoctorAppointmentDetail(appointmentId)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về chi tiết lịch hẹn không hợp lệ.")
        } else {
            if (response.code() == 401) tokenStorage.clearToken()
            parseError(response)
        }
    }

    suspend fun getDoctorTimetable(
        from: String,
        to: String,
    ): ApiResult<List<DoctorWorkShiftDto>> = safeCall {
        val response = api.getDoctorTimetable(from = from, to = to)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về lịch trực không hợp lệ.")
        } else {
            if (response.code() == 401) tokenStorage.clearToken()
            parseError(response)
        }
    }

    suspend fun getDoctorTimetableSlots(
        assignmentId: Long,
    ): ApiResult<List<DoctorOnlineSlotDto>> = safeCall {
        val response = api.getDoctorTimetableSlots(assignmentId)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về danh sách slot trực tuyến không hợp lệ.")
        } else {
            if (response.code() == 401) tokenStorage.clearToken()
            parseError(response)
        }
    }

    suspend fun getDoctorSlotPatient(
        slotId: Long,
    ): ApiResult<DoctorSlotPatientDto> = safeCall {
        val response = api.getDoctorSlotPatient(slotId)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = "Máy chủ trả về thông tin bệnh nhân không hợp lệ.")
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
                message = "Có lỗi xảy ra khi tải lịch khám.",
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
