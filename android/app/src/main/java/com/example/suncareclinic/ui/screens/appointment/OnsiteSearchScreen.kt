package com.example.suncareclinic.ui.screens.appointment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.suncareclinic.data.dto.OnsiteAppointmentBookingResponseDto
import com.example.suncareclinic.data.dto.OnsiteAvailabilityDto
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
import androidx.compose.material.icons.outlined.MedicalServices
import com.example.suncareclinic.ui.components.SuncareIconTile
import com.example.suncareclinic.ui.theme.SuncareTheme
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VI_LOCALE = Locale.forLanguageTag("vi-VN")
private val SHORT_DATE_FMT = DateTimeFormatter.ofPattern("EEE, dd/MM", VI_LOCALE)
private val FULL_DATE_FMT = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", VI_LOCALE)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnsiteSearchScreen(
    state: OnsiteSearchUiState,
    contentPadding: PaddingValues,
    onReloadOptions: () -> Unit,
    onSelectSpecialty: (Long?) -> Unit,
    onSelectShift: (Long?) -> Unit,
    onUpdateDateRange: (String, String) -> Unit,
    onApplyQuickPresetDays: (Long) -> Unit,
    onDoctorNameChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    onRetrySearch: () -> Unit,
    onToggleDoctor: (Long) -> Unit,
    onSelectDoctorDate: (String) -> Unit,
    onToggleSelectSession: (OnsiteAvailabilityDto) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAssignmentMode: (DoctorAssignmentMode) -> Unit,
    onSymptomsChange: (String) -> Unit,
    onConfirmBooking: () -> Unit,
    onStartNewSearch: () -> Unit,
) {
    val colors = SuncareTheme.colors
    var showDateRangeSheet by rememberSaveable { mutableStateOf(false) }
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .suncarePageWidth()
            .imePadding(),
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
                title = "Đặt lịch khám",
                description = "Chọn chuyên khoa, ngày và ca khám tại phòng khám.",
                icon = Icons.Outlined.Search,
            )
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusBadge(label = "Khám trực tiếp", tone = BadgeTone.Warning)
                StatusBadge(label = "Khám trực tuyến · Sắp mở", tone = BadgeTone.Neutral)
            }
        }

        // Nếu vừa đặt lịch thành công: hiện thẻ xác nhận nổi bật ngay phía trên
        val bookingResult = state.bookingResult
        if (state.bookingState == BookingState.Success && bookingResult != null) {
            item {
                BookingConfirmationCard(
                    result = bookingResult,
                    onStartNewSearch = onStartNewSearch,
                )
            }
            return@LazyColumn
        }

        if (state.optionsLoading && state.options == null) {
            item {
                SuncareLoadingView(message = "Đang tải chuyên khoa và ca khám...")
            }
            return@LazyColumn
        }

        if (state.optionsError != null && state.options == null) {
            item {
                SuncareErrorView(
                    title = "Không tải được bộ lọc",
                    message = state.optionsError,
                    onRetry = onReloadOptions,
                )
            }
            return@LazyColumn
        }

        val options = state.options ?: return@LazyColumn

        // Khối bộ lọc
        item {
            SuncareCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SuncareSectionHeading("Bộ lọc lịch khám")
                    if (state.filters.hasAnyCriteria()) {
                        TextButton(
                            onClick = onClearFilters,
                            enabled = !state.isBookingBusy,
                        ) {
                            Text(
                                text = "Xóa lọc",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.brandText,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 1. Chuyên khoa
                Text(
                    text = "Chuyên khoa",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.ink,
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (options.specialties.isEmpty()) {
                    Text(
                        text = "Chưa có chuyên khoa đang hoạt động.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.specialties.forEach { specialty ->
                            FilterChip(
                                selected = state.filters.specialtyId == specialty.id,
                                enabled = !state.isBookingBusy,
                                onClick = { onSelectSpecialty(specialty.id) },
                                label = { Text(specialty.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.warningSoft,
                                    selectedLabelColor = colors.ink,
                                ),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Khoảng ngày (mở ModalBottomSheet)
                Text(
                    text = "Khoảng ngày khám",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.ink,
                )
                Spacer(modifier = Modifier.height(6.dp))
                val dateSummary = when {
                    state.filters.dateFrom.isNotBlank() && state.filters.dateTo.isNotBlank() ->
                        "${formatShortDate(state.filters.dateFrom)} → ${formatShortDate(state.filters.dateTo)}"
                    state.filters.dateFrom.isNotBlank() -> "Từ ${formatShortDate(state.filters.dateFrom)}"
                    state.filters.dateTo.isNotBlank() -> "Đến ${formatShortDate(state.filters.dateTo)}"
                    else -> "Chọn khoảng ngày"
                }
                SuncareButton(
                    text = dateSummary,
                    onClick = { showDateRangeSheet = true },
                    variant = SuncareButtonVariant.Outline,
                    enabled = !state.isBookingBusy,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Ca khám
                Text(
                    text = "Ca khám",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.ink,
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    options.shifts.forEach { shift ->
                        FilterChip(
                            selected = state.filters.shiftId == shift.id,
                            enabled = !state.isBookingBusy,
                            onClick = { onSelectShift(shift.id) },
                            label = { Text("${shift.name} (${shift.startTime}–${shift.endTime})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.warningSoft,
                                selectedLabelColor = colors.ink,
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Tìm tên bác sĩ
                SuncareTextField(
                    label = "Tên bác sĩ (tùy chọn)",
                    value = state.filters.doctorName,
                    onValueChange = onDoctorNameChange,
                    placeholder = "Nhập tên bác sĩ cần tìm...",
                    enabled = !state.isBookingBusy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
        }

        if (!state.validationError.isNullOrBlank()) {
            item {
                SuncareInlineBanner(message = state.validationError, tone = BadgeTone.Danger)
            }
        }

        // Thẻ đặt lịch cho buổi đang chọn (A05)
        val selected = state.selectedSession
        if (selected != null) {
            item {
                BookingDraftCard(
                    session = selected,
                    state = state,
                    onClear = onClearSelection,
                    onSelectAssignmentMode = onSelectAssignmentMode,
                    onSymptomsChange = onSymptomsChange,
                    onRequestConfirm = { showConfirmDialog = true },
                    onRetrySubmit = onConfirmBooking,
                    onRefreshSearch = onStartNewSearch,
                )
            }
        }

        // Khu vực kết quả tra cứu
        when (state.searchState) {
            SearchState.Idle -> {
                item {
                    SuncareEmptyView(
                        title = "Chọn ít nhất một tiêu chí để tra cứu",
                        description = "Chọn chuyên khoa, khoảng ngày, ca khám hoặc nhập tên bác sĩ. Hệ thống chỉ hiển thị lịch đã công bố, còn chỗ và có giá hiệu lực.",
                        actionLabel = "Xem lịch 7 ngày tới",
                        onAction = { onApplyQuickPresetDays(7) },
                    )
                }
            }

            SearchState.Loading -> {
                item {
                    SuncareLoadingView(message = "Đang tìm lịch khám phù hợp...")
                }
            }

            SearchState.Error -> {
                item {
                    SuncareErrorView(
                        title = "Không tra cứu được lịch khám",
                        message = state.searchError ?: "Không kết nối được máy chủ.",
                        onRetry = onRetrySearch,
                    )
                }
            }

            SearchState.Ready -> {
                if (state.doctorGroups.isEmpty()) {
                    item {
                        SuncareEmptyView(
                            title = "Không có lịch khám phù hợp",
                            description = "Thử mở rộng khoảng ngày hoặc bỏ bớt tiêu chí lọc (lưu ý phòng khám làm việc Thứ Hai – Thứ Bảy).",
                            actionLabel = "Xóa bộ lọc",
                            onAction = onClearFilters,
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Bác sĩ có lịch trống",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                            )
                            StatusBadge(
                                label = "${state.doctorGroups.size} bác sĩ · ${state.totalMatchingSessions} ca",
                                tone = BadgeTone.Warning,
                            )
                        }
                    }

                    items(
                        items = state.doctorGroups,
                        key = { it.doctorId },
                    ) { group ->
                        DoctorAvailabilityCard(
                            group = group,
                            expanded = state.expandedDoctorId == group.doctorId,
                            selectedDate = if (state.expandedDoctorId == group.doctorId) state.expandedDate else null,
                            selectedSession = state.selectedSession,
                            busy = state.isBookingBusy,
                            onToggleExpand = { onToggleDoctor(group.doctorId) },
                            onSelectDate = onSelectDoctorDate,
                            onToggleSession = onToggleSelectSession,
                        )
                    }
                }
            }
        }
    }

    if (showDateRangeSheet) {
        DateRangeBottomSheet(
            initialFrom = state.filters.dateFrom,
            initialTo = state.filters.dateTo,
            onDismiss = { showDateRangeSheet = false },
            onApply = { from, to ->
                showDateRangeSheet = false
                onUpdateDateRange(from, to)
            },
            onPreset = { days ->
                showDateRangeSheet = false
                onApplyQuickPresetDays(days)
            },
            onClear = {
                showDateRangeSheet = false
                onUpdateDateRange("", "")
            },
        )
    }

    val confirmSession = state.selectedSession
    if (showConfirmDialog && confirmSession != null) {
        val autoMode = state.assignmentMode == DoctorAssignmentMode.AutoAssign && !state.autoAssignDisabled
        AlertDialog(
            onDismissRequest = { if (!state.isBookingBusy) showConfirmDialog = false },
            title = {
                Text(
                    text = "Xác nhận đặt lịch khám",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Chuyên khoa: ${confirmSession.specialtyName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                    )
                    Text(
                        text = "Thời gian: ${formatFullDate(confirmSession.date)} · ${confirmSession.shiftName} (${confirmSession.startTime}–${confirmSession.endTime})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                    )
                    Text(
                        text = if (autoMode) {
                            "Bác sĩ: Hệ thống tự phân cân bằng theo lượt đặt/sức chứa"
                        } else {
                            "Bác sĩ: ${confirmSession.doctorName} · Phòng ${confirmSession.roomCode}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                    )
                    Text(
                        text = "Phí dự kiến: ${formatFeeRange(confirmSession.minimumFee, confirmSession.maximumFee)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.brandText,
                    )
                    if (state.symptoms.isNotBlank()) {
                        Text(
                            text = "Triệu chứng: ${state.symptoms.trim()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lưu ý: Lịch khám trực tiếp sau khi đặt không hỗ trợ tự đổi hoặc hủy trực tuyến.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                }
            },
            confirmButton = {
                SuncareButton(
                    text = "Xác nhận đặt lịch",
                    onClick = {
                        showConfirmDialog = false
                        onConfirmBooking()
                    },
                    variant = SuncareButtonVariant.Primary,
                )
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Quay lại", color = colors.ink)
                }
            },
            containerColor = colors.surface,
        )
    }
}

@Composable
private fun BookingDraftCard(
    session: OnsiteAvailabilityDto,
    state: OnsiteSearchUiState,
    onClear: () -> Unit,
    onSelectAssignmentMode: (DoctorAssignmentMode) -> Unit,
    onSymptomsChange: (String) -> Unit,
    onRequestConfirm: () -> Unit,
    onRetrySubmit: () -> Unit,
    onRefreshSearch: () -> Unit,
) {
    val colors = SuncareTheme.colors
    val autoMode = state.assignmentMode == DoctorAssignmentMode.AutoAssign && !state.autoAssignDisabled
    val isConflict = state.bookingErrorStatus == 409
    val canSafeRetry = state.bookingState == BookingState.Error && (state.bookingErrorStatus == null || state.bookingErrorStatus >= 500)

    SuncareCard(highlighted = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SuncareSectionHeading(
                title = "Buổi khám đã chọn",
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear, enabled = !state.isBookingBusy) {
                Text("Bỏ chọn", color = colors.brandText, style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${session.specialtyName} · ${formatFullDate(session.date)}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.ink,
        )
        Text(
            text = "${session.shiftName} (${session.startTime}–${session.endTime})",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )
        Text(
            text = if (autoMode) {
                "Khoa & phòng sẽ được chốt theo bác sĩ hệ thống phân."
            } else {
                "${session.departmentName} · Phòng ${session.roomCode} (${session.roomName})"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Phí khám dự kiến: ${formatFeeRange(session.minimumFee, session.maximumFee)}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.brandText,
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.warningBorder)
        Spacer(modifier = Modifier.height(12.dp))

        // 2 thẻ chọn chế độ bác sĩ
        Text(
            text = "Chế độ chọn bác sĩ",
            style = MaterialTheme.typography.labelLarge,
            color = colors.ink,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssignmentOptionCard(
                title = "Giữ bác sĩ của ca: ${session.doctorName}",
                description = "Đặt đúng bác sĩ và phòng ${session.roomCode} bạn vừa chọn.",
                selected = !autoMode,
                enabled = !state.isBookingBusy,
                onClick = { onSelectAssignmentMode(DoctorAssignmentMode.SelectedDoctor) },
            )
            AssignmentOptionCard(
                title = "Để hệ thống tự phân cân bằng",
                description = if (state.autoAssignDisabled) {
                    "Đang khóa vì bạn đang lọc theo tên bác sĩ. Xóa ô tên bác sĩ nếu muốn hệ thống tự phân."
                } else {
                    "Hệ thống chọn bác sĩ có tỷ lệ lượt đặt/sức chứa thấp nhất trong cùng chuyên khoa, ngày và ca."
                },
                selected = autoMode,
                enabled = !state.isBookingBusy && !state.autoAssignDisabled,
                onClick = { onSelectAssignmentMode(DoctorAssignmentMode.AutoAssign) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        SuncareTextField(
            label = "Triệu chứng / lý do khám (tùy chọn)",
            value = state.symptoms,
            onValueChange = onSymptomsChange,
            placeholder = "Mô tả ngắn triệu chứng ban đầu (tối đa 2.000 ký tự)...",
            helperText = "${state.symptoms.trim().length} / ${OnsiteSearchViewModel.MAX_SYMPTOMS_LENGTH} ký tự",
            errorText = state.symptomsError,
            enabled = !state.isBookingBusy,
            singleLine = false,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        )

        if (!state.bookingError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            SuncareInlineBanner(message = state.bookingError, tone = BadgeTone.Danger)
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (canSafeRetry) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SuncareButton(
                    text = "Thử gửi lại (chống đặt trùng)",
                    onClick = onRetrySubmit,
                    variant = SuncareButtonVariant.Primary,
                    loading = state.isBookingBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                SuncareButton(
                    text = "Tìm lịch khác",
                    onClick = onRefreshSearch,
                    variant = SuncareButtonVariant.Outline,
                    enabled = !state.isBookingBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (isConflict) {
            SuncareButton(
                text = "Tải lại lịch còn chỗ",
                onClick = onRefreshSearch,
                variant = SuncareButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SuncareButton(
                text = "Đặt lịch khám",
                onClick = onRequestConfirm,
                variant = SuncareButtonVariant.Primary,
                enabled = state.symptomsError == null,
                loading = state.isBookingBusy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AssignmentOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = SuncareTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.surface else colors.canvas.copy(alpha = if (enabled) 1f else 0.6f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colors.brandText else colors.border,
        ),
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
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) colors.ink else colors.muted,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    StatusBadge(label = "Đã chọn", tone = BadgeTone.Success)
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun BookingConfirmationCard(
    result: OnsiteAppointmentBookingResponseDto,
    onStartNewSearch: () -> Unit,
) {
    val colors = SuncareTheme.colors
    SuncareCard(contentPadding = 20.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SuncareIconTile(Icons.Outlined.EventAvailable)
            SuncareSectionHeading(
                title = if (result.replayed) "Đã khôi phục lịch hẹn" else "Đặt lịch thành công",
                description = "Đến đúng phòng và ca khám. Thanh toán tại quầy thu ngân.",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = colors.border)
        Spacer(modifier = Modifier.height(12.dp))

        ConfirmationRow("Mã lịch hẹn", result.appointmentCode)
        ConfirmationRow("Mã lượt khám", result.encounterCode)
        ConfirmationRow("Bác sĩ phụ trách", result.doctorName)
        ConfirmationRow("Khoa & Phòng", "${result.departmentName} · Phòng ${result.roomCode} (${result.roomName})")
        ConfirmationRow("Chuyên khoa", result.specialtyName)
        ConfirmationRow("Ngày & Ca khám", "${formatFullDate(result.date)} · ${result.shiftName}")
        ConfirmationRow("Phí khám đã chốt", formatVnd(result.examinationAmount))
        ConfirmationRow("Trạng thái", "${result.appointmentStatus} / ${result.encounterStatus}")

        Spacer(modifier = Modifier.height(16.dp))
        SuncareButton(
            text = "Tìm lịch khám khác",
            onClick = onStartNewSearch,
            variant = SuncareButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConfirmationRow(label: String, value: String) {
    val colors = SuncareTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink,
        )
    }
}

@Composable
private fun DoctorAvailabilityCard(
    group: DoctorAvailabilityGroup,
    expanded: Boolean,
    selectedDate: String?,
    selectedSession: OnsiteAvailabilityDto?,
    busy: Boolean,
    onToggleExpand: () -> Unit,
    onSelectDate: (String) -> Unit,
    onToggleSession: (OnsiteAvailabilityDto) -> Unit,
) {
    val colors = SuncareTheme.colors
    SuncareCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SuncareIconTile(Icons.Outlined.MedicalServices)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.doctorName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${group.departmentName} · ${group.specialties.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }
            StatusBadge(
                label = "${group.totalSlots} ca",
                tone = BadgeTone.Success,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        SuncareButton(
            text = if (expanded) "Thu gọn lịch" else "Xem lịch (${group.availableDates.size} ngày)",
            onClick = onToggleExpand,
            variant = if (expanded) SuncareButtonVariant.Outline else SuncareButtonVariant.Primary,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )

        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Chọn ngày",
                style = MaterialTheme.typography.labelLarge,
                color = colors.ink,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.availableDates.forEach { date ->
                    FilterChip(
                        selected = selectedDate == date,
                        enabled = !busy,
                        onClick = { onSelectDate(date) },
                        label = { Text(formatShortDate(date)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.warningSoft,
                            selectedLabelColor = colors.ink,
                        ),
                    )
                }
            }

            val daySessions = selectedDate?.let { group.itemsByDate[it] }.orEmpty()
            if (daySessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    daySessions.forEach { session ->
                        val isSelected = selectedSession != null &&
                            selectedSession.doctorId == session.doctorId &&
                            selectedSession.date == session.date &&
                            selectedSession.shiftId == session.shiftId &&
                            selectedSession.specialtyId == session.specialtyId

                        Surface(
                            onClick = { onToggleSession(session) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) colors.warningSoft else colors.canvas,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) colors.brandText else colors.border,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${session.shiftName} · ${session.startTime}–${session.endTime}",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.ink,
                                    )
                                    StatusBadge(
                                        label = if (isSelected) "Đang chọn" else "Còn ${session.availablePlaces} chỗ",
                                        tone = if (isSelected) BadgeTone.Warning else BadgeTone.Success,
                                    )
                                }
                                Text(
                                    text = "${session.specialtyName} · Phòng ${session.roomCode} (${session.roomName})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.muted,
                                )
                                Text(
                                    text = "Phí khám dự kiến: ${formatFeeRange(session.minimumFee, session.maximumFee)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.brandText,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DateRangeBottomSheet(
    initialFrom: String,
    initialTo: String,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit,
    onPreset: (Long) -> Unit,
    onClear: () -> Unit,
) {
    val colors = SuncareTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftFrom by rememberSaveable { mutableStateOf(initialFrom) }
    var draftTo by rememberSaveable { mutableStateOf(initialTo) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.canvas,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Chọn khoảng ngày khám",
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
            )
            Text(
                text = "Chọn nhanh theo lối tắt hoặc nhập ngày bắt đầu / kết thúc (YYYY-MM-DD).",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuncareButton(
                    text = "Ngày mai",
                    onClick = { onPreset(1) },
                    variant = SuncareButtonVariant.Outline,
                )
                SuncareButton(
                    text = "7 ngày tới",
                    onClick = { onPreset(7) },
                    variant = SuncareButtonVariant.Outline,
                )
                SuncareButton(
                    text = "30 ngày tới",
                    onClick = { onPreset(30) },
                    variant = SuncareButtonVariant.Outline,
                )
            }

            if (!localError.isNullOrBlank()) {
                SuncareInlineBanner(message = localError!!, tone = BadgeTone.Danger)
            }

            SuncareTextField(
                label = "Từ ngày",
                value = draftFrom,
                onValueChange = { draftFrom = it; localError = null },
                placeholder = "YYYY-MM-DD",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
            )
            SuncareTextField(
                label = "Đến ngày",
                value = draftTo,
                onValueChange = { draftTo = it; localError = null },
                placeholder = "YYYY-MM-DD",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuncareButton(
                    text = "Bỏ khoảng",
                    onClick = onClear,
                    variant = SuncareButtonVariant.Outline,
                    modifier = Modifier.weight(1f),
                )
                SuncareButton(
                    text = "Áp dụng",
                    onClick = {
                        val f = draftFrom.trim()
                        val t = draftTo.trim()
                        if (f.isNotEmpty() && t.isNotEmpty() && f > t) {
                            localError = "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu."
                        } else {
                            onApply(f, t)
                        }
                    },
                    variant = SuncareButtonVariant.Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun formatShortDate(isoDate: String): String = runCatching {
    LocalDate.parse(isoDate).format(SHORT_DATE_FMT)
}.getOrDefault(isoDate)

private fun formatFullDate(isoDate: String): String = runCatching {
    LocalDate.parse(isoDate).format(FULL_DATE_FMT)
}.getOrDefault(isoDate)

private fun formatVnd(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return runCatching {
        val formatter = NumberFormat.getNumberInstance(VI_LOCALE)
        formatter.maximumFractionDigits = 0
        "${formatter.format(BigDecimal(raw))} ₫"
    }.getOrDefault("$raw ₫")
}

private fun formatFeeRange(minFee: String?, maxFee: String?): String {
    if (minFee.isNullOrBlank()) return "Đang cập nhật"
    return if (maxFee.isNullOrBlank() || minFee == maxFee) {
        formatVnd(minFee)
    } else {
        "${formatVnd(minFee)} – ${formatVnd(maxFee)}"
    }
}
