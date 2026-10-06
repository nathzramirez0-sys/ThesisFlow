package com.nathzramirez.thesisflow.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nathzramirez.thesisflow.designsystem.theme.AuroraTheme
import com.nathzramirez.thesisflow.designsystem.theme.SpaceGrotesk

/** One number and its unit, e.g. 15 / DAYS. */
data class CountdownUnit(val value: Long, val label: String)

/**
 * Glass tiles with big gradient numbers, like a mission clock. Screen readers
 * hear [description] as one sentence instead of three separate numbers.
 */
@Composable
fun CountdownTiles(units: List<CountdownUnit>, description: String, modifier: Modifier = Modifier) {
    val aurora = AuroraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        units.forEach { unit ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(aurora.glassFillStrong)
                    .border(1.dp, aurora.glassBorder, MaterialTheme.shapes.medium)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    unit.value.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        brush = aurora.brandGradient,
                    ),
                )
                HudLabel(unit.label)
            }
        }
    }
}
