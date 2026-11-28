package com.example.suncareclinic.data.dto

data class AppointmentScheduleListItemDto(
    val appointmentId: Long,
    val appointmentCode: String,
    val visitMode: String,
    val startsAt: String? = null,
    val appointmentStatus: String,
    val patientName: String? = null,
    val doctorName: String? = null,
    val specialtyName: String? = null,
)

data class AppointmentScheduleDetailDto(
    val appointmentId: Long,
    val appointmentCode: String,
    val visitMode: String,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val appointmentStatus: String,
    val encounterStatus: String? = null,
    val patientCode: String? = null,
    val patientName: String? = null,
    val doctorCode: String? = null,
    val doctorName: String? = null,
    val departmentName: String? = null,
    val specialtyName: String? = null,
    val roomCode: String? = null,
    val roomName: String? = null,
    val symptoms: String? = null,
    val examinationAmount: String? = null,
)

data class DoctorWorkShiftDto(
    val assignmentId: Long,
    val workDate: String,
    val shiftCode: String? = null,
    val shiftName: String? = null,
    val visitMode: String,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val departmentName: String? = null,
    val specialtyName: String? = null,
    val roomCode: String? = null,
    val roomName: String? = null,
)

data class DoctorOnlineSlotDto(
    val slotId: Long,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val open: Boolean = false,
    val appointmentId: Long? = null,
    val appointmentStatus: String? = null,
    val patientName: String? = null,
)

data class DoctorSlotPatientDto(
    val patientCode: String? = null,
    val fullName: String? = null,
    val birthDate: String? = null,
    val sex: String? = null,
    val phone: String? = null,
)
