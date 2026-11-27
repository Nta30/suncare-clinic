package com.example.suncareclinic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.suncareclinic.ui.theme.SuncareTheme

@Composable
fun SuncareLoadingView(
    message: String = "Đang tải dữ liệu...",
    modifier: Modifier = Modifier,
) {
    val colors = SuncareTheme.colors
    SuncareCard(
        modifier = modifier,
        contentPadding = 24.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = colors.brandText,
                trackColor = colors.warningBorder,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SuncareEmptyView(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = SuncareTheme.colors
    SuncareCard(
        modifier = modifier,
        contentPadding = 24.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SuncareIconTile(Icons.Outlined.Inbox)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                SuncareButton(
                    text = actionLabel,
                    onClick = onAction,
                    variant = SuncareButtonVariant.Primary,
                )
            }
        }
    }
}

@Composable
fun SuncareErrorView(
    title: String = "Không thể tải dữ liệu",
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = SuncareTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.dangerSoft,
        border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.ReportProblem, contentDescription = null, tint = colors.danger)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.danger,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(4.dp))
                SuncareButton(
                    text = "Thử lại",
                    onClick = onRetry,
                    variant = SuncareButtonVariant.Outline,
                )
            }
        }
    }
}

@Composable
fun SuncareInlineBanner(
    message: String,
    tone: BadgeTone,
    modifier: Modifier = Modifier,
) {
    val colors = SuncareTheme.colors
    val (background, border, textColor) = when (tone) {
        BadgeTone.Success -> Triple(colors.leafSoft, colors.leaf, colors.success)
        BadgeTone.Warning -> Triple(colors.warningSoft, colors.warningBorder, colors.warning)
        BadgeTone.Danger -> Triple(colors.dangerSoft, colors.danger.copy(alpha = 0.5f), colors.danger)
        BadgeTone.Neutral -> Triple(colors.surface, colors.border, colors.ink)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }
    }
}
