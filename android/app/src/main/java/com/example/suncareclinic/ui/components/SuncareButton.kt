package com.example.suncareclinic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.suncareclinic.ui.theme.SuncareTheme

enum class SuncareButtonVariant {
    Primary,
    Outline,
    Danger,
}

@Composable
fun SuncareButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SuncareButtonVariant = SuncareButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = SuncareTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val isInteractive = enabled && !loading

    when (variant) {
        SuncareButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                enabled = isInteractive,
                shape = shape,
                contentPadding = contentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandAccent,
                    contentColor = colors.ink,
                    disabledContainerColor = colors.brandAccent.copy(alpha = 0.45f),
                    disabledContentColor = colors.ink.copy(alpha = 0.55f),
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                ),
                modifier = modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                ButtonContent(
                    text = text,
                    loading = loading,
                    indicatorColor = colors.ink,
                )
            }
        }

        SuncareButtonVariant.Outline -> {
            OutlinedButton(
                onClick = onClick,
                enabled = isInteractive,
                shape = shape,
                contentPadding = contentPadding,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isInteractive) colors.borderStrong else colors.border,
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.surface,
                    contentColor = colors.ink,
                    disabledContainerColor = colors.surface.copy(alpha = 0.6f),
                    disabledContentColor = colors.muted,
                ),
                modifier = modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                ButtonContent(
                    text = text,
                    loading = loading,
                    indicatorColor = colors.ink,
                )
            }
        }

        SuncareButtonVariant.Danger -> {
            OutlinedButton(
                onClick = onClick,
                enabled = isInteractive,
                shape = shape,
                contentPadding = contentPadding,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isInteractive) colors.danger else colors.danger.copy(alpha = 0.4f),
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.surface,
                    contentColor = colors.danger,
                    disabledContainerColor = colors.surface.copy(alpha = 0.6f),
                    disabledContentColor = colors.danger.copy(alpha = 0.45f),
                ),
                modifier = modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                ButtonContent(
                    text = text,
                    loading = loading,
                    indicatorColor = colors.danger,
                )
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    loading: Boolean,
    indicatorColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = indicatorColor,
            )
        }
        Text(
            text = if (loading) "Đang xử lý..." else text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
