package com.ireddragonicy.konabessnext.repository

import java.io.File
import com.ireddragonicy.konabessnext.core.model.DomainResult
import com.ireddragonicy.konabessnext.model.Dtb
import com.ireddragonicy.konabessnext.model.TargetPartition

interface DeviceRepositoryInterface {
    val dtsPath: String?
    val selectedPartition: TargetPartition
    val availablePartitions: List<TargetPartition>
    
    fun tryRestoreLastChipset(): Boolean
    fun getDtsFile(): File
    fun setSelectedPartition(partition: TargetPartition)
    fun getDtbs(partition: TargetPartition): List<Dtb>
    suspend fun getRunTimeGpuFrequencies(): DomainResult<List<Long>>
}
