package com.ireddragonicy.konabessnext.core

import android.app.Activity
import android.content.Context
import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.model.Dtb
import com.ireddragonicy.konabessnext.model.DtbType
import com.ireddragonicy.konabessnext.utils.AssetsUtil
import com.ireddragonicy.konabessnext.utils.RootHelper
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object KonaBessCore {
    private val REQUIRED_BINARIES = arrayOf("dtc", "magiskboot")
    private val DTB_MAGIC = byteArrayOf(0xD0.toByte(), 0x0D.toByte(), 0xFE.toByte(), 0xED.toByte())
    private const val DTB_HEADER_SIZE = 8
    private const val BYTE_MASK = 0xFF
    private val MODEL_PROPERTY = Pattern.compile("model\\s*=\\s*\"([^\"]+)\"")
    private val PROPERTY_CACHE = ConcurrentHashMap<String, String>()

    @JvmField var dts_path: String? = null
    @JvmField var boot_name: String? = null
    @JvmField var dtbs: MutableList<Dtb>? = null
    @JvmStatic var currentDtb: Dtb? = null

    private var dtb_num = 0
    private var dtb_type: DtbType? = null
    private var filesDir: String? = null

    @JvmStatic
    @Throws(IOException::class)
    fun cleanEnv(context: Context) {
        filesDir = context.filesDir.absolutePath
        RootHelper.execShForOutput("rm -rf $filesDir/*")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun setupEnv(context: Context) {
        if (ChipInfo.definitions.isEmpty()) ChipInfo.loadDefinitions(context)
        filesDir = context.filesDir.absolutePath
        for (binary in REQUIRED_BINARIES) {
            val file = File(filesDir, binary)
            AssetsUtil.exportFiles(context, binary, file.absolutePath)
            file.setExecutable(true)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun checkDevice(context: Context) {
        setupEnv(context)
        dtbs = ArrayList()
        filesDir?.let { dir ->
            val dtsFiles = File(dir).listFiles { _, name -> name.endsWith(".dts") }
            dtb_num = dtsFiles?.size ?: 0
            for (i in 0 until dtb_num) {
                val dtsFile = File(dir, "$i.dts")
                if (!dtsFile.exists()) continue
                val content = dtsFile.readText()
                val chipId = detectChipType(content)
                val def = if (chipId != null) ChipInfo.getById(chipId) else null
                
                // Even if definition is null, we create a fallback so the UI list isn't empty
                val finalDef = def ?: ChipDefinition(
                    id = "unsupported_$i",
                    name = "Unsupported Structure (DTB $i)",
                    maxTableLevels = 0,
                    ignoreVoltTable = true,
                    minLevelOffset = 1,
                    strategyType = "",
                    levelCount = 416,
                    levels = mapOf(),
                    models = listOf("Unknown")
                )
                dtbs?.add(Dtb(i, finalDef))
            }
        }
    }

    private fun detectChipType(content: String): String? {
        val m = MODEL_PROPERTY.matcher(content)
        val modelContent = if (m.find()) m.group(1) ?: "" else ""
        for (def: ChipDefinition in ChipInfo.definitions) {
            for (model in def.models) {
                if (modelContent.contains(model, ignoreCase = true)) {
                    if (content.contains("qcom,gpu-pwrlevels {")) {
                        if (def.strategyType == "SINGLE_BIN") return def.id
                        val singleBinId = def.id + "_singleBin"
                        if (ChipInfo.getById(singleBinId) != null) return singleBinId
                    }
                    return def.id
                }
            }
        }
        return null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun bootImage2dts(context: Context) {
        RootHelper.execShForOutput("cd ${context.filesDir} && ./magiskboot unpack boot.img")
        val hasKernelDtb = File(context.filesDir, "kernel_dtb").exists()
        val hasDtb = File(context.filesDir, "dtb").exists()
        dtb_type = when {
            hasKernelDtb && hasDtb -> DtbType.BOTH
            hasKernelDtb -> DtbType.KERNEL_DTB
            hasDtb -> DtbType.DTB
            else -> throw IOException("No DTB found")
        }
        val dtbFile = File(context.filesDir, if (dtb_type == DtbType.DTB || dtb_type == DtbType.BOTH) "dtb" else "kernel_dtb")
        val data = Files.readAllBytes(dtbFile.toPath())
        val offsets = mutableListOf<Int>()
        var i = 0
        while (i + 8 < data.size) {
            if (data[i] == DTB_MAGIC[0] && data[i+1] == DTB_MAGIC[1] && data[i+2] == DTB_MAGIC[2] && data[i+3] == DTB_MAGIC[3]) {
                offsets.add(i)
                val size = ((data[i+4].toInt() and 0xFF) shl 24) or ((data[i+5].toInt() and 0xFF) shl 16) or ((data[i+6].toInt() and 0xFF) shl 8) or (data[i+7].toInt() and 0xFF)
                i += size.coerceAtLeast(1)
            } else i++
        }
        dtb_num = offsets.size
        for (idx in offsets.indices) {
            val start = offsets[idx]
            val end = if (idx + 1 < offsets.size) offsets[idx + 1] else data.size
            Files.write(Paths.get(context.filesDir.absolutePath, "$idx.dtb"), data.copyOfRange(start, end))
        }
        for (idx in 0 until dtb_num) {
            RootHelper.execShForOutput("cd ${context.filesDir} && ./dtc -I dtb -O dts $idx.dtb -o $idx.dts && rm $idx.dtb")
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun dts2bootImage(context: Context) {
        val dir = context.filesDir.absolutePath
        for (idx in 0 until dtb_num) RootHelper.execShForOutput("cd $dir && ./dtc -I dts -O dtb $idx.dts -o $idx.dtb")
        val out = if (dtb_type == DtbType.KERNEL_DTB) "kernel_dtb" else "dtb"
        val cmd = StringBuilder("cd $dir && cat")
        for (idx in 0 until dtb_num) cmd.append(" $idx.dtb")
        cmd.append(" > $out")
        if (dtb_type == DtbType.BOTH) cmd.append(" && cp dtb kernel_dtb")
        cmd.append(" && ./magiskboot repack boot.img boot_new.img")
        RootHelper.execShForOutput(cmd.toString())
    }

    @JvmStatic
    fun chooseTarget(dtb: Dtb, activity: Activity) {
        dts_path = "${activity.filesDir}/$dtb.id.dts"
        ChipInfo.current = dtb.type
        currentDtb = dtb
    }

    @JvmStatic
    fun getCurrent(name: String): String {
        return PROPERTY_CACHE.computeIfAbsent(name.lowercase()) { key ->
            val prop = when(key) {
                "brand" -> "ro.product.brand"
                "model" -> "ro.product.model"
                "device" -> "ro.product.device"
                "slot" -> "ro.boot.slot_suffix"
                else -> ""
            }
            if (prop.isEmpty()) "" else RootHelper.execShForOutput("getprop $prop").firstOrNull() ?: ""
        }
    }
}