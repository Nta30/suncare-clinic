package com.example.suncareclinic

import com.example.suncareclinic.data.api.ApiClient
import com.example.suncareclinic.data.dto.ApiErrorDto
import com.example.suncareclinic.data.dto.AppointmentScheduleDetailDto
import com.example.suncareclinic.data.dto.AppointmentScheduleListItemDto
import com.example.suncareclinic.data.dto.DoctorOnlineSlotDto
import com.example.suncareclinic.data.dto.DoctorSlotPatientDto
import com.example.suncareclinic.data.dto.DoctorWorkShiftDto
import com.example.suncareclinic.data.dto.InvoiceResponseDto
import com.example.suncareclinic.data.dto.OnsiteAppointmentBookingResponseDto
import com.example.suncareclinic.data.dto.PersonalProfileResponseDto
import com.example.suncareclinic.ui.screens.appointment.DoctorAppointmentsUiState
import com.example.suncareclinic.ui.screens.appointment.DoctorVisitModeFilter
import com.example.suncareclinic.ui.screens.appointment.PatientAppointmentFilter
import com.example.suncareclinic.ui.screens.appointment.PatientAppointmentsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuncareAndroidUnitTest {

    private val sampleAppointments = listOf(
        AppointmentScheduleListItemDto(
            appointmentId = 1L,
            appointmentCode = "APT-2026-0001",
            visitMode = "ONSITE",
            startsAt = "2026-11-27T01:00:00Z",
            appointmentStatus = "BOOKED",
            patientName = "Nguyễn Văn An",
            doctorName = "BS. Trần Minh",
            specialtyName = "Nội tổng quát",
        ),
        AppointmentScheduleListItemDto(
            appointmentId = 2L,
            appointmentCode = "APT-2026-0002",
            visitMode = "ONLINE",
            startsAt = "2026-11-27T02:00:00Z",
            appointmentStatus = "COMPLETED",
            patientName = "Lê Thị Bình",
            doctorName = "BS. Trần Minh",
            specialtyName = "Tim mạch",
        ),
        AppointmentScheduleListItemDto(
            appointmentId = 3L,
            appointmentCode = "APT-2026-0003",
            visitMode = "ONSITE",
            startsAt = "2026-11-28T01:00:00Z",
            appointmentStatus = "CANCELLED",
            patientName = "Phạm Văn Cường",
            doctorName = "BS. Trần Minh",
            specialtyName = "Nội tổng quát",
        ),
    )

    @Test
    fun patientAppointmentsUiState_filtersByStatusCorrectly() {
        val base = PatientAppointmentsUiState(loading = false, items = sampleAppointments)
        assertEquals(3, base.filteredItems.size)

        val booked = base.copy(selectedFilter = PatientAppointmentFilter.Booked)
        assertEquals(1, booked.filteredItems.size)
        assertEquals("APT-2026-0001", booked.filteredItems.first().appointmentCode)

        val completed = base.copy(selectedFilter = PatientAppointmentFilter.Completed)
        assertEquals(1, completed.filteredItems.size)
        assertEquals("APT-2026-0002", completed.filteredItems.first().appointmentCode)

        val noShow = base.copy(selectedFilter = PatientAppointmentFilter.NoShow)
        assertTrue(noShow.filteredItems.isEmpty())
    }

    @Test
    fun doctorAppointmentsUiState_filtersByStatusAndVisitMode() {
        val base = DoctorAppointmentsUiState(
            appointmentsLoading = false,
            appointments = sampleAppointments,
        )
        assertEquals(3, base.filteredAppointments.size)

        val onsiteOnly = base.copy(visitModeFilter = DoctorVisitModeFilter.Onsite)
        assertEquals(2, onsiteOnly.filteredAppointments.size)

        val onlineCompleted = base.copy(
            statusFilter = PatientAppointmentFilter.Completed,
            visitModeFilter = DoctorVisitModeFilter.Online,
        )
        assertEquals(1, onlineCompleted.filteredAppointments.size)
        assertEquals(2L, onlineCompleted.filteredAppointments.first().appointmentId)

        val onsiteCompleted = base.copy(
            statusFilter = PatientAppointmentFilter.Completed,
            visitModeFilter = DoctorVisitModeFilter.Onsite,
        )
        assertTrue(onsiteCompleted.filteredAppointments.isEmpty())
    }

    @Test
    fun gsonDeserializesBackendAppointmentAndProfileContracts() {
        val detailJson = """
            {
              "appointmentId": 42,
              "appointmentCode": "APT-0042",
              "visitMode": "ONSITE",
              "startsAt": "2026-11-27T01:00:00Z",
              "endsAt": "2026-11-27T04:30:00Z",
              "appointmentStatus": "BOOKED",
              "encounterStatus": "PENDING",
              "patientCode": "BN-001",
              "patientName": "Nguyễn Văn An",
              "doctorCode": "BS-001",
              "doctorName": "BS. Trần Minh",
              "departmentName": "Khoa Nội",
              "specialtyName": "Nội tổng quát",
              "roomCode": "P101",
              "roomName": "Phòng khám Nội 1",
              "symptoms": "Đau đầu nhẹ 2 ngày",
              "examinationAmount": "250000.00"
            }
        """.trimIndent()
        val detail = ApiClient.gson.fromJson(detailJson, AppointmentScheduleDetailDto::class.java)
        assertEquals(42L, detail.appointmentId)
        assertEquals("250000.00", detail.examinationAmount)
        assertEquals("Đau đầu nhẹ 2 ngày", detail.symptoms)

        val shiftJson = """
            {
              "assignmentId": 99,
              "workDate": "2026-11-27",
              "shiftCode": "MORNING",
              "shiftName": "Ca sáng",
              "visitMode": "ONLINE",
              "startsAt": "2026-11-27T01:00:00Z",
              "endsAt": "2026-11-27T04:00:00Z",
              "departmentName": "Khoa Nội",
              "specialtyName": "Tim mạch",
              "roomCode": "OL01",
              "roomName": "Phòng trực tuyến 1"
            }
        """.trimIndent()
        val shift = ApiClient.gson.fromJson(shiftJson, DoctorWorkShiftDto::class.java)
        assertEquals(99L, shift.assignmentId)
        assertEquals("ONLINE", shift.visitMode)

        val slotJson = """
            {
              "slotId": 501,
              "startsAt": "2026-11-27T01:00:00Z",
              "endsAt": "2026-11-27T01:20:00Z",
              "open": false,
              "appointmentId": 42,
              "appointmentStatus": "BOOKED",
              "patientName": "Nguyễn Văn An"
            }
        """.trimIndent()
        val slot = ApiClient.gson.fromJson(slotJson, DoctorOnlineSlotDto::class.java)
        assertFalse(slot.open)
        assertEquals(42L, slot.appointmentId)

        val slotPatientJson = """
            {
              "patientCode": "BN-001",
              "fullName": "Nguyễn Văn An",
              "birthDate": "1998-05-15",
              "sex": "MALE",
              "phone": "0901234567"
            }
        """.trimIndent()
        val slotPatient = ApiClient.gson.fromJson(slotPatientJson, DoctorSlotPatientDto::class.java)
        assertEquals("BN-001", slotPatient.patientCode)
        assertEquals("0901234567", slotPatient.phone)

        val bookingJson = """
            {
              "appointmentId": 42,
              "appointmentCode": "APT-0042",
              "encounterId": 84,
              "encounterCode": "ENC-0084",
              "doctorId": 7,
              "doctorName": "BS. Trần Minh",
              "departmentId": 2,
              "departmentName": "Khoa Nội",
              "specialtyId": 3,
              "specialtyName": "Nội tổng quát",
              "roomCode": "P101",
              "roomName": "Phòng khám Nội 1",
              "date": "2026-11-27",
              "shiftId": 1,
              "shiftName": "Ca sáng",
              "examinationAmount": "250000.00",
              "appointmentStatus": "BOOKED",
              "encounterStatus": "PENDING",
              "replayed": true
            }
        """.trimIndent()
        val booking = ApiClient.gson.fromJson(bookingJson, OnsiteAppointmentBookingResponseDto::class.java)
        assertTrue(booking.replayed)
        assertEquals("ENC-0084", booking.encounterCode)

        val profileJson = """
            {
              "accountId": 10,
              "accountType": "PATIENT",
              "loginName": "nguyenvanan",
              "fullName": "Nguyễn Văn An",
              "phone": "0901234567",
              "email": "an@example.com",
              "version": "AAAAAAAAB9E="
            }
        """.trimIndent()
        val profile = ApiClient.gson.fromJson(profileJson, PersonalProfileResponseDto::class.java)
        assertEquals("AAAAAAAAB9E=", profile.version)

        val errorJson = """
            {
              "code": "CONFLICT",
              "message": "Hồ sơ đã được cập nhật bởi phiên khác.",
              "fieldErrors": {"phone": "Số điện thoại không hợp lệ"}
            }
        """.trimIndent()
        val error = ApiClient.gson.fromJson(errorJson, ApiErrorDto::class.java)
        assertEquals("CONFLICT", error.code)
        assertEquals("Số điện thoại không hợp lệ", error.fieldErrors?.get("phone"))

        val invoiceJson = """
            {
              "id": 301,
              "code": "INV-2026-0301",
              "encounterId": 84,
              "patientId": 12,
              "status": "ISSUED",
              "patientName": "Nguyễn Văn An",
              "patientAddress": "Hà Nội",
              "facilityName": "Phòng khám Suncare",
              "facilityAddress": "123 Đường A",
              "totalAmount": 500000.00,
              "currency": "VND",
              "issuedAt": "2026-11-26T10:00:00Z",
              "version": "AAAAAAAAC1Q=",
              "replayed": false,
              "lines": [
                {
                  "id": 901,
                  "lineNo": 1,
                  "lineType": "EXAMINATION",
                  "serviceId": 5,
                  "priceId": 11,
                  "serviceName": "Khám nội tổng quát",
                  "unitName": "Lượt",
                  "quantity": 1,
                  "unitPrice": 500000.00,
                  "amount": 500000.00,
                  "status": "ACTIVE"
                }
              ]
            }
        """.trimIndent()
        val invoice = ApiClient.gson.fromJson(invoiceJson, InvoiceResponseDto::class.java)
        assertEquals("INV-2026-0301", invoice.code)
        assertEquals("ISSUED", invoice.status)
        assertEquals(1, invoice.lines.size)
        assertEquals("EXAMINATION", invoice.lines.first().lineType)
    }
}
