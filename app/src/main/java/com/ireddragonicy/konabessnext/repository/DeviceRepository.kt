package com.ireddragonicy.konabessnext.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.model.Dtb
import com.ireddragonicy.konabessnext.model.DtbType
import com.ireddragonicy.konabessnext.utils.AssetsUtil
import com.ireddragonicy.konabessnext.core.KonaBessCore
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
        private const val TAG = "KonaBessDet"
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

    fun getDtbCount(): Int {
        return dtbNum
    }

    fun setCustomChip(def: ChipDefinition, dtbIndex: Int) {
        val dtsFile = File(filesDir, "$dtbIndex.dts")
        if (dtsFile.exists()) {
            dtsPath = dtsFile.absolutePath
            KonaBessCore.dts_path = dtsPath
            ChipInfo.current = def
            currentDtb = Dtb(dtbIndex, def)
            prepared = true
            
            // Persist this custom definition
            saveLastChipset(dtbIndex, def.id)
            saveCustomDefinition(def)
        }
    }

    private fun saveCustomDefinition(def: ChipDefinition) {
        // Simple serialization or just marking it. 
        // For full persistence, we'd need to serialize the whole object to JSON.
        // For now, let's just save the ID and valid flag if it's a simple custom flow.
        // Ideally, we serialize `def` to a separate pref or file.
        // Since we are adding deep scan, we should save the parameters (Strategy, VoltPattern, etc)
        // For MVP, we can save keys like "custom_def_strategy", "custom_def_pattern", etc.
        prefs.edit()
             .putString("custom_def_id", def.id)
             .putString("custom_def_strategy", def.strategyType)
             .putString("custom_def_pattern", def.voltTablePattern)
             .putInt("custom_def_max_levels", def.maxTableLevels)
             .putBoolean("custom_def_ignore_volt", def.ignoreVoltTable)
             .apply()
    }

    fun tryLoadCustomDefinition(id: String): ChipDefinition? {
        if (!id.startsWith("custom_detected")) return null
        
        val strategy = prefs.getString("custom_def_strategy", "MULTI_BIN") ?: "MULTI_BIN"
        val pattern = prefs.getString("custom_def_pattern", null)
        val maxLevels = prefs.getInt("custom_def_max_levels", 11)
        val ignoreVolt = prefs.getBoolean("custom_def_ignore_volt", false)
        
        return ChipDefinition(
            id = id,
            name = "Custom Discovered Device",
            maxTableLevels = maxLevels,
            ignoreVoltTable = ignoreVolt,
            minLevelOffset = 1,
            voltTablePattern = pattern,
            strategyType = strategy,
            levelCount = 416, 
            levels = mapOf(),
            models = listOf("Custom")
        )
    }

    fun getDtsFile(index: Int): File {
        return File(filesDir, "$index.dts")
    }

    // --- Core Logic ---

    suspend fun cleanEnv() = withContext(Dispatchers.IO) {
        resetState()
        // Aggressive cleanup with root to ensure no root-owned files block the app
        shellRepository.exec("rm -f $filesDir/*.dtb", "rm -f $filesDir/*.dts", "rm -f $filesDir/boot.img", "rm -f $filesDir/boot_new.img", "rm -f $filesDir/kernel_dtb", "rm -f $filesDir/dtb")
    }

    private fun resetState() {
        prepared = false
        dtsPath = null
        dtbs.clear()
        bootName = null
        currentDtb = null
    }

    suspend fun setupEnv() = withContext(Dispatchers.IO) {
        if (ChipInfo.definitions.isEmpty()) {
            ChipInfo.loadDefinitions(context)
        }
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
        KonaBessCore.dts_path = dtsPath
        ChipInfo.current = dtb.type
        currentDtb = dtb
        prepared = true
        saveLastChipset(dtb.id, dtb.type.id)
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
    
    fun getBootImageFile(): File {
        val name = bootName ?: run {
            // Try to detect if not set
            if (File("$filesDir/vendor_boot.img").exists()) "vendor_boot"
            else "boot"
        }
        
        // Ensure source exists
        val source = "$filesDir/boot.img"
        if (!File(source).exists()) {
             throw IOException("Boot image not found in working directory. Please detect chipset first.")
        }
        return File(source)
    }

    // --- Device Detection ---

    suspend fun checkDevice() = withContext(Dispatchers.IO) {
        setupEnv()
        dtbs.clear()
        
        // Ensure all extracted files are accessible and writable by the app process
        shellRepository.exec("chmod -R 777 $filesDir")
        
        Log.d(TAG, "Checking device with dtbNum: $dtbNum")
        if (dtbNum == 0) {
            Log.e(TAG, "dtbNum is 0, no DTBs to check!")
        }
        for (i in 0 until dtbNum) {
            val dtsFile = File(filesDir, "$i.dts")
            if (!dtsFile.exists()) {
                Log.w(TAG, "DTS file $i.dts NOT FOUND at $filesDir")
                continue
            }
            
            try {
                val content = readFileToString(dtsFile)
                val chipId = detectChipType(content, i)
                
                Log.d(TAG, "DTB $i detection result: $chipId")
                
                if (chipId != null) {
                    val def = ChipInfo.getById(chipId)
                    if (def != null) {
                        Log.d(TAG, "Mapped to definition: ${def.name}")
                        dtbs.add(Dtb(i, def))
                    } else {
                        Log.e(TAG, "Definition not found for ID: $chipId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking DTB $i: ${e.message}", e)
            }
        }
    }
    
    private fun detectChipType(content: String, index: Int): String? {
        val m = MODEL_PROPERTY.matcher(content)
        var modelContent = ""
        if (m.find()) {
            modelContent = m.group(1) ?: ""
        }
        
        Log.d(TAG, "Detecting DTB $index. Model: '$modelContent'")

        if ("OP4A79" == getCurrent("device") && modelContent.contains("kona v2")) {
            return if (isSingleBin(content)) "kona_singleBin" else "kona"
        }

        // Use dynamic detection from definitions
        for (def: ChipDefinition in ChipInfo.definitions) {
            for (model in def.models) {
                if (modelContent.contains(model, ignoreCase = true)) {
                    Log.d(TAG, "Matched key '$model' to ID '${def.id}'")
                    
                    if (isSingleBin(content)) {
                        if (def.strategyType == "SINGLE_BIN") return def.id
                        
                        // Check if single bin variant exists
                        val singleBinId = def.id + "_singleBin"
                        if (ChipInfo.getById(singleBinId) != null) {
                            return singleBinId
                        }
                    }
                    return def.id
                }
            }
        }
        
        Log.w(TAG, "No mapping found for model: '$modelContent'")
        return null
    }

    private fun isSingleBin(content: String): Boolean {
        return content.contains("qcom,gpu-pwrlevels {")
    }
    
    private fun readFileToString(file: File): String {
        return BufferedReader(FileReader(file)).use { it.readText() }
    }

    // --- DTB Conversion ---

    suspend fun bootImage2dts() = withContext(Dispatchers.IO) {
        unpackBootImage()
        dtbNum = dtbSplit()
        
        Log.d(TAG, "bootImage2dts: dtbNum after split: $dtbNum")
        
        val batchCmd = StringBuilder()
        batchCmd.append("cd $filesDir")
        
        for (i in 0 until dtbNum) {
             batchCmd.append(" && ./dtc -I dtb -O dts ")
                     .append(i).append(".dtb -o ").append(i).append(".dts")
                     .append(" && rm -f ").append(i).append(".dtb")
        }
        val cmdStr = batchCmd.toString()
        Log.d(TAG, "Executing batch conversion: $cmdStr")
        val output = shellRepository.execForOutput(cmdStr)
        
        if (dtbNum > 0 && !File(filesDir, "${dtbNum - 1}.dts").exists()) {
             Log.e(TAG, "DTS conversion failed. Magiskboot output could be useful here.")
             throw IOException("DTB to DTS batch conversion failed: " + output.joinToString("\n"))
        }
    }
    
    private suspend fun unpackBootImage() {
        Log.d(TAG, "Unpacking boot image at $filesDir")
        val output = shellRepository.execForOutput("cd $filesDir && ./magiskboot unpack boot.img")
        Log.d(TAG, "Magiskboot output: ${output.joinToString("\n")}")
        
        // CRITICAL: Magiskboot creates files as root. We MUST grant 777 to allow app read/write/delete.
        shellRepository.exec("chmod -R 777 $filesDir")
        
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
            Log.e(TAG, "No DTB or kernel_dtb found in filesDir after unpack!")
            val filesList = File(filesDir).list()?.joinToString(", ")
            Log.d(TAG, "Files present: $filesList")
            throw IOException("No DTB found in boot image")
        }
        Log.d(TAG, "Determined dtbType: $dtbType")
    }
    
    private fun dtbSplit(): Int {
        val dtbFile = getDtbFile()
        Log.d(TAG, "Splitting DTB file: ${dtbFile.name} (size: ${dtbFile.length()})")
        
        if (dtbType == DtbType.BOTH) {
            File(filesDir, "kernel_dtb").delete()
        }
        
        if (dtbFile.length() == 0L) {
            Log.e(TAG, "DTB file is EMPTY!")
            return 0
        }

        val dtbBytes = Files.readAllBytes(dtbFile.toPath())
        val offsets = findDtbOffsets(dtbBytes)
        
        Log.d(TAG, "Found ${offsets.size} DTB offsets via magic search")
        
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
            val chunkPath = Paths.get(filesDir, "$i.dtb")
            
            // Delete existing file with root just in case it's root-owned and Files.write would fail
            File(chunkPath.toString()).delete() 
            
            Files.write(chunkPath, Arrays.copyOfRange(data, start, end))
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
        
        // Ensure definitions are loaded
        if (ChipInfo.definitions.isEmpty()) {
            ChipInfo.loadDefinitions(context)
        }

        val dtbId = prefs.getInt(KEY_LAST_DTB_ID, -1)
        val chipId = prefs.getString(KEY_LAST_CHIP_TYPE, null) ?: return false
        
        if (dtbId < 0) return false
        
        try {
            var def = ChipInfo.getById(chipId)
            if (def == null && chipId.startsWith("custom_detected")) {
                def = tryLoadCustomDefinition(chipId)
            }
            if (def == null) return false
            val dtsFile = File(filesDir, "$dtbId.dts")
            
            if (dtsFile.exists()) {
                dtsPath = dtsFile.absolutePath
                KonaBessCore.dts_path = dtsPath
                ChipInfo.current = def
                currentDtb = Dtb(dtbId, def)
                prepared = true
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
    fun getDtsFile(): File {
        if (dtsPath == null || !File(dtsPath!!).exists()) {
             throw IOException("DTS file not found. Please detect chipset first.")
        }
        return File(dtsPath!!)
    }
}