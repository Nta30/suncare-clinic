package com.example.suncareclinic.ui.screens.appointment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.suncareclinic.data.dto.AppointmentScheduleDetailDto
import com.example.suncareclinic.data.dto.AppointmentScheduleListItemDto
import com.example.suncareclinic.ui.components.BadgeTone
import com.example.suncareclinic.ui.components.StatusBadge
import com.example.suncareclinic.ui.components.SuncareButton
import com.example.suncareclinic.ui.components.SuncareButtonVariant
import com.example.suncareclinic.ui.components.SuncareEmptyView
import com.example.suncareclinic.ui.components.SuncareErrorView
import com.example.suncareclinic.ui.components.SuncareInlineBanner
import com.example.suncareclinic.ui.components.SuncareLoadingView
import com.example.suncareclinic.ui.components.SuncareScreenHeader
import com.example.suncareclinic.ui.components.SuncareSectionHeading
import com.example.suncareclinic.ui.components.suncarePageWidth
import com.example.suncareclinic.ui.components.SuncareScheduleCard
import com.example.suncareclinic.ui.theme.SuncareTheme
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VIETNAM_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")
private val TIME_ONLY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientAppointmentsScreen(
    state: PatientAppointmentsUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onSelectFilter: (PatientAppointmentFilter) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onRetryDetail: () -> Unit,
    onCloseDetail: () -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val colors = SuncareTheme.colors
    val filtered = state.filteredItems

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
                title = "Lịch khám của tôi",
                description = "Xem giờ hẹn, địa điểm và phí khám đã chốt.",
                icon = Icons.Outlined.CalendarMonth,
                action = {
                    TextButton(onClick = onRefresh, enabled = !state.loading) {
                        Text(if (state.loading) "Đang tải…" else "Làm mới", color = colors.brandText)
                    }
                },
            )
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PatientAppointmentFilter.entries) { filter ->
                    val count = if (filter.code == null) {
                        state.items.size
                    } else {
                        state.items.count { it.appointmentStatus.equals(filter.code, ignoreCase = true) }
                    }
                    val selected = state.selectedFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectFilter(filter) },
                        label = {
                            Text(
                                text = if (state.items.isNotEmpty()) "${filter.label} ($count)" else filter.label,
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
            state.loading && state.items.isEmpty() -> {
                item {
                    SuncareLoadingView(message = "Đang tải danh sách lịch khám...")
                }
            }

            !state.error.isNullOrBlank() && state.items.isEmpty() -> {
                item {
                    SuncareErrorView(
                        title = "Không tải được lịch khám",
                        message = state.error,
                        onRetry = onRefresh,
                    )
                }
            }

            state.items.isEmpty() -> {
                item {
                    SuncareEmptyView(
                        title = "Bạn chưa có lịch khám nào",
                        description = "Hãy tìm chuyên khoa và đặt lịch khám trực tiếp để bắt đầu theo dõi tại đây.",
                        actionLabel = "Tìm lịch khám ngay",
                        onAction = onNavigateToSearch,
                    )
                }
            }

            filtered.isEmpty() -> {
                item {
                    SuncareEmptyView(
                        title = "Không có lịch ở trạng thái \"${state.selectedFilter.label}\"",
                        description = "Chọn bộ lọc \"Tất cả\" để xem toàn bộ lịch hẹn của bạn.",
                        actionLabel = "Xem tất cả lịch",
                        onAction = { onSelectFilter(PatientAppointmentFilter.All) },
                    )
                }
            }

            else -> {
                if (!state.error.isNullOrBlank()) {
                    item {
                        SuncareInlineBanner(
                            message = state.error,
                            tone = BadgeTone.Danger,
                        )
                    }
                }
                items(
                    items = filtered,
                    key = { it.appointmentId },
                ) { appointment ->
                    AppointmentListCard(
                        item = appointment,
                        onClick = { onOpenDetail(appointment.appointmentId) },
                    )
                }
            }
        }
    }

    if (state.selectedAppointmentId != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onCloseDetail,
            sheetState = sheetState,
            containerColor = colors.canvas,
        ) {
            AppointmentDetailSheetContent(
                loading = state.detailLoading,
                detail = state.detail,
                error = state.detailError,
                onRetry = onRetryDetail,
                onClose = onCloseDetail,
            )
        }
    }
}

@Composable
private fun AppointmentListCard(
    item: AppointmentScheduleListItemDto,
    onClick: () -> Unit,
) {
    SuncareScheduleCard(
        title = item.specialtyName?.ifBlank { null } ?: "Chưa cập nhật chuyên khoa",
        subtitle = "Bác sĩ: ${item.doctorName?.ifBlank { null } ?: "Chưa phân công"}",
        code = item.appointmentCode,
        schedule = formatVietnamDateTime(item.startsAt),
        status = appointmentStatusBadge(item.appointmentStatus),
        category = visitModeLabel(item.visitMode),
        onClick = onClick,
    )
}

@Composable
private fun AppointmentDetailSheetContent(
    loading: Boolean,
    detail: AppointmentScheduleDetailDto?,
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SuncareSectionHeading("Chi tiết lịch khám")

        when {
            loading -> {
                SuncareLoadingView(message = "Đang tải chi tiết lịch khám...")
            }

            !error.isNullOrBlank() -> {
                SuncareErrorView(
                    title = "Không tải được chi tiết",
                    message = error,
                    onRetry = onRetry,
                )
            }

            detail != null -> {
                val apptStatus = appointmentStatusBadge(detail.appointmentStatus)
                val encStatus = detail.encounterStatus?.let { encounterStatusBadge(it) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = detail.appointmentCode,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                        fontWeight = FontWeight.Bold,
                    )
                    StatusBadge(label = apptStatus.first, tone = apptStatus.second)
                }

                if (encStatus != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Trạng thái lượt khám",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                        StatusBadge(label = encStatus.first, tone = encStatus.second)
                    }
                }

                HorizontalDivider(color = colors.border)

                DetailInfoRow("Hình thức khám", visitModeLabel(detail.visitMode))
                DetailInfoRow("Thời gian", formatVietnamTimeRange(detail.startsAt, detail.endsAt))
                DetailInfoRow("Chuyên khoa", detail.specialtyName ?: "—")
                if (!detail.departmentName.isNullOrBlank()) {
                    DetailInfoRow("Khoa", detail.departmentName)
                }
                DetailInfoRow(
                    "Bác sĩ phụ trách",
                    listOfNotNull(
                        detail.doctorName?.ifBlank { null },
                        detail.doctorCode?.ifBlank { null }?.let { "($it)" },
                    ).joinToString(" ").ifBlank { "—" },
                )
                val roomLabel = listOfNotNull(
                    detail.roomCode?.ifBlank { null },
                    detail.roomName?.ifBlank { null },
                ).joinToString(" · ")
                if (roomLabel.isNotBlank()) {
                    DetailInfoRow("Phòng khám", roomLabel)
                }
                if (!detail.examinationAmount.isNullOrBlank()) {
                    DetailInfoRow("Phí khám đã chốt", formatVndAmount(detail.examinationAmount))
                }

                HorizontalDivider(color = colors.border)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Triệu chứng / lý do khám tự khai",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.muted,
                    )
                    Text(
                        text = detail.symptoms?.trim()?.ifBlank { null }
                            ?: "Không ghi chú triệu chứng khi đặt lịch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                    )
                }

                if (detail.visitMode.equals("ONSITE", ignoreCase = true)) {
                    SuncareInlineBanner(
                        message = "Lịch khám trực tiếp không hỗ trợ tự đổi hoặc tự hủy trên ứng dụng. Vui lòng đến đúng giờ hẹn hoặc liên hệ quầy tiếp đón của phòng khám nếu cần hỗ trợ.",
                        tone = BadgeTone.Warning,
                    )
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
private fun DetailInfoRow(
    label: String,
    value: String,
) {
    val colors = SuncareTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f),
        )
    }
}

private fun visitModeLabel(mode: String?): String = when (mode?.uppercase(Locale.ROOT)) {
    "ONSITE" -> "Khám trực tiếp"
    "ONLINE" -> "Khám trực tuyến"
    else -> mode ?: "—"
}

private fun appointmentStatusBadge(status: String?): Pair<String, BadgeTone> =
    when (status?.uppercase(Locale.ROOT)) {
        "BOOKED" -> "Đã đặt" to BadgeTone.Warning
        "COMPLETED" -> "Hoàn tất" to BadgeTone.Success
        "CANCELLED" -> "Đã hủy" to BadgeTone.Danger
        "NO_SHOW" -> "Vắng mặt" to BadgeTone.Danger
        else -> (status ?: "Không rõ") to BadgeTone.Neutral
    }

private fun encounterStatusBadge(status: String): Pair<String, BadgeTone> =
    when (status.uppercase(Locale.ROOT)) {
        "PENDING" -> "Chờ bắt đầu" to BadgeTone.Neutral
        "IN_PROGRESS" -> "Đang khám" to BadgeTone.Warning
        "WAITING_CLS" -> "Chờ cận lâm sàng" to BadgeTone.Warning
        "COMPLETED" -> "Đã hoàn tất" to BadgeTone.Success
        "CANCELLED" -> "Đã hủy" to BadgeTone.Danger
        else -> status to BadgeTone.Neutral
    }

private fun formatVietnamDateTime(isoUtc: String?): String {
    if (isoUtc.isNullOrBlank()) return "Chưa rõ thời gian"
    return runCatching {
        OffsetDateTime.parse(isoUtc)
            .atZoneSameInstant(VIETNAM_ZONE)
            .format(DATE_TIME_FORMATTER)
    }.getOrElse { isoUtc }
}

private fun formatVietnamTimeRange(startsAt: String?, endsAt: String?): String {
    if (startsAt.isNullOrBlank()) return "Chưa rõ thời gian"
    return runCatching {
        val startVn = OffsetDateTime.parse(startsAt).atZoneSameInstant(VIETNAM_ZONE)
        val base = startVn.format(DATE_TIME_FORMATTER)
        if (endsAt.isNullOrBlank()) {
            base
        } else {
            val endVn = OffsetDateTime.parse(endsAt).atZoneSameInstant(VIETNAM_ZONE)
            "$base – ${endVn.format(TIME_ONLY_FORMATTER)}"
        }
    }.getOrElse { startsAt }
}

private fun formatVndAmount(raw: String): String {
    return runCatching {
        val amount = BigDecimal(raw.trim())
        val symbols = DecimalFormatSymbols(Locale.forLanguageTag("vi-VN")).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        val formatter = DecimalFormat("#,##0", symbols)
        "${formatter.format(amount)} đ"
    }.getOrElse { "$raw đ" }
}
