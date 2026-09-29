package com.nathzramirez.thesisflow.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nathzramirez.thesisflow.R

/*
 * Space Grotesk (headings) has the technical, geometric feel of the design;
 * Manrope (text) stays readable at small sizes. Each ships as one variable
 * font file, and every weight below is a point on its weight axis.
 */

@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val SpaceGrotesk = FontFamily(
    variable(R.font.space_grotesk, FontWeight.Normal),
    variable(R.font.space_grotesk, FontWeight.Medium),
    variable(R.font.space_grotesk, FontWeight.SemiBold),
    variable(R.font.space_grotesk, FontWeight.Bold),
)

val Manrope = FontFamily(
    variable(R.font.manrope, FontWeight.Normal),
    variable(R.font.manrope, FontWeight.Medium),
    variable(R.font.manrope, FontWeight.SemiBold),
    variable(R.font.manrope, FontWeight.Bold),
)

private fun display(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold, tracking: Double = -0.5) =
    TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

private fun text(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = Manrope,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

internal val AppTypography = Typography(
    displayLarge = display(54, 60, FontWeight.Bold, -1.5),
    displayMedium = display(44, 50, FontWeight.Bold, -1.0),
    displaySmall = display(36, 42, FontWeight.Bold, -0.8),
    headlineLarge = display(32, 38),
    headlineMedium = display(28, 34),
    headlineSmall = display(24, 30),
    titleLarge = display(21, 28, tracking = -0.2),
    titleMedium = display(17, 24, FontWeight.Medium, -0.1),
    titleSmall = display(15, 20, FontWeight.Medium, 0.0),
    bodyLarge = text(16, 24),
    bodyMedium = text(14, 20),
    bodySmall = text(12, 16),
    labelLarge = text(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = text(12, 16, FontWeight.SemiBold, 0.3),
    labelSmall = text(11, 16, FontWeight.SemiBold, 0.5),
)
