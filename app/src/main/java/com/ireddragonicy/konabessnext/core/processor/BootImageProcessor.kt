package com.ireddragonicy.konabessnext.core.processor

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
        private val DTB_MAGIC = byteArrayOf(0xD0.toByte(), 0x0D.toByte(), 0xFE.toByte(), 0xED.toByte())
        private const val DTB_HEADER_SIZE = 8
        private const val BYTE_MASK = 0xFF
    }

    suspend fun unpackBootImage(filesDir: String): DtbType {
        shellRepository.exec("cd $filesDir && ./magiskboot unpack boot.img && chmod -R 777 $filesDir")
        
        val hasKernelDtb = File(filesDir, "kernel_dtb").exists()
        val hasDtb = File(filesDir, "dtb").exists()
        
        return when {
            hasKernelDtb && hasDtb -> DtbType.BOTH
            hasKernelDtb -> DtbType.KERNEL_DTB
            hasDtb -> DtbType.DTB
            else -> throw Exception("No DTB found in boot image")
        }
    }

    suspend fun splitAndConvertDtbs(filesDir: String, dtbType: DtbType): Int {
        val dtbFile = if (dtbType == DtbType.DTB || dtbType == DtbType.BOTH) File(filesDir, "dtb") else File(filesDir, "kernel_dtb")
        if (dtbType == DtbType.BOTH) File(filesDir, "kernel_dtb").delete()
        
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
            Files.write(Paths.get(filesDir, "$idx.dtb"), Arrays.copyOfRange(data, start, end))
        }
        dtbFile.delete()

        // Convert all to DTS
        for (idx in offsets.indices) {
            shellRepository.exec("cd $filesDir && ./dtc -I dtb -O dts $idx.dtb -o $idx.dts && rm -f $idx.dtb")
        }

        return offsets.size
    }

    suspend fun repackBootImage(filesDir: String, dtbCount: Int, dtbType: DtbType?) {
        // Compile DTS to DTB
        for (i in 0 until dtbCount) {
            shellRepository.exec("cd $filesDir && ./dtc -I dts -O dtb $i.dts -o $i.dtb")
        }

        // Concatenate
        val out = if (dtbType == DtbType.KERNEL_DTB) "kernel_dtb" else "dtb"
        val cmd = StringBuilder("cd $filesDir && cat")
        for (i in 0 until dtbCount) cmd.append(" $i.dtb")
        cmd.append(" > $out")
        
        if (dtbType == DtbType.BOTH) cmd.append(" && cp dtb kernel_dtb")
        cmd.append(" && ./magiskboot repack boot.img boot_new.img")
        
        shellRepository.exec(cmd.toString())
    }
}
