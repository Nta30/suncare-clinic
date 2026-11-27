package com.example.suncareclinic.data.dto

data class PersonalProfileResponseDto(
    val accountType: String,
    val code: String,
    val fullName: String,
    val birthDate: String? = null,
    val sex: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val identityNumber: String? = null,
    val emergencyName: String? = null,
    val emergencyPhone: String? = null,
    val professionalLicense: String? = null,
    val qualification: String? = null,
    val departments: List<String> = emptyList(),
    val specialties: List<String> = emptyList(),
    val version: String,
)

data class PersonalProfileRequestDto(
    val fullName: String,
    val birthDate: String? = null,
    val sex: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val identityNumber: String? = null,
    val emergencyName: String? = null,
    val emergencyPhone: String? = null,
    val version: String,
)
