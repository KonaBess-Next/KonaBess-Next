package com.ireddragonicy.konabessnext.core.processor

import com.ireddragonicy.konabessnext.model.DtbType
import com.ireddragonicy.konabessnext.model.TargetPartition
import com.ireddragonicy.konabessnext.repository.ShellRepository
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootImageProcessor @Inject constructor(
    private val shellRepository: ShellRepository
) {
    companion object {
        private const val TAG = "BootImageProcessor"
        private val DTB_MAGIC = byteArrayOf(0xD0.toByte(), 0x0D.toByte(), 0xFE.toByte(), 0xED.toByte())
        private const val DTBO_TABLE_MAGIC = 0xD7B7AB1E.toInt()
        private const val DTBO_HEADER_SIZE = 32
        private const val DTBO_ENTRY_SIZE = 32
        private const val MIN_DTB_SIZE = 40
        private const val DTB_HEADER_SIZE = 8
        private const val BYTE_MASK = 0xFF
    }

    private data class DtboEntryMeta(
        val id: Int,
        val rev: Int,
        val custom: IntArray
    )

    private data class DtboMetadata(
        val usesTable: Boolean,
        val pageSize: Int,
        val entries: List<DtboEntryMeta>
    )

    private val dtboMetadataByDir: MutableMap<String, DtboMetadata> = HashMap()

    private fun renderShellFailure(err: List<String>, out: List<String>): String {
        val lines = (err + out).map { it.trim() }.filter { it.isNotEmpty() }
        return if (lines.isEmpty()) "Unknown shell error" else lines.take(8).joinToString("\n")
    }

    private fun extractErrorLineFromDtcOutput(
        dtsFileName: String,
        err: List<String>,
        out: List<String>
    ): Int? {
        val lines = err + out
        val exactRegex = Regex("""\b${Regex.escape(dtsFileName)}:(\d+)(?:[.:][^\s]*)?""")
        for (line in lines) {
            val match = exactRegex.find(line) ?: continue
            val lineNo = match.groupValues.getOrNull(1)?.toIntOrNull()
            if (lineNo != null) return lineNo
        }

        val genericRegex = Regex("""\b[^:\s]+\.dts:(\d+)(?:[.:][^\s]*)?""")
        for (line in lines) {
            val match = genericRegex.find(line) ?: continue
            val lineNo = match.groupValues.getOrNull(1)?.toIntOrNull()
            if (lineNo != null) return lineNo
        }
        return null
    }

    private fun buildDtsContextSnippet(dtsFile: File, targetLine: Int, radius: Int = 5): String {
        if (!dtsFile.exists()) return ""
        return try {
            val lines = dtsFile.readLines()
            if (targetLine !in 1..lines.size) return ""

            val start = maxOf(1, targetLine - radius)
            val end = minOf(lines.size, targetLine + radius)
            val sb = StringBuilder()
            sb.append("DTS context (${dtsFile.name}:$targetLine)\n")

            for (lineNo in start..end) {
                val marker = if (lineNo == targetLine) ">>" else "  "
                sb.append(marker)
                    .append(lineNo.toString().padStart(6, ' '))
                    .append("| ")
                    .append(lines[lineNo - 1])
                    .append('\n')
            }
            sb.toString().trimEnd()
        } catch (_: Exception) {
            ""
        }
    }

    private fun normalizeGpuModelName(input: String): String {
        val collapsed = input
            .replace("\u0000", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        return collapsed
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    private fun tryRepairGpuModelPropertyLine(dtsFile: File): Boolean {
        if (!dtsFile.exists()) return false
        val regex = Regex("""^(\s*qcom,gpu-model\s*=\s*)(.*?)(\s*;.*)$""")
        val lines = dtsFile.readLines().toMutableList()
        var changed = false

        for (idx in lines.indices) {
            val match = regex.find(lines[idx]) ?: continue
            val rawValue = match.groupValues[2].trim()
            val unquoted = rawValue
                .removePrefix("\"")
                .removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            val normalized = normalizeGpuModelName(unquoted)
            val repairedLine = "${match.groupValues[1]}\"$normalized\"${match.groupValues[3]}"
            if (repairedLine != lines[idx]) {
                lines[idx] = repairedLine
                changed = true
            }
            break
        }

        if (changed) {
            dtsFile.writeText(lines.joinToString("\n"))
        }
        return changed
    }

    private fun tryRepairBareByteArrayLines(dtsFile: File): Boolean {
        if (!dtsFile.exists()) return false
        // Repairs lines accidentally rewritten as:
        //   prop = ff ff ff fe 17 02;
        // back to:
        //   prop = [ff ff ff fe 17 02];
        val regex = Regex(
            """^(\s*[A-Za-z0-9,_+.\-@#]+\s*=\s*)([0-9a-fA-F]{2}(?:\s+[0-9a-fA-F]{2})+)(\s*;.*)$"""
        )
        val lines = dtsFile.readLines().toMutableList()
        var changed = false

        for (idx in lines.indices) {
            val match = regex.find(lines[idx]) ?: continue
            lines[idx] = "${match.groupValues[1]}[${match.groupValues[2]}]${match.groupValues[3]}"
            changed = true
        }

        if (changed) {
            dtsFile.writeText(lines.joinToString("\n"))
        }
        return changed
    }

    private fun normalizeSplitNibbleTokens(content: String): String? {
        val tokens = content
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        if (tokens.any { !it.matches(Regex("[0-9a-fA-F]{1,2}")) }) return null
        if (tokens.none { it.length == 1 }) return null

        val normalized = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val current = tokens[i]
            if (current.length == 2) {
                normalized.add(current.lowercase())
                i++
                continue
            }

            if (i + 1 >= tokens.size || tokens[i + 1].length != 1) {
                return null
            }

            normalized.add((current + tokens[i + 1]).lowercase())
            i += 2
        }

        return normalized.joinToString(" ")
    }

    private fun tryRepairSplitNibbleByteArrayLines(dtsFile: File): Boolean {
        if (!dtsFile.exists()) return false
        // Repairs lines accidentally rewritten as:
        //   prop = [1 d 1 d 1 d];
        // back to:
        //   prop = [1d 1d 1d];
        val regex = Regex(
            """^(\s*[A-Za-z0-9,_+.\-@#]+\s*=\s*\[)([^\]]+)(\]\s*;.*)$"""
        )
        val lines = dtsFile.readLines().toMutableList()
        var changed = false

        for (idx in lines.indices) {
            val match = regex.find(lines[idx]) ?: continue
            val normalized = normalizeSplitNibbleTokens(match.groupValues[2]) ?: continue
            val repaired = "${match.groupValues[1]}$normalized${match.groupValues[3]}"
            if (repaired != lines[idx]) {
                lines[idx] = repaired
                changed = true
            }
        }

        if (changed) {
            dtsFile.writeText(lines.joinToString("\n"))
        }
        return changed
    }

    private fun tryRepairCommonSyntax(dtsFile: File): Boolean {
        var changed = false
        if (tryRepairGpuModelPropertyLine(dtsFile)) changed = true
        if (tryRepairBareByteArrayLines(dtsFile)) changed = true
        if (tryRepairSplitNibbleByteArrayLines(dtsFile)) changed = true
        return changed
    }

    suspend fun unpackBootImage(
        filesDir: String,
        magiskbootPath: String,
        partition: TargetPartition = TargetPartition.BOOT
    ): DtbType {
        val binaryFile = File(magiskbootPath)
        if (!binaryFile.exists()) {
            throw Exception("magiskboot binary not found at $magiskbootPath")
        }

        if (partition == TargetPartition.DTBO) {
            val dtboFile = File(filesDir, partition.imageFileName)
            if (!dtboFile.exists() || dtboFile.length() < MIN_DTB_SIZE) {
                throw Exception("dtbo image not found or invalid at ${dtboFile.absolutePath}")
            }
            return DtbType.DTBO
        }

        val result = shellRepository.execAdaptive("cd $filesDir && $magiskbootPath unpack ${partition.imageFileName}")
        if (!result.isSuccess) {
            val errMsg = result.err.joinToString("\n").ifEmpty { result.out.joinToString("\n") }
            throw Exception("Failed to unpack boot image: $errMsg")
        }
        
        // Ensure all unpacked files are readable/writable
        File(filesDir).listFiles()?.forEach { it.setReadable(true, false); it.setWritable(true, false) }
        
        val hasKernelDtb = File(filesDir, "kernel_dtb").exists()
        val hasDtb = File(filesDir, "dtb").exists()
        
        return when {
            hasKernelDtb && hasDtb -> DtbType.BOTH
            hasKernelDtb -> DtbType.KERNEL_DTB
            hasDtb -> DtbType.DTB
            else -> throw Exception("No DTB found in boot image")
        }
    }

    suspend fun splitAndConvertDtbs(filesDir: String, dtcPath: String, dtbType: DtbType): Int {
        // Cleanup stale split artifacts from previous runs (can be root-owned after repack)
        shellRepository.execAndCheck("cd \"$filesDir\" && rm -f ./*.dtb ./*.dts")

        if (dtbType == DtbType.DTBO) {
            return splitAndConvertDtbo(filesDir, dtcPath)
        }

        val dtbFile = if (dtbType == DtbType.DTB || dtbType == DtbType.BOTH) File(filesDir, "dtb") else File(filesDir, "kernel_dtb")
        if (dtbType == DtbType.BOTH) shellRepository.execAndCheck("cd \"$filesDir\" && rm -f kernel_dtb")
        
        if (!dtbFile.exists()) return 0

        val data = Files.readAllBytes(dtbFile.toPath())
        val offsets = mutableListOf<Int>()
        var i = 0
        while (i + DTB_HEADER_SIZE < data.size) {
            if (data[i] == DTB_MAGIC[0] && data[i+1] == DTB_MAGIC[1] && data[i+2] == DTB_MAGIC[2] && data[i+3] == DTB_MAGIC[3]) {
                offsets.add(i)
                val size = ((data[i+4].toInt() and BYTE_MASK) shl 24) or 
                           ((data[i+5].toInt() and BYTE_MASK) shl 16) or 
                           ((data[i+6].toInt() and BYTE_MASK) shl 8) or 
                           (data[i+7].toInt() and BYTE_MASK)
                i += Math.max(size, 1)
            } else i++
        }

        for (idx in offsets.indices) {
            val start = offsets[idx]
            val end = if (idx + 1 < offsets.size) offsets[idx + 1] else data.size
            Files.deleteIfExists(Paths.get(filesDir, "$idx.dtb"))
            Files.write(Paths.get(filesDir, "$idx.dtb"), Arrays.copyOfRange(data, start, end))
        }
        shellRepository.execAndCheck("cd \"$filesDir\" && rm -f \"${dtbFile.name}\"")

        // Convert all to DTS
        for (idx in offsets.indices) {
            val convertResult = shellRepository.execAdaptive(
                "cd \"$filesDir\" && \"$dtcPath\" -I dtb -O dts \"$idx.dtb\" -o \"$idx.dts\""
            )
            if (!convertResult.isSuccess) {
                throw Exception(
                    "Failed to convert split DTB index $idx:\n${renderShellFailure(convertResult.err, convertResult.out)}"
                )
            }
            File(filesDir, "$idx.dtb").delete()
        }

        return offsets.size
    }

    private suspend fun splitAndConvertDtbo(filesDir: String, dtcPath: String): Int {
        val dtboFile = File(filesDir, TargetPartition.DTBO.imageFileName)
        if (!dtboFile.exists()) {
            throw Exception("Missing dtbo image at ${dtboFile.absolutePath}")
        }

        val data = Files.readAllBytes(dtboFile.toPath())
        val entriesWithMeta = extractDtboEntries(data)

        val dtbEntries: List<ByteArray>
        val metadata: DtboMetadata

        if (entriesWithMeta.isNotEmpty()) {
            dtbEntries = entriesWithMeta.map { it.first }
            metadata = DtboMetadata(
                usesTable = true,
                pageSize = readU32Be(data, 24) ?: 4096,
                entries = entriesWithMeta.map { it.second }
            )
        } else {
            val split = extractRawConcatenatedDtbs(data)
            if (split.isEmpty()) {
                throw Exception("No DTB entries found in dtbo image")
            }
            dtbEntries = split
            metadata = DtboMetadata(
                usesTable = false,
                pageSize = 4096,
                entries = split.map { DtboEntryMeta(0, 0, intArrayOf(0, 0, 0, 0)) }
            )
        }

        dtboMetadataByDir[filesDir] = metadata
        saveDtboMetadata(filesDir, metadata)

        for ((idx, dtbBytes) in dtbEntries.withIndex()) {
            val dtbFile = Paths.get(filesDir, "$idx.dtb")
            Files.deleteIfExists(dtbFile)
            Files.write(dtbFile, dtbBytes)

            val convertResult = shellRepository.execAdaptive(
                "cd \"$filesDir\" && \"$dtcPath\" -I dtb -O dts \"$idx.dtb\" -o \"$idx.dts\""
            )
            if (!convertResult.isSuccess) {
                throw Exception(
                    "Failed to convert DTBO entry index $idx:\n${renderShellFailure(convertResult.err, convertResult.out)}"
                )
            }
            File(filesDir, "$idx.dtb").delete()
        }

        return dtbEntries.size
    }

    suspend fun repackBootImage(
        filesDir: String,
        magiskbootPath: String,
        dtcPath: String,
        dtbCount: Int,
        dtbType: DtbType?,
        partition: TargetPartition = TargetPartition.BOOT
    ) {
        // Compile DTS to DTB
        for (i in 0 until dtbCount) {
            val sourceDts = File(filesDir, "$i.dts")
            if (!sourceDts.exists()) {
                throw Exception("Missing source DTS file: ${sourceDts.absolutePath}")
            }

            var compileResult = shellRepository.execAdaptive(
                "cd \"$filesDir\" && \"$dtcPath\" -I dts -O dtb \"$i.dts\" -o \"$i.dtb\""
            )

            // Some real-world DTS trees emit warnings treated as errors.
            // Retry with -f to force output when possible.
            if (!compileResult.isSuccess) {
                compileResult = shellRepository.execAdaptive(
                    "cd \"$filesDir\" && \"$dtcPath\" -f -I dts -O dtb \"$i.dts\" -o \"$i.dtb\""
                )
            }

            // If compile still fails, try to auto-repair common malformed syntax
            // from older editor rounds, then retry.
            if (!compileResult.isSuccess && tryRepairCommonSyntax(sourceDts)) {
                compileResult = shellRepository.execAdaptive(
                    "cd \"$filesDir\" && \"$dtcPath\" -I dts -O dtb \"$i.dts\" -o \"$i.dtb\""
                )
                if (!compileResult.isSuccess) {
                    compileResult = shellRepository.execAdaptive(
                        "cd \"$filesDir\" && \"$dtcPath\" -f -I dts -O dtb \"$i.dts\" -o \"$i.dtb\""
                    )
                }
            }

            if (!compileResult.isSuccess) {
                val shellFailure = renderShellFailure(compileResult.err, compileResult.out)
                val errorLine = extractErrorLineFromDtcOutput(sourceDts.name, compileResult.err, compileResult.out)
                val contextSnippet = if (errorLine != null) {
                    buildDtsContextSnippet(sourceDts, errorLine, radius = 5)
                } else {
                    ""
                }
                val contextBlock = if (contextSnippet.isNotEmpty()) "\n\n$contextSnippet" else ""
                throw Exception(
                    "Failed to compile DTS index $i:\n$shellFailure$contextBlock"
                )
            }
        }

        if (partition == TargetPartition.DTBO || dtbType == DtbType.DTBO) {
            repackDtboImage(filesDir, dtbCount)
            shellRepository.execAndCheck("cd \"$filesDir\" && rm -f ./*.dtb")
            return
        }

        // Concatenate
        val out = if (dtbType == DtbType.KERNEL_DTB) "kernel_dtb" else "dtb"
        val cmd = StringBuilder("cd \"$filesDir\" && cat")
        for (i in 0 until dtbCount) cmd.append(" \"$i.dtb\"")
        cmd.append(" > \"$out\"")
        
        if (dtbType == DtbType.BOTH) cmd.append(" && cp dtb kernel_dtb")
        cmd.append(" && \"$magiskbootPath\" repack ${partition.imageFileName} ${partition.outputFileName}")
        
        val repackResult = shellRepository.execAdaptive(cmd.toString())
        // Cleanup split temporary dtb files created for repack.
        shellRepository.execAndCheck("cd \"$filesDir\" && rm -f ./*.dtb")
        if (!repackResult.isSuccess) {
            throw Exception("Failed to repack boot image:\n${renderShellFailure(repackResult.err, repackResult.out)}")
        }
    }

    private fun repackDtboImage(filesDir: String, dtbCount: Int) {
        val dtbChunks = (0 until dtbCount).map { idx ->
            val file = File(filesDir, "$idx.dtb")
            if (!file.exists()) {
                throw Exception("Missing compiled DTB chunk for DTBO repack: ${file.absolutePath}")
            }
            file.readBytes()
        }

        var metadata = dtboMetadataByDir[filesDir]
        if (metadata == null) {
            metadata = loadDtboMetadata(filesDir)
            if (metadata != null) {
                dtboMetadataByDir[filesDir] = metadata
            } else {
                // FALLBACK: Try to reconstruct metadata from original dtbo.img
                // This handles cases where user updated app but didn't re-import.
                val originalDtbo = File(filesDir, TargetPartition.DTBO.imageFileName)
                if (originalDtbo.exists()) {
                    try {
                        val data = originalDtbo.readBytes()
                        val entriesWithMeta = extractDtboEntries(data)
                        if (entriesWithMeta.isNotEmpty()) {
                            metadata = DtboMetadata(
                                usesTable = true,
                                pageSize = readU32Be(data, 24) ?: 4096,
                                entries = entriesWithMeta.map { it.second }
                            )
                            dtboMetadataByDir[filesDir] = metadata
                            saveDtboMetadata(filesDir, metadata!!)
                        } else {
                             // Maybe it was concatenanted?
                             val split = extractRawConcatenatedDtbs(data)
                             if (split.isNotEmpty()) {
                                 metadata = DtboMetadata(
                                     usesTable = false,
                                     pageSize = 4096,
                                     entries = split.map { DtboEntryMeta(0, 0, intArrayOf(0, 0, 0, 0)) }
                                 )
                                 dtboMetadataByDir[filesDir] = metadata
                                 saveDtboMetadata(filesDir, metadata!!)
                             }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to recover metadata from original dtbo.img", e)
                    }
                }
            }
        }
        val outputFile = File(filesDir, TargetPartition.DTBO.outputFileName)

        val bytes = if (metadata?.usesTable == true) {
            buildDtboTableImage(dtbChunks, metadata)
        } else {
            dtbChunks.fold(ByteArray(0)) { acc, item -> acc + item }
        }

        outputFile.writeBytes(bytes)
    }

    private fun buildDtboTableImage(dtbChunks: List<ByteArray>, metadata: DtboMetadata): ByteArray {
        val entryCount = dtbChunks.size
        val pageSize = metadata.pageSize.coerceAtLeast(4)
        val entriesOffset = DTBO_HEADER_SIZE
        val entriesSize = entryCount * DTBO_ENTRY_SIZE

        var cursor = align(entriesOffset + entriesSize, pageSize)
        val offsets = IntArray(entryCount)
        for (i in 0 until entryCount) {
            offsets[i] = cursor
            cursor = align(cursor + dtbChunks[i].size, pageSize)
        }

        val totalSize = cursor
        val out = ByteArray(totalSize)

        writeU32Be(out, 0, DTBO_TABLE_MAGIC)
        writeU32Be(out, 4, totalSize)
        writeU32Be(out, 8, DTBO_HEADER_SIZE)
        writeU32Be(out, 12, DTBO_ENTRY_SIZE)
        writeU32Be(out, 16, entryCount)
        writeU32Be(out, 20, entriesOffset)
        writeU32Be(out, 24, pageSize)
        writeU32Be(out, 28, 0)

        for (i in 0 until entryCount) {
            val entry = metadata.entries.getOrNull(i) ?: DtboEntryMeta(0, 0, intArrayOf(0, 0, 0, 0))
            val base = entriesOffset + i * DTBO_ENTRY_SIZE
            writeU32Be(out, base, dtbChunks[i].size)
            writeU32Be(out, base + 4, offsets[i])
            writeU32Be(out, base + 8, entry.id)
            writeU32Be(out, base + 12, entry.rev)
            writeU32Be(out, base + 16, entry.custom.getOrElse(0) { 0 })
            writeU32Be(out, base + 20, entry.custom.getOrElse(1) { 0 })
            writeU32Be(out, base + 24, entry.custom.getOrElse(2) { 0 })
            writeU32Be(out, base + 28, entry.custom.getOrElse(3) { 0 })
        }

        for (i in 0 until entryCount) {
            val chunk = dtbChunks[i]
            System.arraycopy(chunk, 0, out, offsets[i], chunk.size)
        }

        return out
    }

    private fun extractDtboEntries(data: ByteArray): List<Pair<ByteArray, DtboEntryMeta>> {
        if (data.size < DTBO_HEADER_SIZE) return emptyList()
        val magic = readU32Be(data, 0) ?: return emptyList()
        if (magic != DTBO_TABLE_MAGIC) return emptyList()

        val entrySize = readU32Be(data, 12) ?: return emptyList()
        val entryCount = readU32Be(data, 16) ?: return emptyList()
        val entriesOffset = readU32Be(data, 20) ?: return emptyList()

        if (entrySize < DTBO_ENTRY_SIZE || entryCount <= 0 || entryCount > 4096) return emptyList()
        val tableEnd = entriesOffset.toLong() + entrySize.toLong() * entryCount.toLong()
        if (entriesOffset < 0 || tableEnd > data.size.toLong()) return emptyList()

        val entries = mutableListOf<Pair<ByteArray, DtboEntryMeta>>()
        for (i in 0 until entryCount) {
            val entryOffset = entriesOffset + (i * entrySize)
            if (entryOffset < 0 || entryOffset + 8 > data.size) continue

            val dtSize = readU32Be(data, entryOffset) ?: continue
            val dtOffset = readU32Be(data, entryOffset + 4) ?: continue
            if (dtSize < MIN_DTB_SIZE || dtOffset < 0 || dtOffset + dtSize > data.size) continue
            if (!isDtbMagicAt(data, dtOffset)) continue

            val id = readU32Be(data, entryOffset + 8) ?: 0
            val rev = readU32Be(data, entryOffset + 12) ?: 0
            val custom = intArrayOf(
                readU32Be(data, entryOffset + 16) ?: 0,
                readU32Be(data, entryOffset + 20) ?: 0,
                readU32Be(data, entryOffset + 24) ?: 0,
                readU32Be(data, entryOffset + 28) ?: 0
            )

            val dtBytes = data.copyOfRange(dtOffset, dtOffset + dtSize)
            entries.add(dtBytes to DtboEntryMeta(id, rev, custom))
        }

        return entries
    }

    private fun extractRawConcatenatedDtbs(data: ByteArray): List<ByteArray> {
        val segments = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i + DTB_HEADER_SIZE < data.size) {
            if (isDtbMagicAt(data, i)) {
                val size = readU32Be(data, i + 4) ?: -1
                if (size >= MIN_DTB_SIZE && i + size <= data.size) {
                    segments.add(i to size)
                    i += maxOf(size, 1)
                    continue
                }
            }
            i++
        }

        return segments.mapNotNull { (start, size) ->
            val end = start + size
            if (end <= start || end > data.size) null else Arrays.copyOfRange(data, start, end)
        }
    }

    private fun isDtbMagicAt(bytes: ByteArray, offset: Int): Boolean {
        if (offset < 0 || offset + 4 > bytes.size) return false
        return bytes[offset] == DTB_MAGIC[0] &&
            bytes[offset + 1] == DTB_MAGIC[1] &&
            bytes[offset + 2] == DTB_MAGIC[2] &&
            bytes[offset + 3] == DTB_MAGIC[3]
    }

    private fun readU32Be(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        return ((bytes[offset].toInt() and BYTE_MASK) shl 24) or
            ((bytes[offset + 1].toInt() and BYTE_MASK) shl 16) or
            ((bytes[offset + 2].toInt() and BYTE_MASK) shl 8) or
            (bytes[offset + 3].toInt() and BYTE_MASK)
    }

    private fun writeU32Be(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 24) and BYTE_MASK).toByte()
        bytes[offset + 1] = ((value ushr 16) and BYTE_MASK).toByte()
        bytes[offset + 2] = ((value ushr 8) and BYTE_MASK).toByte()
        bytes[offset + 3] = (value and BYTE_MASK).toByte()
    }

    private fun saveDtboMetadata(filesDir: String, metadata: DtboMetadata) {
        try {
            val json = org.json.JSONObject()
            json.put("usesTable", metadata.usesTable)
            json.put("pageSize", metadata.pageSize)
            
            val entriesArray = org.json.JSONArray()
            metadata.entries.forEach { entry ->
                val entryObj = org.json.JSONObject()
                entryObj.put("id", entry.id)
                entryObj.put("rev", entry.rev)
                val customArray = org.json.JSONArray()
                entry.custom.forEach { customArray.put(it) }
                entryObj.put("custom", customArray)
                entriesArray.put(entryObj)
            }
            json.put("entries", entriesArray)
            
            File(filesDir, "dtbo_metadata.json").writeText(json.toString())
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save DTBO metadata", e)
        }
    }

    private fun loadDtboMetadata(filesDir: String): DtboMetadata? {
        val file = File(filesDir, "dtbo_metadata.json")
        if (!file.exists()) return null
        
        return try {
            val json = org.json.JSONObject(file.readText())
            val usesTable = json.optBoolean("usesTable", true)
            val pageSize = json.optInt("pageSize", 2048)
            val entriesArray = json.optJSONArray("entries")
            val entries = mutableListOf<DtboEntryMeta>()
            
            if (entriesArray != null) {
                for (i in 0 until entriesArray.length()) {
                    val entryObj = entriesArray.getJSONObject(i)
                    val id = entryObj.optInt("id")
                    val rev = entryObj.optInt("rev")
                    val customArray = entryObj.optJSONArray("custom")
                    val custom = IntArray(4)
                    if (customArray != null) {
                        for (j in 0 until 4) {
                            if (j < customArray.length()) custom[j] = customArray.getInt(j)
                        }
                    }
                    entries.add(DtboEntryMeta(id, rev, custom))
                }
            }
            
            DtboMetadata(usesTable, pageSize, entries)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load DTBO metadata", e)
            null
        }
    }

    private fun align(value: Int, alignment: Int): Int {
        val align = alignment.coerceAtLeast(1)
        val rem = value % align
        return if (rem == 0) value else value + (align - rem)
    }
}
