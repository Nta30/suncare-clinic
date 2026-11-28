package com.example.suncareclinic.ui.screens.encounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.suncareclinic.data.dto.PrescriptionItemDto
import com.example.suncareclinic.data.dto.PrescriptionResponseDto
import com.example.suncareclinic.data.repository.ApiResult
import com.example.suncareclinic.ui.components.BadgeTone
import com.example.suncareclinic.ui.components.StatusBadge
import com.example.suncareclinic.ui.components.SuncareCard
import com.example.suncareclinic.ui.components.SuncareEmptyView
import com.example.suncareclinic.ui.components.SuncareErrorView
import com.example.suncareclinic.ui.components.SuncareLoadingView
import com.example.suncareclinic.ui.components.SuncareSectionHeading
import com.example.suncareclinic.ui.theme.SuncareTheme

internal fun LazyListScope.encounterPrescriptions(
    result: ApiResult<List<PrescriptionResponseDto>>?,
    onReload: () -> Unit,
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SuncareSectionHeading("Đơn thuốc", "Chỉ xem · Lượt khám hiện tại", modifier = Modifier.weight(1f))
            if (result is ApiResult.Success) {
                TextButton(onClick = onReload) {
                    Text("Làm mới", color = SuncareTheme.colors.brandText)
                }
            }
        }
    }

    when (result) {
        null -> item { SuncareLoadingView("Đang tải đơn thuốc…") }
        is ApiResult.Error -> item {
            val inaccessible = result.status == 403 || result.status == 404
            SuncareErrorView(
                title = if (inaccessible) "Đơn thuốc không khả dụng" else "Không tải được đơn thuốc",
                message = if (inaccessible) "Bạn không thể xem đơn thuốc của lượt khám này." else result.message,
                onRetry = if (inaccessible) null else onReload,
            )
        }
        is ApiResult.Success -> {
            if (result.data.isEmpty()) {
                item {
                    SuncareEmptyView("Chưa có đơn thuốc", "Đơn thuốc sẽ xuất hiện sau khi được lưu trên Web.")
                }
            }
            result.data.forEach { prescription ->
                item(key = "prescription_${prescription.id}") { PrescriptionSummary(prescription) }
                items(prescription.items.sortedBy { it.lineNo }, key = { "prescription_${prescription.id}_drug_${it.id}" }) {
                    PrescriptionDrugCard(it)
                }
            }
        }
    }
}

@Composable
private fun PrescriptionSummary(prescription: PrescriptionResponseDto) {
    val status = when (prescription.status) {
        "DRAFT" -> "Chưa phát hành" to BadgeTone.Warning
        "ISSUED" -> "Đã phát hành" to BadgeTone.Success
        else -> "Trạng thái chưa xác định" to BadgeTone.Neutral
    }
    val issuedAt = prescription.issuedAt?.takeIf { it.isNotBlank() }
    SuncareCard(highlighted = prescription.status == "DRAFT") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(label = status.first, tone = status.second)
            SuncareSectionHeading(
                title = "Đơn thuốc ngoại trú",
                description = issuedAt?.let { "Phát hành lúc ${formatClinDateTime(it)}" },
            )
            if (!prescription.code.isNullOrBlank()) {
                PrescriptionFact("Mã đơn", prescription.code)
            }
            if (!prescription.instruction.isNullOrBlank()) {
                PrescriptionFact("Lời dặn chung", prescription.instruction)
            }
            if (prescription.items.isEmpty()) {
                Text("Đơn thuốc chưa có thuốc.", style = MaterialTheme.typography.bodyMedium, color = SuncareTheme.colors.muted)
            }
        }
    }
}

@Composable
private fun PrescriptionDrugCard(drug: PrescriptionItemDto) {
    val details = listOfNotNull(drug.strength?.ifBlank { null }, drug.dosageForm?.ifBlank { null })
    SuncareCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SuncareSectionHeading(drug.drugName, details.joinToString(" · "))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrescriptionFact("Liều dùng", drug.displayDose(), Modifier.weight(1f))
                PrescriptionFact("Số lượng cấp phát", drug.displayQuantity(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrescriptionFact("Tần suất", drug.frequency, Modifier.weight(1f))
                PrescriptionFact("Thời gian", "${drug.durationDays} ngày", Modifier.weight(1f))
            }
            PrescriptionFact("Đường dùng", drug.route)
            if (!drug.instruction.isNullOrBlank()) {
                PrescriptionFact("Dặn dò", drug.instruction)
            }
        }
    }
}

@Composable
private fun PrescriptionFact(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = SuncareTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
    }
}

private val numericDose = Regex("[0-9]+(?:[.,][0-9]+)?")

internal fun PrescriptionItemDto.displayDose(): String {
    val snapshotUnit = doseUnit?.trim()?.takeIf { it.isNotEmpty() } ?: return dose
    val amount = dose.trim()
    // Đơn cũ có thể đã lưu cả đơn vị trong chuỗi liều dùng.
    return if (numericDose.matches(amount)) "${amount.replace('.', ',')} $snapshotUnit" else dose
}

internal fun PrescriptionItemDto.displayQuantity(): String {
    val snapshotUnit = dispensingUnit?.trim()?.takeIf { it.isNotEmpty() } ?: unit?.trim().orEmpty()
    val amount = quantity.stripTrailingZeros().toPlainString().replace('.', ',')
    return if (snapshotUnit.isEmpty()) amount else "$amount $snapshotUnit"
}
