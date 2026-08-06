package com.ireddragonicy.konabessnext.ui.compose.gpu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.ireddragonicy.konabessnext.model.gpu.GpuSample

/**
 * MPAndroidChart wrapper for the GPU stability screen.
 *
 * Two series are drawn:
 *  - **Cur MHz** on the left axis (Adreno clock in MHz, locked to min/max of the candidate set)
 *  - **Temp °C** on the right axis (0..100 °C)
 *
 * Both series use highly saturated colours so they remain readable in either
 * light or dark theme. Labels and unit formatters come from string resources
 * so they localize properly.
 */
@Composable
fun GpuStabilityChart(
    samples: List<GpuSample>,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    gridColor: Color,
    minFreqHz: Long?,
    maxFreqHz: Long?,
    freqLegend: String,
    tempLegend: String,
    freqAxisFmt: (Int) -> String,
    secondsAxisFmt: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val freqLineColor = primaryColor.toArgb()
    val tempLineColor = accentColor.toArgb()

    val onSurfaceArgb = onSurfaceColor.toArgb()
    val surfaceArgb = surfaceColor.toArgb()
    val gridArgb = gridColor.copy(alpha = 0.18f).toArgb()

    // When the caller pins a candidate min/max (pinning mode) we use that as
    // the axis range. When both are null (freerun mode) we derive the range
    // from the samples themselves, with a small padding so the curve never
    // sits on the chart edge.
    val (axisMinMhz, axisMaxMhz) = computeFreqAxisRange(samples, minFreqHz, maxFreqHz)

    AndroidView(
        modifier = modifier.fillMaxWidth().height(180.dp),
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(false)
                setNoDataText("")
                isDragEnabled = true
                setScaleEnabled(false)
                legend.apply {
                    verticalAlignment = Legend.LegendVerticalAlignment.TOP
                    horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                    orientation = Legend.LegendOrientation.HORIZONTAL
                    textColor = onSurfaceArgb
                    textSize = 11f
                    form = Legend.LegendForm.LINE
                    formLineWidth = 2.5f
                }
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = onSurfaceArgb
                    granularity = 1f
                    textSize = 9f
                }
                axisLeft.apply {
                    textColor = freqLineColor
                    setGridColor(gridArgb)
                    axisMinimum = axisMinMhz
                    axisMaximum = axisMaxMhz
                    textSize = 9f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String = freqAxisFmt(value.toInt())
                    }
                }
                axisRight.apply {
                    textColor = tempLineColor
                    // Temp shares the right axis with a fixed 0..100 °C range.
                    axisMinimum = 0f
                    axisMaximum = 100f
                    textSize = 9f
                    setDrawGridLines(false)
                }
                setBackgroundColor(surfaceArgb)
                setExtraOffsets(8f, 8f, 8f, 12f)
            }
        },
        update = { chart ->
            val data = buildLineData(
                samples = samples,
                freqColor = freqLineColor,
                tempColor = tempLineColor,
                freqLegend = freqLegend,
                tempLegend = tempLegend,
                secondsAxisFmt = secondsAxisFmt,
            )
            chart.data = data
            chart.notifyDataSetChanged()
            chart.invalidate()
            chart.fitScreen()
        },
    )
}

/**
 * Resolve the (min, max) MHz range to use for the freq Y axis.
 *
 * - Pinning mode passes both `minFreqHz` and `maxFreqHz` non-null; we honor those.
 * - Freerun mode passes both null; we derive from the live samples.
 * - If samples are empty (no data yet) we fall back to a sensible default.
 */
private fun computeFreqAxisRange(
    samples: List<GpuSample>,
    minFreqHz: Long?,
    maxFreqHz: Long?,
): Pair<Float, Float> {
    if (minFreqHz != null && maxFreqHz != null) {
        return (minFreqHz / 1_000_000f) - 50f to (maxFreqHz / 1_000_000f) + 50f
    }
    val observed = samples.mapNotNull { s ->
        if (s.curFreqHz > 0L) s.curFreqHz else null
    }
    if (observed.isEmpty()) {
        // Sensible default until the first sample lands.
        return 0f to 1500f
    }
    val lo = observed.min().toFloat() / 1_000_000f
    val hi = observed.max().toFloat() / 1_000_000f
    val padding = ((hi - lo) * 0.1f).coerceAtLeast(50f)
    return (lo - padding).coerceAtLeast(0f) to (hi + padding)
}

/**
 * Builds two LineDataSets (Cur MHz + Temp °C).
 *
 * The X axis is sample-index based (one tick per second); the Y axis for
 * Cur MHz is locked to the freq range passed into the chart, while Temp uses
 * the right axis with a fixed 0..100 °C range.
 */
private fun buildLineData(
    samples: List<GpuSample>,
    freqColor: Int,
    tempColor: Int,
    freqLegend: String,
    tempLegend: String,
    secondsAxisFmt: (Int) -> String,
): LineData {
    val freqEntries = ArrayList<Entry>(samples.size)
    val tempEntries = ArrayList<Entry>(samples.size)
    samples.forEachIndexed { idx, s ->
        // cur_freq is required (always emitted); only skip when 0 to avoid
        // collapsing the line to the X axis when the device hasn't reported
        // a reading yet.
        if (s.curFreqHz > 0L) {
            freqEntries += Entry(idx.toFloat(), (s.curFreqHz / 1_000_000f))
        }
        val temp = s.temperatureC
        if (temp != null) tempEntries += Entry(idx.toFloat(), temp)
    }

    val freqSet = LineDataSet(freqEntries, freqLegend).apply {
        color = freqColor
        setCircleColor(freqColor)
        lineWidth = 2.5f
        circleRadius = 3f
        setDrawValues(false)
        valueTextColor = freqColor
        axisDependency = YAxis.AxisDependency.LEFT
        setDrawCircles(false)
    }
    val tempSet = LineDataSet(tempEntries, tempLegend).apply {
        color = tempColor
        setCircleColor(tempColor)
        lineWidth = 2.5f
        circleRadius = 3f
        setDrawValues(false)
        setDrawCircles(false)
        axisDependency = YAxis.AxisDependency.RIGHT
    }
    val lineData = LineData(freqSet, tempSet)
    lineData.setValueFormatter(object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String = secondsAxisFmt(value.toInt())
    })
    return lineData
}