package com.ireddragonicy.konabessnext.core

import android.app.Activity
import android.content.Context
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
    // Constants
    private val REQUIRED_BINARIES = arrayOf("dtc", "magiskboot")
    private val DTB_MAGIC = byteArrayOf(0xD0.toByte(), 0x0D.toByte(), 0xFE.toByte(), 0xED.toByte())
    private const val DTB_HEADER_SIZE = 8
    private const val BYTE_MASK = 0xFF

    // Regex patterns
    private val MODEL_PROPERTY = Pattern.compile("model\\s*=\\s*\"([^\"]+)\"")

    // Chip mappings (Model string -> Chip ID)
    private val CHIP_MAPPINGS = mapOf(
        "kona v2.1" to "kona",
        "kona v2" to "kona",
        "SM8150 v2" to "msmnile",
        "Lahaina V2.1" to "lahaina",
        "Lahaina v2.1" to "lahaina",
        "Lito" to "lito_v1",
        "Lito v2" to "lito_v2",
        "Lagoon" to "lagoon",
        "Shima" to "shima",
        "Yupik" to "yupik",
        "Waipio" to "waipio_singleBin",
        "Waipio v2" to "waipio_singleBin",
        "Cape" to "cape_singleBin",
        "Kalama v2" to "kalama",
        "KalamaP v2" to "kalama",
        "Diwali" to "diwali",
        "Ukee" to "ukee_singleBin",
        "Pineapple v2" to "pineapple",
        "PineappleP v2" to "pineapple",
        "Cliffs SoC" to "cliffs_singleBin",
        "Cliffs 7 SoC" to "cliffs_7_singleBin",
        "KalamaP SG SoC" to "kalama_sg_singleBin",
        "Sun v2 SoC" to "sun",
        "Sun Alt. Thermal Profile v2 SoC" to "sun",
        "SunP v2 SoC" to "sun",
        "SunP v2 Alt. Thermal Profile SoC" to "sun",
        "Canoe v2 SoC" to "canoe",
        "CanoeP v2 SoC" to "canoe",
        "Tuna 7 SoC" to "tuna",
        "Tuna SoC" to "tuna",
        "PineappleP SG" to "pineapple_sg",
        "KalamaP QCS" to "kalamap_qcs_singleBin"
    )

    private val PROPERTY_CACHE = ConcurrentHashMap<String, String>()

    // Global State (exposed as vars)
    @JvmField
    var dts_path: String? = null
    @JvmField
    var boot_name: String? = null
    @JvmField
    var dtbs: MutableList<Dtb>? = null
    @JvmStatic
    var currentDtb: Dtb? = null

    @JvmStatic
    fun getDtbIndex(): Int {
        return if (dtbs != null && currentDtb != null) {
            dtbs!!.indexOf(currentDtb!!)
        } else -1
    }

    private var dtb_num = 0
    private var prepared = false
    private var dtb_type: DtbType? = null
    private var filesDir: String? = null

    private const val PREFS_NAME = "KonaBessChipset"
    private const val KEY_LAST_DTB_ID = "last_dtb_id"
    private const val KEY_LAST_CHIP_TYPE = "last_chip_type"

    @JvmStatic
    @Throws(IOException::class)
    fun cleanEnv(context: Context) {
        resetState()
        filesDir = context.filesDir.absolutePath
        RootHelper.execShForOutput("rm -rf $filesDir/*")
    }

    @JvmStatic
    fun resetState() {
        prepared = false
        dts_path = null
        dtbs = null
        boot_name = null
        PROPERTY_CACHE.clear()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun setupEnv(context: Context) {
        // Ensure definitions are loaded
        if (ChipInfo.definitions.isEmpty()) {
            ChipInfo.loadDefinitions(context)
        }

        filesDir = context.filesDir.absolutePath
        for (binary in REQUIRED_BINARIES) {
            val file = File(filesDir, binary)
            AssetsUtil.exportFiles(context, binary, file.absolutePath)
            if (!file.setExecutable(true) || !file.canExecute()) {
                throw IOException("Failed to set executable: $binary")
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun checkDevice(context: Context) {
        setupEnv(context)
        dtbs = ArrayList()
        // Grant read perms
        filesDir?.let { dir ->
            RootHelper.execShForOutput("chmod 644 $dir/*.dts")
            
            // If dtb_num is 0, try to find existing dts files
            if (dtb_num == 0) {
                val dtsFiles = File(dir).listFiles { _, name -> name.endsWith(".dts") }
                if (dtsFiles != null) {
                    dtb_num = dtsFiles.size
                }
            }

            for (i in 0 until dtb_num) {
                val dtsFile = File(dir, "$i.dts")
                if (!dtsFile.exists()) continue
                
                val content = dtsFile.readText()
                val chipId = detectChipType(content, i)
                
                if (chipId != null) {
                    val def = ChipInfo.getById(chipId)
                    if (def != null) {
                        dtbs?.add(Dtb(i, def))
                    }
                }
            }
        }
    }

    private fun detectChipType(content: String, index: Int): String? {
        val m = MODEL_PROPERTY.matcher(content)
        var modelContent = ""
        if (m.find()) {
            modelContent = m.group(1) ?: ""
        }

        if ("OP4A79" == getCurrent("device") && modelContent.contains("kona v2")) {
            return if (isSingleBin(content)) "kona_singleBin" else "kona"
        }

        for ((key, baseId) in CHIP_MAPPINGS) {
            if (modelContent.contains(key)) {
                if (baseId == "kona" || baseId == "msmnile" || baseId == "lahaina") {
                     return determineChipVariant(index, baseId, content)
                }
                return baseId
            }
        }
        return null
    }

    private fun isSingleBin(content: String): Boolean {
        return content.contains("qcom,gpu-pwrlevels {")
    }

    private fun determineChipVariant(index: Int, regularId: String, content: String): String {
         val singleBinId = regularId + "_singleBin"
         return if (isSingleBin(content)) singleBinId else regularId
    }

    @JvmStatic
    @Throws(IOException::class)
    fun bootImage2dts(context: Context) {
        unpackBootImage(context)
        dtb_num = dtbSplit(context)

        val batchCmd = StringBuilder()
        batchCmd.append("cd ").append(filesDir)

        for (i in 0 until dtb_num) {
            batchCmd.append(" && ./dtc -I dtb -O dts ")
                    .append(i).append(".dtb -o ").append(i).append(".dts")
                    .append(" && rm -f ").append(i).append(".dtb")
        }
        
        val output = RootHelper.execShForOutput(batchCmd.toString())
        if (dtb_num > 0 && !File(filesDir, "${dtb_num - 1}.dts").exists()) {
            throw IOException("DTB to DTS batch conversion failed: " + output.joinToString("\n"))
        }
    }

    @Throws(IOException::class)
    private fun unpackBootImage(context: Context) {
        // Using RootHelper static for now as we are converting static class 1:1
        RootHelper.execShForOutput("cd $filesDir && ./magiskboot unpack boot.img")
        determineDtbType()
    }

    @Throws(IOException::class)
    private fun determineDtbType() {
        val hasKernelDtb = File(filesDir, "kernel_dtb").exists()
        val hasDtb = File(filesDir, "dtb").exists()

        dtb_type = when {
            hasKernelDtb && hasDtb -> DtbType.BOTH
            hasKernelDtb -> DtbType.KERNEL_DTB
            hasDtb -> DtbType.DTB
            else -> throw IOException("No DTB found in boot image")
        }
    }

    @Throws(IOException::class)
    private fun dtbSplit(context: Context): Int {
        val dtbFile = getDtbFile()
        if (dtb_type == DtbType.BOTH) {
            File(filesDir, "kernel_dtb").delete()
        }
        val dtbBytes = Files.readAllBytes(dtbFile.toPath())
        val offsets = findDtbOffsets(dtbBytes)
        writeDtbChunks(dtbBytes, offsets)
        dtbFile.delete()
        return offsets.size
    }

    @Throws(IOException::class)
    private fun getDtbFile(): File {
        val filename = if (dtb_type == DtbType.DTB || dtb_type == DtbType.BOTH) "dtb" else "kernel_dtb"
        return File(filesDir, filename)
    }

    private fun findDtbOffsets(data: ByteArray): List<Int> {
        val offsets = ArrayList<Int>()
        var i = 0
        while (i + DTB_HEADER_SIZE < data.size) {
            if (isDtbMagic(data, i)) {
                offsets.add(i)
                val size = readDtbSize(data, i + 4)
                i += size.coerceAtLeast(1)
            } else {
                i++
            }
        }
        return offsets
    }

    private fun isDtbMagic(data: ByteArray, offset: Int): Boolean {
        for (j in 0 until 4) {
            if (data[offset + j] != DTB_MAGIC[j]) return false
        }
        return true
    }

    private fun readDtbSize(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and BYTE_MASK) shl 24) or
               ((data[offset + 1].toInt() and BYTE_MASK) shl 16) or
               ((data[offset + 2].toInt() and BYTE_MASK) shl 8) or
               (data[offset + 3].toInt() and BYTE_MASK)
    }

    @Throws(IOException::class)
    private fun writeDtbChunks(data: ByteArray, offsets: List<Int>) {
        for (i in offsets.indices) {
            val start = offsets[i]
            val end = if (i + 1 < offsets.size) offsets[i + 1] else data.size
            Files.write(Paths.get(filesDir, "$i.dtb"), data.copyOfRange(start, end))
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun dts2bootImage(context: Context) {
        val batchCmd = StringBuilder()
        batchCmd.append("cd ").append(filesDir)
        
        for (i in 0 until dtb_num) {
            batchCmd.append(" && ./dtc -I dts -O dtb ")
                    .append(i).append(".dts -o ").append(i).append(".dtb")
        }
        val outputFilename = if (dtb_type == DtbType.KERNEL_DTB) "kernel_dtb" else "dtb"
        batchCmd.append(" && cat")
        for (i in 0 until dtb_num) {
             batchCmd.append(" ").append(i).append(".dtb")
        }
        batchCmd.append(" > ").append(outputFilename)
        
        if (dtb_type == DtbType.BOTH) {
            batchCmd.append(" && cp dtb kernel_dtb")
        }
        batchCmd.append(" && ./magiskboot repack boot.img boot_new.img")
        
        val output = RootHelper.execShForOutput(batchCmd.toString())
        if (!File(filesDir, "boot_new.img").exists()) {
             throw IOException("DTS to boot image conversion failed: " + output.joinToString("\n"))
        }
    }

    @JvmStatic
    fun chooseTarget(dtb: Dtb, activity: Activity) {
        filesDir = activity.filesDir.absolutePath
        dts_path = "$filesDir/${dtb.id}.dts"
        ChipInfo.current = dtb.type
        currentDtb = dtb
        prepared = true
        saveLastChipset(activity, dtb.id, dtb.type.id)
    }

    private fun saveLastChipset(activity: Activity, dtbId: Int, chipId: String) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_LAST_DTB_ID, dtbId)
            .putString(KEY_LAST_CHIP_TYPE, chipId)
            .apply()
    }

    @JvmStatic
    fun hasLastChipset(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_LAST_DTB_ID) && prefs.contains(KEY_LAST_CHIP_TYPE)
    }

    @JvmStatic
    fun tryRestoreLastChipset(activity: Activity): Boolean {
        if (!hasLastChipset(activity)) return false
        
        // Ensure definitions are loaded
        if (ChipInfo.definitions.isEmpty()) {
            ChipInfo.loadDefinitions(activity)
        }

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dtbId = prefs.getInt(KEY_LAST_DTB_ID, -1)
        val chipId = prefs.getString(KEY_LAST_CHIP_TYPE, null)

        if (dtbId < 0 || chipId == null) return false

        return try {
            val def = ChipInfo.getById(chipId) ?: return false

            filesDir = activity.filesDir.absolutePath
            val dtsFile = File(filesDir, "$dtbId.dts")
            
            if (dtsFile.exists()) {
                dts_path = dtsFile.absolutePath
                ChipInfo.current = def
                currentDtb = Dtb(dtbId, def)
                prepared = true
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun isPrepared(): Boolean {
        return prepared && dts_path != null && File(dts_path).exists() && ChipInfo.current != null
    }
    
    // System props
    @JvmStatic
    fun getCurrent(name: String): String {
        return PROPERTY_CACHE.computeIfAbsent(name.lowercase()) { key ->
            val propertyName = when(key) {
                "brand" -> "ro.product.brand"
                "name" -> "ro.product.name"
                "model" -> "ro.product.model"
                "board" -> "ro.product.board"
                "id" -> "ro.product.build.id"
                "version" -> "ro.product.build.version.release"
                "fingerprint" -> "ro.product.build.fingerprint"
                "manufacturer" -> "ro.product.manufacturer"
                "device" -> "ro.product.device"
                "slot" -> "ro.boot.slot_suffix"
                else -> null
            }
            propertyName?.let { getSystemProperty(it, "") } ?: ""
        }
    }
    
    private fun getSystemProperty(key: String, defaultValue: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, defaultValue) as String
        } catch (e: Exception) {
            e.printStackTrace()
            defaultValue
        }
    }
    
    @JvmStatic
    @Throws(IOException::class)
    fun getBootImage(context: Context) {
        try {
            getBootImageByType(context, "vendor_boot")
            boot_name = "vendor_boot"
        } catch (e: Exception) {
             getBootImageByType(context, "boot")
             boot_name = "boot"
        }
    }
    
    @Throws(IOException::class)
    private fun getBootImageByType(context: Context, type: String) {
        if (!RootHelper.isRootAvailable()) {
            throw IOException("Root access not available.")
        }
        
        val bootImgPath = "${filesDir}/boot.img"
        val partition = "/dev/block/bootdevice/by-name/$type${getCurrent("slot")}"
        
        if (!RootHelper.execAndCheck("dd if=$partition of=$bootImgPath && chmod 644 $bootImgPath")) {
             throw IOException("Failed to get $type image.")
        }
        if (!File(bootImgPath).canRead()) {
            throw IOException("Boot image not readable")
        }
    }
    
    @JvmStatic
    @Throws(IOException::class)
    fun writeBootImage(context: Context) {
        val newBootPath = "${filesDir}/boot_new.img"
        val partition = "/dev/block/bootdevice/by-name/${boot_name}${getCurrent("slot")}"
        if (!RootHelper.execAndCheck("dd if=$newBootPath of=$partition")) {
            throw IOException("Failed to write boot image")
        }
    }
    
    @JvmStatic
    @Throws(IOException::class)
    fun reboot() {
        if (!RootHelper.execAndCheck("svc power reboot")) {
             throw IOException("Failed to reboot")
        }
    }
}
