package com.nathzramirez.thesisflow.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathzramirez.thesisflow.designsystem.component.GlowDot
import com.nathzramirez.thesisflow.designsystem.theme.Manrope
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import kotlin.math.ceil

/** One stacked part of every column, e.g. "Tasks done". */
data class ChartSeries(val label: String, val color: Color, val values: List<Int>)

/**
 * A stacked column chart themed for the aurora design: rounded glowing columns,
 * dashed guides and no axis lines. Vico draws it; [series] all share [xLabels].
 * The y axis counts whole items, so its steps are whole numbers.
 */
@Composable
fun StackedColumnChart(
    series: List<ChartSeries>,
    xLabels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    columnWidth: Int = 16,
    /**
     * Space between columns. The chart stretches to fill its width, so with few
     * columns a wider gap is what keeps them from turning into slabs.
     */
    columnSpacing: Int = 32,
    /** Label every n-th column, counting back from the last, when the labels don't all fit. */
    labelEvery: Int = 1,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(series) {
        producer.runTransaction {
            columnModel { series.forEach { series(it.values) } }
        }
    }

    val peak = xLabels.indices.maxOfOrNull { x -> series.sumOf { it.values.getOrElse(x) { 0 } } } ?: 0
    // At least 0..4, so an empty or quiet chart still has a sensible scale.
    val maxY = maxOf(4, peak)
    val step = ceil(maxY / 4.0)
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontFamily = Manrope,
    )
    val guide = MaterialTheme.colorScheme.outlineVariant

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    series.map { item ->
                        rememberLineComponent(
                            fill = Fill(item.color),
                            thickness = columnWidth.dp,
                            shape = RoundedCornerShape(4.dp),
                        )
                    },
                ),
                columnCollectionSpacing = columnSpacing.dp,
                mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                rangeProvider = remember(maxY, step) {
                    CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = ceil(maxY / step) * step)
                },
            ),
            startAxis = VerticalAxis.rememberStart(
                line = null,
                tick = null,
                label = rememberAxisLabelComponent(style = labelStyle),
                guideline = rememberAxisGuidelineComponent(fill = Fill(guide)),
                valueFormatter = remember { CartesianValueFormatter.decimal(decimalCount = 0) },
                itemPlacer = remember(step) { VerticalAxis.ItemPlacer.step({ step }) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = rememberAxisLineComponent(fill = Fill(guide)),
                tick = null,
                guideline = null,
                label = rememberAxisLabelComponent(style = labelStyle),
                // Vico rejects blank labels; which columns get one is the item placer's job.
                valueFormatter = remember(xLabels) {
                    CartesianValueFormatter { _, x, _ -> xLabels.getOrNull(x.toInt())?.ifBlank { null } ?: "${x.toInt() + 1}" }
                },
                itemPlacer = remember(labelEvery, xLabels.size) {
                    HorizontalAxis.ItemPlacer.aligned(
                        spacing = { labelEvery },
                        offset = { (xLabels.size - 1) % labelEvery },
                    )
                },
            ),
        ),
        modelProducer = producer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .semantics { contentDescription = description },
    )
}

@Composable
fun ChartLegend(series: List<ChartSeries>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        series.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlowDot(item.color, size = 8.dp)
                Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
