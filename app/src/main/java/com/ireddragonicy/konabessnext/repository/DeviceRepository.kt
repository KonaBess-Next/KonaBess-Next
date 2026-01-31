package com.ireddragonicy.konabessnext.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.core.processor.BootImageProcessor
import com.ireddragonicy.konabessnext.model.ChipDefinition
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
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shellRepository: ShellRepository,
    private val bootImageProcessor: BootImageProcessor,
    private val prefs: SharedPreferences
) {

    companion object {
        private const val TAG = "KonaBessDet"
        private val REQUIRED_BINARIES = arrayOf("dtc", "magiskboot")
        
        private val MODEL_PROPERTY = Pattern.compile("model\\s*=\\s*\"([^\"]+)\"")
        private val COMPATIBLE_PROPERTY = Pattern.compile("compatible\\s*=\\s*([^;]+);")
        
        private const val KEY_LAST_DTB_ID = "last_dtb_id"
        private const val KEY_LAST_CHIP_TYPE = "last_chip_type"

        fun getSystemProperty(key: String, defaultValue: String): String {
            try {
                val clazz = Class.forName("android.os.SystemProperties")
                val method = clazz.getDeclaredMethod("get", String::class.java, String::class.java)
                return method.invoke(null, key, defaultValue) as String
            } catch (e: Exception) {
                return defaultValue
            }
        }
    }

    private val filesDir: String = context.filesDir.absolutePath
    
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
    
    var activeDtbId: Int = -1
        private set

    private var dtbNum: Int = 0
    private var dtbType: DtbType? = null

    fun getDtbCount(): Int {
        val dtsFiles = File(filesDir).listFiles { _, name -> name.endsWith(".dts") }
        dtbNum = dtsFiles?.size ?: 0
        return dtbNum
    }

    fun setCustomChip(def: ChipDefinition, dtbIndex: Int) {
        val dtsFile = File(filesDir, "$dtbIndex.dts")
        if (dtsFile.exists()) {
            dtsPath = dtsFile.absolutePath
            ChipInfo.current = def
            currentDtb = Dtb(dtbIndex, def)
            prepared = true
            saveLastChipset(dtbIndex, def.id)
            saveCustomDefinition(def)
        }
    }

    private fun saveCustomDefinition(def: ChipDefinition) {
        prefs.edit()
             .putString("custom_def_id", def.id)
             .putString("custom_def_strategy", def.strategyType)
             .putString("custom_def_pattern", def.voltTablePattern)
             .putInt("custom_def_max_levels", def.maxTableLevels)
             .putBoolean("custom_def_ignore_volt", def.ignoreVoltTable)
             .apply()
    }

    fun tryLoadCustomDefinition(id: String): ChipDefinition? {
        if (!id.startsWith("custom")) return null
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
            levelCount = 480,
            levels = mapOf(),
            binDescriptions = null,
            needsCaTargetOffset = false,
            models = listOf("Custom")
        )
    }

    fun getDtsFile(index: Int): File = File(filesDir, "$index.dts")

    suspend fun cleanEnv() = withContext(Dispatchers.IO) {
        resetState()
        shellRepository.exec("rm -f $filesDir/*.dtb", "rm -f $filesDir/*.dts", "rm -f $filesDir/boot.img", "rm -f $filesDir/boot_new.img", "rm -f $filesDir/kernel_dtb", "rm -f $filesDir/dtb")
    }

    private fun resetState() {
        prepared = false
        dtsPath = null
        dtbs.clear()
        bootName = null
        currentDtb = null
        activeDtbId = -1
    }

    suspend fun setupEnv() = withContext(Dispatchers.IO) {
        if (ChipInfo.definitions.isEmpty()) ChipInfo.loadDefinitions(context)
        for (binary in REQUIRED_BINARIES) {
            val file = File(filesDir, binary)
            AssetsUtil.exportFiles(context, binary, file.absolutePath)
            file.setExecutable(true)
        }
    }

    suspend fun reboot() {
        shellRepository.execAndCheck("svc power reboot")
    }

    fun getCurrent(name: String): String {
        return when (name.lowercase()) {
            "brand" -> getSystemProperty("ro.product.brand", "")
            "model" -> getSystemProperty("ro.product.model", "")
            "device" -> getSystemProperty("ro.product.device", "")
            "slot" -> getSystemProperty("ro.boot.slot_suffix", "")
            else -> ""
        }
    }
    
    private fun sanitizeString(input: String): String {
        return input.replace("\u0000", "").replace("\"", "").replace("\n", "").trim()
    }

    private suspend fun getRunningDeviceTreeModel(): String {
        val output = shellRepository.execForOutput("cat /proc/device-tree/model")
        return sanitizeString(output.joinToString(" "))
    }

    private suspend fun getRunningCompatibleStrings(): List<String> {
        val output = shellRepository.execForOutput("cat /proc/device-tree/compatible | tr '\\0' '\\n'")
        return output.map { sanitizeString(it) }.filter { it.isNotEmpty() }
    }

    fun chooseTarget(dtb: Dtb) {
        dtsPath = "$filesDir/${dtb.id}.dts"
        ChipInfo.current = dtb.type
        currentDtb = dtb
        prepared = (dtb.type.strategyType.isNotEmpty())
        saveLastChipset(dtb.id, dtb.type.id)
    }

    fun chooseFallbackTarget(index: Int) {
        val file = File(filesDir, "$index.dts")
        if (file.exists()) {
            dtsPath = file.absolutePath
        }
    }

    suspend fun getBootImage() = withContext(Dispatchers.IO) {
        try { getBootImageByType("vendor_boot"); bootName = "vendor_boot" }
        catch (e: Exception) { getBootImageByType("boot"); bootName = "boot" }
    }

    private suspend fun getBootImageByType(type: String) {
        val bootImgPath = "$filesDir/boot.img"
        val partition = "/dev/block/bootdevice/by-name/$type${getCurrent("slot")}"
        if (!shellRepository.execAndCheck("dd if=$partition of=$bootImgPath && chmod 644 $bootImgPath")) throw IOException("Failed to get $type image.")
    }

    suspend fun writeBootImage() = withContext(Dispatchers.IO) {
        val newBootPath = "$filesDir/boot_new.img"
        val partition = "/dev/block/by-name/$bootName${getCurrent("slot")}"
        shellRepository.execAndCheck("dd if=$newBootPath of=$partition")
    }
    
    fun getBootImageFile(): File = File("$filesDir/boot.img")

    suspend fun checkDevice() = withContext(Dispatchers.IO) {
        setupEnv()
        dtbs.clear()
        shellRepository.exec("chmod -R 777 $filesDir")
        
        val runningModel = getRunningDeviceTreeModel()
        val runningCompatibles = getRunningCompatibleStrings()
        activeDtbId = -1
        
        val count = getDtbCount()
        for (i in 0 until count) {
            val dtsFile = File(filesDir, "$i.dts")
            if (!dtsFile.exists()) continue
            
            try {
                val content = readFileToString(dtsFile)
                
                val dtsModel = MODEL_PROPERTY.matcher(content).let { if (it.find()) sanitizeString(it.group(1) ?: "") else "" }
                val dtsCompatibles = mutableListOf<String>()
                val compMatcher = Pattern.compile("\"([^\"]+)\"").matcher(COMPATIBLE_PROPERTY.matcher(content).let { if (it.find()) it.group(1) ?: "" else "" })
                while (compMatcher.find()) { dtsCompatibles.add(compMatcher.group(1) ?: "") }

                if (activeDtbId == -1) {
                    if (runningCompatibles.intersect(dtsCompatibles.toSet()).isNotEmpty()) {
                        activeDtbId = i
                        Log.i(TAG, "MATCH: DTB #$i via Compatible")
                    } else if (runningModel.isNotEmpty() && dtsModel.isNotEmpty() && (dtsModel.contains(runningModel, true) || runningModel.contains(dtsModel, true))) {
                        activeDtbId = i
                        Log.i(TAG, "MATCH: DTB #$i via Model")
                    }
                }
                
                val chipId = detectChipType(content)
                val def = ChipInfo.getById(chipId ?: "") ?: createGenericPlaceholder(i, content, dtsModel)
                dtbs.add(Dtb(i, def))
            } catch (e: Exception) { Log.e(TAG, "Error checking DTB $i: ${e.message}") }
        }
        if (activeDtbId == -1 && count == 1) activeDtbId = 0
        Log.i(TAG, "Final Active ID: $activeDtbId")
    }

    private fun createGenericPlaceholder(index: Int, content: String, modelName: String): ChipDefinition {
        val displayModel = if (modelName.isNotEmpty()) modelName else "Unknown Device"
        return ChipDefinition(
            id = "unsupported_$index",
            name = "$displayModel (DTB $index)",
            maxTableLevels = 0,
            ignoreVoltTable = true,
            minLevelOffset = 1,
            voltTablePattern = null,
            strategyType = "",
            levelCount = 480,
            levels = mapOf(),
            binDescriptions = null,
            needsCaTargetOffset = false,
            models = listOf(displayModel)
        )
    }
    
    private fun detectChipType(content: String): String? {
        val m = MODEL_PROPERTY.matcher(content)
        val modelContent = if (m.find()) m.group(1) ?: "" else ""
        for (def: ChipDefinition in ChipInfo.definitions) {
            for (model in def.models) {
                if (modelContent.contains(model, ignoreCase = true)) {
                    if (content.contains("qcom,gpu-pwrlevels {")) {
                        if (def.strategyType == "SINGLE_BIN") return def.id
                        val sId = def.id + "_singleBin"
                        if (ChipInfo.getById(sId) != null) return sId
                    }
                    return def.id
                }
            }
        }
        return null
    }

    private fun readFileToString(file: File): String = BufferedReader(FileReader(file)).use { it.readText() }

    suspend fun bootImage2dts() = withContext(Dispatchers.IO) {
        dtbType = bootImageProcessor.unpackBootImage(filesDir)
        dtbNum = bootImageProcessor.splitAndConvertDtbs(filesDir, dtbType!!)
    }
    
    suspend fun dts2bootImage() = withContext(Dispatchers.IO) {
        bootImageProcessor.repackBootImage(filesDir, dtbNum, dtbType)
    }

    private fun saveLastChipset(dtbId: Int, chipType: String) {
        prefs.edit().putInt(KEY_LAST_DTB_ID, dtbId).putString(KEY_LAST_CHIP_TYPE, chipType).apply()
    }
    
    fun tryRestoreLastChipset(): Boolean {
        val dtbId = prefs.getInt(KEY_LAST_DTB_ID, -1)
        val chipId = prefs.getString(KEY_LAST_CHIP_TYPE, null) ?: return false
        val def = ChipInfo.getById(chipId) ?: tryLoadCustomDefinition(chipId) ?: return false
        if (File(filesDir, "$dtbId.dts").exists()) {
            chooseTarget(Dtb(dtbId, def))
            return true
        }
        return false
    }

    fun getDtsFile(): File = File(dtsPath ?: "$filesDir/0.dts")
}