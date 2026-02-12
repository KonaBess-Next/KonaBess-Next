package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.core.interfaces.AssetDataSource
import com.ireddragonicy.konabessnext.core.interfaces.FileDataSource
import com.ireddragonicy.konabessnext.core.interfaces.SystemPropertySource
import com.ireddragonicy.konabessnext.core.processor.BootImageProcessor
import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.model.Dtb
import com.ireddragonicy.konabessnext.model.DtbType
import com.ireddragonicy.konabessnext.model.LevelPresets
import com.ireddragonicy.konabessnext.core.scanner.DtsScanner
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.BufferedReader
import java.io.FileReader
import java.io.OutputStream
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import android.content.SharedPreferences
import android.util.Log

@Singleton
open class DeviceRepository @Inject constructor(
    private val fileDataSource: FileDataSource,
    private val systemPropertySource: SystemPropertySource,
    private val assetDataSource: AssetDataSource,
    private val shellRepository: ShellRepository,
    private val bootImageProcessor: BootImageProcessor,
    private val prefs: SharedPreferences,
    private val chipRepository: ChipRepository,
    private val userMessageManager: com.ireddragonicy.konabessnext.utils.UserMessageManager
) : DeviceRepositoryInterface {

    companion object {
        private const val TAG = "KonaBessDet"
        private val REQUIRED_BINARIES = arrayOf("dtc", "magiskboot")
        
        private val MODEL_PROPERTY = Pattern.compile("model\\s*=\\s*\"([^\"]+)\"")
        private val COMPATIBLE_PROPERTY = Pattern.compile("compatible\\s*=\\s*([^;]+);")
        
        private const val KEY_LAST_DTB_ID = "last_dtb_id"
        private const val KEY_LAST_CHIP_TYPE = "last_chip_type"
    }

    private val filesDir: String = fileDataSource.getFilesDir().absolutePath
    private val nativeLibDir: String = fileDataSource.getNativeLibDir().absolutePath
    private val savedDtsDir: File = File(filesDir, "saved_dts").also { it.mkdirs() }
    
    private var _dtsPath: String? = null
    override val dtsPath: String?
        get() = _dtsPath
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

    /** Whether the app is currently in root mode. */
    val isRootMode: Boolean
        get() = shellRepository.isRootMode

    private var importCounter: Int = 0

    // Maps imported DTB IDs (negative) to their actual file paths.
    // chooseTarget uses this to resolve the correct path for re-selected imports.
    private val importedDtsPaths = HashMap<Int, String>()

    private var dtbNum: Int = 0
    private var dtbType: DtbType? = null

    fun getDtbCount(): Int {
        val dtsFiles = File(filesDir).listFiles { _, name -> name.endsWith(".dts") }
        dtbNum = dtsFiles?.size ?: 0
        return dtbNum
    }

    fun setCustomChip(def: ChipDefinition, dtbIndex: Int) {
        val dtsFile = importedDtsPaths[dtbIndex]?.let { File(it) } ?: File(filesDir, "$dtbIndex.dts")
        if (dtsFile.exists()) {
            _dtsPath = dtsFile.absolutePath
            chipRepository.setCurrentChip(def)
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
            levelPreset = LevelPresets.inferPreset(null, 480),
            binDescriptions = null,
            needsCaTargetOffset = false,
            models = listOf("Custom")
        )
    }

    suspend fun importExternalDts(inputStream: InputStream, filename: String): Dtb {
        val timestamp = System.currentTimeMillis()
        val sanitizedName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .removeSuffix(".dts").removeSuffix(".dtb").removeSuffix(".txt")
        savedDtsDir.mkdirs()
        val rawFile = File(savedDtsDir, "raw_temp_$timestamp")
        withContext(Dispatchers.IO) {
            FileOutputStream(rawFile).use { output -> inputStream.copyTo(output) }
        }

        if (!rawFile.exists() || rawFile.length() == 0L) {
            rawFile.delete()
            throw IOException("Failed to read file — 0 bytes received. Check file permissions.")
        }

        // Detect if the file is a binary DTB or text DTS/TXT
        val importedFile = withContext(Dispatchers.IO) {
            val dtsFile = File(savedDtsDir, "${sanitizedName}_$timestamp.dts")
            if (isDtbBinary(rawFile)) {
                val dtcPath = getBinaryPath("dtc")
                shellRepository.execAndCheck("$dtcPath -I dtb -O dts \"${rawFile.absolutePath}\" -o \"${dtsFile.absolutePath}\"")
                rawFile.delete()
                if (dtsFile.exists() && dtsFile.length() > 0) {
                    dtsFile
                } else {
                    rawFile.renameTo(dtsFile)
                    dtsFile
                }
            } else {
                rawFile.renameTo(dtsFile)
                dtsFile
            }
        }

        // Use sequential negative IDs to distinguish from physical DTBs (0..n)
        importCounter++
        val virtualId = -importCounter
        
        val content = withContext(Dispatchers.IO) { importedFile.readText() }
        
        // Use DtsScanner smart detection to analyze
        val scanResult = DtsScanner.scanContent(content, virtualId)
        val def = if (scanResult.isValid) {
            DtsScanner.toChipDefinition(scanResult)
        } else {
            createGenericPlaceholder(virtualId, content, scanResult.detectedModel ?: "Imported (Unknown)")
        }

        val dtb = Dtb(virtualId, def)
        dtbs.add(dtb)
        _dtsPath = importedFile.absolutePath
        importedDtsPaths[virtualId] = importedFile.absolutePath
        chipRepository.setCurrentChip(def)
        currentDtb = dtb
        prepared = (def.strategyType.isNotEmpty())
        saveLastChipset(virtualId, def.id)
        
        return dtb
    }

    /**
     * Load all previously saved DTS files from the saved_dts directory.
     * Called on non-root startup to restore imported files across app restarts.
     */
    suspend fun loadSavedDts(): List<Dtb> = withContext(Dispatchers.IO) {
        savedDtsDir.mkdirs()
        val savedFiles = savedDtsDir.listFiles { _, name -> name.endsWith(".dts") }
            ?.sortedBy { it.lastModified() } ?: return@withContext emptyList()

        val loaded = mutableListOf<Dtb>()
        for (file in savedFiles) {
            importCounter++
            val virtualId = -importCounter
            val content = file.readText()
            val scanResult = DtsScanner.scanContent(content, virtualId)
            val def = if (scanResult.isValid) {
                DtsScanner.toChipDefinition(scanResult)
            } else {
                createGenericPlaceholder(virtualId, content, scanResult.detectedModel ?: file.nameWithoutExtension)
            }
            val dtb = Dtb(virtualId, def)
            dtbs.add(dtb)
            importedDtsPaths[virtualId] = file.absolutePath
            loaded.add(dtb)
        }
        loaded
    }

    /**
     * Delete a saved/imported DTS file by its virtual ID.
     */
    fun deleteSavedDts(virtualId: Int): Boolean {
        val path = importedDtsPaths[virtualId] ?: return false
        val file = File(path)
        val deleted = if (file.exists()) file.delete() else true
        if (deleted) {
            importedDtsPaths.remove(virtualId)
            dtbs.removeAll { it.id == virtualId }
            if (currentDtb?.id == virtualId) {
                currentDtb = null
                _dtsPath = null
                prepared = false
            }
        }
        return deleted
    }

    /**
     * Detect if a file is a binary DTB by checking for the FDT magic number (0xD00DFEED).
     */
    private fun isDtbBinary(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { stream ->
                val magic = ByteArray(4)
                stream.read(magic)
                magic[0] == 0xD0.toByte() && magic[1] == 0x0D.toByte() &&
                    magic[2] == 0xFE.toByte() && magic[3] == 0xED.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getDtsFile(index: Int): File {
        importedDtsPaths[index]?.let { return File(it) }
        return File(filesDir, "$index.dts")
    }

    suspend fun cleanEnv() = withContext(Dispatchers.IO) {
        resetState()
        if (isRootMode) {
            // Use root shell to delete — handles any file ownership
            shellRepository.execAndCheck("rm -f $filesDir/*.dtb", "rm -f $filesDir/*.dts", "rm -f $filesDir/boot.img", "rm -f $filesDir/boot_new.img", "rm -f $filesDir/kernel_dtb", "rm -f $filesDir/dtb")
        } else {
            // Non-root: use cleanWorkingFiles helper (handles root-owned leftover files)
            cleanWorkingFiles()
        }
    }

    /**
     * Delete all working/intermediate files from filesDir.
     * Handles root-owned leftover files from previous root-mode sessions.
     * On Linux/Android, unlink() depends on parent-directory permissions, not file ownership,
     * so the app CAN delete root-owned files from its own filesDir.
     */
    private fun cleanWorkingFiles() {
        val dir = File(filesDir)
        // Explicitly delete known working files
        listOf("boot.img", "boot_new.img", "kernel_dtb", "dtb").forEach { name ->
            val f = File(dir, name)
            if (f.exists()) {
                val deleted = f.delete()
                Log.d(TAG, "cleanWorkingFiles: $name delete=${deleted}")
            }
        }
        // Delete all .dtb and .dts files (split/converted artifacts)
        dir.listFiles()?.forEach { file ->
            val name = file.name
            if (name.endsWith(".dtb") || name.endsWith(".dts") ||
                name.startsWith("imported_raw_") || name.startsWith("imported_")) {
                val deleted = file.delete()
                Log.d(TAG, "cleanWorkingFiles: $name delete=${deleted}")
            }
        }
    }

    private fun resetState() {
        prepared = false
        _dtsPath = null
        dtbs.clear()
        bootName = null
        currentDtb = null
        activeDtbId = -1
        importCounter = 0
        importedDtsPaths.clear()
    }

    /**
     * Get the absolute path to a bundled binary (magiskboot or dtc).
     * In non-root mode, binaries from nativeLibraryDir are used (proper SELinux context).
     * In root mode, binaries extracted to filesDir are used (root bypasses SELinux).
     */
    fun getBinaryPath(name: String): String {
        val nativeLibFile = File(nativeLibDir, "lib${name}.so")
        if (nativeLibFile.exists() && nativeLibFile.canExecute()) {
            return nativeLibFile.absolutePath
        }
        // Fallback to filesDir for root mode or if nativeLibDir doesn't have the binary
        return File(filesDir, name).absolutePath
    }

    suspend fun setupEnv() = withContext(Dispatchers.IO) {
        if (chipRepository.definitions.value.isEmpty()) chipRepository.loadDefinitions()

        // Prefer binaries from nativeLibraryDir (bundled via jniLibs, proper SELinux context)
        val nativeLibAvailable = REQUIRED_BINARIES.all { binary ->
            File(nativeLibDir, "lib${binary}.so").exists()
        }

        if (!nativeLibAvailable) {
            // Fallback: extract from assets (legacy path, works in root mode)
            Log.d(TAG, "setupEnv: extracting binaries from assets")
            for (binary in REQUIRED_BINARIES) {
                val file = File(filesDir, binary)
                if (file.exists()) file.delete()
                exportResource(binary, file.absolutePath)
                file.setExecutable(true, false)
                file.setReadable(true, false)
            }
        }
        if (isRootMode) {
            shellRepository.execAndCheck("chmod -R 755 $filesDir")
        }
    }

    private fun exportResource(src: String, outPath: String) {
        // Simple export since we know binaries are single files in root or specific paths
        // If directory support is needed, we can implement strict recursive logic,
        // but for now, we only use this for binaries which are files.
        try {
            assetDataSource.open(src).use { input ->
                FileOutputStream(File(outPath)).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun reboot() {
        shellRepository.execAndCheck("svc power reboot")
    }

    fun getCurrent(name: String): String {
        return when (name.lowercase()) {
            "brand" -> systemPropertySource.get("ro.product.brand", "")
            "model" -> systemPropertySource.get("ro.product.model", "")
            "device" -> systemPropertySource.get("ro.product.device", "")
            "slot" -> systemPropertySource.get("ro.boot.slot_suffix", "")
            else -> ""
        }
    }

    private fun getInactiveSlotSuffix(): String {
        return when (getCurrent("slot").trim()) {
            "_a", "a" -> "_b"
            "_b", "b" -> "_a"
            "" -> throw IllegalStateException("Could not determine A/B slot status")
            else -> throw IllegalStateException("This device does not report a valid A/B slot suffix")
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

    override suspend fun getRunTimeGpuFrequencies(): List<Long> = withContext(Dispatchers.IO) {
        val probeCommands = listOf(
            "cat /sys/class/kgsl/kgsl-3d0/frequencies",
            "cat /sys/class/kgsl/kgsl-3d0/available_frequencies",
            "cat /sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
            "cat /sys/devices/platform/soc/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/frequencies",
            "cat /sys/devices/platform/soc/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/available_frequencies",
            "cat /sys/devices/platform/soc/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/devfreq/available_frequencies",
            "cat /sys/devices/platform/soc@0/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/frequencies",
            "cat /sys/devices/platform/soc@0/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/available_frequencies",
            "cat /sys/devices/platform/soc@0/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/devfreq/available_frequencies",
            "cat /sys/devices/platform/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/frequencies",
            "cat /sys/devices/platform/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/available_frequencies",
            "cat /sys/devices/platform/*.qcom,kgsl-3d0/kgsl/kgsl-3d0/devfreq/available_frequencies"
        )

        for (command in probeCommands) {
            val output = try {
                shellRepository.execForOutput(command)
            } catch (securityException: SecurityException) {
                Log.w(TAG, "GPU runtime frequency probe blocked: $command", securityException)
                return@withContext emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            val parsed = parseRuntimeFrequencyOutput(output)
            if (parsed.isNotEmpty()) {
                return@withContext parsed
            }
        }

        emptyList()
    }

    fun chooseTarget(dtb: Dtb) {
        // Imported DTBs have negative IDs and their files are NOT at {id}.dts.
        // Look up the actual path from the import registry.
        val importedPath = importedDtsPaths[dtb.id]
        _dtsPath = importedPath ?: "$filesDir/${dtb.id}.dts"
        chipRepository.setCurrentChip(dtb.type)
        currentDtb = dtb
        prepared = (dtb.type.strategyType.isNotEmpty())
        saveLastChipset(dtb.id, dtb.type.id)
    }

    fun chooseFallbackTarget(index: Int) {
        val file = File(filesDir, "$index.dts")
        if (file.exists()) {
            _dtsPath = file.absolutePath
        }
    }

    suspend fun getBootImage() = withContext(Dispatchers.IO) {
        try { getBootImageByType("vendor_boot"); bootName = "vendor_boot" }
        catch (e: Exception) { getBootImageByType("boot"); bootName = "boot" }
    }

    /**
     * Import a boot image from an InputStream (non-root mode).
     * Copies the stream content to filesDir/boot.img for processing.
     */
    suspend fun importBootImage(inputStream: InputStream, filename: String) = withContext(Dispatchers.IO) {
        // Clean up ALL working artifacts first — old files may be root-owned
        // from a previous root-mode session and can't be overwritten directly.
        cleanWorkingFiles()

        val bootImgFile = File(filesDir, "boot.img")
        FileOutputStream(bootImgFile).use { output ->
            val bytesCopied = inputStream.copyTo(output)
            Log.d(TAG, "importBootImage: copied $bytesCopied bytes from $filename")
        }
        if (!bootImgFile.exists() || bootImgFile.length() == 0L) {
            throw IOException("Failed to import boot image — 0 bytes received.")
        }
        // Determine boot name from filename hint
        bootName = if (filename.contains("vendor", ignoreCase = true)) "vendor_boot" else "boot"
        Log.d(TAG, "importBootImage: ${bootImgFile.length()} bytes, bootName=$bootName")
    }

    /**
     * Export the repacked boot image to an output stream (non-root mode).
     */
    suspend fun exportRepackedImage(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val repackedFile = File(filesDir, "boot_new.img")
        if (!repackedFile.exists() || repackedFile.length() == 0L) {
            throw IOException("Repacked image not found. Run repack first.")
        }
        FileInputStream(repackedFile).use { input ->
            input.copyTo(outputStream)
        }
    }

    private suspend fun getBootImageByType(type: String) {
        val bootImgPath = "$filesDir/boot.img"
        val partition = "/dev/block/bootdevice/by-name/$type${getCurrent("slot")}"
        if (!shellRepository.execAndCheck("dd if=$partition of=$bootImgPath && chmod 644 $bootImgPath")) throw IOException("Failed to get $type image.")
    }

    suspend fun writeBootImage() = withContext(Dispatchers.IO) {
        val newBootPath = "$filesDir/boot_new.img"
        val partition = "/dev/block/by-name/$bootName${getCurrent("slot")}"
        
        verifyPartitionSize(partition, newBootPath)
        
        shellRepository.execAndCheck("dd if=$newBootPath of=$partition")
    }

    suspend fun installToInactiveSlot(shouldBackup: Boolean): String = withContext(Dispatchers.IO) {
        if (!isRootMode) throw IllegalStateException("Root access required")
        val newImg = File(filesDir, "boot_new.img")
        if (!newImg.exists() || newImg.length() == 0L) {
            throw IOException("Repacked image not found. Please 'Build > Repack' first.")
        }

        val resolvedBootName = bootName
            ?: throw IllegalStateException("Boot partition type unknown. Please import/detect first.")
        val targetSlot = getInactiveSlotSuffix()
        val targetPartition = "/dev/block/by-name/${resolvedBootName}${targetSlot}"

        if (!shellRepository.execAndCheck("[ -e '$targetPartition' ]")) {
            throw IOException("Target partition $targetPartition does not exist")
        }

        val sb = StringBuilder()

        if (shouldBackup) {
            val timestamp = System.currentTimeMillis()
            val backupDir = "/sdcard/Download"
            val backupPath = "$backupDir/boot_backup_${resolvedBootName}${targetSlot}_$timestamp.img"

            if (!shellRepository.execAndCheck("mkdir -p '$backupDir'")) {
                throw IOException("Failed to prepare backup directory at $backupDir")
            }

            val backupResult = shellRepository.execAndCheck("dd if='$targetPartition' of='$backupPath'")
            if (!backupResult) throw IOException("Failed to backup inactive slot")
            sb.append("Backup saved to: $backupPath\n")
        }

        verifyPartitionSize(targetPartition, newImg.absolutePath)
        val flashResult = shellRepository.execAndCheck("dd if='${newImg.absolutePath}' of='$targetPartition'")
        if (!flashResult) throw IOException("Failed to write to $targetPartition")

        sb.append("Successfully installed to inactive slot ($targetSlot).")
        return@withContext sb.toString()
    }

    private suspend fun verifyPartitionSize(partitionPath: String, imagePath: String) {
        val imageSize = File(imagePath).length()
        if (imageSize == 0L) throw IOException("New boot image is empty or not found at $imagePath")

        val output = shellRepository.execForOutput("blockdev --getsize64 $partitionPath")
        val partitionSizeStr = output.firstOrNull()?.trim()
        val partitionSize = partitionSizeStr?.toLongOrNull() 
            ?: throw IOException("Failed to get partition size for $partitionPath. Output: $output")

        if (imageSize > partitionSize) {
            throw IOException("Build failed: Image size ($imageSize bytes) exceeds partition size ($partitionSize bytes).")
        }
    }
    
    fun getBootImageFile(): File = File("$filesDir/boot.img")

    suspend fun checkDevice() = withContext(Dispatchers.IO) {
        if (isRootMode && !shellRepository.isRootAvailable()) {
            userMessageManager.emitError("Root Access Required", "This application requires root access to function. Please grant root permissions.")
            return@withContext
        }
        // setupEnv is already called by the caller (detectChipset/importBootImage)
        dtbs.clear()
        if (isRootMode) {
            shellRepository.execAndCheck("chmod -R 777 $filesDir")
        }
        
        val runningModel = if (isRootMode) getRunningDeviceTreeModel() else ""
        val runningCompatibles = if (isRootMode) getRunningCompatibleStrings() else emptyList()
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
                    } else if (runningModel.isNotEmpty() && dtsModel.isNotEmpty() && (dtsModel.contains(runningModel, true) || runningModel.contains(dtsModel, true))) {
                        activeDtbId = i
                    }
                }
                
                val chipId = detectChipType(content)
                val def = if (chipId != null) {
                    // Found in hardcoded definitions
                    chipRepository.getChipById(chipId) ?: createGenericPlaceholder(i, content, dtsModel)
                } else {
                    // Not in definitions.json → use smart detection
                    Log.d(TAG, "DTB $i not in definitions, running smart detection...")
                    val scanResult = DtsScanner.scanContent(content, i)
                    Log.d(TAG, "Smart detect DTB $i: valid=${scanResult.isValid}, " +
                        "strategy=${scanResult.recommendedStrategy}, bins=${scanResult.binCount}, " +
                        "levels=${scanResult.levelCount}, voltType=${scanResult.voltageType}, " +
                        "confidence=${scanResult.confidence}")
                    
                    if (scanResult.isValid) {
                        // Smart detection succeeded — create a proper working ChipDefinition
                        val smartDef = DtsScanner.toChipDefinition(scanResult)
                        // Save it so it persists across sessions
                        saveCustomDefinition(smartDef)
                        smartDef
                    } else {
                        createGenericPlaceholder(i, content, dtsModel)
                    }
                }
                dtbs.add(Dtb(i, def))
            } catch (e: Exception) {
                Log.e(TAG, "Error checking DTB $i: ${e.message}")
            }
        }
        if (activeDtbId == -1 && count == 1) activeDtbId = 0
    }

    private fun createGenericPlaceholder(index: Int, content: String, modelName: String): ChipDefinition {
        val displayModel = if (modelName.isNotEmpty()) modelName else "Unknown Device"
        return ChipDefinition(
            id = "unsupported_$index",
            name = "$displayModel (DTB $index)",
            maxTableLevels = 15,
            ignoreVoltTable = true,
            minLevelOffset = 1,
            voltTablePattern = null,
            strategyType = "",
            levelCount = 480,
            levelPreset = LevelPresets.inferPreset(modelName, 480),
            binDescriptions = null,
            needsCaTargetOffset = false,
            models = listOf(displayModel)
        )
    }
    
    private fun detectChipType(content: String): String? {
        val m = MODEL_PROPERTY.matcher(content)
        val modelContent = if (m.find()) m.group(1) ?: "" else ""
        for (def: ChipDefinition in chipRepository.definitions.value) {
            for (model in def.models) {
                if (modelContent.contains(model, ignoreCase = true)) {
                    if (content.contains("qcom,gpu-pwrlevels {")) {
                        if (def.strategyType == "SINGLE_BIN") return def.id
                        val sId = def.id + "_singleBin"
                        if (chipRepository.getChipById(sId) != null) return sId
                    }
                    return def.id
                }
            }
        }
        return null
    }

    private fun parseRuntimeFrequencyOutput(lines: List<String>): List<Long> {
        if (lines.isEmpty()) return emptyList()

        return lines.asSequence()
            .flatMap { line ->
                line.replace(",", " ")
                    .split(Regex("\\s+"))
                    .asSequence()
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                if (token.startsWith("0x", ignoreCase = true)) {
                    token.substring(2).toLongOrNull(16)
                } else {
                    token.toLongOrNull()
                }
            }
            .filter { it > 0L }
            .toList()
    }

    private fun readFileToString(file: File): String = BufferedReader(FileReader(file)).use { it.readText() }

    suspend fun bootImage2dts() = withContext(Dispatchers.IO) {
        dtbType = bootImageProcessor.unpackBootImage(filesDir, getBinaryPath("magiskboot"))
        dtbNum = bootImageProcessor.splitAndConvertDtbs(filesDir, getBinaryPath("dtc"), dtbType!!)
    }
    
    suspend fun dts2bootImage(): File = withContext(Dispatchers.IO) {
        bootImageProcessor.repackBootImage(filesDir, getBinaryPath("magiskboot"), getBinaryPath("dtc"), dtbNum, dtbType)
        File(filesDir, "boot_new.img")
    }

    private fun saveLastChipset(dtbId: Int, chipType: String) {
        prefs.edit().putInt(KEY_LAST_DTB_ID, dtbId).putString(KEY_LAST_CHIP_TYPE, chipType).apply()
    }
    
    override fun tryRestoreLastChipset(): Boolean {
        val dtbId = prefs.getInt(KEY_LAST_DTB_ID, -1)
        val chipId = prefs.getString(KEY_LAST_CHIP_TYPE, null) ?: return false
        val def = chipRepository.getChipById(chipId) ?: tryLoadCustomDefinition(chipId) ?: return false
        if (File(filesDir, "$dtbId.dts").exists()) {
            chooseTarget(Dtb(dtbId, def))
            return true
        }
        return false
    }

    override fun getDtsFile(): File = File(dtsPath ?: "$filesDir/0.dts")
}