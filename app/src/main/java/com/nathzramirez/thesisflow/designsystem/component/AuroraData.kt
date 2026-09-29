package com.nathzramirez.thesisflow.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.designsystem.theme.SpaceGrotesk

/**
 * A glowing ring that fills with the brand gradient as [percent] rises, with
 * the number in the middle. It animates between values.
 */
@Composable
fun ProgressRing(
    percent: Int,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 10.dp,
    caption: String? = null,
) {
    val aurora = AuroraTheme.colors
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
    val fraction by animateFloatAsState(
        targetValue = percent.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "progress",
    )
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "$percent%" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val glowStroke = stroke * 2.4f
            val inset = glowStroke / 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (fraction > 0f) {
                val sweep = Brush.sweepGradient(listOf(aurora.gradientStart, aurora.gradientEnd, aurora.gradientStart))
                rotate(-90f) {
                    drawArc(sweep, 0f, 360f * fraction, false, topLeft, arcSize, alpha = 0.22f,
                        style = Stroke(glowStroke, cap = StrokeCap.Round))
                    drawArc(sweep, 0f, 360f * fraction, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value / 4.2f).sp,
                    brush = aurora.brandGradient,
                ),
            )
            if (caption != null) HudLabel(caption)
        }
    }
}

/** A thin bar filled with the brand gradient. */
@Composable
fun GradientProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "bar")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(AuroraTheme.colors.brandGradient),
        )
    }
}

/** A pill with a glowing dot, tinted by [color]. Used for chapter statuses and roles. */
@Composable
fun GlowPill(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GlowDot(color, size = 7.dp)
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A small dot with a soft halo, like an indicator light. */
@Composable
fun GlowDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent)),
                    radius = this.size.minDimension * 1.3f,
                )
                drawCircle(color, radius = this.size.minDimension / 2)
            },
    )
}

/** An invite code with each character in its own glass tile, so it's easy to read aloud. */
@Composable
fun CodeTiles(code: String, modifier: Modifier = Modifier) {
    val aurora = AuroraTheme.colors
    Row(
        modifier = modifier.semantics { contentDescription = code.toCharArray().joinToString(" ") },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        code.forEach { char ->
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 52.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(aurora.glassFillStrong)
                    .border(1.dp, aurora.brandGradient, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    char.toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}
