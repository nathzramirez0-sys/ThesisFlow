package com.nathzramirez.thesisflow.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme

/**
 * The app's backdrop: a deep base colour, a faint HUD grid that fades out
 * towards the middle of the screen, and three soft light sources that drift
 * slowly. Everything is drawn in the draw phase, so the drift never recomposes.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val aurora = AuroraTheme.colors
    val base = MaterialTheme.colorScheme.background
    val drift by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                val w = size.width
                val h = size.height

                val step = 32.dp.toPx()
                val gridBottom = h * 0.55f
                val fade = Brush.verticalGradient(listOf(aurora.gridLine, Color.Transparent), endY = gridBottom)
                var x = 0f
                while (x <= w) {
                    drawLine(fade, Offset(x, 0f), Offset(x, gridBottom), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y <= gridBottom) {
                    drawLine(aurora.gridLine.copy(alpha = aurora.gridLine.alpha * (1f - y / gridBottom)),
                        Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                    y += step
                }

                fun glow(color: Color, cx: Float, cy: Float, radius: Float) = drawCircle(
                    brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = radius),
                    radius = radius,
                    center = Offset(cx, cy),
                )
                glow(aurora.blobViolet, w * (0.05f + 0.15f * drift), h * 0.04f, w * 0.95f)
                glow(aurora.blobCyan, w * (1.0f - 0.12f * drift), h * (0.42f + 0.06f * drift), w * 0.8f)
                glow(aurora.blobMagenta, w * (0.15f + 0.1f * drift), h * 0.98f, w * 0.75f)
            },
        content = content,
    )
}

/**
 * A translucent card with a light-catching top edge. Real backdrop blur isn't
 * available on older Android versions, so the glass effect comes from a
 * see-through fill over the glowing background plus a gradient border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    strong: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val aurora = AuroraTheme.colors
    val fill = if (strong) aurora.glassFillStrong else aurora.glassFill
    val border = BorderStroke(1.dp, aurora.glassBorder)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = fill, border = border, content = inner)
    } else {
        Surface(modifier = modifier, shape = shape, color = fill, border = border, content = inner)
    }
}

/** A Scaffold that lets the aurora background show through. */
@Composable
fun AuroraScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content,
    )
}

/** A transparent top bar with an optional back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuroraTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
        ),
    )
}
