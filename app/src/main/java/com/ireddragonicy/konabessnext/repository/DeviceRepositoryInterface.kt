package com.ireddragonicy.konabessnext.repository

import java.io.File
import com.ireddragonicy.konabessnext.core.model.DomainResult

interface DeviceRepositoryInterface {
    val dtsPath: String?
    
    fun tryRestoreLastChipset(): Boolean
    fun getDtsFile(): File
    suspend fun getRunTimeGpuFrequencies(): DomainResult<List<Long>>
}
