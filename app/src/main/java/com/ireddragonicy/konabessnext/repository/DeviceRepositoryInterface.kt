package com.ireddragonicy.konabessnext.repository

import java.io.File

interface DeviceRepositoryInterface {
    val dtsPath: String?
    
    fun tryRestoreLastChipset(): Boolean
    fun getDtsFile(): File
    suspend fun getRunTimeGpuFrequencies(): List<Long>
}
