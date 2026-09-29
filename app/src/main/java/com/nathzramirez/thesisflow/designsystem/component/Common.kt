package com.nathzramirez.thesisflow.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = AuroraTheme.colors.gradientEnd, strokeWidth = 3.dp)
        if (message != null) HudLabel(message)
    }
}

/** A glowing orb around an icon: the visual anchor of empty and error states. */
@Composable
fun GlowOrb(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val aurora = AuroraTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(aurora.glow.copy(alpha = 0.45f), Color.Transparent)),
                    radius = this.size.minDimension,
                )
            }
            .clip(CircleShape)
            .background(aurora.glassFillStrong)
            .border(1.5.dp, aurora.brandGradient, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = aurora.gradientEnd, modifier = Modifier.size(size * 0.42f))
    }
}

/** Centered orb, title and body with optional actions below: empty and error states. */
@Composable
fun MessageScreen(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) GlowOrb(icon, modifier = Modifier.padding(bottom = 12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .widthIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { actions() }
    }
}

/** Profile photo, or initials, inside a thin gradient ring. */
@Composable
fun Avatar(name: String, photoUrl: String?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val aurora = AuroraTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .border(1.5.dp, aurora.brandGradient, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            // Scaled with the avatar, so wide pairs like "CM" fit even at 28 dp.
            Text(
                text = initialsOf(name),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = (size.value * 0.34f).sp, lineHeight = (size.value * 0.4f).sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

internal fun initialsOf(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
