package com.example.suncareclinic.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suncareclinic.ui.components.BadgeTone
import com.example.suncareclinic.ui.components.SuncareButton
import com.example.suncareclinic.ui.components.SuncareButtonVariant
import com.example.suncareclinic.ui.components.SuncareCard
import com.example.suncareclinic.ui.components.SuncareInlineBanner
import com.example.suncareclinic.ui.components.SuncareTextField
import com.example.suncareclinic.ui.theme.SuncareClinicTheme
import com.example.suncareclinic.ui.theme.SuncareTheme

private val SEX_OPTIONS = listOf(
    "UNKNOWN" to "Chưa xác định",
    "MALE" to "Nam",
    "FEMALE" to "Nữ",
    "OTHER" to "Khác",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    state: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onFieldChange: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onRetryHealth: () -> Unit,
) {
    val colors = SuncareTheme.colors
    val registering = state.mode == AuthMode.Register
    val listState = rememberLazyListState()
    var showPasswordHelp by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.mode) {
        listState.scrollToItem(0)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.canvas,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth()
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 24.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { AuthHeader(registering = registering) }

            if (state.healthStatus == HealthStatus.Offline) {
                item { OfflineNotice(onRetry = onRetryHealth) }
            }

            // Bộ chuyển chế độ Đăng nhập / Đăng ký
            item {
                AuthModeSwitch(
                    current = state.mode,
                    enabled = !state.busy,
                    onSelect = onModeChange,
                )
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

            if (registering) {
                item {
                    SuncareCard(contentPadding = 20.dp) {
                        Text(
                            text = "Thông tin bệnh nhân",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SuncareTextField(
                                label = "Họ và tên",
                                value = state.form.fullName,
                                onValueChange = { onFieldChange("fullName", it) },
                                placeholder = "Nguyễn Văn An",
                                errorText = state.fieldErrors["fullName"],
                                required = true,
                                enabled = !state.busy,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            SuncareTextField(
                                label = "Ngày sinh",
                                value = state.form.birthDate,
                                onValueChange = { onFieldChange("birthDate", it) },
                                placeholder = "YYYY-MM-DD (ví dụ 1998-05-20)",
                                errorText = state.fieldErrors["birthDate"],
                                enabled = !state.busy,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                            Column {
                                Text(
                                    text = "Giới tính",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.ink,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    SEX_OPTIONS.forEach { (code, label) ->
                                        val selected = state.form.sex == code
                                        FilterChip(
                                            selected = selected,
                                            enabled = !state.busy,
                                            onClick = { onFieldChange("sex", code) },
                                            label = { Text(label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colors.warningSoft,
                                                selectedLabelColor = colors.ink,
                                            ),
                                        )
                                    }
                                }
                            }
                            SuncareTextField(
                                label = "Số điện thoại",
                                value = state.form.phone,
                                onValueChange = { onFieldChange("phone", it) },
                                placeholder = "09xxxxxxxx",
                                errorText = state.fieldErrors["phone"],
                                enabled = !state.busy,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                            SuncareTextField(
                                label = "Email",
                                value = state.form.email,
                                onValueChange = { onFieldChange("email", it) },
                                placeholder = "ten@example.com",
                                errorText = state.fieldErrors["email"],
                                enabled = !state.busy,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                            SuncareTextField(
                                label = "Số giấy tờ tùy thân (CCCD/CMND)",
                                value = state.form.identityNumber,
                                onValueChange = { onFieldChange("identityNumber", it) },
                                errorText = state.fieldErrors["identityNumber"],
                                enabled = !state.busy,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            SuncareTextField(
                                label = "Địa chỉ",
                                value = state.form.address,
                                onValueChange = { onFieldChange("address", it) },
                                errorText = state.fieldErrors["address"],
                                enabled = !state.busy,
                                singleLine = false,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                        }
                    }
                }

                item {
                    SuncareCard(contentPadding = 20.dp) {
                        Text(
                            text = "Liên hệ khẩn cấp (tùy chọn)",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SuncareTextField(
                                label = "Tên người liên hệ khẩn cấp",
                                value = state.form.emergencyName,
                                onValueChange = { onFieldChange("emergencyName", it) },
                                errorText = state.fieldErrors["emergencyName"],
                                enabled = !state.busy,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            SuncareTextField(
                                label = "Số điện thoại khẩn cấp",
                                value = state.form.emergencyPhone,
                                onValueChange = { onFieldChange("emergencyPhone", it) },
                                errorText = state.fieldErrors["emergencyPhone"],
                                enabled = !state.busy,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                        }
                    }
                }
            }

            // Khối thông tin đăng nhập (dùng chung cho cả Login & Register)
            item {
                SuncareCard(contentPadding = 20.dp) {
                    if (registering) {
                        Text(
                            text = "Thông tin đăng nhập",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SuncareTextField(
                            label = "Tên đăng nhập",
                            value = state.form.loginName,
                            onValueChange = { onFieldChange("loginName", it) },
                            placeholder = "nguyenvanan",
                            helperText = if (registering) "3–100 ký tự không dấu, không khoảng trắng." else null,
                            errorText = state.fieldErrors["loginName"],
                            required = true,
                            enabled = !state.busy,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        SuncareTextField(
                            label = "Mật khẩu",
                            value = state.form.password,
                            onValueChange = { onFieldChange("password", it) },
                            placeholder = if (registering) "Ít nhất 8 ký tự" else "Nhập mật khẩu",
                            helperText = if (registering) "Từ 8 đến 72 ký tự." else null,
                            errorText = state.fieldErrors["password"],
                            required = true,
                            enabled = !state.busy,
                            isPassword = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (registering) {
                                Spacer(modifier = Modifier.height(16.dp))
                            } else {
                                TextButton(
                                    onClick = { showPasswordHelp = true },
                                    enabled = !state.busy,
                                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        text = "Quên mật khẩu?",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (state.busy) colors.muted else colors.brandText,
                                    )
                                }
                            }
                            SuncareButton(
                                text = if (registering) "Tạo tài khoản bệnh nhân" else "Đăng nhập",
                                onClick = onSubmit,
                                variant = SuncareButtonVariant.Primary,
                                loading = state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            )
                        }
                    }
                }
            }
            if (state.healthStatus != HealthStatus.Offline) {
                item { ConnectionStatus(status = state.healthStatus) }
            }
        }
    }

    if (!registering && showPasswordHelp) {
        AlertDialog(
            onDismissRequest = { showPasswordHelp = false },
            title = { Text("Quên mật khẩu?") },
            text = { Text("Vui lòng liên hệ phòng khám Suncare để được hỗ trợ lấy lại quyền truy cập tài khoản.") },
            confirmButton = {
                TextButton(onClick = { showPasswordHelp = false }) {
                    Text("Đã hiểu", color = colors.brandText)
                }
            },
        )
    }
}

@Composable
private fun AuthHeader(registering: Boolean) {
    val colors = SuncareTheme.colors
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.warningSoft,
        border = BorderStroke(1.dp, colors.warningBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Suncare Clinic",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.brandText,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (registering) "Đăng ký" else "Đăng nhập",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 38.sp),
                    color = colors.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (registering) "Dành cho bệnh nhân" else "Bệnh nhân và bác sĩ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }
            SunflowerMark(modifier = Modifier.size(80.dp))
        }
    }
}

@Composable
private fun SunflowerMark(modifier: Modifier = Modifier) {
    val colors = SuncareTheme.colors
    Canvas(modifier) {
        val diameter = size.minDimension
        val petalWidth = diameter * 0.16f
        val petalHeight = diameter * 0.27f

        repeat(8) { petal ->
            rotate(degrees = petal * 45f) {
                drawOval(
                    color = colors.brandAccent,
                    topLeft = Offset(center.x - petalWidth / 2f, center.y - diameter * 0.48f),
                    size = Size(petalWidth, petalHeight),
                )
            }
        }

        drawCircle(color = colors.brandAccent, radius = diameter * 0.18f)
        val arm = diameter * 0.075f
        val stroke = diameter * 0.044f
        drawLine(colors.ink, center - Offset(arm, 0f), center + Offset(arm, 0f), stroke, StrokeCap.Round)
        drawLine(colors.ink, center - Offset(0f, arm), center + Offset(0f, arm), stroke, StrokeCap.Round)
    }
}

@Composable
private fun AuthModeSwitch(
    current: AuthMode,
    enabled: Boolean,
    onSelect: (AuthMode) -> Unit,
) {
    val colors = SuncareTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.canvas,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModeTab(
                label = "Đăng nhập",
                selected = current == AuthMode.Login,
                enabled = enabled,
                onClick = { onSelect(AuthMode.Login) },
                modifier = Modifier.weight(1f),
            )
            ModeTab(
                label = "Đăng ký",
                selected = current == AuthMode.Register,
                enabled = enabled,
                onClick = { onSelect(AuthMode.Register) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SuncareTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.warningSoft else colors.canvas,
        border = if (selected) BorderStroke(1.dp, colors.warningBorder) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.ink else colors.muted,
            )
        }
    }
}

@Composable
private fun OfflineNotice(onRetry: () -> Unit) {
    val colors = SuncareTheme.colors
    Surface(shape = RoundedCornerShape(16.dp), color = colors.dangerSoft) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = colors.danger, modifier = Modifier.size(20.dp))
            Text(
                text = "Không kết nối được máy chủ",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.danger,
            )
            TextButton(onClick = onRetry) {
                Text(
                    text = "Thử lại",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.danger,
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatus(status: HealthStatus) {
    val colors = SuncareTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == HealthStatus.Online) {
            Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = colors.success, modifier = Modifier.size(16.dp))
        }
        Text(
            text = if (status == HealthStatus.Online) "Đã kết nối máy chủ" else "Đang kiểm tra kết nối",
            style = MaterialTheme.typography.labelMedium,
            color = if (status == HealthStatus.Online) colors.success else colors.muted,
        )
    }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun AuthLoginPreview() {
    SuncareClinicTheme {
        AuthScreen(
            state = AuthUiState(
                session = SessionState.Guest,
                mode = AuthMode.Login,
                healthStatus = HealthStatus.Online,
            ),
            onModeChange = {},
            onFieldChange = { _, _ -> },
            onSubmit = {},
            onRetryHealth = {},
        )
    }
}
