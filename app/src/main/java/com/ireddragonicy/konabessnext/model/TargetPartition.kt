package com.ireddragonicy.konabessnext.model

enum class TargetPartition(
    val partitionName: String,
    val imageFileName: String,
    val outputFileName: String,
    val slotAware: Boolean
) {
    VENDOR_BOOT(
        partitionName = "vendor_boot",
        imageFileName = "boot.img",
        outputFileName = "boot_new.img",
        slotAware = true
    ),
    BOOT(
        partitionName = "boot",
        imageFileName = "boot.img",
        outputFileName = "boot_new.img",
        slotAware = true
    ),
    DTBO(
        partitionName = "dtbo",
        imageFileName = "dtbo.img",
        outputFileName = "dtbo_new.img",
        slotAware = true
    );

    val storageKey: String
        get() = partitionName

    companion object {
        fun fromName(name: String?): TargetPartition? {
            if (name.isNullOrBlank()) return null
            return entries.firstOrNull { it.partitionName.equals(name, ignoreCase = true) }
        }
    }
}
