package com.nathzramirez.thesisflow.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.nathzramirez.thesisflow.domain.model.ChapterStatus

/**
 * Brand tokens Material 3 has no slot for: the violet-to-cyan gradient, the
 * glass surfaces, the background glow, and one colour per chapter status.
 * Read them through [AuroraTheme.colors].
 */
@Immutable
data class AuroraColors(
    val isDark: Boolean,
    val gradientStart: Color,
    val gradientEnd: Color,
    /** Deeper than the brand gradient, so white button text keeps 4.5:1 contrast. */
    val buttonStart: Color,
    val buttonEnd: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val glow: Color,
    val blobViolet: Color,
    val blobCyan: Color,
    val blobMagenta: Color,
    val gridLine: Color,
    val statusNotStarted: Color,
    val statusDrafting: Color,
    val statusReview: Color,
    val statusRevisions: Color,
    val statusApproved: Color,
) {
    val brandGradient: Brush get() = Brush.linearGradient(listOf(gradientStart, gradientEnd))
    val buttonGradient: Brush get() = Brush.horizontalGradient(listOf(buttonStart, buttonEnd))
    val glassBorder: Brush get() = Brush.verticalGradient(listOf(glassBorderTop, glassBorderBottom))

    fun statusColor(status: ChapterStatus): Color = when (status) {
        ChapterStatus.NOT_STARTED -> statusNotStarted
        ChapterStatus.DRAFTING -> statusDrafting
        ChapterStatus.FOR_REVIEW -> statusReview
        ChapterStatus.REVISIONS -> statusRevisions
        ChapterStatus.APPROVED -> statusApproved
    }
}

internal val DarkAurora = AuroraColors(
    isDark = true,
    gradientStart = Color(0xFF8B7CFF),
    gradientEnd = Color(0xFF2DD4EF),
    buttonStart = Color(0xFF6D4CFF),
    buttonEnd = Color(0xFF2F6FEB),
    glassFill = Color.White.copy(alpha = 0.05f),
    glassFillStrong = Color.White.copy(alpha = 0.09f),
    glassBorderTop = Color.White.copy(alpha = 0.18f),
    glassBorderBottom = Color.White.copy(alpha = 0.04f),
    glow = Color(0xFF7C5CFF),
    blobViolet = Color(0xFF6D4CFF).copy(alpha = 0.38f),
    blobCyan = Color(0xFF06B6D4).copy(alpha = 0.24f),
    blobMagenta = Color(0xFFC026D3).copy(alpha = 0.14f),
    gridLine = Color.White.copy(alpha = 0.035f),
    statusNotStarted = Color(0xFF7C86A5),
    statusDrafting = Color(0xFF2DD4EF),
    statusReview = Color(0xFFFBBF24),
    statusRevisions = Color(0xFFFB7185),
    statusApproved = Color(0xFF34D399),
)

internal val LightAurora = AuroraColors(
    isDark = false,
    gradientStart = Color(0xFF5B3DF5),
    gradientEnd = Color(0xFF06B6D4),
    buttonStart = Color(0xFF5B3DF5),
    buttonEnd = Color(0xFF2563EB),
    glassFill = Color.White.copy(alpha = 0.72f),
    glassFillStrong = Color.White.copy(alpha = 0.9f),
    glassBorderTop = Color.White,
    glassBorderBottom = Color(0xFF0B1024).copy(alpha = 0.07f),
    glow = Color(0xFF5B3DF5),
    blobViolet = Color(0xFF7C5CFF).copy(alpha = 0.22f),
    blobCyan = Color(0xFF22D3EE).copy(alpha = 0.2f),
    blobMagenta = Color(0xFFE879F9).copy(alpha = 0.12f),
    gridLine = Color(0xFF0B1024).copy(alpha = 0.04f),
    statusNotStarted = Color(0xFF64748B),
    statusDrafting = Color(0xFF0891B2),
    statusReview = Color(0xFFD97706),
    statusRevisions = Color(0xFFE11D48),
    statusApproved = Color(0xFF059669),
)

internal val LocalAurora = staticCompositionLocalOf { DarkAurora }

object AuroraTheme {
    val colors: AuroraColors
        @Composable @ReadOnlyComposable get() = LocalAurora.current
}
