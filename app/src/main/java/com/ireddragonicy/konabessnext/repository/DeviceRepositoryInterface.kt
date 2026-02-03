package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.model.Dtb
import java.io.File

interface DeviceRepositoryInterface {
    val dtsPath: String?
    
    fun tryRestoreLastChipset(): Boolean
    fun getDtsFile(): File
    // Add other methods used by GpuRepository if checking confirms more are needed
    // Checked GpuRepository usages: dtsPath, tryRestoreLastChipset, getDtsFile (implicit)
    // Wait, let's double check GpuRepository usages.
}
