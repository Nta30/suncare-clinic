package com.example.suncareclinic.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.suncareclinic.data.dto.CurrentAccountDto
import com.example.suncareclinic.ui.components.BadgeTone
import com.example.suncareclinic.ui.components.StatusBadge
import com.example.suncareclinic.ui.components.SuncareButton
import com.example.suncareclinic.ui.components.SuncareButtonVariant
import com.example.suncareclinic.ui.components.SuncareCard
import com.example.suncareclinic.ui.components.SuncareInlineBanner
import com.example.suncareclinic.ui.theme.SuncareClinicTheme
import com.example.suncareclinic.ui.theme.SuncareTheme

private val ROLE_LABELS = mapOf(
    "PATIENT" to "Bệnh nhân",
    "DOCTOR" to "Bác sĩ",
    "LAB_STAFF" to "Nhân viên CLS",
    "DEPARTMENT_HEAD" to "Trưởng khoa",
    "CASHIER" to "Thu ngân",
    "SYSTEM_ADMIN" to "Quản trị viên",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignedInHomeScreen(
    account: CurrentAccountDto,
    state: AuthUiState,
    onRefreshAccount: () -> Unit,
    onConfirmLogout: (onSuccess: () -> Unit) -> Unit,
    onClearLogoutError: () -> Unit,
) {
    val colors = SuncareTheme.colors
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.canvas,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SuncareCard(highlighted = true) {
                    Text(
                        text = "SUNCARE CLINIC",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.brandText,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Xin chào, ${account.loginName}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.ink,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Phiên đăng nhập đã được xác thực với máy chủ. Thanh điều hướng, Hồ sơ cá nhân (A03) và Tìm/Đặt lịch trực tiếp (A04–A05) sẽ được nối tiếp ở bước kế tiếp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
            }

            if (!state.successMessage.isNullOrBlank()) {
                item {
                    SuncareInlineBanner(message = state.successMessage, tone = BadgeTone.Success)
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                item {
                    SuncareInlineBanner(message = state.errorMessage, tone = BadgeTone.Danger)
                }
            }

            item {
                SuncareCard {
                    Text(
                        text = "Thông tin phiên hiện tại",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Mã tài khoản: #${account.accountId}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink,
                        )
                        Text(
                            text = "Loại tài khoản: ${if (account.accountType == "PATIENT") "Bệnh nhân" else "Nhân viên"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
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
                                    tone = BadgeTone.Success,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuncareButton(
                            text = "Làm mới quyền",
                            onClick = onRefreshAccount,
                            variant = SuncareButtonVariant.Outline,
                            loading = state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SuncareButton(
                            text = "Đăng xuất",
                            onClick = {
                                onClearLogoutError()
                                showLogoutDialog = true
                            },
                            variant = SuncareButtonVariant.Danger,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.logoutBusy) showLogoutDialog = false
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
                    if (!state.logoutError.isNullOrBlank()) {
                        SuncareInlineBanner(message = state.logoutError, tone = BadgeTone.Danger)
                    }
                }
            },
            confirmButton = {
                SuncareButton(
                    text = "Đăng xuất",
                    onClick = { onConfirmLogout { showLogoutDialog = false } },
                    variant = SuncareButtonVariant.Primary,
                    loading = state.logoutBusy,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    enabled = !state.logoutBusy,
                ) {
                    Text("Ở lại", color = colors.ink)
                }
            },
            containerColor = colors.surface,
        )
    }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun SignedInHomePreview() {
    SuncareClinicTheme {
        SignedInHomeScreen(
            account = CurrentAccountDto(
                accountId = 12,
                loginName = "nguyenvanan",
                accountType = "PATIENT",
                patientId = 8,
                roles = listOf("PATIENT"),
                permissions = listOf("APPOINTMENT_CREATE_SELF"),
            ),
            state = AuthUiState(),
            onRefreshAccount = {},
            onConfirmLogout = {},
            onClearLogoutError = {},
        )
    }
}
