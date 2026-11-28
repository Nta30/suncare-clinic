package com.example.suncareclinic.ui.screens.profile

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.suncareclinic.data.dto.PersonalProfileResponseDto
import com.example.suncareclinic.ui.components.BadgeTone
import com.example.suncareclinic.ui.components.StatusBadge
import com.example.suncareclinic.ui.components.SuncareButton
import com.example.suncareclinic.ui.components.SuncareButtonVariant
import com.example.suncareclinic.ui.components.SuncareCard
import com.example.suncareclinic.ui.components.SuncareErrorView
import com.example.suncareclinic.ui.components.SuncareInlineBanner
import com.example.suncareclinic.ui.components.SuncareLoadingView
import com.example.suncareclinic.ui.components.SuncareTextField
import com.example.suncareclinic.ui.components.SuncareScreenHeader
import com.example.suncareclinic.ui.components.SuncareSectionHeading
import com.example.suncareclinic.ui.components.suncarePageWidth
import com.example.suncareclinic.ui.theme.SuncareTheme

private val SEX_OPTIONS = listOf(
    "UNKNOWN" to "Chưa xác định",
    "MALE" to "Nam",
    "FEMALE" to "Nữ",
    "OTHER" to "Khác",
)

private val SEX_LABELS = SEX_OPTIONS.toMap()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    contentPadding: PaddingValues,
    onReload: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onFieldChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onRequestLogout: () -> Unit,
) {
    val colors = SuncareTheme.colors

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
        if (state.loading && state.profile == null) {
            item {
                SuncareLoadingView(message = "Đang tải hồ sơ cá nhân...")
            }
            return@LazyColumn
        }

        if (state.loadError != null && state.profile == null) {
            item {
                SuncareErrorView(
                    title = "Không tải được hồ sơ",
                    message = state.loadError,
                    onRetry = onReload,
                )
            }
            return@LazyColumn
        }

        val profile = state.profile ?: return@LazyColumn
        val isPatient = profile.accountType == "PATIENT"

        item {
            SuncareScreenHeader(
                title = profile.fullName.ifBlank { "Hồ sơ cá nhân" },
                description = "Mã hồ sơ: ${profile.code}",
                icon = Icons.Outlined.Badge,
                action = {
                    StatusBadge(
                        label = if (isPatient) "Bệnh nhân" else "Nhân viên",
                        tone = BadgeTone.Neutral,
                    )
                },
            )
        }

        if (!state.editing) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SuncareButton(
                        text = "Chỉnh sửa hồ sơ",
                        onClick = onStartEdit,
                        modifier = Modifier.weight(1f),
                    )
                    SuncareButton(
                        text = "Tải lại",
                        onClick = onReload,
                        variant = SuncareButtonVariant.Outline,
                        loading = state.loading,
                    )
                }
            }
        }

        if (!state.successMessage.isNullOrBlank()) {
            item {
                SuncareInlineBanner(message = state.successMessage, tone = BadgeTone.Success)
            }
        }

        if (!state.actionError.isNullOrBlank()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuncareInlineBanner(message = state.actionError, tone = BadgeTone.Danger)
                    if (state.conflictError) {
                        SuncareButton(
                            text = "Tải lại phiên bản mới nhất",
                            onClick = onReload,
                            variant = SuncareButtonVariant.Outline,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (!state.editing) {
            // Chế độ xem chỉ đọc
            item {
                ReadOnlyProfileSection(profile = profile, isPatient = isPatient)
            }

            if (isPatient) {
                item {
                    SuncareCard {
                        SuncareSectionHeading("Thông tin bệnh nhân", "Giấy tờ và liên hệ khẩn cấp")
                        Spacer(modifier = Modifier.height(12.dp))
                        InfoRow("Số giấy tờ tùy thân", profile.identityNumber)
                        InfoDivider()
                        InfoRow("Người liên hệ khẩn cấp", profile.emergencyName)
                        InfoDivider()
                        InfoRow("SĐT khẩn cấp", profile.emergencyPhone)
                    }
                }
            } else {
                item {
                    SuncareCard {
                        SuncareSectionHeading("Thông tin chuyên môn", "Do quản trị viên quản lý")
                        Spacer(modifier = Modifier.height(12.dp))
                        InfoRow("Số giấy phép hành nghề", profile.professionalLicense)
                        InfoDivider()
                        InfoRow("Trình độ chuyên môn", profile.qualification)
                        InfoDivider()
                        InfoRow(
                            "Khoa công tác",
                            profile.departments.joinToString(", ").ifBlank { null },
                        )
                        InfoDivider()
                        InfoRow(
                            "Chuyên khoa",
                            profile.specialties.joinToString(", ").ifBlank { null },
                        )
                    }
                }
            }

            item {
                SuncareButton(
                    text = "Đăng xuất",
                    onClick = onRequestLogout,
                    variant = SuncareButtonVariant.Danger,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            // Chế độ chỉnh sửa
            item {
                SuncareCard {
                    SuncareSectionHeading("Chỉnh sửa thông tin cá nhân")
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SuncareTextField(
                            label = "Họ và tên",
                            value = state.form.fullName,
                            onValueChange = { onFieldChange("fullName", it) },
                            errorText = state.fieldErrors["fullName"],
                            required = true,
                            enabled = !state.saving,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        SuncareTextField(
                            label = "Ngày sinh",
                            value = state.form.birthDate,
                            onValueChange = { onFieldChange("birthDate", it) },
                            placeholder = "YYYY-MM-DD",
                            errorText = state.fieldErrors["birthDate"],
                            enabled = !state.saving,
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
                                    FilterChip(
                                        selected = state.form.sex == code,
                                        enabled = !state.saving,
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
                            errorText = state.fieldErrors["phone"],
                            enabled = !state.saving,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        SuncareTextField(
                            label = "Email",
                            value = state.form.email,
                            onValueChange = { onFieldChange("email", it) },
                            errorText = state.fieldErrors["email"],
                            enabled = !state.saving,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        SuncareTextField(
                            label = "Địa chỉ",
                            value = state.form.address,
                            onValueChange = { onFieldChange("address", it) },
                            errorText = state.fieldErrors["address"],
                            enabled = !state.saving,
                            singleLine = false,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )

                        if (isPatient) {
                            HorizontalDivider(color = colors.border)
                            SuncareTextField(
                                label = "Số giấy tờ tùy thân",
                                value = state.form.identityNumber,
                                onValueChange = { onFieldChange("identityNumber", it) },
                                errorText = state.fieldErrors["identityNumber"],
                                enabled = !state.saving,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            SuncareTextField(
                                label = "Người liên hệ khẩn cấp",
                                value = state.form.emergencyName,
                                onValueChange = { onFieldChange("emergencyName", it) },
                                errorText = state.fieldErrors["emergencyName"],
                                enabled = !state.saving,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            SuncareTextField(
                                label = "SĐT khẩn cấp",
                                value = state.form.emergencyPhone,
                                onValueChange = { onFieldChange("emergencyPhone", it) },
                                errorText = state.fieldErrors["emergencyPhone"],
                                enabled = !state.saving,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done,
                                ),
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SuncareButton(
                                text = "Bỏ thay đổi",
                                onClick = onCancelEdit,
                                variant = SuncareButtonVariant.Outline,
                                enabled = !state.saving,
                                modifier = Modifier.weight(1f),
                            )
                            SuncareButton(
                                text = "Lưu thay đổi",
                                onClick = onSave,
                                variant = SuncareButtonVariant.Primary,
                                loading = state.saving,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyProfileSection(
    profile: PersonalProfileResponseDto,
    isPatient: Boolean,
) {
    val colors = SuncareTheme.colors
    SuncareCard {
        SuncareSectionHeading("Thông tin cá nhân")
        Spacer(modifier = Modifier.height(12.dp))
        InfoRow("Ngày sinh", profile.birthDate)
        InfoDivider()
        InfoRow("Giới tính", SEX_LABELS[profile.sex] ?: profile.sex)
        InfoDivider()
        InfoRow("Số điện thoại", profile.phone)
        InfoDivider()
        InfoRow("Email", profile.email)
        InfoDivider()
        InfoRow("Địa chỉ", profile.address)
        if (!isPatient) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mã hồ sơ và loại tài khoản là cố định theo hệ thống.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    val colors = SuncareTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "Chưa cập nhật",
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isNullOrBlank()) colors.muted else colors.ink,
        )
    }
}

@Composable
private fun InfoDivider() {
    Spacer(modifier = Modifier.height(10.dp))
    HorizontalDivider(color = SuncareTheme.colors.border)
    Spacer(modifier = Modifier.height(10.dp))
}
