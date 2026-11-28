package com.example.suncareclinic.ui.screens.appointment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.suncareclinic.data.dto.AppointmentScheduleDetailDto
import com.example.suncareclinic.data.dto.AppointmentScheduleListItemDto
import com.example.suncareclinic.data.dto.DoctorOnlineSlotDto
import com.example.suncareclinic.data.dto.DoctorSlotPatientDto
import com.example.suncareclinic.data.dto.DoctorWorkShiftDto
import com.example.suncareclinic.ui.components.BadgeTone
import com.example.suncareclinic.ui.components.StatusBadge
import com.example.suncareclinic.ui.components.SuncareButton
import com.example.suncareclinic.ui.components.SuncareButtonVariant
import com.example.suncareclinic.ui.components.SuncareCard
import com.example.suncareclinic.ui.components.SuncareEmptyView
import com.example.suncareclinic.ui.components.SuncareErrorView
import com.example.suncareclinic.ui.components.SuncareInlineBanner
import com.example.suncareclinic.ui.components.SuncareLoadingView
import com.example.suncareclinic.ui.components.SuncareScreenHeader
import com.example.suncareclinic.ui.components.SuncareSectionHeading
import com.example.suncareclinic.ui.components.SuncareIconTile
import com.example.suncareclinic.ui.components.suncarePageWidth
import com.example.suncareclinic.ui.components.SuncareScheduleCard
import com.example.suncareclinic.ui.theme.SuncareTheme
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VN_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
private val VN_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val VN_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")
private val VN_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorAppointmentsScreen(
    state: DoctorAppointmentsUiState,
    contentPadding: PaddingValues,
    onSelectSection: (DoctorScheduleSection) -> Unit,
    onRefresh: () -> Unit,
    onSelectStatusFilter: (PatientAppointmentFilter) -> Unit,
    onSelectVisitModeFilter: (DoctorVisitModeFilter) -> Unit,
    onOpenAppointmentDetail: (Long) -> Unit,
    onRetryAppointmentDetail: () -> Unit,
    onCloseAppointmentDetail: () -> Unit,
    onSelectTimetableRange: (DoctorTimetableRange) -> Unit,
    onToggleShiftSlots: (Long) -> Unit,
    onRetryShiftSlots: (Long) -> Unit,
    onOpenSlotPatient: (Long) -> Unit,
    onRetrySlotPatient: () -> Unit,
    onCloseSlotPatient: () -> Unit,
) {
    val colors = SuncareTheme.colors
    val isBusy = when (state.section) {
        DoctorScheduleSection.Appointments -> state.appointmentsLoading
        DoctorScheduleSection.Timetable -> state.timetableLoading
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
            SuncareScreenHeader(
                title = "Lịch làm việc",
                description = "Theo dõi lịch hẹn được giao và các ca trực đã công bố.",
                icon = Icons.Outlined.CalendarMonth,
                action = {
                    TextButton(onClick = onRefresh, enabled = !isBusy) {
                        Text(if (isBusy) "Đang tải…" else "Làm mới", color = colors.brandText)
                    }
                },
            )
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DoctorScheduleSection.entries.forEachIndexed { index, section ->
                    SegmentedButton(
                        selected = state.section == section,
                        onClick = { onSelectSection(section) },
                        shape = SegmentedButtonDefaults.itemShape(index, DoctorScheduleSection.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = colors.warningSoft,
                            activeContentColor = colors.brandText,
                            activeBorderColor = colors.warningBorder,
                            inactiveContainerColor = colors.surface,
                            inactiveContentColor = colors.muted,
                            inactiveBorderColor = colors.borderStrong,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(section.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        when (state.section) {
            DoctorScheduleSection.Appointments -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PatientAppointmentFilter.entries) { filter ->
                                val count = if (filter.code == null) {
                                    state.appointments.size
                                } else {
                                    state.appointments.count {
                                        it.appointmentStatus.equals(filter.code, ignoreCase = true)
                                    }
                                }
                                FilterChip(
                                    selected = state.statusFilter == filter,
                                    onClick = { onSelectStatusFilter(filter) },
                                    label = {
                                        Text(
                                            text = if (state.appointments.isNotEmpty()) {
                                                "${filter.label} ($count)"
                                            } else {
                                                filter.label
                                            },
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
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DoctorVisitModeFilter.entries) { modeFilter ->
                                FilterChip(
                                    selected = state.visitModeFilter == modeFilter,
                                    onClick = { onSelectVisitModeFilter(modeFilter) },
                                    label = {
                                        Text(
                                            text = modeFilter.label,
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
                }

                val filtered = state.filteredAppointments
                when {
                    state.appointmentsLoading && state.appointments.isEmpty() -> {
                        item {
                            SuncareLoadingView(message = "Đang tải danh sách lịch hẹn được giao...")
                        }
                    }

                    !state.appointmentsError.isNullOrBlank() && state.appointments.isEmpty() -> {
                        item {
                            SuncareErrorView(
                                title = "Không tải được danh sách lịch hẹn",
                                message = state.appointmentsError,
                                onRetry = onRefresh,
                            )
                        }
                    }

                    state.appointments.isEmpty() -> {
                        item {
                            SuncareEmptyView(
                                title = "Chưa có lịch hẹn nào được giao",
                                description = "Khi bệnh nhân đặt lịch khám vào ca trực của bạn, danh sách sẽ hiển thị tại đây.",
                                actionLabel = "Xem lịch trực công bố",
                                onAction = { onSelectSection(DoctorScheduleSection.Timetable) },
                            )
                        }
                    }

                    filtered.isEmpty() -> {
                        item {
                            SuncareEmptyView(
                                title = "Không có lịch hẹn khớp bộ lọc",
                                description = "Thử bỏ lọc trạng thái hoặc hình thức khám để xem toàn bộ lịch hẹn được giao.",
                                actionLabel = "Xóa bộ lọc",
                                onAction = {
                                    onSelectStatusFilter(PatientAppointmentFilter.All)
                                    onSelectVisitModeFilter(DoctorVisitModeFilter.All)
                                },
                            )
                        }
                    }

                    else -> {
                        if (!state.appointmentsError.isNullOrBlank()) {
                            item {
                                SuncareInlineBanner(
                                    message = state.appointmentsError,
                                    tone = BadgeTone.Danger,
                                )
                            }
                        }
                        items(
                            items = filtered,
                            key = { it.appointmentId },
                        ) { appointment ->
                            DoctorAppointmentCard(
                                item = appointment,
                                onClick = { onOpenAppointmentDetail(appointment.appointmentId) },
                            )
                        }
                    }
                }
            }

            DoctorScheduleSection.Timetable -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DoctorTimetableRange.entries) { range ->
                                FilterChip(
                                    selected = state.timetableRange == range,
                                    onClick = { onSelectTimetableRange(range) },
                                    label = {
                                        Text(
                                            text = range.label,
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
                        Text(
                            text = "Khoảng xem: ${formatIsoDate(state.timetableFrom)} – ${formatIsoDate(state.timetableTo)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                }

                when {
                    state.timetableLoading && state.shifts.isEmpty() -> {
                        item {
                            SuncareLoadingView(message = "Đang tải lịch trực công bố...")
                        }
                    }

                    !state.timetableError.isNullOrBlank() && state.shifts.isEmpty() -> {
                        item {
                            SuncareErrorView(
                                title = "Không tải được lịch trực",
                                message = state.timetableError,
                                onRetry = onRefresh,
                            )
                        }
                    }

                    state.shifts.isEmpty() -> {
                        item {
                            SuncareEmptyView(
                                title = "Không có ca trực công bố trong khoảng này",
                                description = "Thử chọn khoảng ngày rộng hơn (14 hoặc 30 ngày tới) để xem các ca đã được phân công.",
                                actionLabel = "Xem 30 ngày tới",
                                onAction = { onSelectTimetableRange(DoctorTimetableRange.Next30Days) },
                            )
                        }
                    }

                    else -> {
                        if (!state.timetableError.isNullOrBlank()) {
                            item {
                                SuncareInlineBanner(
                                    message = state.timetableError,
                                    tone = BadgeTone.Danger,
                                )
                            }
                        }
                        items(
                            items = state.shifts,
                            key = { it.assignmentId },
                        ) { shift ->
                            val isExpanded = state.expandedShiftIds.contains(shift.assignmentId)
                            val slotGroup = state.shiftSlots[shift.assignmentId]
                            DoctorWorkShiftCard(
                                shift = shift,
                                expanded = isExpanded,
                                slotGroup = slotGroup,
                                onToggleSlots = { onToggleShiftSlots(shift.assignmentId) },
                                onRetrySlots = { onRetryShiftSlots(shift.assignmentId) },
                                onClickSlot = { slot ->
                                    if (slot.appointmentId != null) {
                                        onOpenAppointmentDetail(slot.appointmentId)
                                    } else {
                                        onOpenSlotPatient(slot.slotId)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.selectedAppointmentId != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onCloseAppointmentDetail,
            sheetState = sheetState,
            containerColor = colors.canvas,
        ) {
            DoctorAppointmentDetailSheet(
                loading = state.detailLoading,
                detail = state.detail,
                error = state.detailError,
                onRetry = onRetryAppointmentDetail,
                onClose = onCloseAppointmentDetail,
            )
        }
    }

    if (state.selectedSlotId != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onCloseSlotPatient,
            sheetState = sheetState,
            containerColor = colors.canvas,
        ) {
            SlotPatientDetailSheet(
                loading = state.slotPatientLoading,
                patient = state.slotPatient,
                error = state.slotPatientError,
                onRetry = onRetrySlotPatient,
                onClose = onCloseSlotPatient,
            )
        }
    }
}

@Composable
private fun DoctorAppointmentCard(
    item: AppointmentScheduleListItemDto,
    onClick: () -> Unit,
) {
    SuncareScheduleCard(
        title = item.patientName?.ifBlank { null } ?: "Chưa rõ tên bệnh nhân",
        subtitle = item.specialtyName?.ifBlank { null } ?: "Chưa cập nhật chuyên khoa",
        code = item.appointmentCode,
        schedule = formatDoctorDateTime(item.startsAt),
        status = doctorAppointmentStatusBadge(item.appointmentStatus),
        category = doctorVisitModeLabel(item.visitMode),
        onClick = onClick,
    )
}

@Composable
private fun DoctorWorkShiftCard(
    shift: DoctorWorkShiftDto,
    expanded: Boolean,
    slotGroup: SlotGroupState?,
    onToggleSlots: () -> Unit,
    onRetrySlots: () -> Unit,
    onClickSlot: (DoctorOnlineSlotDto) -> Unit,
) {
    val colors = SuncareTheme.colors
    val isOnline = shift.visitMode.equals("ONLINE", ignoreCase = true)

    SuncareCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SuncareIconTile(Icons.Outlined.Schedule)
            Text(
                text = formatIsoDate(shift.workDate),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                fontWeight = FontWeight.Bold,
            )
            StatusBadge(
                label = doctorVisitModeLabel(shift.visitMode),
                tone = if (isOnline) BadgeTone.Warning else BadgeTone.Success,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val shiftLabel = listOfNotNull(
            shift.shiftName?.ifBlank { null },
            shift.shiftCode?.ifBlank { null }?.let { "($it)" },
        ).joinToString(" ").ifBlank { "Ca làm việc" }
        SuncareSectionHeading(shiftLabel, formatDoctorTimeOnlyRange(shift.startsAt, shift.endsAt))

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = listOfNotNull(
                shift.specialtyName?.ifBlank { null },
                shift.departmentName?.ifBlank { null },
            ).joinToString(" · ").ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )

        val roomLabel = listOfNotNull(
            shift.roomCode?.ifBlank { null },
            shift.roomName?.ifBlank { null },
        ).joinToString(" · ")
        if (roomLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Phòng: $roomLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }

        if (isOnline) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(6.dp))

            SuncareButton(
                text = if (expanded) "Ẩn danh sách slot 20 phút" else "Xem danh sách slot 20 phút",
                onClick = onToggleSlots,
                variant = SuncareButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth(),
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                when {
                    slotGroup == null || slotGroup.loading -> {
                        SuncareLoadingView(message = "Đang tải các slot trực tuyến...")
                    }

                    !slotGroup.error.isNullOrBlank() -> {
                        SuncareErrorView(
                            title = "Không tải được danh sách slot",
                            message = slotGroup.error,
                            onRetry = onRetrySlots,
                        )
                    }

                    slotGroup.slots.isEmpty() -> {
                        Text(
                            text = "Ca trực tuyến này chưa có slot nào.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }

                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slotGroup.slots.forEach { slot ->
                                val hasBooking = slot.appointmentId != null || !slot.patientName.isNullOrBlank()
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (hasBooking) Modifier.clickable { onClickSlot(slot) }
                                            else Modifier
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (hasBooking) colors.warningSoft else colors.canvas,
                                    border = BorderStroke(1.dp, colors.border),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = formatDoctorTimeOnlyRange(slot.startsAt, slot.endsAt),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = colors.ink,
                                            )
                                            if (!slot.patientName.isNullOrBlank()) {
                                                Text(
                                                    text = "BN: ${slot.patientName}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colors.brandText,
                                                )
                                            }
                                        }
                                        val badge = when {
                                            hasBooking -> "Đã đặt" to BadgeTone.Warning
                                            slot.open -> "Còn trống" to BadgeTone.Success
                                            else -> "Đã đóng" to BadgeTone.Neutral
                                        }
                                        StatusBadge(label = badge.first, tone = badge.second)
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

@Composable
private fun DoctorAppointmentDetailSheet(
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
        SuncareSectionHeading("Chi tiết lịch hẹn được giao")

        when {
            loading -> {
                SuncareLoadingView(message = "Đang tải chi tiết lịch hẹn...")
            }

            !error.isNullOrBlank() -> {
                SuncareErrorView(
                    title = "Không tải được chi tiết",
                    message = error,
                    onRetry = onRetry,
                )
            }

            detail != null -> {
                val apptStatus = doctorAppointmentStatusBadge(detail.appointmentStatus)
                val encStatus = detail.encounterStatus?.let { doctorEncounterStatusBadge(it) }

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

                DoctorDetailRow(
                    "Bệnh nhân",
                    listOfNotNull(
                        detail.patientName?.ifBlank { null },
                        detail.patientCode?.ifBlank { null }?.let { "($it)" },
                    ).joinToString(" ").ifBlank { "—" },
                )
                DoctorDetailRow("Hình thức khám", doctorVisitModeLabel(detail.visitMode))
                DoctorDetailRow("Thời gian", formatDoctorDateTimeRange(detail.startsAt, detail.endsAt))
                DoctorDetailRow("Chuyên khoa", detail.specialtyName ?: "—")
                if (!detail.departmentName.isNullOrBlank()) {
                    DoctorDetailRow("Khoa", detail.departmentName)
                }
                val roomLabel = listOfNotNull(
                    detail.roomCode?.ifBlank { null },
                    detail.roomName?.ifBlank { null },
                ).joinToString(" · ")
                if (roomLabel.isNotBlank()) {
                    DoctorDetailRow("Phòng khám", roomLabel)
                }
                if (!detail.examinationAmount.isNullOrBlank()) {
                    DoctorDetailRow("Phí khám đã chốt", formatDoctorVnd(detail.examinationAmount))
                }

                HorizontalDivider(color = colors.border)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Triệu chứng / lý do khám bệnh nhân tự khai",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.muted,
                    )
                    Text(
                        text = detail.symptoms?.trim()?.ifBlank { null }
                            ?: "Bệnh nhân không ghi chú triệu chứng khi đặt lịch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
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
private fun SlotPatientDetailSheet(
    loading: Boolean,
    patient: DoctorSlotPatientDto?,
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
        Text(
            text = "Thông tin bệnh nhân của slot",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )

        when {
            loading -> {
                SuncareLoadingView(message = "Đang tải thông tin bệnh nhân...")
            }

            !error.isNullOrBlank() -> {
                SuncareErrorView(
                    title = "Không tải được thông tin bệnh nhân",
                    message = error,
                    onRetry = onRetry,
                )
            }

            patient != null -> {
                DoctorDetailRow("Mã bệnh nhân", patient.patientCode ?: "—")
                DoctorDetailRow("Họ và tên", patient.fullName ?: "—")
                DoctorDetailRow("Ngày sinh", patient.birthDate?.let { formatIsoDate(it) } ?: "—")
                DoctorDetailRow("Giới tính", formatSexLabel(patient.sex))
                DoctorDetailRow("Số điện thoại", patient.phone ?: "—")
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
private fun DoctorDetailRow(
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

private fun doctorVisitModeLabel(mode: String?): String = when (mode?.uppercase(Locale.ROOT)) {
    "ONSITE" -> "Khám trực tiếp"
    "ONLINE" -> "Khám trực tuyến"
    else -> mode ?: "—"
}

private fun doctorAppointmentStatusBadge(status: String?): Pair<String, BadgeTone> =
    when (status?.uppercase(Locale.ROOT)) {
        "BOOKED" -> "Đã đặt" to BadgeTone.Warning
        "COMPLETED" -> "Hoàn tất" to BadgeTone.Success
        "CANCELLED" -> "Đã hủy" to BadgeTone.Danger
        "NO_SHOW" -> "Vắng mặt" to BadgeTone.Danger
        else -> (status ?: "Không rõ") to BadgeTone.Neutral
    }

private fun doctorEncounterStatusBadge(status: String): Pair<String, BadgeTone> =
    when (status.uppercase(Locale.ROOT)) {
        "PENDING" -> "Chờ bắt đầu" to BadgeTone.Neutral
        "IN_PROGRESS" -> "Đang khám" to BadgeTone.Warning
        "WAITING_CLS" -> "Chờ cận lâm sàng" to BadgeTone.Warning
        "COMPLETED" -> "Đã hoàn tất" to BadgeTone.Success
        "CANCELLED" -> "Đã hủy" to BadgeTone.Danger
        else -> status to BadgeTone.Neutral
    }

private fun formatSexLabel(sex: String?): String = when (sex?.uppercase(Locale.ROOT)) {
    "MALE" -> "Nam"
    "FEMALE" -> "Nữ"
    "OTHER" -> "Khác"
    else -> sex ?: "—"
}

private fun formatIsoDate(raw: String): String {
    return runCatching {
        LocalDate.parse(raw).format(VN_DATE_FORMATTER)
    }.getOrElse { raw }
}

private fun formatDoctorDateTime(isoUtc: String?): String {
    if (isoUtc.isNullOrBlank()) return "Chưa rõ thời gian"
    return runCatching {
        OffsetDateTime.parse(isoUtc)
            .atZoneSameInstant(VN_ZONE)
            .format(VN_DATE_TIME_FORMATTER)
    }.getOrElse { isoUtc }
}

private fun formatDoctorDateTimeRange(startsAt: String?, endsAt: String?): String {
    if (startsAt.isNullOrBlank()) return "Chưa rõ thời gian"
    return runCatching {
        val startVn = OffsetDateTime.parse(startsAt).atZoneSameInstant(VN_ZONE)
        val base = startVn.format(VN_DATE_TIME_FORMATTER)
        if (endsAt.isNullOrBlank()) base
        else "$base – ${OffsetDateTime.parse(endsAt).atZoneSameInstant(VN_ZONE).format(VN_TIME_FORMATTER)}"
    }.getOrElse { startsAt }
}

private fun formatDoctorTimeOnlyRange(startsAt: String?, endsAt: String?): String {
    if (startsAt.isNullOrBlank()) return "—"
    return runCatching {
        val startStr = OffsetDateTime.parse(startsAt).atZoneSameInstant(VN_ZONE).format(VN_TIME_FORMATTER)
        if (endsAt.isNullOrBlank()) startStr
        else "$startStr – ${OffsetDateTime.parse(endsAt).atZoneSameInstant(VN_ZONE).format(VN_TIME_FORMATTER)}"
    }.getOrElse { startsAt }
}

private fun formatDoctorVnd(raw: String): String {
    return runCatching {
        val amount = BigDecimal(raw.trim())
        val symbols = DecimalFormatSymbols(Locale.forLanguageTag("vi-VN")).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        "${DecimalFormat("#,##0", symbols).format(amount)} đ"
    }.getOrElse { "$raw đ" }
}
