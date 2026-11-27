package com.example.suncareclinic.data.api

import com.example.suncareclinic.data.dto.AppointmentScheduleDetailDto
import com.example.suncareclinic.data.dto.AppointmentScheduleListItemDto
import com.example.suncareclinic.data.dto.CurrentAccountDto
import com.example.suncareclinic.data.dto.DiagnosisConceptSearchResponseDto
import com.example.suncareclinic.data.dto.DiagnosisRequestDto
import com.example.suncareclinic.data.dto.DoctorOnlineSlotDto
import com.example.suncareclinic.data.dto.DoctorSlotPatientDto
import com.example.suncareclinic.data.dto.DoctorWorkShiftDto
import com.example.suncareclinic.data.dto.EncounterClinicalResponseDto
import com.example.suncareclinic.data.dto.EncounterCompleteRequestDto
import com.example.suncareclinic.data.dto.EncounterCompleteResponseDto
import com.example.suncareclinic.data.dto.EncounterHistoryResponseDto
import com.example.suncareclinic.data.dto.EncounterNotesRequestDto
import com.example.suncareclinic.data.dto.EncounterStartRequestDto
import com.example.suncareclinic.data.dto.EncounterStartResponseDto
import com.example.suncareclinic.data.dto.EncounterWorklistDto
import com.example.suncareclinic.data.dto.HealthResponseDto
import com.example.suncareclinic.data.dto.InvoiceResponseDto
import com.example.suncareclinic.data.dto.IssueInvoiceRequestDto
import com.example.suncareclinic.data.dto.PrescriptionResponseDto
import com.example.suncareclinic.data.dto.LoginRequestDto
import com.example.suncareclinic.data.dto.LoginResponseDto
import com.example.suncareclinic.data.dto.OnsiteAppointmentBookingRequestDto
import com.example.suncareclinic.data.dto.OnsiteAppointmentBookingResponseDto
import com.example.suncareclinic.data.dto.OnsiteAppointmentOptionsDto
import com.example.suncareclinic.data.dto.OnsiteAvailabilityDto
import com.example.suncareclinic.data.dto.PatientAllergyRequestDto
import com.example.suncareclinic.data.dto.PatientHistoryRequestDto
import com.example.suncareclinic.data.dto.PatientRegistrationRequestDto
import com.example.suncareclinic.data.dto.PatientRegistrationResponseDto
import com.example.suncareclinic.data.dto.PersonalProfileRequestDto
import com.example.suncareclinic.data.dto.PersonalProfileResponseDto
import com.example.suncareclinic.data.dto.PreviousEncounterDetailDto
import com.example.suncareclinic.data.dto.PreviousEncounterPageDto
import com.example.suncareclinic.data.dto.VitalSignRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SuncareApi {
    @GET("api/health")
    suspend fun checkHealth(): Response<HealthResponseDto>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<LoginResponseDto>

    @GET("api/auth/me")
    suspend fun getCurrentAccount(): Response<CurrentAccountDto>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("api/accounts/register")
    suspend fun registerPatient(
        @Body request: PatientRegistrationRequestDto,
    ): Response<PatientRegistrationResponseDto>

    @GET("api/accounts/profile")
    suspend fun getPersonalProfile(): Response<PersonalProfileResponseDto>

    @PUT("api/accounts/profile")
    suspend fun updatePersonalProfile(
        @Body request: PersonalProfileRequestDto,
    ): Response<PersonalProfileResponseDto>

    @GET("api/appointments/onsite/options")
    suspend fun getOnsiteAppointmentOptions(): Response<OnsiteAppointmentOptionsDto>

    @GET("api/appointments/onsite/availability")
    suspend fun searchOnsiteAppointmentAvailability(
        @Query("specialtyId") specialtyId: Long? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("shiftId") shiftId: Long? = null,
        @Query("doctorName") doctorName: String? = null,
    ): Response<List<OnsiteAvailabilityDto>>

    @POST("api/appointments/onsite/bookings")
    suspend fun bookOnsiteAppointment(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: OnsiteAppointmentBookingRequestDto,
    ): Response<OnsiteAppointmentBookingResponseDto>

    @GET("api/patient/appointments")
    suspend fun getPatientAppointments(): Response<List<AppointmentScheduleListItemDto>>

    @GET("api/patient/appointments/{appointmentId}")
    suspend fun getPatientAppointmentDetail(
        @Path("appointmentId") appointmentId: Long,
    ): Response<AppointmentScheduleDetailDto>

    @GET("api/doctor/appointments")
    suspend fun getDoctorAppointments(): Response<List<AppointmentScheduleListItemDto>>

    @GET("api/doctor/appointments/{appointmentId}")
    suspend fun getDoctorAppointmentDetail(
        @Path("appointmentId") appointmentId: Long,
    ): Response<AppointmentScheduleDetailDto>

    @GET("api/doctor/appointments/timetable")
    suspend fun getDoctorTimetable(
        @Query("from") from: String,
        @Query("to") to: String,
    ): Response<List<DoctorWorkShiftDto>>

    @GET("api/doctor/appointments/timetable/{assignmentId}/slots")
    suspend fun getDoctorTimetableSlots(
        @Path("assignmentId") assignmentId: Long,
    ): Response<List<DoctorOnlineSlotDto>>

    @GET("api/doctor/appointments/timetable/slots/{slotId}/patient")
    suspend fun getDoctorSlotPatient(
        @Path("slotId") slotId: Long,
    ): Response<DoctorSlotPatientDto>

    @GET("api/doctor/encounters")
    suspend fun getDoctorEncounterWorklist(
        @Query("date") date: String,
        @Query("status") status: String = "ALL",
        @Query("search") search: String = "",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 10,
    ): Response<EncounterWorklistDto>

    @GET("api/doctor/encounters/{encounterId}")
    suspend fun getDoctorEncounterDetail(
        @Path("encounterId") encounterId: Long,
    ): Response<EncounterHistoryResponseDto>

    @POST("api/doctor/encounters/{encounterId}/start")
    suspend fun startDoctorEncounter(
        @Path("encounterId") encounterId: Long,
        @Body request: EncounterStartRequestDto,
    ): Response<EncounterStartResponseDto>

    @POST("api/doctor/encounters/{encounterId}/complete")
    suspend fun completeDoctorEncounter(
        @Path("encounterId") encounterId: Long,
        @Body request: EncounterCompleteRequestDto,
    ): Response<EncounterCompleteResponseDto>

    @POST("api/doctor/encounters/{encounterId}/histories")
    suspend fun createEncounterHistory(
        @Path("encounterId") encounterId: Long,
        @Body request: PatientHistoryRequestDto,
    ): Response<EncounterHistoryResponseDto>

    @PUT("api/doctor/encounters/{encounterId}/histories/{itemId}")
    suspend fun updateEncounterHistory(
        @Path("encounterId") encounterId: Long,
        @Path("itemId") itemId: Long,
        @Body request: PatientHistoryRequestDto,
    ): Response<EncounterHistoryResponseDto>

    @POST("api/doctor/encounters/{encounterId}/allergies")
    suspend fun createEncounterAllergy(
        @Path("encounterId") encounterId: Long,
        @Body request: PatientAllergyRequestDto,
    ): Response<EncounterHistoryResponseDto>

    @PUT("api/doctor/encounters/{encounterId}/allergies/{itemId}")
    suspend fun updateEncounterAllergy(
        @Path("encounterId") encounterId: Long,
        @Path("itemId") itemId: Long,
        @Body request: PatientAllergyRequestDto,
    ): Response<EncounterHistoryResponseDto>

    @GET("api/doctor/encounters/{encounterId}/clinical")
    suspend fun getEncounterClinical(
        @Path("encounterId") encounterId: Long,
    ): Response<EncounterClinicalResponseDto>

    @POST("api/doctor/encounters/{encounterId}/clinical/vitals")
    suspend fun recordVitalSign(
        @Path("encounterId") encounterId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: VitalSignRequestDto,
    ): Response<EncounterClinicalResponseDto>

    @PUT("api/doctor/encounters/{encounterId}/clinical/notes")
    suspend fun saveEncounterNotes(
        @Path("encounterId") encounterId: Long,
        @Body request: EncounterNotesRequestDto,
    ): Response<EncounterClinicalResponseDto>

    @POST("api/doctor/encounters/{encounterId}/clinical/diagnoses")
    suspend fun createDiagnosis(
        @Path("encounterId") encounterId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: DiagnosisRequestDto,
    ): Response<EncounterClinicalResponseDto>

    @PUT("api/doctor/encounters/{encounterId}/clinical/diagnoses/{diagnosisId}")
    suspend fun updateDiagnosis(
        @Path("encounterId") encounterId: Long,
        @Path("diagnosisId") diagnosisId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: DiagnosisRequestDto,
    ): Response<EncounterClinicalResponseDto>

    @GET("api/doctor/diagnosis-concepts/search")
    suspend fun searchDiagnosisConcepts(
        @Query("search") search: String = "",
        @Query("page") page: Int = 1,
        @Query("groups") groups: Boolean = false,
    ): Response<DiagnosisConceptSearchResponseDto>

    @GET("api/doctor/encounters/{contextId}/history")
    suspend fun getPreviousEncounters(
        @Path("contextId") contextId: Long,
        @Query("page") page: Int = 1,
    ): Response<PreviousEncounterPageDto>

    @GET("api/doctor/encounters/{contextId}/history/{previousId}")
    suspend fun getPreviousEncounterDetail(
        @Path("contextId") contextId: Long,
        @Path("previousId") previousId: Long,
    ): Response<PreviousEncounterDetailDto>

    @GET("api/doctor/encounters/{encounterId}/prescriptions")
    suspend fun getEncounterPrescriptions(
        @Path("encounterId") encounterId: Long,
    ): Response<List<PrescriptionResponseDto>>

    @GET("api/doctor/encounters/{encounterId}/invoices")
    suspend fun getEncounterInvoices(
        @Path("encounterId") encounterId: Long,
    ): Response<List<InvoiceResponseDto>>

    @POST("api/doctor/encounters/{encounterId}/invoices")
    suspend fun createExaminationInvoice(
        @Path("encounterId") encounterId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<InvoiceResponseDto>

    @POST("api/doctor/encounters/{encounterId}/invoices/{invoiceId}/issue")
    suspend fun issueExaminationInvoice(
        @Path("encounterId") encounterId: Long,
        @Path("invoiceId") invoiceId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: IssueInvoiceRequestDto,
    ): Response<InvoiceResponseDto>
}
