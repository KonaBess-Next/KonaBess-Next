package com.ireddragonicy.konabessnext.ui.fragments

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level
import com.ireddragonicy.konabessnext.ui.widget.GpuActionToolbar
import com.ireddragonicy.konabessnext.utils.DtsHelper
import com.ireddragonicy.konabessnext.viewmodel.GpuFrequencyViewModel
import com.ireddragonicy.konabessnext.viewmodel.UiState
import dagger.hilt.android.AndroidEntryPoint
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap

/**
 * GPU Curve Editor Fragment - Allows visual editing of GPU frequency curves.
 */
@AndroidEntryPoint
class GpuCurveEditorFragment : Fragment() {

    private val gpuFrequencyViewModel: GpuFrequencyViewModel by activityViewModels()
    private var localBins: List<Bin> = ArrayList()

    private var chart: LineChart? = null
    private var globalOffsetSlider: Slider? = null
    private var offsetValueText: TextView? = null
    private var btnPlus: MaterialButton? = null
    private var btnMinus: MaterialButton? = null
    private var toolbarContainer: LinearLayout? = null

    // Data
    private val referenceEntries = ArrayList<Entry>()
    private val activeEntries = ArrayList<Entry>()
    private var originalLevels: List<Level>? = null
    private val xLabels = ArrayList<String>()
    private val voltageLabelMap = HashMap<Int, String>()

    private var binIndex = 0
    private var globalOffset = 0
    private var draggingEntry: Entry? = null
    private var voltageLevels: IntArray? = null // Store voltage level for each entry
    private var touchDownX = 0f
    private var touchDownY = 0f // For tap detection

    companion object {
        private const val TAP_THRESHOLD = 20f // Pixels
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gpu_curve_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Parse arguments
        if (arguments != null) {
            binIndex = requireArguments().getInt("binId", 0)
        }

        initViews(view)

        // Observe Bins
        gpuFrequencyViewModel.binsLiveData.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) {
                localBins = state.data
                loadData()
                refreshChart()
            }
        }

        loadData()
        setupChart()
        setupInteractions()

        // Register OnBackPressedCallback for gesture navigation
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Pop this fragment from backstack
                    if (parentFragmentManager.backStackEntryCount > 0) {
                        parentFragmentManager.popBackStack()
                    }
                }
            })
    }

    private fun initViews(view: View) {
        chart = view.findViewById(R.id.gpu_curve_chart)
        globalOffsetSlider = view.findViewById(R.id.slider_global_offset)
        offsetValueText = view.findViewById(R.id.text_offset_value)
        btnPlus = view.findViewById(R.id.btn_offset_plus)
        btnMinus = view.findViewById(R.id.btn_offset_minus)
        toolbarContainer = view.findViewById(R.id.toolbar_container)

        // Setup toolbar back navigation
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                requireActivity().onBackPressed()
            }
        }

        // Add action toolbar using modular widget
        if (activity != null) {
            val actionToolbar = GpuActionToolbar(requireContext())
            actionToolbar.setParentViewForVolt(toolbarContainer!!)
            actionToolbar.build(requireActivity())
            toolbarContainer!!.addView(actionToolbar)
        }
    }

    private fun loadData() {
        if (localBins.isEmpty()) {
            return
        }

        // Find bin by index
        var targetBin: Bin? = null
        var idx = 0
        for (b in localBins) {
            if (idx == binIndex) {
                targetBin = b
                break
            }
            idx++
        }

        if (targetBin?.levels == null || targetBin.levels.isEmpty()) {
            Toast.makeText(context, "Invalid bin data", Toast.LENGTH_SHORT).show()
            return
        }

        originalLevels = targetBin.levels
        referenceEntries.clear()
        activeEntries.clear()
        xLabels.clear()
        voltageLabelMap.clear()

        // Populate entries - X = voltage level (for proportional spacing)
        val size = originalLevels!!.size
        voltageLevels = IntArray(size)

        // Iterate in reverse order (highest level first in list?)
        // Wait, original logic: for (int i = size - 1; i >= 0; i--)
        // `originalLevels` usually sorted descending? or ascending?
        // Let's assume original logic was correct for list order.
        for (i in size - 1 downTo 0) {
            val lvl = originalLevels!![i]
            val entryIndex = size - 1 - i // Index in our arrays (0 to size-1)

            // Get frequency
            var freq: Long = 0
            for (line in lvl.lines) {
                if (line.contains("qcom,gpu-freq")) {
                    try {
                        freq = DtsHelper.decode_int_line(line).value
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    break
                }
            }
            val freqMhz = freq / 1_000_000f

            // Get voltage level
            var voltLevel = 0
            for (line in lvl.lines) {
                if (line.contains("qcom,level") || line.contains("qcom,cx-level")) {
                    try {
                        voltLevel = DtsHelper.decode_int_line(line).value.toInt()
                    } catch (ignored: Exception) {
                    }
                    break
                }
            }
            voltageLevels!![entryIndex] = voltLevel

            // Use voltage level as X coordinate for proportional spacing
            referenceEntries.add(Entry(voltLevel.toFloat(), freqMhz))
            activeEntries.add(Entry(voltLevel.toFloat(), freqMhz))

            // Get voltage label - format: "LABEL_NAME (level)" or just level number
            var voltLabel: String? = null
            try {
                val fullLabel = getLevelStr(voltLevel.toLong())
                // Extract just the name part (e.g., "256 - NOM" -> "NOM")
                if (fullLabel.contains(" - ")) {
                    voltLabel = fullLabel.split(" - ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                } else {
                    voltLabel = fullLabel
                }
            } catch (ignored: Exception) {
            }
            val finalLabel = voltLabel ?: voltLevel.toString()
            xLabels.add(finalLabel)
            voltageLabelMap[voltLevel] = finalLabel
        }

        // Sort entries by X (voltage level) for proper line connection
        Collections.sort(referenceEntries) { a, b -> java.lang.Float.compare(a.x, b.x) }
        Collections.sort(activeEntries) { a, b -> java.lang.Float.compare(a.x, b.x) }
    }

    private fun setupChart() {
        if (context == null || chart == null) return

        val colorOnSurface = MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorOnSurface,
            Color.WHITE
        )
        val colorOutline = MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorOutline,
            Color.GRAY
        )

        // Chart config - Timeline-like zoom experience
        chart!!.description.isEnabled = false
        chart!!.setTouchEnabled(true)
        chart!!.isDragEnabled = true
        chart!!.setScaleEnabled(true)
        chart!!.isScaleXEnabled = true // Allow X-axis zoom independently
        chart!!.isScaleYEnabled = true // Allow Y-axis zoom independently
        chart!!.setPinchZoom(false) // Independent X/Y zoom for timeline-like experience
        chart!!.isDoubleTapToZoomEnabled = false // Disable - conflicts with tap-to-edit
        chart!!.setDrawGridBackground(false)
        chart!!.legend.isEnabled = false
        chart!!.extraBottomOffset = 16f // More space for rotated labels
        chart!!.setVisibleXRangeMinimum(3f) // Minimum 3 points visible when zoomed

        // X-Axis - shows voltage level values with proportional spacing
        val xAxis = chart!!.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = colorOnSurface
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = colorOutline
        xAxis.gridLineWidth = 0.8f
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = -45f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val voltLevel = value.toInt()
                // Show voltage label at exact voltage positions
                return if (voltageLabelMap.containsKey(voltLevel)) {
                    voltageLabelMap[voltLevel].toString() + " (" + voltLevel + ")"
                } else voltLevel.toString()
            }
        }

        // Y-Axis
        val leftAxis = chart!!.axisLeft
        leftAxis.textColor = colorOnSurface
        leftAxis.gridColor = colorOutline
        leftAxis.axisLineColor = colorOutline
        chart!!.axisRight.isEnabled = false

        refreshChart()
    }

    private fun refreshChart() {
        if (context == null || chart == null) return

        val colorPrimary = MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorPrimary,
            Color.GREEN
        )
        val colorOnSurface = MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorOnSurface,
            Color.WHITE
        )

        // Reference line (dashed - original values)
        val refSet = LineDataSet(referenceEntries, "Reference")
        refSet.color = colorOnSurface
        refSet.lineWidth = 1.5f
        refSet.setDrawCircles(false)
        refSet.setDrawValues(false)
        refSet.enableDashedLine(10f, 5f, 0f)
        refSet.formLineDashEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)

        // Active line (current values)
        val activeSet = LineDataSet(activeEntries, "Current")
        activeSet.color = colorPrimary
        activeSet.lineWidth = 2.5f
        activeSet.setCircleColor(colorPrimary)
        activeSet.circleRadius = 5f
        activeSet.setDrawCircleHole(true)
        activeSet.circleHoleRadius = 3f
        activeSet.valueTextColor = colorPrimary
        activeSet.valueTextSize = 10f
        activeSet.setDrawValues(true)
        activeSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%.0f", value)
            }
        }
        activeSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        activeSet.setDrawFilled(true)

        if (Build.VERSION.SDK_INT >= 18) {
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(MaterialColors.compositeARGBWithAlpha(colorPrimary, 100), Color.TRANSPARENT)
            )
            activeSet.fillDrawable = drawable
        } else {
            activeSet.fillColor = colorPrimary
            activeSet.fillAlpha = 50
        }

        chart!!.data = LineData(refSet, activeSet)
        chart!!.invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupInteractions() {
        if (chart == null || globalOffsetSlider == null) return

        // Slider
        globalOffsetSlider!!.addOnChangeListener { _, value, _ ->
            applyGlobalOffset(value.toInt())
        }

        globalOffsetSlider!!.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                saveToMemory(true)
            }
        })

        // Buttons
        btnMinus!!.setOnClickListener {
            val `val` = globalOffsetSlider!!.value
            val newOffset = Math.max(globalOffsetSlider!!.valueFrom.toInt(), `val`.toInt() - 10)
            globalOffsetSlider!!.value = newOffset.toFloat()
            applyGlobalOffset(newOffset)
            saveToMemory(true)
        }

        btnPlus!!.setOnClickListener {
            val `val` = globalOffsetSlider!!.value
            val newOffset = Math.min(globalOffsetSlider!!.valueTo.toInt(), `val`.toInt() + 10)
            globalOffsetSlider!!.value = newOffset.toFloat()
            applyGlobalOffset(newOffset)
            saveToMemory(true)
        }

        // Chart drag listener
        chart!!.onChartGestureListener = object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent, lastPerformedGesture: ChartTouchListener.ChartGesture) {}
            override fun onChartGestureEnd(me: MotionEvent, lastPerformedGesture: ChartTouchListener.ChartGesture) {}
            override fun onChartLongPressed(me: MotionEvent) {}
            override fun onChartDoubleTapped(me: MotionEvent) {}
            override fun onChartSingleTapped(me: MotionEvent) {}
            override fun onChartFling(me1: MotionEvent, me2: MotionEvent, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(me: MotionEvent, scaleX: Float, scaleY: Float) {}
            override fun onChartTranslate(me: MotionEvent, dX: Float, dY: Float) {}
        }

        chart!!.setOnTouchListener { v, event ->
            val pointerCount = event.pointerCount

            // Request parent to not intercept touches so chart can pan/zoom
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                v.parent.requestDisallowInterceptTouchEvent(true)
            }

            // Let chart handle multi-touch (pinch zoom) natively
            if (pointerCount > 1) {
                if (draggingEntry != null) {
                    // Cancel any ongoing drag when pinch starts
                    draggingEntry = null
                    chart!!.highlightValue(null)
                }
                return@setOnTouchListener false // Let chart handle pinch
            }

            val h = chart!!.getHighlightByTouchPoint(event.x, event.y)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    if (h != null) {
                        val voltageX = h.x
                        // Find entry by matching X value (voltage level)
                        var matchedEntry: Entry? = null
                        for (e in activeEntries) {
                            if (Math.abs(e.x - voltageX) < 0.5f) {
                                matchedEntry = e
                                break
                            }
                        }

                        if (matchedEntry != null) {
                            // Calculate pixel position of the data point
                            val pointPixel = chart!!.getTransformer(YAxis.AxisDependency.LEFT)
                                .getPixelForValues(matchedEntry.x, matchedEntry.y)

                            // Check if touch is within 50 pixels of the point
                            val dx = Math.abs(event.x - pointPixel.x.toFloat())
                            val dy = Math.abs(event.y - pointPixel.y.toFloat())
                            val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                            if (distance < 50f) { // Only start drag if close to point
                                draggingEntry = matchedEntry
                                chart!!.highlightValue(h)
                                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                return@setOnTouchListener true // Consume to start drag
                            }
                        }
                    }
                    return@setOnTouchListener false // No point hit or too far, let chart handle pan
                }

                MotionEvent.ACTION_MOVE -> {
                    if (draggingEntry != null) {
                        var yVal = chart!!.getValuesByTouchPoint(
                            event.x, event.y,
                            YAxis.AxisDependency.LEFT
                        ).y.toFloat()
                        yVal = Math.max(50f, Math.min(3000f, yVal)) // Clamp
                        draggingEntry!!.y = yVal
                        refreshChart()
                        return@setOnTouchListener true // Consume drag
                    }
                    return@setOnTouchListener false // Not dragging, let chart handle
                }

                MotionEvent.ACTION_UP -> {
                    if (draggingEntry != null) {
                        // Check if this was a tap (minimal movement)
                        val dx = Math.abs(event.x - touchDownX)
                        val dy = Math.abs(event.y - touchDownY)
                        val wasTap = dx < TAP_THRESHOLD && dy < TAP_THRESHOLD

                        if (wasTap) {
                            // Tap detected - show voltage edit dialog
                            // Find index in voltageLevels array by matching draggingEntry's X value
                            val voltLevel = draggingEntry!!.x.toInt()
                            var entryIdx = -1
                            if (voltageLevels != null) {
                                for (i in voltageLevels!!.indices) {
                                    if (voltageLevels!![i] == voltLevel) {
                                        entryIdx = i
                                        break
                                    }
                                }
                            }
                            if (entryIdx >= 0) {
                                showVoltageEditDialog(entryIdx)
                            }
                        } else {
                            // Drag completed - save changes
                            saveToMemory(true)
                        }

                        draggingEntry = null
                        chart!!.highlightValue(null)
                        return@setOnTouchListener true
                    }
                    return@setOnTouchListener false
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (draggingEntry != null) {
                        draggingEntry = null
                        chart!!.highlightValue(null)
                    }
                    return@setOnTouchListener false
                }
            }
            false
        }
    }

    private fun showVoltageEditDialog(entryIndex: Int) {
        if (context == null || voltageLevels == null) return

        // Get available voltage levels from ChipInfo
        val levelStrings = ChipInfo.rpmh_levels.level_str()
        val levelValues = ChipInfo.rpmh_levels.levels()

        if (levelStrings == null || levelStrings.isEmpty()) {
            Toast.makeText(context, "Voltage levels not available", Toast.LENGTH_SHORT).show()
            return
        }

        // Find current selection
        val currentLevel = voltageLevels!![entryIndex]
        var currentSelection = 0
        for (i in levelValues.indices) {
            if (levelValues[i] == currentLevel) {
                currentSelection = i
                break
            }
        }

        val selectedIndex = intArrayOf(currentSelection)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Voltage Level")
            .setSingleChoiceItems(levelStrings, currentSelection) { _, which ->
                selectedIndex[0] = which
            }
            .setPositiveButton("Apply") { _, _ ->
                val newLevel = levelValues[selectedIndex[0]]
                voltageLevels!![entryIndex] = newLevel

                // Update x-axis label
                var newLabel: String
                try {
                    val voltLabel = getLevelStr(newLevel.toLong())
                    newLabel = "$newLevel - $voltLabel"
                } catch (e: Exception) {
                    newLabel = newLevel.toString()
                }
                xLabels[entryIndex] = newLabel // Wait, xLabels is generic ArrayList, just set.
                // Rebuild entry with new X
                // We need to re-sort activeEntries afterwards because X changed?
                // Yes, voltage level (X) determines order on chart.
                activeEntries[entryIndex].x = newLevel.toFloat()
                referenceEntries[entryIndex].x = newLevel.toFloat()

                // Sort again
                Collections.sort(referenceEntries) { a, b -> java.lang.Float.compare(a.x, b.x) }
                Collections.sort(activeEntries) { a, b -> java.lang.Float.compare(a.x, b.x) }

                refreshChart()
                saveToMemory(true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyGlobalOffset(offset: Int) {
        globalOffset = offset
        offsetValueText!!.text = "$offset MHz"

        for (i in activeEntries.indices) {
            val refVal = referenceEntries[i].y
            activeEntries[i].y = Math.max(50f, refVal + offset)
        }
        refreshChart()
    }

    private fun saveToMemory(silent: Boolean) {
        if (localBins.isEmpty()) return

        var targetFound = false
        var idx = 0
        for (b in localBins) {
            if (idx == binIndex) {
                targetFound = true
                break
            }
            idx++
        }

        if (!targetFound) return

        // Capture new frequency values from chart
        val newFreqValues = ArrayList<Long>()
        // activeEntries might have been sorted, so we should map them back to original array order if possible?
        // OR we just write them in the order they appear?
        // The originalLevels list in localBins is ordered.
        // xLabels and voltageLevels are ordered by index of originalLevels logic (reverse order).
        // If sorting changed the order of activeEntries, we might mismatch voltageLevels if we use index.
        // We sorted activeEntries in loadData.
        // voltageLevels was populated based on originalLevels loop.
        // If sort changed order, activeEntries[i] does not correspond to voltageLevels[i] necessarily?
        // Wait, loadData populates them, then sorts.
        // If I use index i to access voltageLevels, I need to match it with activeEntries.
        // BUT voltageLevels array is not sorted with Collections.sort.
        // I should reconstruct the map or ensure alignment.
        // For simplicity: We can use the X value of activeEntries[i] to find the voltage level.
        // activeEntries[i].x IS the voltage level.

        // So for each activeEntry:
        // Freq = Y * 1M
        // Voltage = X

        // But we need to write back to 'levels'.
        // Levels are in 'mutableBin.levels'.
        // We need to match which Level object corresponds to which entry.
        // Level objects contain the lines.
        // We can iterate originalLevels (from mutableBin) and find the matching voltage level.

        gpuFrequencyViewModel.performBatchEdit { bins ->
            // Find target bin in the mutable copy
            if (binIndex >= bins.size) return@performBatchEdit

            val mutableBin = bins[binIndex]
            val size = mutableBin.levels.size

            // We need to update ALL levels in this bin based on our chart entries.
            // Iterate over all levels in the bin
            for (lvl in mutableBin.levels) {
                // Find current voltage of this level
                var currentVolt = -1
                for (line in lvl.lines) {
                    if (line.contains("qcom,level") || line.contains("qcom,cx-level")) {
                        try {
                            currentVolt = DtsHelper.decode_int_line(line).value.toInt()
                        } catch (e: Exception) {
                        }
                        break
                    }
                }

                if (currentVolt != -1) {
                    // Find matching entry in activeEntries
                    for (entry in activeEntries) {
                        if (entry.x.toInt() == currentVolt) {
                            // Match found, update frequency
                            val newFreq = (entry.y * 1_000_000).toLong()

                            // Update lines
                            for (j in lvl.lines.indices) {
                                val line = lvl.lines[j]
                                if (line.contains("qcom,gpu-freq")) {
                                    val propName = line.substring(0, line.indexOf("=")).trim { it <= ' ' }
                                    val newLine = "$propName = <0x${java.lang.Long.toHexString(newFreq)}>;"
                                    lvl.lines[j] = newLine
                                }
                            }
                            break // Level updated
                        }
                    }
                }
            }
            // return Unit implicit
        }

        if (!silent) {
            Toast.makeText(context, "Changes saved to memory", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLevelStr(level: Long): String {
        try {
            val levels = ChipInfo.rpmh_levels.levels()
            val strs = ChipInfo.rpmh_levels.level_str()
            for (i in levels.indices) {
                if (levels[i].toLong() == level) {
                    return strs[i]
                }
            }
        } catch (e: Exception) {
        }
        return level.toString()
    }
}
