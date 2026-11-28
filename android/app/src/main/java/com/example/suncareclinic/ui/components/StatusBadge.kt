package com.example.suncareclinic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.suncareclinic.ui.theme.SuncareTheme

enum class BadgeTone {
    Neutral,
    Success,
    Warning,
    Danger,
}

@Composable
fun StatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
    showDot: Boolean = true,
) {
    val colors = SuncareTheme.colors
    val (background, border, content, dotColor) = when (tone) {
        BadgeTone.Neutral -> Quad(colors.surface, colors.border, colors.ink, colors.muted)
        BadgeTone.Success -> Quad(colors.leafSoft, colors.leaf, colors.success, colors.success)
        BadgeTone.Warning -> Quad(colors.warningSoft, colors.warningBorder, colors.warning, colors.warning)
        BadgeTone.Danger -> Quad(colors.dangerSoft, colors.danger, colors.danger, colors.danger)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
            )
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
