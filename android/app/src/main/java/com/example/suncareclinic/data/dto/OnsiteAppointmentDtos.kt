package com.example.suncareclinic.data.dto

data class OnsiteAppointmentOptionsDto(
    val specialties: List<SpecialtyOptionDto> = emptyList(),
    val shifts: List<ShiftOptionDto> = emptyList(),
)

data class SpecialtyOptionDto(
    val id: Long,
    val code: String,
    val name: String,
)

data class ShiftOptionDto(
    val id: Long,
    val code: String,
    val name: String,
    val startTime: String,
    val endTime: String,
)

data class OnsiteAvailabilityDto(
    val doctorId: Long,
    val doctorName: String,
    val departmentName: String,
    val roomCode: String,
    val roomName: String,
    val specialtyId: Long,
    val specialtyCode: String,
    val specialtyName: String,
    val date: String,
    val shiftId: Long,
    val shiftCode: String,
    val shiftName: String,
    val startTime: String,
    val endTime: String,
    val availablePlaces: Long,
    val minimumFee: String? = null,
    val maximumFee: String? = null,
)

data class OnsiteAppointmentBookingRequestDto(
    val specialtyId: Long,
    val date: String,
    val shiftId: Long,
    val doctorId: Long? = null,
    val symptoms: String? = null,
)

data class OnsiteAppointmentBookingResponseDto(
    val appointmentId: Long,
    val appointmentCode: String,
    val encounterId: Long,
    val encounterCode: String,
    val doctorId: Long,
    val doctorName: String,
    val departmentId: Long,
    val departmentName: String,
    val specialtyId: Long,
    val specialtyName: String,
    val roomCode: String,
    val roomName: String,
    val date: String,
    val shiftId: Long,
    val shiftName: String,
    val examinationAmount: String,
    val appointmentStatus: String,
    val encounterStatus: String,
    val replayed: Boolean = false,
)
