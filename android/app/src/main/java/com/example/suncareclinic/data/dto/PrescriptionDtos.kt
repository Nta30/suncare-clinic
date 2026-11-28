package com.example.suncareclinic.data.dto

import java.math.BigDecimal

data class PrescriptionResponseDto(
    val id: Long,
    val code: String?,
    val status: String,
    val issuedAt: String?,
    val instruction: String?,
    val items: List<PrescriptionItemDto>,
)

data class PrescriptionItemDto(
    val id: Long,
    val lineNo: Int,
    val drugName: String,
    val strength: String?,
    val dosageForm: String?,
    val unit: String?,
    val dose: String,
    val route: String,
    val frequency: String,
    val durationDays: Int,
    val quantity: BigDecimal,
    val instruction: String?,
    val doseUnit: String?,
    val dispensingUnit: String?,
)
