package com.ireddragonicy.konabessnext.model

import com.ireddragonicy.konabessnext.model.ChipDefinition

data class Dtb(
    @JvmField val id: Int,
    @JvmField val type: ChipDefinition,
    @JvmField val partition: TargetPartition = TargetPartition.VENDOR_BOOT
)
