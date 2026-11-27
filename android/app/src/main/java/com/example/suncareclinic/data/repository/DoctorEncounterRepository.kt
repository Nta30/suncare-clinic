package com.example.suncareclinic.data.repository

import com.example.suncareclinic.data.api.ApiClient
import com.example.suncareclinic.data.api.SuncareApi
import com.example.suncareclinic.data.dto.ApiErrorDto
import com.example.suncareclinic.data.dto.DiagnosisConceptSearchResponseDto
import com.example.suncareclinic.data.dto.DiagnosisRequestDto
import com.example.suncareclinic.data.dto.EncounterClinicalResponseDto
import com.example.suncareclinic.data.dto.EncounterCompleteRequestDto
import com.example.suncareclinic.data.dto.EncounterCompleteResponseDto
import com.example.suncareclinic.data.dto.EncounterHistoryResponseDto
import com.example.suncareclinic.data.dto.EncounterNotesRequestDto
import com.example.suncareclinic.data.dto.EncounterStartRequestDto
import com.example.suncareclinic.data.dto.EncounterStartResponseDto
import com.example.suncareclinic.data.dto.EncounterWorklistDto
import com.example.suncareclinic.data.dto.InvoiceResponseDto
import com.example.suncareclinic.data.dto.IssueInvoiceRequestDto
import com.example.suncareclinic.data.dto.PrescriptionResponseDto
import com.example.suncareclinic.data.dto.PatientAllergyRequestDto
import com.example.suncareclinic.data.dto.PatientHistoryRequestDto
import com.example.suncareclinic.data.dto.PreviousEncounterDetailDto
import com.example.suncareclinic.data.dto.PreviousEncounterPageDto
import com.example.suncareclinic.data.dto.VitalSignRequestDto
import com.example.suncareclinic.data.local.TokenStorage
import retrofit2.Response
import java.io.IOException
import java.time.OffsetDateTime

internal fun EncounterCompleteResponseDto.confirmsCompletionOf(expectedEncounterId: Long): Boolean {
    return encounterId == expectedEncounterId && status == "COMPLETED" &&
        completedAt != null && runCatching { OffsetDateTime.parse(completedAt) }.isSuccess
}

class DoctorEncounterRepository(
    private val api: SuncareApi,
    private val tokenStorage: TokenStorage,
) {
    suspend fun getWorklist(
        date: String,
        status: String = "ALL",
        search: String = "",
        page: Int = 1,
        pageSize: Int = 10,
    ): ApiResult<EncounterWorklistDto> = safeCall {
        val response = api.getDoctorEncounterWorklist(
            date = date,
            status = status,
            search = search.trim(),
            page = page,
            pageSize = pageSize,
        )
        handleResponse(response, "Máy chủ trả về danh sách lượt khám không hợp lệ.")
    }

    suspend fun getDetail(encounterId: Long): ApiResult<EncounterHistoryResponseDto> = safeCall {
        val response = api.getDoctorEncounterDetail(encounterId)
        handleResponse(response, "Máy chủ trả về chi tiết lượt khám không hợp lệ.")
    }

    suspend fun startEncounter(encounterId: Long): ApiResult<EncounterStartResponseDto> = safeCall {
        val response = api.startDoctorEncounter(
            encounterId = encounterId,
            request = EncounterStartRequestDto(presenceConfirmed = true),
        )
        handleResponse(response, "Máy chủ trả về kết quả bắt đầu khám không hợp lệ.")
    }

    suspend fun completeEncounter(encounterId: Long): ApiResult<EncounterCompleteResponseDto> = safeCall {
        val response = api.completeDoctorEncounter(encounterId, EncounterCompleteRequestDto())
        val message = "Máy chủ trả về kết quả hoàn tất lượt khám không hợp lệ."
        when (val result = handleResponse(response, message)) {
            is ApiResult.Success -> if (result.data.confirmsCompletionOf(encounterId)) result
                else ApiResult.Error(message = message)
            is ApiResult.Error -> result
        }
    }

    suspend fun saveHistory(
        encounterId: Long,
        itemId: Long?,
        request: PatientHistoryRequestDto,
    ): ApiResult<EncounterHistoryResponseDto> = safeCall {
        val response = if (itemId == null) {
            api.createEncounterHistory(encounterId, request)
        } else {
            api.updateEncounterHistory(encounterId, itemId, request)
        }
        handleResponse(response, "Máy chủ trả về tiền sử bệnh không hợp lệ.")
    }

    suspend fun saveAllergy(
        encounterId: Long,
        itemId: Long?,
        request: PatientAllergyRequestDto,
    ): ApiResult<EncounterHistoryResponseDto> = safeCall {
        val response = if (itemId == null) {
            api.createEncounterAllergy(encounterId, request)
        } else {
            api.updateEncounterAllergy(encounterId, itemId, request)
        }
        handleResponse(response, "Máy chủ trả về thông tin dị ứng không hợp lệ.")
    }

    suspend fun getClinical(encounterId: Long): ApiResult<EncounterClinicalResponseDto> = safeCall {
        val response = api.getEncounterClinical(encounterId)
        handleResponse(response, "Máy chủ trả về dữ liệu lâm sàng không hợp lệ.")
    }

    suspend fun recordVitalSign(
        encounterId: Long,
        idempotencyKey: String,
        request: VitalSignRequestDto,
    ): ApiResult<EncounterClinicalResponseDto> = safeCall {
        val response = api.recordVitalSign(encounterId, idempotencyKey, request)
        handleResponse(response, "Máy chủ trả về dữ liệu sinh hiệu không hợp lệ.")
    }

    suspend fun saveNotes(
        encounterId: Long,
        request: EncounterNotesRequestDto,
    ): ApiResult<EncounterClinicalResponseDto> = safeCall {
        val response = api.saveEncounterNotes(encounterId, request)
        handleResponse(response, "Máy chủ trả về ghi chú lâm sàng không hợp lệ.")
    }

    suspend fun saveDiagnosis(
        encounterId: Long,
        diagnosisId: Long?,
        idempotencyKey: String,
        request: DiagnosisRequestDto,
    ): ApiResult<EncounterClinicalResponseDto> = safeCall {
        val response = if (diagnosisId == null) {
            api.createDiagnosis(encounterId, idempotencyKey, request)
        } else {
            api.updateDiagnosis(encounterId, diagnosisId, idempotencyKey, request)
        }
        handleResponse(response, "Máy chủ trả về dữ liệu chẩn đoán không hợp lệ.")
    }

    suspend fun searchDiagnosisConcepts(
        search: String,
        page: Int = 1,
        groups: Boolean = false,
    ): ApiResult<DiagnosisConceptSearchResponseDto> = safeCall {
        val response = api.searchDiagnosisConcepts(search = search.trim(), page = page, groups = groups)
        handleResponse(response, "Máy chủ trả về danh mục ICD-10 không hợp lệ.")
    }

    suspend fun getPreviousEncounters(
        contextId: Long,
        page: Int = 1,
    ): ApiResult<PreviousEncounterPageDto> = safeCall {
        val response = api.getPreviousEncounters(contextId = contextId, page = page)
        handleResponse(response, "Máy chủ trả về danh sách lịch sử khám không hợp lệ.")
    }

    suspend fun getPreviousEncounterDetail(
        contextId: Long,
        previousId: Long,
    ): ApiResult<PreviousEncounterDetailDto> = safeCall {
        val response = api.getPreviousEncounterDetail(contextId = contextId, previousId = previousId)
        handleResponse(response, "Máy chủ trả về chi tiết lượt khám cũ không hợp lệ.")
    }

    suspend fun getPrescriptions(encounterId: Long): ApiResult<List<PrescriptionResponseDto>> = safeCall {
        val response = api.getEncounterPrescriptions(encounterId)
        handleResponse(response, "Máy chủ trả về danh sách đơn thuốc không hợp lệ.")
    }

    suspend fun getInvoices(encounterId: Long): ApiResult<List<InvoiceResponseDto>> = safeCall {
        val response = api.getEncounterInvoices(encounterId)
        handleResponse(response, "Máy chủ trả về danh sách hóa đơn không hợp lệ.")
    }

    suspend fun createExaminationInvoice(
        encounterId: Long,
        idempotencyKey: String,
    ): ApiResult<InvoiceResponseDto> = safeCall {
        val response = api.createExaminationInvoice(encounterId, idempotencyKey)
        handleResponse(response, "Máy chủ trả về hóa đơn nháp không hợp lệ.")
    }

    suspend fun issueExaminationInvoice(
        encounterId: Long,
        invoiceId: Long,
        version: String,
        idempotencyKey: String,
    ): ApiResult<InvoiceResponseDto> = safeCall {
        val response = api.issueExaminationInvoice(
            encounterId = encounterId,
            invoiceId = invoiceId,
            idempotencyKey = idempotencyKey,
            request = IssueInvoiceRequestDto(version = version),
        )
        handleResponse(response, "Máy chủ trả về hóa đơn đã phát hành không hợp lệ.")
    }

    private fun <T> handleResponse(response: Response<T>, emptyMessage: String): ApiResult<T> {
        return if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body)
            else ApiResult.Error(message = emptyMessage)
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
                message = "Có lỗi xảy ra khi xử lý lượt khám.",
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
