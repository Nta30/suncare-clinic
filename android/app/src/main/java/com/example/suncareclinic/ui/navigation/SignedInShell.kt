package com.example.suncareclinic.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.suncareclinic.data.dto.CurrentAccountDto
import com.example.suncareclinic.data.repository.AppointmentScheduleRepository
import com.example.suncareclinic.data.repository.DoctorEncounterRepository
import com.example.suncareclinic.data.repository.OnsiteAppointmentRepository
import com.example.suncareclinic.data.repository.ProfileRepository
import com.example.suncareclinic.ui.components.BadgeTone
import com.example.suncareclinic.ui.components.StatusBadge
import com.example.suncareclinic.ui.components.SuncareButton
import com.example.suncareclinic.ui.components.SuncareButtonVariant
import com.example.suncareclinic.ui.components.SuncareCard
import com.example.suncareclinic.ui.components.SuncareEmptyView
import com.example.suncareclinic.ui.components.SuncareInlineBanner
import com.example.suncareclinic.ui.components.SuncareIconTile
import com.example.suncareclinic.ui.components.SuncareScreenHeader
import com.example.suncareclinic.ui.components.SuncareSectionHeading
import com.example.suncareclinic.ui.components.suncarePageWidth
import com.example.suncareclinic.ui.screens.appointment.DoctorAppointmentsScreen
import com.example.suncareclinic.ui.screens.appointment.DoctorAppointmentsViewModel
import com.example.suncareclinic.ui.screens.appointment.OnsiteSearchScreen
import com.example.suncareclinic.ui.screens.appointment.OnsiteSearchViewModel
import com.example.suncareclinic.ui.screens.appointment.PatientAppointmentsScreen
import com.example.suncareclinic.ui.screens.appointment.PatientAppointmentsViewModel
import com.example.suncareclinic.ui.screens.auth.AuthUiState
import com.example.suncareclinic.ui.screens.encounter.DoctorEncountersScreen
import com.example.suncareclinic.ui.screens.encounter.DoctorEncountersViewModel
import com.example.suncareclinic.ui.screens.profile.ProfileScreen
import com.example.suncareclinic.ui.screens.profile.ProfileViewModel
import com.example.suncareclinic.ui.theme.SuncareTheme

enum class AppTab(
    val label: String,
    val icon: ImageVector,
) {
    Home("Tổng quan", Icons.Filled.Home),
    Search("Tìm lịch", Icons.Filled.Search),
    Appointments("Lịch khám", Icons.Filled.CalendarMonth),
    Encounters("Khám bệnh", Icons.Filled.MedicalServices),
    Profile("Hồ sơ", Icons.Filled.Person),
}

private val ROLE_LABELS = mapOf(
    "PATIENT" to "Bệnh nhân",
    "DOCTOR" to "Bác sĩ",
    "LAB_STAFF" to "Nhân viên CLS",
    "DEPARTMENT_HEAD" to "Trưởng khoa",
    "CASHIER" to "Thu ngân",
    "SYSTEM_ADMIN" to "Quản trị viên",
)

@Composable
fun SignedInShell(
    account: CurrentAccountDto,
    authState: AuthUiState,
    profileRepository: ProfileRepository,
    onsiteAppointmentRepository: OnsiteAppointmentRepository,
    appointmentScheduleRepository: AppointmentScheduleRepository,
    doctorEncounterRepository: DoctorEncounterRepository,
    onRefreshAccount: () -> Unit,
    onSessionExpired: () -> Unit,
    onConfirmLogout: (onSuccess: () -> Unit) -> Unit,
    onClearLogoutError: () -> Unit,
) {
    val colors = SuncareTheme.colors
    val isPatient = account.roles.contains("PATIENT")
    val isDoctor = account.roles.contains("DOCTOR")

    val tabs = remember(isPatient, isDoctor) {
        buildList {
            add(AppTab.Home)
            if (isPatient) add(AppTab.Search)
            if (isPatient || isDoctor) add(AppTab.Appointments)
            if (isDoctor) add(AppTab.Encounters)
            add(AppTab.Profile)
        }
    }

    var currentTabName by rememberSaveable { mutableStateOf(AppTab.Home.name) }
    val currentTab = tabs.firstOrNull { it.name == currentTabName } ?: AppTab.Home
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    val profileViewModel: ProfileViewModel = viewModel(
        key = "profile_${account.accountId}",
        factory = ProfileViewModel.Factory(profileRepository, onSessionExpired),
    )
    val profileState by profileViewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        containerColor = colors.canvas,
        bottomBar = {
            Column {
                HorizontalDivider(color = colors.border)
                NavigationBar(
                    containerColor = colors.surface,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTabName = tab.name },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.brandText,
                                selectedTextColor = colors.brandText,
                                indicatorColor = colors.warningBorder,
                                unselectedIconColor = colors.muted,
                                unselectedTextColor = colors.muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when (currentTab) {
            AppTab.Home -> OverviewTab(
                account = account,
                displayName = profileState.profile?.fullName,
                authState = authState,
                contentPadding = innerPadding,
                onRefreshAccount = onRefreshAccount,
                onOpenTab = { currentTabName = it.name },
            )

            AppTab.Search -> {
                val searchViewModel: OnsiteSearchViewModel = viewModel(
                    key = "onsite_search_${account.accountId}",
                    factory = OnsiteSearchViewModel.Factory(onsiteAppointmentRepository, onSessionExpired),
                )
                val searchState by searchViewModel.uiState.collectAsState()
                OnsiteSearchScreen(
                    state = searchState,
                    contentPadding = innerPadding,
                    onReloadOptions = searchViewModel::loadOptions,
                    onSelectSpecialty = searchViewModel::selectSpecialty,
                    onSelectShift = searchViewModel::selectShift,
                    onUpdateDateRange = searchViewModel::updateDateRange,
                    onApplyQuickPresetDays = searchViewModel::applyQuickPresetDays,
                    onDoctorNameChange = searchViewModel::updateDoctorName,
                    onClearFilters = searchViewModel::clearFilters,
                    onRetrySearch = searchViewModel::retrySearch,
                    onToggleDoctor = searchViewModel::toggleDoctorExpand,
                    onSelectDoctorDate = searchViewModel::selectDoctorDate,
                    onToggleSelectSession = searchViewModel::toggleSelectSession,
                    onClearSelection = searchViewModel::clearSelection,
                    onSelectAssignmentMode = searchViewModel::selectAssignmentMode,
                    onSymptomsChange = searchViewModel::updateSymptoms,
                    onConfirmBooking = searchViewModel::submitBooking,
                    onStartNewSearch = searchViewModel::startNewSearchAfterBooking,
                )
            }

            AppTab.Appointments -> {
                if (isPatient) {
                    val appointmentsViewModel: PatientAppointmentsViewModel = viewModel(
                        key = "patient_appointments_${account.accountId}",
                        factory = PatientAppointmentsViewModel.Factory(
                            appointmentScheduleRepository,
                            onSessionExpired,
                        ),
                    )
                    val appointmentsState by appointmentsViewModel.uiState.collectAsState()
                    PatientAppointmentsScreen(
                        state = appointmentsState,
                        contentPadding = innerPadding,
                        onRefresh = appointmentsViewModel::loadAppointments,
                        onSelectFilter = appointmentsViewModel::selectFilter,
                        onOpenDetail = appointmentsViewModel::openDetail,
                        onRetryDetail = appointmentsViewModel::retryDetail,
                        onCloseDetail = appointmentsViewModel::closeDetail,
                        onNavigateToSearch = { currentTabName = AppTab.Search.name },
                    )
                } else {
                    val doctorAppointmentsViewModel: DoctorAppointmentsViewModel = viewModel(
                        key = "doctor_appointments_${account.accountId}",
                        factory = DoctorAppointmentsViewModel.Factory(
                            appointmentScheduleRepository,
                            onSessionExpired,
                        ),
                    )
                    val doctorState by doctorAppointmentsViewModel.uiState.collectAsState()
                    DoctorAppointmentsScreen(
                        state = doctorState,
                        contentPadding = innerPadding,
                        onSelectSection = doctorAppointmentsViewModel::selectSection,
                        onRefresh = doctorAppointmentsViewModel::refreshCurrentSection,
                        onSelectStatusFilter = doctorAppointmentsViewModel::selectStatusFilter,
                        onSelectVisitModeFilter = doctorAppointmentsViewModel::selectVisitModeFilter,
                        onOpenAppointmentDetail = doctorAppointmentsViewModel::openAppointmentDetail,
                        onRetryAppointmentDetail = doctorAppointmentsViewModel::retryAppointmentDetail,
                        onCloseAppointmentDetail = doctorAppointmentsViewModel::closeAppointmentDetail,
                        onSelectTimetableRange = doctorAppointmentsViewModel::selectTimetableRange,
                        onToggleShiftSlots = doctorAppointmentsViewModel::toggleShiftSlots,
                        onRetryShiftSlots = doctorAppointmentsViewModel::loadShiftSlots,
                        onOpenSlotPatient = doctorAppointmentsViewModel::openSlotPatient,
                        onRetrySlotPatient = doctorAppointmentsViewModel::retrySlotPatient,
                        onCloseSlotPatient = doctorAppointmentsViewModel::closeSlotPatient,
                    )
                }
            }

            AppTab.Encounters -> {
                val encountersViewModel: DoctorEncountersViewModel = viewModel(
                    key = "doctor_encounters_${account.accountId}",
                    factory = DoctorEncountersViewModel.Factory(
                        doctorEncounterRepository,
                        onSessionExpired,
                    ),
                )
                val encountersState by encountersViewModel.uiState.collectAsState()
                DoctorEncountersScreen(
                    state = encountersState,
                    doctorEncounterRepository = doctorEncounterRepository,
                    onSessionExpired = onSessionExpired,
                    contentPadding = innerPadding,
                    onShiftDate = encountersViewModel::shiftDate,
                    onSelectToday = encountersViewModel::selectToday,
                    onSelectStatusFilter = encountersViewModel::selectStatusFilter,
                    onSearchQueryChange = encountersViewModel::updateSearchQuery,
                    onLoadPage = encountersViewModel::loadWorklist,
                    onOpenEncounter = encountersViewModel::openEncounter,
                    onCloseEncounter = encountersViewModel::closeEncounter,
                    onReloadDetail = encountersViewModel::reloadDetail,
                    onStartEncounter = encountersViewModel::startSelectedEncounter,
                    onCompleteEncounter = encountersViewModel::completeSelectedEncounter,
                    onOpenAddHistory = encountersViewModel::openAddHistory,
                    onOpenEditHistory = encountersViewModel::openEditHistory,
                    onUpdateHistoryForm = encountersViewModel::updateHistoryForm,
                    onCloseHistoryForm = encountersViewModel::closeHistoryForm,
                    onSubmitHistoryForm = encountersViewModel::submitHistoryForm,
                    onOpenAddAllergy = encountersViewModel::openAddAllergy,
                    onOpenEditAllergy = encountersViewModel::openEditAllergy,
                    onUpdateAllergyForm = encountersViewModel::updateAllergyForm,
                    onCloseAllergyForm = encountersViewModel::closeAllergyForm,
                    onSubmitAllergyForm = encountersViewModel::submitAllergyForm,
                )
            }

            AppTab.Profile -> ProfileScreen(
                state = profileState,
                contentPadding = innerPadding,
                onReload = profileViewModel::loadProfile,
                onStartEdit = profileViewModel::startEditing,
                onCancelEdit = profileViewModel::cancelEditing,
                onFieldChange = profileViewModel::updateField,
                onSave = profileViewModel::save,
                onRequestLogout = {
                    onClearLogoutError()
                    showLogoutDialog = true
                },
            )
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!authState.logoutBusy) showLogoutDialog = false
            },
            title = {
                Text(
                    text = "Bạn muốn đăng xuất?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Phiên hiện tại sẽ bị thu hồi trên máy chủ. Bạn có thể đăng nhập lại bất cứ lúc nào.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                    if (!authState.logoutError.isNullOrBlank()) {
                        SuncareInlineBanner(message = authState.logoutError, tone = BadgeTone.Danger)
                    }
                }
            },
            confirmButton = {
                SuncareButton(
                    text = "Đăng xuất",
                    onClick = { onConfirmLogout { showLogoutDialog = false } },
                    variant = SuncareButtonVariant.Primary,
                    loading = authState.logoutBusy,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    enabled = !authState.logoutBusy,
                ) {
                    Text("Ở lại", color = colors.ink)
                }
            },
            containerColor = colors.surface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(
    account: CurrentAccountDto,
    displayName: String?,
    authState: AuthUiState,
    contentPadding: PaddingValues,
    onRefreshAccount: () -> Unit,
    onOpenTab: (AppTab) -> Unit,
) {
    val colors = SuncareTheme.colors
    val isPatient = "PATIENT" in account.roles
    val isDoctor = "DOCTOR" in account.roles
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
                title = displayName?.takeIf { it.isNotBlank() }?.let { "Xin chào, $it" } ?: "Xin chào!",
                description = "Suncare Clinic",
                icon = Icons.Filled.Home,
            )
        }

        if (!authState.successMessage.isNullOrBlank()) {
            item {
                SuncareInlineBanner(message = authState.successMessage, tone = BadgeTone.Success)
            }
        }

        if (!authState.errorMessage.isNullOrBlank()) {
            item {
                SuncareInlineBanner(message = authState.errorMessage, tone = BadgeTone.Danger)
            }
        }

        item { SuncareSectionHeading("Truy cập nhanh") }
        if (isPatient) {
            item {
                OverviewLink(AppTab.Search, "Đặt lịch khám", "Tìm chuyên khoa, bác sĩ và ca còn chỗ", onOpenTab)
            }
        }
        if (isDoctor) {
            item {
                OverviewLink(AppTab.Encounters, "Khám bệnh", "Mở danh sách lượt khám phụ trách", onOpenTab)
            }
        }
        if (isPatient || isDoctor) {
            item {
                OverviewLink(
                    AppTab.Appointments,
                    if (isPatient) "Lịch khám của tôi" else "Lịch làm việc",
                    if (isPatient) "Xem thông tin lịch hẹn đã đặt" else "Xem lịch trực và lịch hẹn được giao",
                    onOpenTab,
                )
            }
        }
        item {
            OverviewLink(AppTab.Profile, "Hồ sơ cá nhân", "Xem và cập nhật thông tin của bạn", onOpenTab)
        }

        item {
            SuncareCard {
                SuncareSectionHeading("Tài khoản", "Mã tài khoản: #${account.accountId}")
                Text(account.loginName, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (account.roles.isEmpty()) {
                        StatusBadge(label = "Chưa có vai trò", tone = BadgeTone.Neutral)
                    } else {
                        account.roles.forEach { role ->
                            StatusBadge(
                                label = ROLE_LABELS[role] ?: role,
                                tone = BadgeTone.Neutral,
                            )
                        }
                    }
                }
                TextButton(onClick = onRefreshAccount, enabled = !authState.busy) {
                    Text(if (authState.busy) "Đang làm mới…" else "Làm mới quyền", color = colors.brandText)
                }
            }
        }
    }
}

@Composable
private fun OverviewLink(tab: AppTab, title: String, detail: String, onOpenTab: (AppTab) -> Unit) {
    val colors = SuncareTheme.colors
    Surface(
        onClick = { onOpenTab(tab) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SuncareIconTile(tab.icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.brandText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PlaceholderTab(
    title: String,
    description: String,
    contentPadding: PaddingValues,
    actionLabel: String,
    onAction: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            SuncareEmptyView(
                title = title,
                description = description,
                actionLabel = actionLabel,
                onAction = onAction,
            )
        }
    }
}
