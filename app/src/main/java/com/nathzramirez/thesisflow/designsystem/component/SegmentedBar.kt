package com.nathzramirez.thesisflow.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme

data class BarSegment(val label: String, val value: Int, val color: Color)

/**
 * A rounded bar split in proportion to [segments], with a legend underneath.
 * For a handful of categories this reads faster than a pie, and empty
 * categories still appear in the legend with a zero.
 */
@Composable
fun SegmentedBar(segments: List<BarSegment>, modifier: Modifier = Modifier) {
    val track = AuroraTheme.colors.glassFillStrong
    val total = segments.sumOf { it.value }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clearAndSetSemantics {
                    contentDescription = segments.joinToString { "${it.label} ${it.value}" }
                },
        ) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = radius)
            if (total == 0) return@Canvas
            val gap = 3.dp.toPx()
            val visible = segments.filter { it.value > 0 }
            val usable = size.width - gap * (visible.size - 1)
            var x = 0f
            visible.forEach { segment ->
                val width = usable * segment.value / total
                drawRoundRect(segment.color, Offset(x, 0f), Size(width, size.height), radius)
                x += width + gap
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            segments.forEach { segment ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlowDot(segment.color, size = 8.dp)
                    Text(
                        "${segment.label} ${segment.value}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
