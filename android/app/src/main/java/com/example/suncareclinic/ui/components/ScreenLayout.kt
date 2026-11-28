package com.example.suncareclinic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.suncareclinic.ui.theme.SuncareTheme

fun Modifier.suncarePageWidth(): Modifier = fillMaxSize()
    .wrapContentWidth()
    .widthIn(max = 680.dp)
    .fillMaxWidth()

@Composable
fun SuncareIconTile(icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = SuncareTheme.colors
    Surface(
        modifier = modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.goldSoft,
        contentColor = colors.brandText,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun SuncareScreenHeader(
    title: String,
    description: String,
    icon: ImageVector,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = SuncareTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.warningSoft,
        border = BorderStroke(1.dp, colors.warningBorder),
    ) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                repeat(3) { ring ->
                    drawCircle(
                        color = colors.brandAccent.copy(alpha = 0.20f),
                        radius = (28 + ring * 15).dp.toPx(),
                        center = Offset(size.width, size.height),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SuncareIconTile(icon)
                    action?.invoke()
                }
                SuncareSectionHeading(title, description, prominent = true)
            }
        }
    }
}

@Composable
fun SuncareSectionHeading(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    val colors = SuncareTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = if (prominent) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )
        if (!description.isNullOrBlank()) {
            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuncareScheduleCard(
    title: String,
    subtitle: String,
    code: String,
    schedule: String,
    status: Pair<String, BadgeTone>,
    onClick: () -> Unit,
    category: String? = null,
) {
    val colors = SuncareTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SuncareIconTile(Icons.Outlined.Schedule)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.brandText,
                    modifier = Modifier.size(20.dp),
                )
            }
            HorizontalDivider(color = colors.border)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(schedule, style = MaterialTheme.typography.labelLarge, color = colors.ink)
                Text(code, style = MaterialTheme.typography.labelMedium, color = colors.muted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusBadge(label = status.first, tone = status.second)
                if (category != null) {
                    StatusBadge(label = category, tone = BadgeTone.Neutral)
                }
            }
        }
    }
}
