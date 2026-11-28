package com.example.suncareclinic.ui.screens.encounter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.suncareclinic.data.dto.AllergenOptionDto
import com.example.suncareclinic.data.dto.EncounterHistoryResponseDto
import com.example.suncareclinic.data.dto.EncounterWorkItemDto
import com.example.suncareclinic.data.dto.PatientAllergyItemDto
import com.example.suncareclinic.data.dto.PatientHistoryItemDto
import com.example.suncareclinic.data.repository.DoctorEncounterRepository
import com.example.suncareclinic.ui.components.BadgeTone
import com.example.suncareclinic.ui.components.StatusBadge
import com.example.suncareclinic.ui.components.SuncareButton
import com.example.suncareclinic.ui.components.SuncareButtonVariant
import com.example.suncareclinic.ui.components.SuncareCard
import com.example.suncareclinic.ui.components.SuncareEmptyView
import com.example.suncareclinic.ui.components.SuncareErrorView
import com.example.suncareclinic.ui.components.SuncareInlineBanner
import com.example.suncareclinic.ui.components.SuncareLoadingView
import com.example.suncareclinic.ui.components.SuncareTextField
import com.example.suncareclinic.ui.components.SuncareScreenHeader
import com.example.suncareclinic.ui.components.SuncareSectionHeading
import com.example.suncareclinic.ui.components.suncarePageWidth
import com.example.suncareclinic.ui.components.SuncareScheduleCard
import com.example.suncareclinic.ui.theme.SuncareTheme
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ENC_VN_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
private val ENC_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val ENC_DATE_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")
private val ENC_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

enum class EncounterDetailTab(val label: String) {
    History("Tiền sử & Dị ứng"),
    VitalsNotes("Sinh hiệu & Ghi chú"),
    Diagnoses("Chẩn đoán"),
    Prescriptions("Đơn thuốc"),
    PreviousHistory("Lịch sử khám"),
    Invoices("Hóa đơn"),
}

@Composable
fun DoctorEncountersScreen(
    state: DoctorEncountersUiState,
    doctorEncounterRepository: DoctorEncounterRepository,
    onSessionExpired: () -> Unit,
    contentPadding: PaddingValues,
    onShiftDate: (Long) -> Unit,
    onSelectToday: () -> Unit,
    onSelectStatusFilter: (EncounterStatusFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLoadPage: (Int) -> Unit,
    onOpenEncounter: (Long) -> Unit,
    onCloseEncounter: () -> Unit,
    onReloadDetail: () -> Unit,
    onStartEncounter: () -> Unit,
    onCompleteEncounter: () -> Unit,
    onOpenAddHistory: () -> Unit,
    onOpenEditHistory: (PatientHistoryItemDto) -> Unit,
    onUpdateHistoryForm: ((HistoryFormState) -> HistoryFormState) -> Unit,
    onCloseHistoryForm: () -> Unit,
    onSubmitHistoryForm: () -> Unit,
    onOpenAddAllergy: () -> Unit,
    onOpenEditAllergy: (PatientAllergyItemDto) -> Unit,
    onUpdateAllergyForm: ((AllergyFormState) -> AllergyFormState) -> Unit,
    onCloseAllergyForm: () -> Unit,
    onSubmitAllergyForm: () -> Unit,
) {
    if (state.selectedEncounterId == null) {
        EncounterWorklistView(
            state = state,
            contentPadding = contentPadding,
            onShiftDate = onShiftDate,
            onSelectToday = onSelectToday,
            onSelectStatusFilter = onSelectStatusFilter,
            onSearchQueryChange = onSearchQueryChange,
            onLoadPage = onLoadPage,
            onOpenEncounter = onOpenEncounter,
        )
    } else {
        EncounterDetailView(
            state = state,
            encounterId = state.selectedEncounterId,
            doctorEncounterRepository = doctorEncounterRepository,
            onSessionExpired = onSessionExpired,
            contentPadding = contentPadding,
            onBack = onCloseEncounter,
            onReload = onReloadDetail,
            onStartEncounter = onStartEncounter,
            onCompleteEncounter = onCompleteEncounter,
            onOpenAddHistory = onOpenAddHistory,
            onOpenEditHistory = onOpenEditHistory,
            onUpdateHistoryForm = onUpdateHistoryForm,
            onCloseHistoryForm = onCloseHistoryForm,
            onSubmitHistoryForm = onSubmitHistoryForm,
            onOpenAddAllergy = onOpenAddAllergy,
            onOpenEditAllergy = onOpenEditAllergy,
            onUpdateAllergyForm = onUpdateAllergyForm,
            onCloseAllergyForm = onCloseAllergyForm,
            onSubmitAllergyForm = onSubmitAllergyForm,
        )
    }
}

@Composable
private fun EncounterWorklistView(
    state: DoctorEncountersUiState,
    contentPadding: PaddingValues,
    onShiftDate: (Long) -> Unit,
    onSelectToday: () -> Unit,
    onSelectStatusFilter: (EncounterStatusFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLoadPage: (Int) -> Unit,
    onOpenEncounter: (Long) -> Unit,
) {
    val colors = SuncareTheme.colors

    LazyColumn(
        modifier = Modifier.suncarePageWidth(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SuncareScreenHeader(
                title = "Lượt khám phụ trách",
                description = "Tìm bệnh nhân theo ngày và trạng thái khám.",
                icon = Icons.Outlined.MedicalServices,
                action = {
                    TextButton(onClick = { onLoadPage(state.page) }, enabled = !state.worklistLoading) {
                        Text(if (state.worklistLoading) "Đang tải…" else "Làm mới", color = colors.brandText)
                    }
                },
            )
        }

        item {
            SuncareCard(contentPadding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SuncareButton(
                        text = "‹ Trước",
                        onClick = { onShiftDate(-1) },
                        variant = SuncareButtonVariant.Outline,
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onSelectToday),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.border),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = formatEncIsoDate(state.selectedDate),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Chạm về hôm nay",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.muted,
                            )
                        }
                    }
                    SuncareButton(
                        text = "Sau ›",
                        onClick = { onShiftDate(1) },
                        variant = SuncareButtonVariant.Outline,
                    )
                }
            }
        }

        item {
            SuncareTextField(
                label = "Tìm bệnh nhân / mã lượt khám",
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Nhập tên bệnh nhân, mã BN hoặc mã lượt khám...",
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EncounterStatusFilter.entries) { filter ->
                    FilterChip(
                        selected = state.statusFilter == filter,
                        onClick = { onSelectStatusFilter(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.warningSoft,
                            selectedLabelColor = colors.ink,
                            containerColor = colors.surface,
                            labelColor = colors.muted,
                        ),
                    )
                }
            }
        }

        when {
            state.worklistLoading && state.items.isEmpty() -> {
                item {
                    SuncareLoadingView(message = "Đang tải danh sách lượt khám...")
                }
            }

            !state.worklistError.isNullOrBlank() && state.items.isEmpty() -> {
                item {
                    SuncareErrorView(
                        title = "Không tải được danh sách lượt khám",
                        message = state.worklistError,
                        onRetry = { onLoadPage(state.page) },
                    )
                }
            }

            state.items.isEmpty() -> {
                item {
                    SuncareEmptyView(
                        title = "Không có lượt khám trong ngày ${formatEncIsoDate(state.selectedDate)}",
                        description = "Chuyển ngày khác hoặc chọn bộ lọc \"Tất cả\" để kiểm tra các lượt khám được phân công.",
                        actionLabel = "Về hôm nay",
                        onAction = onSelectToday,
                    )
                }
            }

            else -> {
                if (!state.worklistError.isNullOrBlank()) {
                    item {
                        SuncareInlineBanner(message = state.worklistError, tone = BadgeTone.Danger)
                    }
                }
                item {
                    Text(
                        text = "Tổng ${state.totalElements} lượt khám · Trang ${state.page}/${maxOf(state.totalPages, 1)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                }
                items(items = state.items, key = { it.encounterId }) { item ->
                    EncounterWorkItemCard(
                        item = item,
                        onClick = { onOpenEncounter(item.encounterId) },
                    )
                }
                if (state.totalPages > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            SuncareButton(
                                text = "Trang trước",
                                onClick = { onLoadPage(state.page - 1) },
                                enabled = state.page > 1 && !state.worklistLoading,
                                variant = SuncareButtonVariant.Outline,
                            )
                            SuncareButton(
                                text = "Trang sau",
                                onClick = { onLoadPage(state.page + 1) },
                                enabled = state.page < state.totalPages && !state.worklistLoading,
                                variant = SuncareButtonVariant.Outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EncounterWorkItemCard(
    item: EncounterWorkItemDto,
    onClick: () -> Unit,
) {
    SuncareScheduleCard(
        title = listOfNotNull(item.patientName?.ifBlank { null }, item.patientCode?.ifBlank { null })
            .joinToString(" · ").ifBlank { "Bệnh nhân" },
        subtitle = listOfNotNull(item.specialtyName?.ifBlank { null }, item.roomName?.ifBlank { null })
            .joinToString(" · ").ifBlank { "Chưa cập nhật chuyên khoa và phòng" },
        code = listOfNotNull(item.encounterCode, item.appointmentCode).joinToString(" · "),
        schedule = formatEncTimeRange(item.startsAt, item.endsAt),
        status = encounterStatusUi(item.status),
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncounterDetailView(
    state: DoctorEncountersUiState,
    encounterId: Long,
    doctorEncounterRepository: DoctorEncounterRepository,
    onSessionExpired: () -> Unit,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onStartEncounter: () -> Unit,
    onCompleteEncounter: () -> Unit,
    onOpenAddHistory: () -> Unit,
    onOpenEditHistory: (PatientHistoryItemDto) -> Unit,
    onUpdateHistoryForm: ((HistoryFormState) -> HistoryFormState) -> Unit,
    onCloseHistoryForm: () -> Unit,
    onSubmitHistoryForm: () -> Unit,
    onOpenAddAllergy: () -> Unit,
    onOpenEditAllergy: (PatientAllergyItemDto) -> Unit,
    onUpdateAllergyForm: ((AllergyFormState) -> AllergyFormState) -> Unit,
    onCloseAllergyForm: () -> Unit,
    onSubmitAllergyForm: () -> Unit,
) {
    val colors = SuncareTheme.colors
    var showStartConfirm by rememberSaveable { mutableStateOf(false) }
    var showCompletionConfirm by rememberSaveable(encounterId) { mutableStateOf(false) }
    var activeTabName by rememberSaveable(encounterId) { mutableStateOf(EncounterDetailTab.History.name) }
    val activeTab = EncounterDetailTab.entries.firstOrNull { it.name == activeTabName } ?: EncounterDetailTab.History

    val clinicalViewModel: EncounterClinicalViewModel = viewModel(
        key = "encounter_clinical_$encounterId",
        factory = EncounterClinicalViewModel.Factory(
            encounterId = encounterId,
            repository = doctorEncounterRepository,
            onSessionExpired = onSessionExpired,
        ),
    )
    val clinicalState by clinicalViewModel.uiState.collectAsState()
    val encounterStatus = state.detail?.encounter?.status
    val displayedClinicalState = if (encounterStatus == "COMPLETED" && clinicalState.clinical?.editable == true) {
        clinicalState.copy(clinical = clinicalState.clinical?.copy(editable = false))
    } else clinicalState
    val hasUnfinishedEdits = clinicalState.notesDraft.dirty || clinicalState.notesDraft.saving ||
        clinicalState.vitalForm != null || clinicalState.diagnosisForm != null ||
        clinicalState.invoiceActionBusy != null || state.historyForm != null || state.allergyForm != null
    val completionLocked = state.completionPhase != EncounterCompletionPhase.Idle

    LaunchedEffect(encounterStatus) {
        if (encounterStatus == "COMPLETED" && clinicalState.clinical?.editable == true) {
            clinicalViewModel.loadClinical(forceReplaceNotes = true)
        }
    }

    if (activeTab == EncounterDetailTab.Prescriptions) {
        DisposableEffect(clinicalViewModel) {
            clinicalViewModel.loadPrescriptions()
            onDispose { clinicalViewModel.clearPrescriptions() }
        }
    }

    LaunchedEffect(activeTab, encounterId) {
        when (activeTab) {
            EncounterDetailTab.VitalsNotes, EncounterDetailTab.Diagnoses -> clinicalViewModel.ensureClinicalLoaded()
            EncounterDetailTab.PreviousHistory -> clinicalViewModel.ensureHistoryLoaded()
            EncounterDetailTab.Invoices -> clinicalViewModel.ensureInvoicesLoaded()
            EncounterDetailTab.History, EncounterDetailTab.Prescriptions -> Unit
        }
    }

    LazyColumn(
        modifier = Modifier.suncarePageWidth(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, enabled = !completionLocked) {
                    Text(text = "← Danh sách lượt khám", color = colors.brandText)
                }
                TextButton(onClick = onReload, enabled = !state.detailLoading && !completionLocked) {
                    Text(
                        text = if (state.detailLoading) "Đang tải..." else "Tải lại",
                        color = colors.brandText,
                    )
                }
            }
        }

        if (!state.actionBanner.isNullOrBlank()) {
            item {
                SuncareInlineBanner(message = state.actionBanner, tone = BadgeTone.Success)
            }
        }
        if (!state.actionError.isNullOrBlank()) {
            item {
                SuncareInlineBanner(message = state.actionError, tone = BadgeTone.Danger)
            }
        }

        when {
            state.detailLoading && state.detail == null -> {
                item {
                    SuncareLoadingView(message = "Đang tải chi tiết lượt khám...")
                }
            }

            !state.detailError.isNullOrBlank() && state.detail == null -> {
                item {
                    SuncareErrorView(
                        title = "Không tải được chi tiết lượt khám",
                        message = state.detailError,
                        onRetry = onReload,
                    )
                }
            }

            state.detail != null -> {
                val detail: EncounterHistoryResponseDto = state.detail
                val enc = detail.encounter
                val statusBadge = encounterStatusUi(enc.status)

                item {
                    SuncareScreenHeader(
                        title = enc.patientName?.takeIf { it.isNotBlank() } ?: "Chi tiết lượt khám",
                        description = listOfNotNull(
                            enc.encounterCode,
                            enc.patientCode?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        icon = Icons.Outlined.PersonOutline,
                        action = { StatusBadge(label = statusBadge.first, tone = statusBadge.second) },
                    )
                }

                item {
                    SuncareCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SuncareSectionHeading(
                                title = "Thông tin lượt khám",
                                description = listOfNotNull(
                                    enc.specialtyName?.takeIf { it.isNotBlank() },
                                    enc.roomName?.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                            )
                            Text(
                                text = formatEncTimeRange(enc.startsAt, enc.endsAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.brandText,
                            )
                            if (enc.status.equals("PENDING", ignoreCase = true)) {
                                SuncareButton(
                                    text = "Xác nhận có mặt & bắt đầu khám",
                                    onClick = { showStartConfirm = true },
                                    loading = state.startBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (state.canCompleteEncounter) {
                                SuncareButton(
                                    text = "Hoàn tất lượt khám",
                                    onClick = { showCompletionConfirm = true },
                                    enabled = !hasUnfinishedEdits && !state.detailLoading && !completionLocked,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (hasUnfinishedEdits) {
                                    Text(
                                        text = "Lưu hoặc bỏ phần đang chỉnh sửa trước khi hoàn tất.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.muted,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EncounterDetailTab.entries) { tab ->
                            FilterChip(
                                selected = activeTab == tab,
                                onClick = { activeTabName = tab.name },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.warningSoft,
                                    selectedLabelColor = colors.ink,
                                    containerColor = colors.surface,
                                    labelColor = colors.muted,
                                ),
                            )
                        }
                    }
                }

                if (activeTab == EncounterDetailTab.VitalsNotes) {
                    item {
                        VitalsAndNotesSection(
                            state = displayedClinicalState,
                            onReloadClinical = { clinicalViewModel.loadClinical(forceReplaceNotes = true) },
                            onOpenRecordVital = clinicalViewModel::openRecordVital,
                            onUpdateNotesDraft = clinicalViewModel::updateNotesDraft,
                            onSaveNotes = clinicalViewModel::saveNotes,
                        )
                    }
                } else if (activeTab == EncounterDetailTab.Diagnoses) {
                    item {
                        DiagnosesSection(
                            state = displayedClinicalState,
                            onReloadClinical = { clinicalViewModel.loadClinical(forceReplaceNotes = false) },
                            onOpenAddDiagnosis = clinicalViewModel::openAddDiagnosis,
                            onOpenEditDiagnosis = clinicalViewModel::openEditDiagnosis,
                        )
                    }
                } else if (activeTab == EncounterDetailTab.Prescriptions) {
                    encounterPrescriptions(clinicalState.prescriptions, clinicalViewModel::loadPrescriptions)
                } else if (activeTab == EncounterDetailTab.PreviousHistory) {
                    item {
                        PreviousEncountersSection(
                            state = clinicalState,
                            onLoadPage = clinicalViewModel::loadPreviousEncounters,
                            onOpenDetail = clinicalViewModel::openPreviousEncounterDetail,
                        )
                    }
                } else if (activeTab == EncounterDetailTab.Invoices) {
                    val invoiceWritable = enc.status.equals("IN_PROGRESS", ignoreCase = true) ||
                        enc.status.equals("WAITING_CLS", ignoreCase = true)
                    item {
                        EncounterInvoicesSection(
                            state = clinicalState,
                            writable = invoiceWritable,
                            onReload = clinicalViewModel::loadInvoices,
                            onCreateDraft = clinicalViewModel::createExaminationInvoice,
                            onToggleConfirmIssue = clinicalViewModel::toggleConfirmIssue,
                            onIssueInvoice = clinicalViewModel::issueInvoice,
                        )
                    }
                } else {
                if (detail.snapshot != null) {
                    item {
                        SuncareCard {
                            SuncareSectionHeading(
                                title = "Tiền sử và dị ứng lúc bắt đầu khám",
                                description = "Ghi nhận lúc ${formatEncDateTime(detail.snapshot.recordedAt)}",
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tiền sử bệnh:",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.ink,
                            )
                            Text(
                                text = detail.snapshot.historyText?.ifBlank { null } ?: "Không có tiền sử đang hoạt động.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Dị ứng:",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.ink,
                            )
                            Text(
                                text = detail.snapshot.allergyText?.ifBlank { null } ?: "Không có dị ứng đang hoạt động.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                        }
                    }
                }

                item {
                    SuncareCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Tiền sử bệnh (${detail.histories.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (detail.editable) {
                                TextButton(onClick = onOpenAddHistory) {
                                    Text(text = "+ Thêm tiền sử", color = colors.brandText)
                                }
                            }
                        }
                        if (detail.histories.isEmpty()) {
                            Text(
                                text = "Chưa có ghi nhận tiền sử bệnh.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                detail.histories.forEach { h ->
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
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = historyTypeLabel(h.historyType),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = colors.brandText,
                                                )
                                                StatusBadge(
                                                    label = if (h.isActive) "Đang hiệu lực" else "Ngừng",
                                                    tone = if (h.isActive) BadgeTone.Success else BadgeTone.Neutral,
                                                )
                                            }
                                            Text(
                                                text = h.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.ink,
                                            )
                                            if (detail.editable) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End,
                                                ) {
                                                    TextButton(onClick = { onOpenEditHistory(h) }) {
                                                        Text("Chỉnh sửa", color = colors.brandText)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SuncareCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Dị ứng (${detail.allergies.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (detail.editable) {
                                TextButton(onClick = onOpenAddAllergy) {
                                    Text(text = "+ Thêm dị ứng", color = colors.brandText)
                                }
                            }
                        }
                        if (detail.allergies.isEmpty()) {
                            Text(
                                text = "Chưa có ghi nhận dị ứng.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                detail.allergies.forEach { a ->
                                    val sevBadge = allergySeverityUi(a.severity)
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
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = a.substance?.ifBlank { null }
                                                        ?: a.otherNote?.ifBlank { null }
                                                        ?: "Dị nguyên khác",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = colors.ink,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                StatusBadge(label = sevBadge.first, tone = sevBadge.second)
                                            }
                                            if (!a.reaction.isNullOrBlank()) {
                                                Text(
                                                    text = "Phản ứng: ${a.reaction}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colors.ink,
                                                )
                                            }
                                            if (!a.isActive) {
                                                Text(
                                                    text = "Trạng thái: Ngừng hiệu lực",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = colors.muted,
                                                )
                                            }
                                            if (detail.editable) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End,
                                                ) {
                                                    TextButton(onClick = { onOpenEditAllergy(a) }) {
                                                        Text("Chỉnh sửa", color = colors.brandText)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    clinicalState.vitalForm?.let { form ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = clinicalViewModel::closeVitalForm,
            sheetState = sheetState,
            containerColor = colors.canvas,
        ) {
            VitalFormSheetContent(
                form = form,
                onUpdate = clinicalViewModel::updateVitalForm,
                onSubmit = clinicalViewModel::submitVitalForm,
                onClose = clinicalViewModel::closeVitalForm,
            )
        }
    }

    clinicalState.diagnosisForm?.let { form ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = clinicalViewModel::closeDiagnosisForm,
            sheetState = sheetState,
            containerColor = colors.canvas,
        ) {
            DiagnosisFormSheetContent(
                form = form,
                onUpdate = clinicalViewModel::updateDiagnosisForm,
                onIcdQueryChange = clinicalViewModel::updateIcdQuery,
                onToggleIcdGroups = clinicalViewModel::toggleIcdGroupsOnly,
                onLoadIcdPage = clinicalViewModel::loadIcdPage,
                onSelectConcept = clinicalViewModel::selectDiagnosisConcept,
                onClearConcept = clinicalViewModel::clearDiagnosisConcept,
                onSubmit = clinicalViewModel::submitDiagnosisForm,
                onReloadOnConflict = {
                    clinicalViewModel.closeDiagnosisForm()
                    clinicalViewModel.loadClinical(forceReplaceNotes = false)
                },
                onClose = clinicalViewModel::closeDiagnosisForm,
            )
        }
    }

    if (clinicalState.selectedPreviousId != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = clinicalViewModel::closePreviousDetail,
            sheetState = sheetState,
            containerColor = colors.canvas,
        ) {
            PreviousEncounterDetailSheetContent(
                loading = clinicalState.previousDetailLoading,
                detail = clinicalState.previousDetail,
                error = clinicalState.previousDetailError,
                onRetry = clinicalViewModel::retryPreviousDetail,
                onClose = clinicalViewModel::closePreviousDetail,
            )
        }
    }

    if (showCompletionConfirm || completionLocked) {
        val sending = state.completionPhase == EncounterCompletionPhase.Sending
        val verifying = state.completionPhase == EncounterCompletionPhase.NeedsVerification
        val encounter = state.detail?.encounter
        AlertDialog(
            onDismissRequest = { if (!completionLocked) showCompletionConfirm = false },
            properties = DialogProperties(
                dismissOnBackPress = !completionLocked,
                dismissOnClickOutside = !completionLocked,
            ),
            title = { Text(if (verifying) "Xác minh hoàn tất lượt khám" else "Hoàn tất lượt khám?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (encounter != null) {
                        SuncareSectionHeading(
                            title = encounter.patientName?.ifBlank { null } ?: "Lượt khám",
                            description = encounter.encounterCode,
                        )
                    }
                    Text(
                        when {
                            sending -> "Đang kiểm tra và xác nhận với máy chủ…"
                            verifying -> "Chưa xác minh được kết quả. Hồ sơ tạm khóa. Thử lại để nhận trạng thái từ máy chủ."
                            else -> "Sau khi hoàn tất, hồ sơ chuyển sang chỉ đọc. Máy chủ sẽ kiểm tra điều kiện hoàn tất."
                        },
                    )
                }
            },
            confirmButton = {
                SuncareButton(
                    text = if (verifying) "Thử lại xác minh" else "Xác nhận hoàn tất",
                    onClick = {
                        showCompletionConfirm = false
                        onCompleteEncounter()
                    },
                    enabled = verifying || (state.canCompleteEncounter && !hasUnfinishedEdits && !state.detailLoading),
                    loading = sending,
                )
            },
            dismissButton = {
                if (!completionLocked) {
                    TextButton(onClick = { showCompletionConfirm = false }) {
                        Text("Quay lại", color = colors.ink)
                    }
                }
            },
            containerColor = colors.surface,
        )
    }

    if (showStartConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.startBusy) showStartConfirm = false },
            title = {
                Text(
                    text = "Xác nhận bắt đầu lượt khám?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = "Hệ thống sẽ xác nhận bệnh nhân có mặt, chuyển lượt khám sang Đang khám và chốt một lần snapshot tiền sử bệnh & dị ứng đang hiệu lực.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            },
            confirmButton = {
                SuncareButton(
                    text = "Bắt đầu khám",
                    onClick = {
                        showStartConfirm = false
                        onStartEncounter()
                    },
                    loading = state.startBusy,
                )
            },
            dismissButton = {
                TextButton(onClick = { showStartConfirm = false }) {
                    Text("Hủy", color = colors.ink)
                }
            },
            containerColor = colors.surface,
        )
    }

    state.historyForm?.let { form ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onCloseHistoryForm,
            sheetState = sheetState,
            containerColor = colors.canvas,
        ) {
            HistoryFormSheet(
                form = form,
                onUpdate = onUpdateHistoryForm,
                onSubmit = onSubmitHistoryForm,
                onReloadOnConflict = {
                    onCloseHistoryForm()
                    onReload()
                },
                onClose = onCloseHistoryForm,
            )
        }
    }

    state.allergyForm?.let { form ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onCloseAllergyForm,
            sheetState = sheetState,
            containerColor = colors.canvas,
        ) {
            AllergyFormSheet(
                form = form,
                allergenOptions = state.detail?.allergenOptions.orEmpty(),
                onUpdate = onUpdateAllergyForm,
                onSubmit = onSubmitAllergyForm,
                onReloadOnConflict = {
                    onCloseAllergyForm()
                    onReload()
                },
                onClose = onCloseAllergyForm,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryFormSheet(
    form: HistoryFormState,
    onUpdate: ((HistoryFormState) -> HistoryFormState) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (form.editingId == null) "Thêm tiền sử bệnh" else "Cập nhật tiền sử bệnh",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )

        Text(text = "Nhóm tiền sử", style = MaterialTheme.typography.labelLarge, color = colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryTypeOption.entries.forEach { option ->
                FilterChip(
                    selected = form.historyType == option.code,
                    onClick = { onUpdate { it.copy(historyType = option.code) } },
                    label = { Text(option.label) },
                )
            }
        }

        SuncareTextField(
            label = "Mô tả chi tiết *",
            value = form.description,
            onValueChange = { value -> onUpdate { it.copy(description = value) } },
            placeholder = "Ví dụ: Tăng huyết áp từ năm 2022, đang dùng thuốc đều...",
            singleLine = false,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Đang hiệu lực", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            Switch(
                checked = form.isActive,
                onCheckedChange = { checked -> onUpdate { it.copy(isActive = checked) } },
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
                text = "Lưu",
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
private fun AllergyFormSheet(
    form: AllergyFormState,
    allergenOptions: List<AllergenOptionDto>,
    onUpdate: ((AllergyFormState) -> AllergyFormState) -> Unit,
    onSubmit: () -> Unit,
    onReloadOnConflict: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = SuncareTheme.colors
    val activeOptions = allergenOptions.filter { it.isActive || it.id == form.allergenId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (form.editingId == null) "Thêm thông tin dị ứng" else "Cập nhật thông tin dị ứng",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )

        Text(text = "Chọn dị nguyên danh mục", style = MaterialTheme.typography.labelLarge, color = colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = form.allergenId == null,
                onClick = { onUpdate { it.copy(allergenId = null) } },
                label = { Text("Khác (tự ghi)") },
            )
            activeOptions.forEach { option ->
                FilterChip(
                    selected = form.allergenId == option.id,
                    onClick = { onUpdate { it.copy(allergenId = option.id) } },
                    label = { Text(option.name) },
                )
            }
        }

        SuncareTextField(
            label = if (form.allergenId == null) "Tên dị nguyên khác *" else "Ghi chú thêm về dị nguyên",
            value = form.otherNote,
            onValueChange = { value -> onUpdate { it.copy(otherNote = value) } },
            placeholder = "Ví dụ: Hải sản có vỏ, phấn hoa...",
        )

        SuncareTextField(
            label = "Biểu hiện / phản ứng",
            value = form.reaction,
            onValueChange = { value -> onUpdate { it.copy(reaction = value) } },
            placeholder = "Ví dụ: Nổi mề đay, khó thở nhẹ...",
            singleLine = false,
        )

        Text(text = "Mức độ", style = MaterialTheme.typography.labelLarge, color = colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AllergySeverityOption.entries.forEach { sev ->
                FilterChip(
                    selected = form.severity == sev.code,
                    onClick = { onUpdate { it.copy(severity = sev.code) } },
                    label = { Text(sev.label) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Đang hiệu lực", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            Switch(
                checked = form.isActive,
                onCheckedChange = { checked -> onUpdate { it.copy(isActive = checked) } },
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
                text = "Lưu",
                onClick = onSubmit,
                loading = form.saving,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun encounterStatusUi(status: String?): Pair<String, BadgeTone> =
    when (status?.uppercase(Locale.ROOT)) {
        "PENDING" -> "Chờ khám" to BadgeTone.Neutral
        "IN_PROGRESS" -> "Đang khám" to BadgeTone.Warning
        "WAITING_CLS" -> "Chờ CLS" to BadgeTone.Warning
        "COMPLETED" -> "Hoàn tất" to BadgeTone.Success
        "CANCELLED" -> "Đã hủy" to BadgeTone.Danger
        else -> (status ?: "Không rõ") to BadgeTone.Neutral
    }

private fun historyTypeLabel(code: String): String = when (code.uppercase(Locale.ROOT)) {
    "PERSONAL" -> "Bệnh bản thân"
    "FAMILY" -> "Tiền sử gia đình"
    "SURGERY" -> "Phẫu thuật / thủ thuật"
    else -> code
}

private fun allergySeverityUi(code: String): Pair<String, BadgeTone> = when (code.uppercase(Locale.ROOT)) {
    "MILD" -> "Nhẹ" to BadgeTone.Neutral
    "MODERATE" -> "Trung bình" to BadgeTone.Warning
    "SEVERE" -> "Nặng" to BadgeTone.Danger
    else -> "Chưa rõ" to BadgeTone.Neutral
}

private fun formatEncIsoDate(raw: String): String =
    runCatching { LocalDate.parse(raw).format(ENC_DATE_FMT) }.getOrElse { raw }

private fun formatEncDateTime(isoUtc: String?): String {
    if (isoUtc.isNullOrBlank()) return "—"
    return runCatching {
        OffsetDateTime.parse(isoUtc).atZoneSameInstant(ENC_VN_ZONE).format(ENC_DATE_TIME_FMT)
    }.getOrElse { isoUtc }
}

private fun formatEncTimeRange(startsAt: String?, endsAt: String?): String {
    if (startsAt.isNullOrBlank()) return "—"
    return runCatching {
        val startStr = OffsetDateTime.parse(startsAt).atZoneSameInstant(ENC_VN_ZONE).format(ENC_TIME_FMT)
        if (endsAt.isNullOrBlank()) startStr
        else "$startStr – ${OffsetDateTime.parse(endsAt).atZoneSameInstant(ENC_VN_ZONE).format(ENC_TIME_FMT)}"
    }.getOrElse { startsAt }
}
