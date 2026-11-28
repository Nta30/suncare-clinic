package com.example.suncareclinic.data.dto

data class HealthResponseDto(
    val status: String? = null,
    val service: String? = null,
)

data class ApiErrorDto(
    val code: String? = null,
    val message: String? = null,
    val fieldErrors: Map<String, String>? = null,
)

data class LoginRequestDto(
    val loginName: String,
    val password: String,
)

data class LoginResponseDto(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
)

data class CurrentAccountDto(
    val accountId: Long,
    val loginName: String,
    val accountType: String,
    val patientId: Long? = null,
    val staffId: Long? = null,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
)

// Chỉ gửi các trường được backend chấp nhận trong PatientRegistrationRequest; không gửi role/staffId/accountType.
data class PatientRegistrationRequestDto(
    val loginName: String,
    val password: String,
    val fullName: String,
    val birthDate: String? = null,
    val sex: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val identityNumber: String? = null,
    val emergencyName: String? = null,
    val emergencyPhone: String? = null,
)

data class PatientRegistrationResponseDto(
    val accountId: Long,
    val patientCode: String,
)
