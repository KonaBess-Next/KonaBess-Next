package com.ireddragonicy.konabessnext.core.editor

import android.app.Activity
import android.content.res.ColorStateList
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.core.GpuVoltEditor
import com.ireddragonicy.konabessnext.core.KonaBessCore
import com.ireddragonicy.konabessnext.data.KonaBessStr
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.ui.MainActivity
import com.ireddragonicy.konabessnext.ui.SettingsActivity
import com.ireddragonicy.konabessnext.ui.adapters.GpuBinAdapter
import com.ireddragonicy.konabessnext.ui.adapters.GpuFreqAdapter
import com.ireddragonicy.konabessnext.ui.adapters.GpuParamDetailAdapter
import com.ireddragonicy.konabessnext.ui.widget.GpuActionToolbar
import com.ireddragonicy.konabessnext.utils.DialogUtil
import com.ireddragonicy.konabessnext.utils.DtsHelper
import com.ireddragonicy.konabessnext.utils.ItemTouchHelperCallback
import java.util.ArrayList

/**
 * Generates all UI views for bins, levels, and parameters.
 */
object EditorUIBuilder {

    interface UIActionListener {
        @Throws(Exception::class)
        fun onOpenLevels(binIndex: Int)

        @Throws(Exception::class)
        fun onOpenParamDetails(binIndex: Int, levelIndex: Int)

        @Throws(Exception::class)
        fun onBack()

        @Throws(Exception::class)
        fun onAddLevelTop(binIndex: Int)

        @Throws(Exception::class)
        fun onAddLevelBottom(binIndex: Int)

        @Throws(Exception::class)
        fun onRemoveLevel(binIndex: Int, levelIndex: Int)

        @Throws(Exception::class)
        fun onReorderLevels(binIndex: Int, items: List<GpuFreqAdapter.FreqItem>)

        @Throws(Exception::class)
        fun onCurveEditor(binIndex: Int)

        @Throws(Exception::class)
        fun onParamEdit(
            binIndex: Int, levelIndex: Int, lineIndex: Int,
            rawName: String, rawValue: String, paramTitle: String
        )

        @Throws(Exception::class)
        fun onFrequencyAdjust(
            binIndex: Int, levelIndex: Int, lineIndex: Int,
            rawName: String, deltaMHz: Int
        )

        @Throws(Exception::class)
        fun onChangeChipset()
    }

    @JvmStatic
    fun generateToolBar(activity: Activity, showedView: LinearLayout) {
        val toolbar = GpuActionToolbar(activity)
        toolbar.setParentViewForVolt(showedView)
        toolbar.build(activity)
        showedView.addView(toolbar)
    }

    // ===== Bins UI =====

    @JvmStatic
    fun generateBins(
        activity: Activity, page: LinearLayout, bins: ArrayList<Bin>,
        listener: UIActionListener, chipsetListener: ChipsetManager.OnChipsetSwitchedListener
    ) {
        // Build the bin items list
        val items = ArrayList<GpuBinAdapter.BinItem>()
        for (i in bins.indices) {
            val binName: String = try {
                KonaBessStr.convert_bins(bins[i].id, activity)
            } catch (e: Exception) {
                activity.getString(R.string.unknown_table) + bins[i].id
            }
            items.add(GpuBinAdapter.BinItem(binName, ""))
        }

        val density = activity.resources.displayMetrics.density

        val existingRecyclerView = findBinRecyclerView(page)

        if (existingRecyclerView != null && existingRecyclerView.adapter is GpuBinAdapter) {
            // Hot path: Reuse existing views
            val adapter = existingRecyclerView.adapter as GpuBinAdapter
            adapter.updateData(items)
        } else {
            // Cold path: Create new view hierarchy
            val mainLayout = LinearLayout(activity)
            mainLayout.orientation = LinearLayout.VERTICAL
            mainLayout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )

            // Add chipset selector card if multiple chipsets are available
            if (!KonaBessCore.dtbs.isNullOrEmpty() && KonaBessCore.dtbs!!.size > 1) {
                val chipsetNameView = TextView(activity) // Placeholder, will be populated by createChipsetSelectorCard
                mainLayout.addView(
                    ChipsetManager.createChipsetSelectorCard(
                        activity,
                        page,
                        chipsetNameView,
                        chipsetListener
                    )
                )
            }

            val recyclerView = RecyclerView(activity)
            recyclerView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            recyclerView.layoutManager = LinearLayoutManager(activity)
            recyclerView.clipToPadding = false
            recyclerView.setPadding(0, (density * 8).toInt(), 0, (density * 16).toInt())

            val adapter = GpuBinAdapter(items, activity)
            adapter.setOnItemClickListener { position ->
                try {
                    listener.onOpenLevels(position)
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.error_occur)
                }
            }

            recyclerView.adapter = adapter
            mainLayout.addView(recyclerView)

            page.removeAllViews()
            page.addView(
                mainLayout, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun findBinRecyclerView(page: LinearLayout): RecyclerView? {
        if (page.childCount == 0) return null
        val firstChild = page.getChildAt(0)

        if (firstChild is LinearLayout) {
            for (i in 0 until firstChild.childCount) {
                val child = firstChild.getChildAt(i)
                if (child is RecyclerView) {
                    if (child.adapter is GpuBinAdapter) {
                        return child
                    }
                }
            }
        }
        return null
    }

    // ===== Levels UI =====

    @JvmStatic
    @Throws(Exception::class)
    fun generateLevels(
        activity: Activity, page: LinearLayout, bins: ArrayList<Bin>, binIndex: Int,
        listener: UIActionListener
    ) {
        val bin = bins[binIndex]

        if (activity is MainActivity) {
            activity.updateGpuToolbarTitle(
                activity.getString(R.string.edit_freq_table)
                        + " - " + KonaBessStr.convert_bins(bin.id, activity)
            )
        }

        val items = ArrayList<GpuFreqAdapter.FreqItem>()

        // Headers
        items.add(
            GpuFreqAdapter.FreqItem(
                activity.resources.getString(R.string.back), "", GpuFreqAdapter.FreqItem.ActionType.BACK
            )
        )
        items.add(
            GpuFreqAdapter.FreqItem(
                activity.resources.getString(R.string.add_freq_top),
                activity.resources.getString(R.string.add_freq_top_desc),
                GpuFreqAdapter.FreqItem.ActionType.ADD_TOP
            )
        )
        items.add(
            GpuFreqAdapter.FreqItem(
                activity.resources.getString(R.string.gpu_curve_editor_title),
                "Edit frequency curve for this bin",
                GpuFreqAdapter.FreqItem.ActionType.CURVE_EDITOR
            )
        )

        // Levels
        for (i in bin.levels.indices) {
            val lvl = bin.levels[i]
            val freq = LevelOperations.getFrequencyFromLevel(lvl)
            if (freq == 0L) continue

            val item = GpuFreqAdapter.FreqItem(
                SettingsActivity.formatFrequency(freq, activity), ""
            )
            item.originalPosition = i
            item.frequencyHz = freq

            try {
                for (line in lvl.lines) {
                    val paramName = DtsHelper.decode_hex_line(line).name
                    val `val` = DtsHelper.decode_int_line(line).value

                    if ("qcom,bus-max" == paramName) item.busMax = `val`.toString()
                    else if ("qcom,bus-min" == paramName) item.busMin = `val`.toString()
                    else if ("qcom,bus-freq" == paramName) item.busFreq = `val`.toString()
                    else if ("qcom,level" == paramName || "qcom,cx-level" == paramName)
                        item.voltageLevel = GpuVoltEditor.levelint2str(`val`.toLong())
                }
            } catch (ignored: Exception) {
            }

            items.add(item)
        }

        // Footer
        items.add(
            GpuFreqAdapter.FreqItem(
                activity.resources.getString(R.string.add_freq_bottom),
                activity.resources.getString(R.string.add_freq_bottom_desc),
                GpuFreqAdapter.FreqItem.ActionType.ADD_BOTTOM
            )
        )

        // RecyclerView setup
        val recyclerView: RecyclerView
        val adapter: GpuFreqAdapter

        if (page.childCount > 0 && page.getChildAt(0) is RecyclerView
            && (page.getChildAt(0) as RecyclerView).adapter is GpuFreqAdapter
        ) {
            recyclerView = page.getChildAt(0) as RecyclerView
            adapter = recyclerView.adapter as GpuFreqAdapter
            recyclerView.clearOnScrollListeners()
            adapter.updateData(items)
        } else {
            recyclerView = RecyclerView(activity)
            recyclerView.layoutManager = LinearLayoutManager(activity)
            val density = activity.resources.displayMetrics.density
            recyclerView.clipToPadding = false
            recyclerView.setPadding(0, 0, 0, (density * 80).toInt())

            adapter = GpuFreqAdapter(items, activity)
            val helper = ItemTouchHelper(ItemTouchHelperCallback(adapter))
            helper.attachToRecyclerView(recyclerView)
            adapter.setOnStartDragListener { viewHolder: RecyclerView.ViewHolder? -> viewHolder?.let { helper.startDrag(it) } }
            recyclerView.adapter = adapter

            page.removeAllViews()
            page.addView(recyclerView)
        }

        // Listeners
        adapter.setOnItemClickListener { position: Int ->
            val item = items[position]
            when (item.actionType) {
                GpuFreqAdapter.FreqItem.ActionType.BACK -> try {
                    listener.onBack()
                } catch (ignored: Exception) {
                }

                GpuFreqAdapter.FreqItem.ActionType.ADD_TOP -> try {
                    listener.onAddLevelTop(binIndex)
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.error_occur)
                }

                GpuFreqAdapter.FreqItem.ActionType.ADD_BOTTOM -> try {
                    listener.onAddLevelBottom(binIndex)
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.error_occur)
                }

                GpuFreqAdapter.FreqItem.ActionType.CURVE_EDITOR -> try {
                    listener.onCurveEditor(binIndex)
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.error_occur)
                }

                else -> if (item.isLevelItem) {
                    var lvlIdx = 0
                    for (k in 0 until position) {
                        if (items[k].isLevelItem) lvlIdx++
                    }
                    try {
                        listener.onOpenParamDetails(binIndex, lvlIdx)
                    } catch (e: Exception) {
                        DialogUtil.showError(activity, R.string.error_occur)
                    }
                }
            }
        }

        adapter.setOnDeleteClickListener { position: Int ->
            if (bin.levels.size == 1) {
                Toast.makeText(activity, R.string.unable_add_more, Toast.LENGTH_SHORT).show()
            } else {
                val levelPos = position - 3 // Offset headers
                try {
                    val freqMsg = LevelOperations.getFrequencyLabel(bin.levels[levelPos], activity)
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.remove)
                        .setMessage(activity.getString(R.string.remove_frequency_message, freqMsg))
                        .setPositiveButton(R.string.yes) { _, _ ->
                            try {
                                listener.onRemoveLevel(binIndex, levelPos)
                            } catch (e: Exception) {
                                DialogUtil.showError(activity, R.string.error_occur)
                            }
                        }
                        .setNegativeButton(R.string.no, null)
                        .create().show()
                } catch (ignored: Exception) {
                }
            }
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    try {
                        listener.onReorderLevels(binIndex, adapter.items)
                    } catch (ignored: Exception) {
                    }
                }
            }
        })
    }

    // ===== Parameter Details UI =====

    @JvmStatic
    @Throws(Exception::class)
    fun generateALevel(
        activity: Activity, page: LinearLayout, bins: ArrayList<Bin>,
        binIndex: Int, levelIndex: Int, listener: UIActionListener
    ) {
        val recyclerView = RecyclerView(activity)
        recyclerView.layoutManager = LinearLayoutManager(activity)

        val items = ArrayList<GpuParamDetailAdapter.ParamDetailItem>()
        items.add(
            GpuParamDetailAdapter.ParamDetailItem(
                activity.resources.getString(R.string.back), R.drawable.ic_back, true
            )
        )

        val statsGroup = ArrayList<GpuParamDetailAdapter.StatItem>()
        val otherParams = ArrayList<GpuParamDetailAdapter.ParamDetailItem>()

        val level = bins[binIndex].levels[levelIndex]
        var lineIndex = 0

        for (line in level.lines) {
            val paramName = DtsHelper.decode_hex_line(line).name ?: ""
            val paramTitle = KonaBessStr.convert_level_params(paramName, activity)
            val paramValue = generateSubtitle(line)
            val iconRes = GpuParamDetailAdapter.getIconForParam(paramName)

            if (paramName.contains("bus-freq") || paramName.contains("bus-min") || paramName.contains("bus-max")) {
                var statLabel = paramTitle
                if (paramName.contains("bus-freq")) statLabel = "Bus Freq"
                else if (paramName.contains("bus-min")) statLabel = "Bus Min"
                else if (paramName.contains("bus-max")) statLabel = "Bus Max"

                statsGroup.add(
                    GpuParamDetailAdapter.StatItem(
                        statLabel,
                        paramValue,
                        paramName,
                        iconRes,
                        lineIndex
                    )
                )
            } else {
                var displayTitle = paramTitle
                if (paramName.contains("gpu-freq") || (paramName.contains("frequency") && !paramName.contains("bus"))) {
                    displayTitle = "GPU Frequency"
                }
                val item = GpuParamDetailAdapter.ParamDetailItem(
                    displayTitle, paramValue, paramName, iconRes
                )
                if (displayTitle == "GPU Frequency") item.isFrequencyControl = true
                item.lineIndex = lineIndex
                otherParams.add(item)
            }
            lineIndex++
        }

        if (!statsGroup.isEmpty()) items.add(GpuParamDetailAdapter.ParamDetailItem(statsGroup))
        items.addAll(otherParams)

        val adapter = GpuParamDetailAdapter(items, activity)
        adapter.setOnItemClickListener(object : GpuParamDetailAdapter.OnItemClickListener {
            override fun onBackClicked() {
                try {
                    listener.onBack()
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.error_occur)
                }
            }

            override fun onStatItemClicked(statItem: GpuParamDetailAdapter.StatItem?) {
                if (statItem == null) return
                try {
                    val line = level.lines[statItem.lineIndex]
                    val rawValue =
                        if (DtsHelper.shouldUseHex(line)) DtsHelper.decode_hex_line(line).value!!
                        else DtsHelper.decode_int_line(line).value.toString() + ""
                    listener.onParamEdit(
                        binIndex,
                        levelIndex,
                        statItem.lineIndex,
                        statItem.paramName,
                        rawValue,
                        statItem.label
                    )
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.error_occur)
                }
            }

            override fun onParamClicked(item: GpuParamDetailAdapter.ParamDetailItem?) {
                if (item == null || item.isStatsGroup) return
                try {
                    val line = level.lines[item.lineIndex]
                    val rawValue =
                        if (DtsHelper.shouldUseHex(line)) DtsHelper.decode_hex_line(line).value!!
                        else DtsHelper.decode_int_line(line).value.toString() + ""
                    listener.onParamEdit(
                        binIndex,
                        levelIndex,
                        item.lineIndex,
                        item.paramName!!,
                        rawValue,
                        item.title!!
                    )
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.error_occur)
                }
            }

            override fun onFrequencyAdjust(
                item: GpuParamDetailAdapter.ParamDetailItem?,
                deltaMHz: Int
            ) {
                if (item == null) return
                try {
                    listener.onFrequencyAdjust(
                        binIndex,
                        levelIndex,
                        item.lineIndex,
                        item.paramName!!,
                        deltaMHz
                    )
                } catch (e: Exception) {
                    DialogUtil.showError(activity, R.string.save_failed)
                }
            }
        })

        recyclerView.adapter = adapter
        page.removeAllViews()
        page.addView(recyclerView)
    }

    @Throws(Exception::class)
    private fun generateSubtitle(line: String): String {
        val raw_name = DtsHelper.decode_hex_line(line).name
        if ("qcom,level" == raw_name || "qcom,cx-level" == raw_name) {
            return GpuVoltEditor.levelint2str(DtsHelper.decode_int_line(line).value)
        }
        return if (DtsHelper.shouldUseHex(line)) DtsHelper.decode_hex_line(line).value!!
        else DtsHelper.decode_int_line(line).value.toString() + ""
    }

    // ===== UI Helpers =====

    @JvmStatic
    fun createCompactChip(activity: Activity, textRes: Int, iconRes: Int): MaterialButton {
        val chip = MaterialButton(activity)
        chip.isAllCaps = false
        chip.setText(textRes)
        chip.setIconResource(iconRes)
        chip.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START

        val density = activity.resources.displayMetrics.density
        chip.iconSize = (density * 18).toInt()
        chip.iconPadding = (density * 4).toInt()
        chip.setPadding(
            (density * 12).toInt(),
            (density * 8).toInt(),
            (density * 12).toInt(),
            (density * 8).toInt()
        )
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        chip.cornerRadius = (density * 20).toInt()
        chip.strokeWidth = 0

        val bgColor =
            MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSecondaryContainer)
        val fgColor =
            MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSecondaryContainer)
        val ripple = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSecondary)

        chip.backgroundTintList = ColorStateList.valueOf(bgColor)
        chip.setTextColor(fgColor)
        chip.iconTint = ColorStateList.valueOf(fgColor)
        chip.rippleColor = ColorStateList.valueOf(ripple)

        return chip
    }
}
