package com.example.suncareclinic.ui.screens.encounter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.suncareclinic.data.dto.DiagnosisConceptDto
import com.example.suncareclinic.data.dto.DiagnosisItemDto
import com.example.suncareclinic.data.dto.InvoiceResponseDto
import com.example.suncareclinic.data.dto.PreviousEncounterDetailDto
import com.example.suncareclinic.data.dto.PreviousEncounterSummaryDto
import com.example.suncareclinic.data.dto.VitalSignDto
import com.example.suncareclinic.ui.components.BadgeTone
import com.example.suncareclinic.ui.components.StatusBadge
import com.example.suncareclinic.ui.components.SuncareButton
import com.example.suncareclinic.ui.components.SuncareButtonVariant
import com.example.suncareclinic.ui.components.SuncareCard
import com.example.suncareclinic.ui.components.SuncareEmptyView
import com.example.suncareclinic.ui.components.SuncareErrorView
import com.example.suncareclinic.ui.components.SuncareInlineBanner
import com.example.suncareclinic.ui.components.SuncareLoadingView
import com.example.suncareclinic.ui.components.SuncareSectionHeading
import com.example.suncareclinic.ui.components.SuncareTextField
import com.example.suncareclinic.ui.theme.SuncareTheme
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLIN_VN_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
private val CLIN_DATE_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")

@Composable
fun VitalsAndNotesSection(
    state: EncounterClinicalUiState,
    onReloadClinical: () -> Unit,
    onOpenRecordVital: () -> Unit,
    onUpdateNotesDraft: ((NotesDraftState) -> NotesDraftState) -> Unit,
    onSaveNotes: () -> Unit,
) {
    val colors = SuncareTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.clinicalBanner.isNullOrBlank()) {
            SuncareInlineBanner(message = state.clinicalBanner, tone = BadgeTone.Success)
        }

        when {
            state.clinicalLoading && state.clinical == null -> {
                SuncareLoadingView(message = "Đang tải sinh hiệu và ghi chú lâm sàng...")
            }

            !state.clinicalError.isNullOrBlank() && state.clinical == null -> {
                SuncareErrorView(
                    title = "Không tải được dữ liệu lâm sàng",
                    message = state.clinicalError,
                    onRetry = onReloadClinical,
                )
            }

            state.clinical != null -> {
                val editable = state.clinical.editable
                val vitals = state.clinical.vitals

                SuncareCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SuncareSectionHeading(
                            title = "Sinh hiệu",
                            description = "${vitals.size} lần đo",
                            modifier = Modifier.weight(1f),
                        )
                        if (editable) {
                            TextButton(onClick = onOpenRecordVital) {
                                Text("+ Ghi sinh hiệu", color = colors.brandText)
                            }
                        }
                    }

                    if (vitals.isEmpty()) {
                        Text(
                            text = "Chưa có lần đo sinh hiệu nào trong lượt khám này.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            vitals.forEach { v ->
                                VitalSignRowCard(vital = v)
                            }
                        }
                    }
                }

                val draft = state.notesDraft
                SuncareCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SuncareSectionHeading("Ghi chú lâm sàng", modifier = Modifier.weight(1f))
                        if (!editable) {
                            StatusBadge(label = "Chỉ đọc", tone = BadgeTone.Neutral)
                        }
                    }

                    if (!draft.successBanner.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SuncareInlineBanner(message = draft.successBanner, tone = BadgeTone.Success)
                    }
                    if (!draft.error.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SuncareInlineBanner(message = draft.error, tone = BadgeTone.Danger)
                    }
                    if (draft.conflict) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SuncareButton(
                            text = "Tải lại phiên bản mới nhất",
                            onClick = onReloadClinical,
                            variant = SuncareButtonVariant.Outline,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SuncareTextField(
                            label = "Lý do đến khám",
                            value = draft.chiefComplaint,
                            onValueChange = { value -> onUpdateNotesDraft { it.copy(chiefComplaint = value) } },
                            placeholder = "Triệu chứng chính khiến bệnh nhân đi khám...",
                            enabled = editable && !draft.saving,
                            singleLine = false,
                        )
                        SuncareTextField(
                            label = "Bệnh sử hiện tại",
                            value = draft.historyOfPresentIllness,
                            onValueChange = { value -> onUpdateNotesDraft { it.copy(historyOfPresentIllness = value) } },
                            placeholder = "Diễn tiến bệnh, thời gian khởi phát...",
                            enabled = editable && !draft.saving,
                            singleLine = false,
                        )
                        SuncareTextField(
                            label = "Khám lâm sàng",
                            value = draft.examinationNote,
                            onValueChange = { value -> onUpdateNotesDraft { it.copy(examinationNote = value) } },
                            placeholder = "Ghi nhận khám toàn thân và cơ quan...",
                            enabled = editable && !draft.saving,
                            singleLine = false,
                        )
                        SuncareTextField(
                            label = "Hướng xử trí / điều trị",
                            value = draft.treatmentPlan,
                            onValueChange = { value -> onUpdateNotesDraft { it.copy(treatmentPlan = value) } },
                            placeholder = "Kế hoạch theo dõi, chỉ định hoặc điều trị...",
                            enabled = editable && !draft.saving,
                            singleLine = false,
                        )
                        SuncareTextField(
                            label = "Lời dặn tái khám",
                            value = draft.followupNote,
                            onValueChange = { value -> onUpdateNotesDraft { it.copy(followupNote = value) } },
                            placeholder = "Dấu hiệu cần tái khám sớm, lịch hẹn lại...",
                            enabled = editable && !draft.saving,
                            singleLine = false,
                        )
                    }

                    if (editable) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SuncareButton(
                            text = "Lưu ghi chú lâm sàng",
                            onClick = onSaveNotes,
                            loading = draft.saving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (draft.dirty && !draft.conflict) {
                            TextButton(onClick = onReloadClinical, enabled = !draft.saving, modifier = Modifier.align(Alignment.End)) {
                                Text("Bỏ thay đổi", color = colors.brandText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VitalSignRowCard(vital: VitalSignDto) {
    val colors = SuncareTheme.colors
    val metrics = buildList {
        vital.temperatureC?.let { add("Nhiệt độ: $it °C") }
        vital.pulseBpm?.let { add("Mạch: $it lần/phút") }
        if (vital.systolicMmhg != null || vital.diastolicMmhg != null) {
            add("Huyết áp: ${vital.systolicMmhg ?: "—"}/${vital.diastolicMmhg ?: "—"} mmHg")
        }
        vital.respiratoryRate?.let { add("Nhịp thở: $it lần/phút") }
        vital.spo2Percent?.let { add("SpO2: $it %") }
        vital.weightKg?.let { add("Cân nặng: $it kg") }
        vital.heightCm?.let { add("Chiều cao: $it cm") }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.canvas,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Đo lúc ${formatClinDateTime(vital.measuredAt)}",
                style = MaterialTheme.typography.labelLarge,
                color = colors.brandText,
            )
            Text(
                text = metrics.joinToString(" · ").ifBlank { "Không có chỉ số" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
            )
        }
    }
}

@Composable
fun DiagnosesSection(
    state: EncounterClinicalUiState,
    onReloadClinical: () -> Unit,
    onOpenAddDiagnosis: () -> Unit,
    onOpenEditDiagnosis: (DiagnosisItemDto) -> Unit,
) {
    val colors = SuncareTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.clinicalBanner.isNullOrBlank()) {
            SuncareInlineBanner(message = state.clinicalBanner, tone = BadgeTone.Success)
        }

        when {
            state.clinicalLoading && state.clinical == null -> {
                SuncareLoadingView(message = "Đang tải danh sách chẩn đoán...")
            }

            !state.clinicalError.isNullOrBlank() && state.clinical == null -> {
                SuncareErrorView(
                    title = "Không tải được chẩn đoán",
                    message = state.clinicalError,
                    onRetry = onReloadClinical,
                )
            }

            state.clinical != null -> {
                val editable = state.clinical.editable
                val diagnoses = state.clinical.diagnoses

                SuncareCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SuncareSectionHeading("Chẩn đoán", "${diagnoses.size} mục", modifier = Modifier.weight(1f))
                        if (editable) {
                            TextButton(onClick = onOpenAddDiagnosis) {
                                Text("+ Thêm chẩn đoán", color = colors.brandText)
                            }
                        }
                    }

                    Text(
                        text = "Chẩn đoán xác định (FINAL) bắt buộc chọn mã từ danh mục ICD-10-VN. Mỗi lượt khám có tối đa một chẩn đoán chính.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (diagnoses.isEmpty()) {
                        Text(
                            text = "Chưa có chẩn đoán nào cho lượt khám này.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            diagnoses.forEach { d ->
                                DiagnosisRowCard(
                                    item = d,
                                    editable = editable,
                                    onEdit = { onOpenEditDiagnosis(d) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosisRowCard(
    item: DiagnosisItemDto,
    editable: Boolean,
    onEdit: () -> Unit,
) {
    val colors = SuncareTheme.colors
    val isFinal = item.diagnosisType.equals("FINAL", ignoreCase = true)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.canvas,
        border = BorderStroke(1.dp, if (item.isPrimary) colors.brandText else colors.border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(
                    label = if (isFinal) "Xác định (FINAL)" else "Sơ bộ",
                    tone = if (isFinal) BadgeTone.Success else BadgeTone.Warning,
                )
                if (item.isPrimary) {
                    StatusBadge(label = "Bệnh chính", tone = BadgeTone.Warning)
                }
            }
            if (!item.diagnosisCode.isNullOrBlank()) {
                Text(
                    text = "${item.diagnosisCode} · ${item.diagnosisName.orEmpty()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.brandText,
                )
            }
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
            )
            if (editable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onEdit) {
                        Text("Chỉnh sửa", color = colors.brandText)
                    }
                }
            }
        }
    }
}

@Composable
fun PreviousEncountersSection(
    state: EncounterClinicalUiState,
    onLoadPage: (Int) -> Unit,
    onOpenDetail: (Long) -> Unit,
) {
    val colors = SuncareTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SuncareSectionHeading("Lịch sử khám", "Các lượt khám đã hoàn tất, ở chế độ chỉ đọc.")
        when {
            state.historyLoading && state.previousItems.isEmpty() -> {
                SuncareLoadingView(message = "Đang tải lịch sử khám đã hoàn tất...")
            }

            !state.historyError.isNullOrBlank() && state.previousItems.isEmpty() -> {
                SuncareErrorView(
                    title = "Không tải được lịch sử khám",
                    message = state.historyError,
                    onRetry = { onLoadPage(state.historyPage) },
                )
            }

            state.previousItems.isEmpty() -> {
                SuncareEmptyView(
                    title = "Chưa có lượt khám trước đã hoàn tất",
                    description = "Các lượt khám đã hoàn tất của bệnh nhân sẽ hiển thị tại đây.",
                )
            }

            else -> {
                Text(
                    text = "Lịch sử khám chỉ đọc · Tổng ${state.historyTotalElements} lượt · Trang ${state.historyPage}/${maxOf(state.historyTotalPages, 1)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
                state.previousItems.forEach { prev ->
                    PreviousEncounterCard(item = prev, onClick = { onOpenDetail(prev.encounterId) })
                }
                if (state.historyTotalPages > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SuncareButton(
                            text = "Trang trước",
                            onClick = { onLoadPage(state.historyPage - 1) },
                            enabled = state.historyPage > 1 && !state.historyLoading,
                            variant = SuncareButtonVariant.Outline,
                        )
                        SuncareButton(
                            text = "Trang sau",
                            onClick = { onLoadPage(state.historyPage + 1) },
                            enabled = state.historyPage < state.historyTotalPages && !state.historyLoading,
                            variant = SuncareButtonVariant.Outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviousEncounterCard(
    item: PreviousEncounterSummaryDto,
    onClick: () -> Unit,
) {
    val colors = SuncareTheme.colors
    SuncareCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.encounterCode,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                fontWeight = FontWeight.SemiBold,
            )
            StatusBadge(label = "Hoàn tất", tone = BadgeTone.Success)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatClinDateTime(item.completedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        Text(
            text = listOfNotNull(
                item.doctorName?.ifBlank { null },
                item.specialtyName?.ifBlank { null },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )
        if (!item.primaryDiagnosis.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Chẩn đoán chính: ${item.primaryDiagnosis}",
                style = MaterialTheme.typography.labelLarge,
                color = colors.brandText,
            )
        }
    }
}

@Composable
fun VitalFormSheetContent(
    form: VitalFormState,
    onUpdate: ((VitalFormState) -> VitalFormState) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = SuncareTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "Ghi nhận sinh hiệu", style = MaterialTheme.typography.titleLarge, color = colors.ink)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SuncareTextField(
                label = "Nhiệt độ (°C)",
                value = form.temperatureC,
                onValueChange = { v -> onUpdate { it.copy(temperatureC = v) } },
                placeholder = "36.8",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            SuncareTextField(
                label = "Mạch (lần/phút)",
                value = form.pulseBpm,
                onValueChange = { v -> onUpdate { it.copy(pulseBpm = v) } },
                placeholder = "78",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SuncareTextField(
                label = "HA tâm thu (mmHg)",
                value = form.systolicMmhg,
                onValueChange = { v -> onUpdate { it.copy(systolicMmhg = v) } },
                placeholder = "120",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            SuncareTextField(
                label = "HA tâm trương",
                value = form.diastolicMmhg,
                onValueChange = { v -> onUpdate { it.copy(diastolicMmhg = v) } },
                placeholder = "80",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SuncareTextField(
                label = "Nhịp thở",
                value = form.respiratoryRate,
                onValueChange = { v -> onUpdate { it.copy(respiratoryRate = v) } },
                placeholder = "18",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            SuncareTextField(
                label = "SpO2 (%)",
                value = form.spo2Percent,
                onValueChange = { v -> onUpdate { it.copy(spo2Percent = v) } },
                placeholder = "98",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SuncareTextField(
                label = "Cân nặng (kg)",
                value = form.weightKg,
                onValueChange = { v -> onUpdate { it.copy(weightKg = v) } },
                placeholder = "60.5",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            SuncareTextField(
                label = "Chiều cao (cm)",
                value = form.heightCm,
                onValueChange = { v -> onUpdate { it.copy(heightCm = v) } },
                placeholder = "168",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        if (!form.error.isNullOrBlank()) {
            SuncareInlineBanner(message = form.error, tone = BadgeTone.Danger)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SuncareButton(
                text = "Hủy",
                onClick = onClose,
                variant = SuncareButtonVariant.Outline,
                enabled = !form.saving,
                modifier = Modifier.weight(1f),
            )
            SuncareButton(
                text = "Lưu sinh hiệu",
                onClick = onSubmit,
                loading = form.saving,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosisFormSheetContent(
    form: DiagnosisFormState,
    onUpdate: ((DiagnosisFormState) -> DiagnosisFormState) -> Unit,
    onIcdQueryChange: (String) -> Unit,
    onToggleIcdGroups: (Boolean) -> Unit,
    onLoadIcdPage: (Int) -> Unit,
    onSelectConcept: (DiagnosisConceptDto) -> Unit,
    onClearConcept: () -> Unit,
    onSubmit: () -> Unit,
    onReloadOnConflict: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = SuncareTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (form.editingId == null) "Thêm chẩn đoán" else "Cập nhật chẩn đoán",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )

        Text(text = "Loại chẩn đoán", style = MaterialTheme.typography.labelLarge, color = colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = form.diagnosisType == "PRELIMINARY",
                onClick = { onUpdate { it.copy(diagnosisType = "PRELIMINARY") } },
                label = { Text("Sơ bộ (PRELIMINARY)") },
            )
            FilterChip(
                selected = form.diagnosisType == "FINAL",
                onClick = { onUpdate { it.copy(diagnosisType = "FINAL") } },
                label = { Text("Xác định (FINAL)") },
            )
        }

        if (form.diagnosisConceptId != null && !form.selectedCode.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.warningSoft,
                border = BorderStroke(1.dp, colors.brandText),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mã ICD-10-VN: ${form.selectedCode}",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.brandText,
                        )
                        Text(
                            text = form.selectedName.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink,
                        )
                    }
                    TextButton(onClick = onClearConcept) {
                        Text("Đổi mã", color = colors.brandText)
                    }
                }
            }
        } else {
            SuncareTextField(
                label = "Tra cứu danh mục ICD-10-VN",
                value = form.icdQuery,
                onValueChange = onIcdQueryChange,
                placeholder = "Nhập mã (vd: J00, I10) hoặc tên bệnh...",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Chỉ xem nhóm mã",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
                Switch(
                    checked = form.icdGroupsOnly,
                    onCheckedChange = onToggleIcdGroups,
                )
            }

            if (form.icdShowResults) {
                when {
                    form.icdLoading -> {
                        SuncareLoadingView(message = "Đang tra cứu ICD-10-VN...")
                    }

                    !form.icdError.isNullOrBlank() -> {
                        SuncareErrorView(
                            title = "Không tra cứu được danh mục ICD",
                            message = form.icdError,
                            onRetry = { onLoadIcdPage(form.icdPage) },
                        )
                    }

                    form.icdResults.isEmpty() -> {
                        Text(
                            text = "Không tìm thấy mã ICD khớp từ khóa.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }

                    else -> {
                        Text(
                            text = "Tìm thấy ${form.icdTotalElements} mã · Trang ${form.icdPage}/${maxOf(form.icdTotalPages, 1)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.muted,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            form.icdResults.take(8).forEach { concept ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectConcept(concept) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.canvas,
                                    border = BorderStroke(1.dp, colors.border),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = concept.code,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = colors.brandText,
                                            )
                                            if (!concept.primaryAllowed) {
                                                Text(
                                                    text = "Không làm bệnh chính",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = colors.muted,
                                                )
                                            }
                                        }
                                        Text(
                                            text = concept.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.ink,
                                        )
                                    }
                                }
                            }
                        }
                        if (form.icdTotalPages > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(
                                    onClick = { onLoadIcdPage(form.icdPage - 1) },
                                    enabled = form.icdPage > 1,
                                ) {
                                    Text("← Trang trước", color = colors.brandText)
                                }
                                TextButton(
                                    onClick = { onLoadIcdPage(form.icdPage + 1) },
                                    enabled = form.icdPage < form.icdTotalPages,
                                ) {
                                    Text("Trang sau →", color = colors.brandText)
                                }
                            }
                        }
                    }
                }
            }
        }

        SuncareTextField(
            label = "Diễn giải chẩn đoán *",
            value = form.description,
            onValueChange = { v -> onUpdate { it.copy(description = v) } },
            placeholder = "Tên bệnh hoặc mô tả lâm sàng chi tiết...",
            singleLine = false,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Đặt làm chẩn đoán chính", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                if (!form.primaryAllowed) {
                    Text(
                        text = "Mã ICD này không được phép chọn làm bệnh chính.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                }
            }
            Switch(
                checked = form.isPrimary,
                onCheckedChange = { checked -> onUpdate { it.copy(isPrimary = checked) } },
                enabled = form.primaryAllowed,
            )
        }

        if (!form.error.isNullOrBlank()) {
            SuncareInlineBanner(message = form.error, tone = BadgeTone.Danger)
        }
        if (form.conflict) {
            SuncareButton(
                text = "Tải lại phiên bản mới nhất",
                onClick = onReloadOnConflict,
                variant = SuncareButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SuncareButton(
                text = "Hủy",
                onClick = onClose,
                variant = SuncareButtonVariant.Outline,
                enabled = !form.saving,
                modifier = Modifier.weight(1f),
            )
            SuncareButton(
                text = "Lưu chẩn đoán",
                onClick = onSubmit,
                loading = form.saving,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun PreviousEncounterDetailSheetContent(
    loading: Boolean,
    detail: PreviousEncounterDetailDto?,
    error: String?,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = SuncareTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Chi tiết lượt khám cũ (chỉ đọc)",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )

        when {
            loading -> SuncareLoadingView(message = "Đang tải chi tiết lượt khám cũ...")
            !error.isNullOrBlank() -> SuncareErrorView(
                title = "Không tải được chi tiết",
                message = error,
                onRetry = onRetry,
            )
            detail != null -> {
                Text(
                    text = "${detail.encounter.encounterCode} · ${formatClinDateTime(detail.encounter.completedAt)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.brandText,
                )
                Text(
                    text = listOfNotNull(
                        detail.encounter.doctorName?.ifBlank { null },
                        detail.encounter.specialtyName?.ifBlank { null },
                        detail.encounter.departmentName?.ifBlank { null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                )

                HorizontalDivider(color = colors.border)

                val diagnoses = detail.clinical?.diagnoses.orEmpty()
                Text(
                    text = "Chẩn đoán (${diagnoses.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                )
                if (diagnoses.isEmpty()) {
                    Text("Không có chẩn đoán.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                } else {
                    diagnoses.forEach { d ->
                        Text(
                            text = "• ${d.diagnosisCode?.let { "[$it] " }.orEmpty()}${d.description}${if (d.isPrimary) " (Chính)" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink,
                        )
                    }
                }

                detail.clinical?.notes?.let { notes ->
                    HorizontalDivider(color = colors.border)
                    Text("Ghi chú lâm sàng", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    if (!notes.chiefComplaint.isNullOrBlank()) {
                        Text("Lý do khám: ${notes.chiefComplaint}", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                    }
                    if (!notes.examinationNote.isNullOrBlank()) {
                        Text("Khám: ${notes.examinationNote}", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                    }
                    if (!notes.treatmentPlan.isNullOrBlank()) {
                        Text("Xử trí: ${notes.treatmentPlan}", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                    }
                }

                if (detail.prescriptions.isNotEmpty()) {
                    HorizontalDivider(color = colors.border)
                    Text("Đơn thuốc đã phát hành (${detail.prescriptions.size})", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    detail.prescriptions.forEach { rx ->
                        Text(
                            text = "• ${rx.code ?: "Đơn thuốc"} (${rx.items.size} thuốc)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink,
                        )
                    }
                }

                if (detail.results.isNotEmpty()) {
                    HorizontalDivider(color = colors.border)
                    Text("Kết quả CLS đã công bố (${detail.results.size})", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    detail.results.forEach { res ->
                        Text(
                            text = "• ${res.serviceName ?: "Dịch vụ CLS"} (v${res.versionNo}): ${res.conclusion ?: res.description ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink,
                        )
                    }
                }
            }
        }

        SuncareButton(
            text = "Đóng",
            onClick = onClose,
            variant = SuncareButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun EncounterInvoicesSection(
    state: EncounterClinicalUiState,
    writable: Boolean,
    onReload: () -> Unit,
    onCreateDraft: () -> Unit,
    onToggleConfirmIssue: (Long, Boolean) -> Unit,
    onIssueInvoice: (InvoiceResponseDto) -> Unit,
) {
    val colors = SuncareTheme.colors
    val busy = state.invoiceActionBusy != null
    val hasExamInvoice = state.hasActiveExaminationInvoice

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SuncareCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SuncareSectionHeading("Hóa đơn phí khám", "Khám trực tiếp", modifier = Modifier.weight(1f))
                TextButton(onClick = onReload, enabled = !busy && !state.invoicesLoading) {
                    Text(
                        text = if (state.invoicesLoading) "Đang tải..." else "Tải lại",
                        color = colors.brandText,
                    )
                }
            }

            if (!writable) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Chỉ được lập hoặc phát hành hóa đơn sau khi bắt đầu khám và trước khi hoàn tất lượt khám.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusBadge(
                        label = if (hasExamInvoice) "Đã lập hóa đơn phí khám" else "Chưa lập hóa đơn",
                        tone = if (hasExamInvoice) BadgeTone.Success else BadgeTone.Warning,
                    )
                    if (!hasExamInvoice) {
                        SuncareButton(
                            text = if (state.invoiceActionBusy == "create") "Đang tạo..." else "Lập hóa đơn nháp",
                            onClick = onCreateDraft,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && !state.invoiceActionConflict,
                            loading = state.invoiceActionBusy == "create",
                        )
                    }
                }
            }

            if (!state.invoiceActionBanner.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                SuncareInlineBanner(message = state.invoiceActionBanner, tone = BadgeTone.Success)
            }
            if (!state.invoiceActionError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                SuncareInlineBanner(message = state.invoiceActionError, tone = BadgeTone.Danger)
            }
            if (state.invoiceActionConflict) {
                Spacer(modifier = Modifier.height(8.dp))
                SuncareButton(
                    text = "Tải lại hóa đơn mới nhất",
                    onClick = onReload,
                    variant = SuncareButtonVariant.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        when {
            state.invoicesLoading && state.invoices.isEmpty() -> {
                SuncareLoadingView(message = "Đang tải danh sách hóa đơn...")
            }

            !state.invoicesLoadError.isNullOrBlank() && state.invoices.isEmpty() -> {
                SuncareErrorView(
                    title = "Không tải được danh sách hóa đơn",
                    message = state.invoicesLoadError,
                    onRetry = onReload,
                )
            }

            state.invoices.isEmpty() -> {
                SuncareEmptyView(
                    title = "Lượt khám chưa có hóa đơn",
                    description = if (writable) {
                        "Nhấn \"Lập hóa đơn nháp\" để tạo hóa đơn phí khám từ giá đã chốt khi đặt lịch."
                    } else {
                        "Bắt đầu lượt khám để có thể lập và phát hành hóa đơn phí khám."
                    },
                )
            }

            else -> {
                state.invoices.forEach { invoice ->
                    InvoiceCard(
                        invoice = invoice,
                        writable = writable,
                        confirmed = state.confirmedIssueIds.contains(invoice.id),
                        busy = busy,
                        issuingThis = state.invoiceActionBusy == "issue-${invoice.id}",
                        conflict = state.invoiceActionConflict,
                        onToggleConfirm = { checked -> onToggleConfirmIssue(invoice.id, checked) },
                        onIssue = { onIssueInvoice(invoice) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceCard(
    invoice: InvoiceResponseDto,
    writable: Boolean,
    confirmed: Boolean,
    busy: Boolean,
    issuingThis: Boolean,
    conflict: Boolean,
    onToggleConfirm: (Boolean) -> Unit,
    onIssue: () -> Unit,
) {
    val colors = SuncareTheme.colors
    val statusBadge = invoiceStatusBadge(invoice.status)
    val isDraft = invoice.status.equals("DRAFT", ignoreCase = true)
    val isIssued = invoice.status.equals("ISSUED", ignoreCase = true)

    SuncareCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = invoice.code,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(label = statusBadge.first, tone = statusBadge.second)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Bệnh nhân: ${invoice.patientName ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )
        if (!invoice.patientAddress.isNullOrBlank()) {
            Text(
                text = "Địa chỉ: ${invoice.patientAddress}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Cơ sở: ${invoice.facilityName ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )
        if (!invoice.facilityAddress.isNullOrBlank()) {
            Text(
                text = invoice.facilityAddress,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        if (!invoice.issuedAt.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Phát hành lúc: ${formatClinDateTime(invoice.issuedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.brandText,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = colors.border)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Chi tiết dòng thu (${invoice.lines.size})",
            style = MaterialTheme.typography.labelLarge,
            color = colors.muted,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            invoice.lines.forEach { line ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.canvas,
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line.serviceName ?: "Phí khám",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.ink,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${line.quantity ?: "1"} ${line.unitName ?: "Lượt"} × ${formatClinVnd(line.unitPrice)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.muted,
                            )
                        }
                        Text(
                            text = formatClinVnd(line.amount),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tổng giá trị hóa đơn",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )
            Text(
                text = formatClinVnd(invoice.totalAmount),
                style = MaterialTheme.typography.titleMedium,
                color = colors.brandText,
                fontWeight = FontWeight.Bold,
            )
        }

        if (isIssued) {
            Spacer(modifier = Modifier.height(8.dp))
            SuncareInlineBanner(
                message = "Hóa đơn đã phát hành và không thể chỉnh sửa. Phát hành chưa đồng nghĩa đã thanh toán; thu ngân sẽ xác nhận thu tiền tại quầy.",
                tone = BadgeTone.Warning,
            )
        }

        if (isDraft && writable) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy && !conflict) { onToggleConfirm(!confirmed) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = confirmed,
                    onCheckedChange = onToggleConfirm,
                    enabled = !busy && !conflict,
                )
                Text(
                    text = "Tôi đã kiểm tra nội dung hóa đơn và xác nhận phát hành.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            SuncareButton(
                text = if (issuingThis) "Đang phát hành..." else "Phát hành hóa đơn",
                onClick = onIssue,
                enabled = confirmed && !busy && !conflict,
                loading = issuingThis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun invoiceStatusBadge(status: String?): Pair<String, BadgeTone> =
    when (status?.uppercase(Locale.ROOT)) {
        "DRAFT" -> "Bản nháp" to BadgeTone.Warning
        "ISSUED" -> "Đã phát hành" to BadgeTone.Success
        "VOID" -> "Đã hủy nháp" to BadgeTone.Danger
        else -> (status ?: "Không rõ") to BadgeTone.Neutral
    }

private fun formatClinVnd(raw: String?): String {
    if (raw.isNullOrBlank()) return "0 đ"
    return runCatching {
        val amount = BigDecimal(raw.trim())
        val symbols = DecimalFormatSymbols(Locale.forLanguageTag("vi-VN")).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        "${DecimalFormat("#,##0", symbols).format(amount)} đ"
    }.getOrElse { "$raw đ" }
}

internal fun formatClinDateTime(isoUtc: String?): String {
    if (isoUtc.isNullOrBlank()) return "—"
    return runCatching {
        OffsetDateTime.parse(isoUtc).atZoneSameInstant(CLIN_VN_ZONE).format(CLIN_DATE_TIME_FMT)
    }.getOrElse { isoUtc }
}
