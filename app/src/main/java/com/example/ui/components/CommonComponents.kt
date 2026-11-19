package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalBaddieColors
import com.example.ui.theme.LocalBaddieSpacing

@Composable
fun BaddieCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalBaddieColors.current
    val spacing = LocalBaddieSpacing.current
    val containerColor = if (elevated) colors.surfaceElevated else MaterialTheme.colorScheme.surface
    val cardModifier = modifier.fillMaxWidth()

    Surface(
        modifier = if (onClick != null) cardModifier.clickable(onClick = onClick) else cardModifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier.padding(spacing.cardPadding),
            content = content
        )
    }
}

@Composable
fun BaddiePill(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun BaddieCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(180),
        label = "checkbox_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else LocalBaddieColors.current.border,
        animationSpec = tween(180),
        label = "checkbox_border"
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onToggle)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .border(1.5.dp, borderColor, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun BaddieSectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun BaddieEmptyState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showArtwork: Boolean = true
) {
    BaddieCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showArtwork) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_focus),
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

data class BarDatum(val label: String, val value: Float)

@Composable
fun BaddieBarChart(
    data: List<BarDatum>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val trackColor = LocalBaddieColors.current.borderSubtle
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            if (data.isEmpty()) return@Canvas
            val maxVal = (data.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(1f)
            val count = data.size
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.52f).coerceAtMost(28.dp.toPx())

            data.forEachIndexed { idx, item ->
                val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                val barHeight = ((item.value / maxVal) * size.height).coerceAtLeast(if (item.value > 0f) 4.dp.toPx() else 0f)
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            data.forEach { item ->
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun BaddieActivityHeatmap(
    countsLast28Days: List<Int>,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val emptyColor = LocalBaddieColors.current.borderSubtle
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
    ) {
        val cols = 7
        val rows = 4
        val gap = 6.dp.toPx()
        val cellW = (size.width - gap * (cols - 1)) / cols
        val cellH = (size.height - gap * (rows - 1)) / rows
        val maxVal = (countsLast28Days.maxOrNull() ?: 1).coerceAtLeast(1)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                val value = countsLast28Days.getOrElse(idx) { 0 }
                val cellColor = if (value <= 0) {
                    emptyColor
                } else {
                    val alpha = (0.25f + 0.75f * (value.toFloat() / maxVal)).coerceIn(0.25f, 1f)
                    accent.copy(alpha = alpha)
                }
                drawRoundRect(
                    color = cellColor,
                    topLeft = Offset(c * (cellW + gap), r * (cellH + gap)),
                    size = Size(cellW, cellH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
    }
}
