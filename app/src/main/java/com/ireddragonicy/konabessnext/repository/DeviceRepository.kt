package com.ireddragonicy.konabessnext.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.model.Dtb
import com.ireddragonicy.konabessnext.model.DtbType
import com.ireddragonicy.konabessnext.utils.AssetsUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Arrays
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRepository: ShellRepository,
    private val prefs: SharedPreferences
) {

    companion object {
        private val REQUIRED_BINARIES = arrayOf("dtc", "magiskboot")
        private val DTB_MAGIC = byteArrayOf(0xD0.toByte(), 0x0D.toByte(), 0xFE.toByte(), 0xED.toByte())
        private const val DTB_HEADER_SIZE = 8
        private const val BYTE_MASK = 0xFF
        private val MODEL_PROPERTY = Pattern.compile("model\\s*=\\s*\"([^\"]+)\"")
        private const val PREFS_NAME = "KonaBessChipset"
        private const val KEY_LAST_DTB_ID = "last_dtb_id"
        private const val KEY_LAST_CHIP_TYPE = "last_chip_type"

        fun getSystemProperty(key: String, defaultValue: String): String {
            try {
                val clazz = Class.forName("android.os.SystemProperties")
                val method = clazz.getDeclaredMethod("get", String::class.java, String::class.java)
                return method.invoke(null, key, defaultValue) as String
            } catch (e: Exception) {
                e.printStackTrace()
                return defaultValue
            }
        }
    }

    private val filesDir: String = context.filesDir.absolutePath
    
    // State
    var dtsPath: String? = null
        private set
    var bootName: String? = null
        private set
    var dtbs: MutableList<Dtb> = ArrayList()
        private set
    var prepared: Boolean = false
        private set
    var currentDtb: Dtb? = null
        private set

    private var dtbNum: Int = 0
    private var dtbType: DtbType? = null

    // Chip Mappings
    private val chipMappings: Map<String, ChipInfo.Type> = mapOf(
        "kona v2.1" to ChipInfo.Type.kona,
        "kona v2" to ChipInfo.Type.kona,
        "SM8150 v2" to ChipInfo.Type.msmnile,
        "Lahaina V2.1" to ChipInfo.Type.lahaina,
        "Lahaina v2.1" to ChipInfo.Type.lahaina,
        "Lito" to ChipInfo.Type.lito_v1,
        "Lito v2" to ChipInfo.Type.lito_v2,
        "Lagoon" to ChipInfo.Type.lagoon,
        "Shima" to ChipInfo.Type.shima,
        "Yupik" to ChipInfo.Type.yupik,
        "Waipio" to ChipInfo.Type.waipio_singleBin,
        "Waipio v2" to ChipInfo.Type.waipio_singleBin,
        "Cape" to ChipInfo.Type.cape_singleBin,
        "Kalama v2" to ChipInfo.Type.kalama,
        "KalamaP v2" to ChipInfo.Type.kalama,
        "Diwali" to ChipInfo.Type.diwali,
        "Ukee" to ChipInfo.Type.ukee_singleBin,
        "Pineapple v2" to ChipInfo.Type.pineapple,
        "PineappleP v2" to ChipInfo.Type.pineapple,
        "Cliffs SoC" to ChipInfo.Type.cliffs_singleBin,
        "Cliffs 7 SoC" to ChipInfo.Type.cliffs_7_singleBin,
        "KalamaP SG SoC" to ChipInfo.Type.kalama_sg_singleBin,
        "Sun v2 SoC" to ChipInfo.Type.sun,
        "Sun Alt. Thermal Profile v2 SoC" to ChipInfo.Type.sun,
        "SunP v2 SoC" to ChipInfo.Type.sun,
        "SunP v2 Alt. Thermal Profile SoC" to ChipInfo.Type.sun,
        "Canoe v2 SoC" to ChipInfo.Type.canoe,
        "CanoeP v2 SoC" to ChipInfo.Type.canoe,
        "Tuna 7 SoC" to ChipInfo.Type.tuna,
        "Tuna SoC" to ChipInfo.Type.tuna,
        "PineappleP SG" to ChipInfo.Type.pineapple_sg,
        "KalamaP QCS" to ChipInfo.Type.kalamap_qcs_singleBin
    )

    // --- Core Logic ---

    suspend fun cleanEnv() = withContext(Dispatchers.IO) {
        resetState()
        shellRepository.execForOutput("rm -rf $filesDir/*")
    }

    private fun resetState() {
        prepared = false
        dtsPath = null
        dtbs.clear()
        bootName = null
        currentDtb = null
    }

    suspend fun setupEnv() = withContext(Dispatchers.IO) {
        for (binary in REQUIRED_BINARIES) {
            val file = File(filesDir, binary)
            AssetsUtil.exportFiles(context, binary, file.absolutePath)
            if (!file.setExecutable(true) || !file.canExecute()) {
                throw IOException("Failed to set executable: $binary")
            }
        }
    }

    suspend fun reboot() {
        if (!shellRepository.execAndCheck("svc power reboot")) {
            throw IOException("Failed to reboot")
        }
    }

    fun getCurrent(name: String): String {
        return when (name.lowercase()) {
            "brand" -> getSystemProperty("ro.product.brand", "")
            "name" -> getSystemProperty("ro.product.name", "")
            "model" -> getSystemProperty("ro.product.model", "")
            "board" -> getSystemProperty("ro.product.board", "")
            "id" -> getSystemProperty("ro.product.build.id", "")
            "version" -> getSystemProperty("ro.product.build.version.release", "")
            "fingerprint" -> getSystemProperty("ro.product.build.fingerprint", "")
            "manufacturer" -> getSystemProperty("ro.product.manufacturer", "")
            "device" -> getSystemProperty("ro.product.device", "")
            "slot" -> getSystemProperty("ro.boot.slot_suffix", "")
            else -> ""
        }
    }

    fun chooseTarget(dtb: Dtb) {
        dtsPath = "$filesDir/${dtb.id}.dts"
        com.ireddragonicy.konabessnext.core.KonaBessCore.dts_path = dtsPath
        ChipInfo.which = dtb.type
        currentDtb = dtb
        prepared = true
        saveLastChipset(dtb.id, dtb.type.name)
    }
    
    // --- Boot Image ---

    suspend fun getBootImage() = withContext(Dispatchers.IO) {
        try {
            getBootImageByType("vendor_boot")
            bootName = "vendor_boot"
        } catch (e: Exception) {
            getBootImageByType("boot")
            bootName = "boot"
        }
    }

    private suspend fun getBootImageByType(type: String) {
        if (!shellRepository.isRootAvailable()) {
            throw IOException("Root access not available.")
        }

        val bootImgPath = "$filesDir/boot.img"
        val partition = "/dev/block/bootdevice/by-name/$type${getCurrent("slot")}"

        if (!shellRepository.execAndCheck("dd if=$partition of=$bootImgPath && chmod 644 $bootImgPath")) {
            throw IOException("Failed to get $type image.")
        }

        val target = File(bootImgPath)
        if (!target.exists() || !target.canRead()) {
            target.delete()
            throw IOException("Boot image not readable")
        }
    }

    suspend fun writeBootImage() = withContext(Dispatchers.IO) {
        val newBootPath = "$filesDir/boot_new.img"
        val partition = "/dev/block/bootdevice/by-name/$bootName${getCurrent("slot")}"

        if (!shellRepository.execAndCheck("dd if=$newBootPath of=$partition")) {
            throw IOException("Failed to write boot image")
        }
    }
    
    suspend fun backupBootImage() = withContext(Dispatchers.IO) {
        val name = bootName ?: run {
            // Try to detect if not set
            if (File("$filesDir/vendor_boot.img").exists()) "vendor_boot"
            else "boot"
            // Note: If getBootImage wasn't called, we might not have the image in filesDir yet.
            // But usually backup is called after detection.
        }
        
        // Ensure source exists
        val source = "$filesDir/boot.img"
        if (!File(source).exists()) {
             throw IOException("Boot image not found in working directory. Please detect chipset first.")
        }

        val destDir = File(Environment.getExternalStorageDirectory(), "KonaBess/Backup")
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val dest = "${destDir.absolutePath}/$name.img"
        shellRepository.execForOutput("cp -f $source $dest")
        
        // Return dest path for UI
        dest
    }

    // --- Device Detection ---

    suspend fun checkDevice() = withContext(Dispatchers.IO) {
        setupEnv()
        dtbs.clear()
        
        // Optimize: verify logic is same as core
        shellRepository.execForOutput("chmod 644 $filesDir/*.dts")
        
        for (i in 0 until dtbNum) {
            val dtsFile = File(filesDir, "$i.dts")
            if (!dtsFile.exists()) continue
            
            val content = readFileToString(dtsFile)
            val chipType = detectChipType(content, i)
            
            if (chipType != ChipInfo.Type.unknown) {
                dtbs.add(Dtb(i, chipType))
            }
        }
    }
    
    private fun detectChipType(content: String, index: Int): ChipInfo.Type {
        val m = MODEL_PROPERTY.matcher(content)
        var modelContent = ""
        if (m.find()) {
            modelContent = m.group(1) ?: ""
        }

        if ("OP4A79" == getCurrent("device") && modelContent.contains("kona v2")) {
            return if (isSingleBin(content)) ChipInfo.Type.kona_singleBin else ChipInfo.Type.kona
        }

        for ((key, baseType) in chipMappings) {
            if (modelContent.contains(key)) {
                if (baseType == ChipInfo.Type.kona || baseType == ChipInfo.Type.msmnile || baseType == ChipInfo.Type.lahaina) {
                    return determineChipVariant(index, baseType, content)
                }
                return baseType
            }
        }
        return ChipInfo.Type.unknown
    }

    private fun isSingleBin(content: String): Boolean {
        return content.contains("qcom,gpu-pwrlevels {")
    }

    private fun determineChipVariant(index: Int, regular: ChipInfo.Type, content: String): ChipInfo.Type {
        return try {
            val singleBin = ChipInfo.Type.valueOf(regular.name + "_singleBin")
            if (isSingleBin(content)) singleBin else regular
        } catch (e: IllegalArgumentException) {
            regular
        }
    }
    
    private fun readFileToString(file: File): String {
        return BufferedReader(FileReader(file)).use { it.readText() }
    }

    // --- DTB Conversion ---

    suspend fun bootImage2dts() = withContext(Dispatchers.IO) {
        unpackBootImage()
        dtbNum = dtbSplit()
        
        val batchCmd = StringBuilder()
        batchCmd.append("cd $filesDir")
        
        for (i in 0 until dtbNum) {
             batchCmd.append(" && ./dtc -I dtb -O dts ")
                     .append(i).append(".dtb -o ").append(i).append(".dts")
                     .append(" && rm -f ").append(i).append(".dtb")
        }
        val output = shellRepository.execForOutput(batchCmd.toString())
        
        if (dtbNum > 0 && !File(filesDir, "${dtbNum - 1}.dts").exists()) {
             throw IOException("DTB to DTS batch conversion failed: " + output.joinToString("\n"))
        }
    }
    
    private suspend fun unpackBootImage() {
        shellRepository.execForOutput("cd $filesDir && ./magiskboot unpack boot.img")
        determineDtbType()
    }
    
    private fun determineDtbType() {
        val hasKernelDtb = File(filesDir, "kernel_dtb").exists()
        val hasDtb = File(filesDir, "dtb").exists()

        if (hasKernelDtb && hasDtb) {
            dtbType = DtbType.BOTH
        } else if (hasKernelDtb) {
            dtbType = DtbType.KERNEL_DTB
        } else if (hasDtb) {
            dtbType = DtbType.DTB
        } else {
            throw IOException("No DTB found in boot image")
        }
    }
    
    private fun dtbSplit(): Int {
        val dtbFile = getDtbFile()
        if (dtbType == DtbType.BOTH) {
            File(filesDir, "kernel_dtb").delete()
        }
        
        val dtbBytes = Files.readAllBytes(dtbFile.toPath())
        val offsets = findDtbOffsets(dtbBytes)
        
        writeDtbChunks(dtbBytes, offsets)
        dtbFile.delete()
        
        return offsets.size
    }
    
    private fun getDtbFile(): File {
        val filename = if (dtbType == DtbType.DTB || dtbType == DtbType.BOTH) "dtb" else "kernel_dtb"
        return File(filesDir, filename)
    }
    
    private fun findDtbOffsets(data: ByteArray): List<Int> {
         val offsets = ArrayList<Int>()
         var i = 0
         while (i + DTB_HEADER_SIZE < data.size) {
             if (isDtbMagic(data, i)) {
                 offsets.add(i)
                 val size = readDtbSize(data, i + 4)
                 i += Math.max(size, 1)
             } else {
                 i++
             }
         }
         return offsets
    }
    
    private fun isDtbMagic(data: ByteArray, offset: Int): Boolean {
        val slice = Arrays.copyOfRange(data, offset, offset + 4)
        return Arrays.equals(slice, DTB_MAGIC)
    }

    private fun readDtbSize(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and BYTE_MASK) shl 24) or
               ((data[offset + 1].toInt() and BYTE_MASK) shl 16) or
               ((data[offset + 2].toInt() and BYTE_MASK) shl 8) or
               (data[offset + 3].toInt() and BYTE_MASK)
    }
    
    private fun writeDtbChunks(data: ByteArray, offsets: List<Int>) {
        for (i in offsets.indices) {
            val start = offsets[i]
            val end = if (i + 1 < offsets.size) offsets[i + 1] else data.size
            Files.write(Paths.get(filesDir, "$i.dtb"), Arrays.copyOfRange(data, start, end))
        }
    }
    
    suspend fun dts2bootImage() = withContext(Dispatchers.IO) {
        val batchCmd = StringBuilder()
        batchCmd.append("cd $filesDir")
        
        for (i in 0 until dtbNum) {
             batchCmd.append(" && ./dtc -I dts -O dtb ")
                     .append(i).append(".dts -o ").append(i).append(".dtb")
        }
        
        val outputFilename = if (dtbType == DtbType.KERNEL_DTB) "kernel_dtb" else "dtb"
        batchCmd.append(" && cat")
        for (i in 0 until dtbNum) {
            batchCmd.append(" ").append(i).append(".dtb")
        }
        batchCmd.append(" > ").append(outputFilename)
        
        if (dtbType == DtbType.BOTH) {
            batchCmd.append(" && cp dtb kernel_dtb")
        }
        
        batchCmd.append(" && ./magiskboot repack boot.img boot_new.img")
        
        val output = shellRepository.execForOutput(batchCmd.toString())
        
        if (!File(filesDir, "boot_new.img").exists()) {
             throw IOException("DTS to boot image conversion failed: " + output.joinToString("\n"))
        }
    }

    // --- Preferences ---
    
    private fun saveLastChipset(dtbId: Int, chipType: String) {
        prefs.edit()
             .putInt(KEY_LAST_DTB_ID, dtbId)
             .putString(KEY_LAST_CHIP_TYPE, chipType)
             .apply()
    }
    
    fun tryRestoreLastChipset(): Boolean {
        if (!prefs.contains(KEY_LAST_DTB_ID)) return false
        
        val dtbId = prefs.getInt(KEY_LAST_DTB_ID, -1)
        val chipTypeName = prefs.getString(KEY_LAST_CHIP_TYPE, null) ?: return false
        
        if (dtbId < 0) return false
        
        try {
            val chipType = ChipInfo.Type.valueOf(chipTypeName)
            val dtsFile = File(filesDir, "$dtbId.dts")
            
            if (dtsFile.exists()) {
                dtsPath = dtsFile.absolutePath
                com.ireddragonicy.konabessnext.core.KonaBessCore.dts_path = dtsPath
                ChipInfo.which = chipType
                currentDtb = Dtb(dtbId, chipType)
                prepared = true
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
    suspend fun exportDtsFile(destPath: String) = withContext(Dispatchers.IO) {
        if (dtsPath == null || !File(dtsPath!!).exists()) {
             throw IOException("DTS file not found. Please detect chipset first.")
        }
        shellRepository.execForOutput("cp -f $dtsPath $destPath")
    }
}
