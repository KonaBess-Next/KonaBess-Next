package com.ireddragonicy.konabessnext.core

import android.app.Activity
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.ui.adapters.ParamAdapter
import com.ireddragonicy.konabessnext.utils.DialogUtil
import com.ireddragonicy.konabessnext.utils.DtsHelper
import com.ireddragonicy.konabessnext.utils.FileUtil
import com.ireddragonicy.konabessnext.utils.ThreadUtil
import java.io.IOException
import java.util.ArrayList

object GpuVoltEditor {

    class Opp {
        var frequency: Long = 0
        var volt: Long = 0
    }

    @JvmStatic
    @Throws(Exception::class)
    fun levelint2int(level: Long): Int {
        val levels = ChipInfo.rpmh_levels!!.levels()
        for (i in levels.indices) {
            if (levels[i].toLong() == level) return i
        }
        throw Exception("Level not found")
    }

    @JvmStatic
    fun levelint2str(level: Long): String {
        return try {
            ChipInfo.rpmh_levels?.let {
                it.level_str()[levelint2int(level)]
            } ?: level.toString()
        } catch (e: Exception) {
            level.toString()
        }
    }

    @JvmStatic
    fun getLevelList(): List<String> {
        val list = ArrayList<String>()
        if (ChipInfo.rpmh_levels != null) {
            val levels = ChipInfo.rpmh_levels!!.levels()
            val levelStrs = ChipInfo.rpmh_levels!!.level_str()
            for (i in levels.indices) {
                list.add("LEVEL ${levels[i]} - ${levelStrs[i]}")
            }
        }
        return list
    }

    private val opps = ArrayList<Opp>()
    private var linesInDts = ArrayList<String>()
    private var oppPosition = -1

    @Throws(IOException::class)
    fun init() {
        opps.clear()
        oppPosition = -1
        linesInDts = ArrayList(FileUtil.readLines(KonaBessCore.dts_path!!))
    }

    @Throws(Exception::class)
    fun decode() {
        val pattern = ChipInfo.which?.voltTablePattern ?: return

        var i = -1
        var isInGpuTable = false
        var bracket = 0
        var start = -1

        // Convert to while loop to manage index manually as in original Java
        // Or simply iterate with index. Original used strict parsing.
        
        while (++i < linesInDts.size) {
            val line = linesInDts[i].trim()
            if (line.isEmpty()) continue

            if (line.contains(pattern) && line.contains("{")) {
                isInGpuTable = true
                bracket++
                continue
            }

            if (!isInGpuTable) continue

            if (line.contains("opp-") && line.contains("{")) {
                start = i
                if (oppPosition < 0) oppPosition = i
                bracket++
                continue
            }

            if (line.contains("}")) {
                bracket--
                if (bracket == 0) break
                if (bracket != 1) throw Exception("Structure error")

                opps.add(decodeOpp(linesInDts.subList(start, i + 1)))
                linesInDts.subList(start, i + 1).clear()
                i = start - 1
            }
        }
    }

    @Throws(Exception::class)
    private fun decodeOpp(lines: List<String>): Opp {
        val opp = Opp()
        for (line in lines) {
            if (line.contains("opp-hz"))
                opp.frequency = DtsHelper.decode_int_line_hz(line).value
            if (line.contains("opp-microvolt"))
                opp.volt = DtsHelper.decode_int_line(line).value
        }
        return opp
    }

    fun genTable(): List<String> {
        val table = ArrayList<String>()
        for (opp in opps) {
            table.add("opp-" + opp.frequency + " {")
            table.add("opp-hz = <0x0 " + opp.frequency + ">;")
            table.add("opp-microvolt = <" + opp.volt + ">;")
            table.add("};")
        }
        return table
    }

    fun genBack(table: List<String>): List<String> {
        val result = ArrayList(linesInDts)
        if (oppPosition >= 0 && oppPosition <= result.size) {
            result.addAll(oppPosition, table)
        }
        return result
    }

    class gpuVoltLogic(
        private val activity: Activity,
        private val showedView: LinearLayout
    ) : Thread() {
        private var waiting: AlertDialog? = null

        override fun run() {
            ThreadUtil.runOnMain {
                waiting = DialogUtil.getWaitDialog(activity, R.string.getting_volt)
                waiting!!.show()
            }

            ThreadUtil.runInBackground {
                try {
                    init()
                    decode()
                    ThreadUtil.runOnMain {
                        waiting?.dismiss()
                        showedView.removeAllViews()
                        showedView.addView(generateToolBar(activity))
                        val page = LinearLayout(activity)
                        page.orientation = LinearLayout.VERTICAL
                        if (opps.isNotEmpty()) {
                            generateVolts(activity, page)
                            showedView.addView(page)
                        } else {
                            DialogUtil.showError(activity, R.string.incompatible_device)
                        }
                    }
                } catch (e: Exception) {
                    ThreadUtil.runOnMain {
                        waiting?.takeIf { it.isShowing }?.dismiss()
                        DialogUtil.showError(activity, R.string.getting_volt_failed)
                    }
                }
            }
        }
    }

    private fun generateToolBar(activity: Activity): View {
        val toolbar = LinearLayout(activity)
        val scrollView = HorizontalScrollView(activity)
        scrollView.addView(toolbar)

        val saveParamsBtn = Button(activity)
        saveParamsBtn.setText(R.string.save_volt_table)
        saveParamsBtn.setOnClickListener {
            try {
                FileUtil.writeLines(KonaBessCore.dts_path!!, genBack(genTable()))
                Toast.makeText(activity, R.string.save_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                DialogUtil.showError(activity, R.string.save_failed)
            }
        }
        toolbar.addView(saveParamsBtn)
        return scrollView
    }

    fun writeOut(new_dts: List<String>) {
        FileUtil.writeLines(KonaBessCore.dts_path!!, new_dts)
    }

    private fun generateVolts(activity: Activity, page: LinearLayout) {
        page.removeAllViews()
        val listView = ListView(activity)
        val items = ArrayList<ParamAdapter.Item>()

        for (opp in opps) {
            items.add(createItem(opp.frequency.toString() + " MHz", getLevelStr(opp.volt)))
        }

        listView.adapter = ParamAdapter(items, activity)
        listView.setOnItemClickListener { _, _, position, _ ->
            generateAVolt(activity, page, position)
        }

        page.addView(listView)
    }

    private fun generateAVolt(activity: Activity, page: LinearLayout, index: Int) {
        val listView = ListView(activity)
        val items = ArrayList<ParamAdapter.Item>()
        val opp = opps[index]

        items.add(createItem(activity.getString(R.string.back), ""))
        items.add(createItem(activity.getString(R.string.freq), opp.frequency.toString() + ""))
        items.add(createItem(activity.getString(R.string.volt), getLevelStr(opp.volt)))

        listView.adapter = ParamAdapter(items, activity)

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                generateVolts(activity, page)
            } else if (position == 1) { // Edit Freq
                DialogUtil.showEditDialog(
                    activity,
                    activity.getString(R.string.edit),
                    activity.getString(R.string.volt_freq_msg),
                    opp.frequency.toString(),
                    InputType.TYPE_CLASS_NUMBER
                ) { text ->
                    try {
                        opp.frequency = text.toLong()
                        generateAVolt(activity, page, index)
                    } catch (e: Exception) {
                        DialogUtil.showError(activity, R.string.save_failed)
                    }
                }
            } else if (position == 2) { // Edit Volt
                DialogUtil.showSingleChoiceDialog(
                    activity,
                    activity.getString(R.string.edit),
                    ChipInfo.rpmh_levels.level_str(),
                    getLevelIndex(opp.volt)
                ) { dialog, which ->
                    opp.volt = ChipInfo.rpmh_levels.levels()[which].toLong()
                    dialog.dismiss()
                    generateAVolt(activity, page, index)
                }
            }
        }

        page.removeAllViews()
        page.addView(listView)
    }

    private fun createItem(title: String, subtitle: String): ParamAdapter.Item {
        val item = ParamAdapter.Item()
        item.title = title
        item.subtitle = subtitle
        return item
    }

    private fun getLevelIndex(level: Long): Int {
        val levels = ChipInfo.rpmh_levels!!.levels()
        for (i in levels.indices) {
            if (levels[i].toLong() == level) return i
        }
        return 0
    }

    private fun getLevelStr(level: Long): String {
        val idx = getLevelIndex(level)
        return ChipInfo.rpmh_levels!!.level_str()[idx]
    }
}
