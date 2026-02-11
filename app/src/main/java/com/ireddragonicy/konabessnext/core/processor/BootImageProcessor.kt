package com.ireddragonicy.konabessnext.core.processor

import android.util.Log
import com.ireddragonicy.konabessnext.model.DtbType
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
        private const val DTB_HEADER_SIZE = 8
        private const val BYTE_MASK = 0xFF
    }

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

    suspend fun unpackBootImage(filesDir: String, magiskbootPath: String): DtbType {
        val binaryFile = File(magiskbootPath)
        if (!binaryFile.exists()) {
            throw Exception("magiskboot binary not found at $magiskbootPath")
        }

        val result = shellRepository.execAdaptive("cd $filesDir && $magiskbootPath unpack boot.img")
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

    suspend fun repackBootImage(filesDir: String, magiskbootPath: String, dtcPath: String, dtbCount: Int, dtbType: DtbType?) {
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

        // Concatenate
        val out = if (dtbType == DtbType.KERNEL_DTB) "kernel_dtb" else "dtb"
        val cmd = StringBuilder("cd \"$filesDir\" && cat")
        for (i in 0 until dtbCount) cmd.append(" \"$i.dtb\"")
        cmd.append(" > \"$out\"")
        
        if (dtbType == DtbType.BOTH) cmd.append(" && cp dtb kernel_dtb")
        cmd.append(" && \"$magiskbootPath\" repack boot.img boot_new.img")
        
        val repackResult = shellRepository.execAdaptive(cmd.toString())
        // Cleanup split temporary dtb files created for repack.
        shellRepository.execAndCheck("cd \"$filesDir\" && rm -f ./*.dtb")
        if (!repackResult.isSuccess) {
            throw Exception("Failed to repack boot image:\n${renderShellFailure(repackResult.err, repackResult.out)}")
        }
    }
}
