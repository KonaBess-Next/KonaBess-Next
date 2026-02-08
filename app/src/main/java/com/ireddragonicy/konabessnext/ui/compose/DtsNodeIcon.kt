package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps DTS node names to Material Design icons for a modern IDE look.
 * Uses 3-tier resolution: exact HashMap → prefix scan → keyword fallback.
 *
 * Comprehensive mapping based on full Qualcomm DTS analysis:
 * - Processor cores (CPU, GPU, DSP, NPU)
 * - Hypervisor / VM (Gunyah, TrustZone, OEM VM, TUI VM)
 * - Bus protocols (I2C, SPI, UART, USB, PCIe, SPMI, SLIM)
 * - Power management (regulators, PMIC, BCL, DCVS, OPP)
 * - Memory (DDR, SRAM, cache, CMA, pools, reserved regions)
 * - Peripherals (camera, display, audio, LED, sensors, NFC)
 * - Debug/Trace (QDSS, CTI, ETM, ETF, ETR, TPDA, TPDM, DCC)
 * - Interconnect (NOC, QTB, SMMU, IOMMU, BCM)
 * - Modem/RF (MPSS, LTE, NR, mmWave, SDR, IPA)
 * - Thermal (TSENS, cooling, thermal zones, trips)
 * - Security (SCM, QSEE, TME, crypto, RNG, hypervisor)
 * - Clocks (GCC, GPU_CC, disp_cc, cam_cc)
 * - System (timer, RTC, watchdog, restart, firmware, PSCI)
 */
object DtsNodeIcon {

    // ─── Exact-match map (O(1) lookup) ─────────────────────────────
    private val exactMatch: Map<String, ImageVector> = hashMapOf(
        // Top-level system nodes
        "soc" to Icons.Rounded.DeveloperBoard,
        "cpus" to Icons.Rounded.Memory,
        "memory" to Icons.Rounded.SdStorage,
        "aliases" to Icons.AutoMirrored.Rounded.Label,
        "chosen" to Icons.Rounded.Settings,
        "firmware" to Icons.Rounded.SystemUpdate,
        "reserved-memory" to Icons.Rounded.Lock,
        "idle-states" to Icons.Rounded.PowerSettingsNew,
        "cpu-map" to Icons.Rounded.AccountTree,
        "psci" to Icons.Rounded.PowerSettingsNew,
        "timer" to Icons.Rounded.Timer,
        "hypervisor" to Icons.Rounded.Security,

        // DDR / Memory
        "ddr" to Icons.Rounded.SdStorage,
        "ddr-regions" to Icons.Rounded.SdStorage,
        "ddr-freq-table" to Icons.Rounded.Speed,
        "ddrqos" to Icons.Rounded.Speed,
        "ddrqos-freq-table" to Icons.Rounded.Speed,
        "linux,cma" to Icons.Rounded.SdStorage,

        // GPU
        "zap-shader" to Icons.Rounded.Bolt,
        "gpu" to Icons.Rounded.Videocam,

        // Clusters
        "cluster0" to Icons.Rounded.Hub,
        "cluster1" to Icons.Rounded.Hub,
        "cluster2" to Icons.Rounded.Hub,
        "cluster3" to Icons.Rounded.Hub,
        "cluster-pd" to Icons.Rounded.PowerSettingsNew,
        "cluster-device" to Icons.Rounded.Hub,
        "cluster-d4" to Icons.Rounded.Hub,
        "cluster-e3" to Icons.Rounded.Hub,

        // CPU performance groups
        "silver" to Icons.Rounded.Memory,
        "gold" to Icons.Rounded.Memory,
        "gold-compute" to Icons.Rounded.Speed,
        "prime" to Icons.Rounded.Memory,
        "prime-compute" to Icons.Rounded.Speed,
        "prime-latfloor" to Icons.Rounded.Speed,

        // Audio / Sound
        "sound" to Icons.AutoMirrored.Rounded.VolumeUp,
        "audio-pkt" to Icons.AutoMirrored.Rounded.VolumeUp,
        "audio-qmi" to Icons.AutoMirrored.Rounded.VolumeUp,
        "spf_core" to Icons.AutoMirrored.Rounded.VolumeUp,
        "spf_core_platform" to Icons.AutoMirrored.Rounded.VolumeUp,
        "lpass-cdc" to Icons.AutoMirrored.Rounded.VolumeUp,
        "lpass-cdc-clk-rsc-mngr" to Icons.AutoMirrored.Rounded.VolumeUp,
        "rx_swr_master" to Icons.AutoMirrored.Rounded.VolumeUp,
        "va_swr_master" to Icons.Rounded.Mic,
        "wsa_swr_master" to Icons.AutoMirrored.Rounded.VolumeUp,
        "q6prm" to Icons.AutoMirrored.Rounded.VolumeUp,

        // Modem / RF
        "modem" to Icons.Rounded.SignalCellularAlt,
        "modem_bcl" to Icons.Rounded.SignalCellularAlt,
        "modem_diag" to Icons.Rounded.SignalCellularAlt,
        "modem_vdd" to Icons.Rounded.SignalCellularAlt,
        "sdr0" to Icons.Rounded.SignalCellularAlt,
        "sdr0_pa" to Icons.Rounded.SignalCellularAlt,

        // Power / Restart / Reboot
        "reboot_reason" to Icons.Rounded.RestartAlt,
        "dma_dev" to Icons.Rounded.SwapHoriz,
        "hwlock" to Icons.Rounded.Lock,
        "walt" to Icons.Rounded.Speed,
        "config" to Icons.Rounded.Settings,
        "clocks" to Icons.Rounded.Schedule,
        "data" to Icons.Rounded.Storage,
        "mux" to Icons.Rounded.SwapHoriz,
        "fcm" to Icons.Rounded.Hub,
        "gladiator" to Icons.Rounded.DeveloperBoard,

        // IPC / shared
        "ipclite" to Icons.Rounded.Share,
        "glink-edge" to Icons.Rounded.Share,
        "smem_mailbox" to Icons.Rounded.Share,

        // Watchdog / Debug
        "iommu_test_device" to Icons.Rounded.BugReport,
        "gic-interrupt-router" to Icons.Rounded.Router,
        "dynamic_mem_dump" to Icons.Rounded.BugReport,
        "static_dump" to Icons.Rounded.BugReport,
        "mem_dump" to Icons.Rounded.BugReport,
        "mem_dump_region" to Icons.Rounded.BugReport,
        "dmesg-dump" to Icons.Rounded.BugReport,
        "boot_stats@6b0" to Icons.Rounded.BugReport,

        // Misc system
        "thermal-zones" to Icons.Rounded.Thermostat,
        "pmic" to Icons.Rounded.Battery5Bar,
        "rpmh" to Icons.Rounded.PowerSettingsNew,
        "sp" to Icons.Rounded.Security,
        "spr" to Icons.Rounded.Info,
        "endpoint" to Icons.Rounded.Cable,
        "connector" to Icons.Rounded.Cable,
        "shared_ice" to Icons.Rounded.Share,
        "sleep_clk" to Icons.Rounded.Schedule,
        "alt_sleep_clk" to Icons.Rounded.Schedule,
        "xo_board" to Icons.Rounded.Schedule,
        "apss" to Icons.Rounded.Memory,
        "apps" to Icons.Rounded.Memory,
        "nsp" to Icons.Rounded.Sensors,
        "cam" to Icons.Rounded.CameraAlt,
        "video" to Icons.Rounded.Videocam,

        // Keys / Buttons
        "gpio_key" to Icons.Rounded.Keyboard,
        "pwrkey" to Icons.Rounded.PowerSettingsNew,
        "resin" to Icons.Rounded.PowerSettingsNew,
        "vol_up" to Icons.AutoMirrored.Rounded.VolumeUp,

        // LED
        "led-controller" to Icons.Rounded.LightMode,
    )

    // ─── Prefix patterns (first match wins) ────────────────────────
    private data class PrefixRule(val prefix: String, val icon: ImageVector)

    private val prefixRules: List<PrefixRule> = listOf(
        // ── CPU / Core ──
        PrefixRule("cpu@", Icons.Rounded.Memory),
        PrefixRule("cpu-pd", Icons.Rounded.PowerSettingsNew),
        PrefixRule("cpu-cluster", Icons.Rounded.Hub),
        PrefixRule("cpu-pmu", Icons.Rounded.Speed),
        PrefixRule("cpuss", Icons.Rounded.Memory),
        PrefixRule("core", Icons.Rounded.Memory),
        PrefixRule("pause-cpu", Icons.Rounded.PauseCircle),

        // ── Cache / LLC ──
        PrefixRule("l1_", Icons.Rounded.Layers),
        PrefixRule("l1d", Icons.Rounded.Layers),
        PrefixRule("l2_", Icons.Rounded.Layers),
        PrefixRule("l2-cache", Icons.Rounded.Layers),
        PrefixRule("l2d", Icons.Rounded.Layers),
        PrefixRule("l2t", Icons.Rounded.Layers),
        PrefixRule("l2victim", Icons.Rounded.Layers),
        PrefixRule("l3", Icons.Rounded.Layers),
        PrefixRule("l3-cache", Icons.Rounded.Layers),
        PrefixRule("cache-controller", Icons.Rounded.Layers),
        PrefixRule("llcc", Icons.Rounded.Layers),

        // ── GPU / Graphics ──
        PrefixRule("kgsl", Icons.Rounded.Videocam),
        PrefixRule("gpu_", Icons.Rounded.Videocam),
        PrefixRule("gpu-", Icons.Rounded.Videocam),
        PrefixRule("gpu0", Icons.Rounded.Videocam),
        PrefixRule("gpu1", Icons.Rounded.Videocam),
        PrefixRule("gpu2", Icons.Rounded.Videocam),
        PrefixRule("gpu3", Icons.Rounded.Videocam),
        PrefixRule("gpu4", Icons.Rounded.Videocam),
        PrefixRule("gpu5", Icons.Rounded.Videocam),
        PrefixRule("gfx3d", Icons.Rounded.Videocam),
        PrefixRule("adreno", Icons.Rounded.Videocam),
        PrefixRule("scandump_gpu", Icons.Rounded.Videocam),
        PrefixRule("mdp", Icons.Rounded.Tv),

        // ── Hypervisor / VM / Gunyah ──
        PrefixRule("hypervisor", Icons.Rounded.Security),
        PrefixRule("hyp_", Icons.Rounded.Security),
        PrefixRule("gunyah", Icons.Rounded.Security),
        PrefixRule("gh-", Icons.Rounded.Security),
        PrefixRule("oem_vm", Icons.Rounded.Security),
        PrefixRule("tuivm", Icons.Rounded.Security),
        PrefixRule("trust_ui_vm", Icons.Rounded.Security),
        PrefixRule("trust_ui", Icons.Rounded.Security),
        PrefixRule("pvm_fw", Icons.Rounded.Security),

        // ── I2C Bus ──
        PrefixRule("i2c@", Icons.Rounded.Cable),
        PrefixRule("i2c_", Icons.Rounded.Cable),
        PrefixRule("i2s", Icons.AutoMirrored.Rounded.VolumeUp),

        // ── SPI Bus ──
        PrefixRule("spi@", Icons.Rounded.Cable),
        PrefixRule("spi_", Icons.Rounded.Cable),

        // ── UART ──
        PrefixRule("uart", Icons.Rounded.Cable),
        PrefixRule("qup_uart", Icons.Rounded.Cable),
        PrefixRule("qupv3_se", Icons.Rounded.Cable),

        // ── USB ──
        PrefixRule("usb", Icons.Rounded.Usb),
        PrefixRule("dwc3", Icons.Rounded.Usb),
        PrefixRule("ssphy", Icons.Rounded.Usb),
        PrefixRule("hsphy", Icons.Rounded.Usb),
        PrefixRule("ssusb", Icons.Rounded.Usb),

        // ── PCIe ──
        PrefixRule("pcie", Icons.Rounded.Cable),

        // ── Storage ──
        PrefixRule("ufshc", Icons.Rounded.Storage),
        PrefixRule("ufsphy", Icons.Rounded.Storage),
        PrefixRule("sdhci", Icons.Rounded.SdCard),
        PrefixRule("sdhc", Icons.Rounded.SdCard),
        PrefixRule("mmc", Icons.Rounded.SdCard),
        PrefixRule("sdc2", Icons.Rounded.SdCard),

        // ── SPMI Bus ──
        PrefixRule("qcom,spmi", Icons.Rounded.Cable),

        // ── SLIM Bus ──
        PrefixRule("slim@", Icons.Rounded.Cable),

        // ── Camera ──
        PrefixRule("camera", Icons.Rounded.CameraAlt),
        PrefixRule("cam_sensor", Icons.Rounded.CameraAlt),
        PrefixRule("cam_smmu", Icons.Rounded.CameraAlt),
        PrefixRule("cci_i2c", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,cam", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,cci", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,csid", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,csiphy", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,ife", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,ife-lite", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,icp", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,ipe", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,jpeg", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,ofe", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,tpg", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,rt-cdm", Icons.Rounded.CameraAlt),
        PrefixRule("qcom,flash", Icons.Rounded.FlashOn),
        PrefixRule("qcom,torch", Icons.Rounded.FlashOn),
        PrefixRule("qcom,led_switch", Icons.Rounded.FlashOn),
        PrefixRule("msm_cam_smmu", Icons.Rounded.CameraAlt),
        PrefixRule("cvp", Icons.Rounded.Videocam),

        // ── Display / Panel / DSI / DP ──
        PrefixRule("display", Icons.Rounded.Tv),
        PrefixRule("panel", Icons.Rounded.Tv),
        PrefixRule("dsi", Icons.Rounded.Tv),
        PrefixRule("qcom,mdss", Icons.Rounded.Tv),
        PrefixRule("qcom,sde", Icons.Rounded.Tv),
        PrefixRule("qcom,dp_", Icons.Rounded.Tv),
        PrefixRule("qcom,hdcp", Icons.Rounded.Tv),
        PrefixRule("qcom,msm-ext-disp", Icons.Rounded.Tv),
        PrefixRule("non_secure_display", Icons.Rounded.Tv),

        // ── Video encoder/decoder ──
        PrefixRule("video", Icons.Rounded.Videocam),
        PrefixRule("qcom,vidc", Icons.Rounded.Videocam),

        // ── Audio / Sound / Codec ──
        PrefixRule("lpass", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("lpi_pinctrl", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("rx-macro", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("tx-macro", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("wsa-macro", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("va-macro", Icons.Rounded.Mic),
        PrefixRule("wcd", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("codec", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("spkr_", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("audio", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("bt_swr", Icons.Rounded.Bluetooth),
        PrefixRule("qcom,msm-audio", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("qcom,msm-stub-codec", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("vote_lpass", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("usb_audio", Icons.AutoMirrored.Rounded.VolumeUp),

        // ── LED ──
        PrefixRule("led@", Icons.Rounded.LightMode),
        PrefixRule("led-", Icons.Rounded.LightMode),

        // ── Keys / Buttons ──
        PrefixRule("key_vol", Icons.AutoMirrored.Rounded.VolumeUp),
        PrefixRule("pwrkey", Icons.Rounded.PowerSettingsNew),

        // ── Remote processors (DSP, modem, WPSS) ──
        PrefixRule("remoteproc", Icons.Rounded.DeveloperBoard),
        PrefixRule("adsp", Icons.Rounded.Hearing),
        PrefixRule("cdsp", Icons.Rounded.Sensors),
        PrefixRule("wpss", Icons.Rounded.Wifi),
        PrefixRule("wlan", Icons.Rounded.Wifi),
        PrefixRule("nsp_", Icons.Rounded.Sensors),
        PrefixRule("nsph", Icons.Rounded.Sensors),

        // ── Modem / RF / Cellular ──
        PrefixRule("modem", Icons.Rounded.SignalCellularAlt),
        PrefixRule("mdmss", Icons.Rounded.SignalCellularAlt),
        PrefixRule("mpss_", Icons.Rounded.SignalCellularAlt),
        PrefixRule("mmw", Icons.Rounded.SignalCellularAlt),
        PrefixRule("lte_", Icons.Rounded.SignalCellularAlt),
        PrefixRule("nr_", Icons.Rounded.SignalCellularAlt),
        PrefixRule("nr_scg", Icons.Rounded.SignalCellularAlt),
        PrefixRule("lbat_", Icons.Rounded.SignalCellularAlt),
        PrefixRule("pa_lte", Icons.Rounded.SignalCellularAlt),
        PrefixRule("pa_nr", Icons.Rounded.SignalCellularAlt),
        PrefixRule("qcom,modem", Icons.Rounded.SignalCellularAlt),
        PrefixRule("qcom,rmnet", Icons.Rounded.SignalCellularAlt),

        // ── IPA (IP Accelerator / Network) ──
        PrefixRule("ipa_", Icons.Rounded.Router),
        PrefixRule("qcom,ipa", Icons.Rounded.Router),
        PrefixRule("qcom,rmtfs", Icons.Rounded.SdStorage),

        // ── Regulators / PMIC / Power ──
        PrefixRule("rpmh-regulator", Icons.Rounded.ElectricalServices),
        PrefixRule("vreg-", Icons.Rounded.ElectricalServices),
        PrefixRule("regulator", Icons.Rounded.ElectricalServices),
        PrefixRule("pm8010", Icons.Rounded.Battery5Bar),
        PrefixRule("pm8550", Icons.Rounded.Battery5Bar),
        PrefixRule("pmg", Icons.Rounded.Battery5Bar),
        PrefixRule("pmk", Icons.Rounded.Battery5Bar),
        PrefixRule("pmr", Icons.Rounded.Battery5Bar),
        PrefixRule("pmxr", Icons.Rounded.Battery5Bar),
        PrefixRule("pmic", Icons.Rounded.Battery5Bar),
        PrefixRule("qcom,pmic", Icons.Rounded.Battery5Bar),
        PrefixRule("qcom,pm", Icons.Rounded.Battery5Bar),
        PrefixRule("pon_", Icons.Rounded.PowerSettingsNew),
        PrefixRule("bcl", Icons.Rounded.ElectricalServices),
        PrefixRule("power-controller", Icons.Rounded.PowerSettingsNew),
        PrefixRule("cpuss_reg", Icons.Rounded.ElectricalServices),

        // ── Clocks ──
        PrefixRule("clock-controller", Icons.Rounded.Schedule),
        PrefixRule("gcc_", Icons.Rounded.Schedule),
        PrefixRule("gpu_cc", Icons.Rounded.Schedule),
        PrefixRule("clk", Icons.Rounded.Schedule),

        // ── DCVS / OPP / Frequency ──
        PrefixRule("opp-", Icons.Rounded.Speed),
        PrefixRule("qcom,dcvs", Icons.Rounded.Speed),
        PrefixRule("qcom,cpufreq", Icons.Rounded.Speed),
        PrefixRule("qcom,memlat", Icons.Rounded.Speed),
        PrefixRule("qcom,bwmon", Icons.Rounded.Speed),
        PrefixRule("qcom,devfreq", Icons.Rounded.Speed),
        PrefixRule("qcom,cycle", Icons.Rounded.Speed),
        PrefixRule("qcom,dynpf", Icons.Rounded.Speed),
        PrefixRule("qcom,llcc-l3", Icons.Rounded.Speed),
        PrefixRule("osm_reg", Icons.Rounded.Speed),
        PrefixRule("llcc-freq", Icons.Rounded.Speed),

        // ── SMMU / IOMMU ──
        PrefixRule("apps-smmu", Icons.Rounded.Shield),
        PrefixRule("kgsl-smmu", Icons.Rounded.Shield),
        PrefixRule("smmu_sde", Icons.Rounded.Shield),
        PrefixRule("smmu", Icons.Rounded.Shield),
        PrefixRule("iommu", Icons.Rounded.Shield),
        PrefixRule("iova-mem", Icons.Rounded.Shield),
        PrefixRule("scandump_smmu", Icons.Rounded.Shield),
        PrefixRule("qti,smmu", Icons.Rounded.Shield),

        // ── Interconnect / NOC / QTB ──
        PrefixRule("interconnect", Icons.Rounded.Hub),
        PrefixRule("anoc_", Icons.Rounded.Hub),
        PrefixRule("cam_hf_qtb", Icons.Rounded.Hub),
        PrefixRule("sf_qtb", Icons.Rounded.Hub),
        PrefixRule("pcie_qtb", Icons.Rounded.Hub),
        PrefixRule("nsp_qtb", Icons.Rounded.Hub),
        PrefixRule("gpu_qtb", Icons.Rounded.Hub),
        PrefixRule("lpass_qtb", Icons.Rounded.Hub),
        PrefixRule("mdp_hf_qtb", Icons.Rounded.Hub),

        // ── Interrupts ──
        PrefixRule("interrupt-controller", Icons.Rounded.Bolt),
        PrefixRule("msi-controller", Icons.Rounded.Bolt),
        PrefixRule("pdc@", Icons.Rounded.Bolt),

        // ── Debug / Trace / QDSS ──
        PrefixRule("cti@", Icons.Rounded.BugReport),
        PrefixRule("ete", Icons.Rounded.BugReport),
        PrefixRule("etf", Icons.Rounded.BugReport),
        PrefixRule("etr", Icons.Rounded.BugReport),
        PrefixRule("tpda@", Icons.Rounded.BugReport),
        PrefixRule("tpdm@", Icons.Rounded.BugReport),
        PrefixRule("tgu@", Icons.Rounded.BugReport),
        PrefixRule("tn@", Icons.Rounded.BugReport),
        PrefixRule("TN@", Icons.Rounded.BugReport),
        PrefixRule("stm@", Icons.Rounded.BugReport),
        PrefixRule("funnel", Icons.Rounded.BugReport),
        PrefixRule("replicator", Icons.Rounded.BugReport),
        PrefixRule("traceNoc", Icons.Rounded.BugReport),
        PrefixRule("qdss", Icons.Rounded.BugReport),
        PrefixRule("dcc_v2", Icons.Rounded.BugReport),
        PrefixRule("tmc-", Icons.Rounded.BugReport),
        PrefixRule("tmc@", Icons.Rounded.BugReport),
        PrefixRule("lpass-stm", Icons.Rounded.BugReport),
        PrefixRule("audio_etm", Icons.Rounded.BugReport),
        PrefixRule("modem-etm", Icons.Rounded.BugReport),
        PrefixRule("modem2-etm", Icons.Rounded.BugReport),
        PrefixRule("turing-etm", Icons.Rounded.BugReport),
        PrefixRule("wpss_etm", Icons.Rounded.BugReport),

        // ── Thermal / Cooling / Sensors ──
        PrefixRule("thermal", Icons.Rounded.Thermostat),
        PrefixRule("cooling", Icons.Rounded.AcUnit),
        PrefixRule("tsens", Icons.Rounded.Thermostat),
        PrefixRule("tzone", Icons.Rounded.Thermostat),
        PrefixRule("trip-", Icons.Rounded.Thermostat),
        PrefixRule("trip0", Icons.Rounded.Thermostat),
        PrefixRule("trip1", Icons.Rounded.Thermostat),
        PrefixRule("trip2", Icons.Rounded.Thermostat),
        PrefixRule("trips", Icons.Rounded.Thermostat),
        PrefixRule("tj_cfg", Icons.Rounded.Thermostat),
        PrefixRule("sys-therm", Icons.Rounded.Thermostat),
        PrefixRule("vadc@", Icons.Rounded.Thermostat),

        // ── Cooling devices ──
        PrefixRule("cpu0_cdev", Icons.Rounded.AcUnit),
        PrefixRule("cpu1_cdev", Icons.Rounded.AcUnit),
        PrefixRule("cpu2_cdev", Icons.Rounded.AcUnit),
        PrefixRule("cpu3_hot", Icons.Rounded.AcUnit),
        PrefixRule("cpu4_hot", Icons.Rounded.AcUnit),
        PrefixRule("cpu5_hot", Icons.Rounded.AcUnit),
        PrefixRule("cpu6_hot", Icons.Rounded.AcUnit),
        PrefixRule("cpu7_hot", Icons.Rounded.AcUnit),
        PrefixRule("apc0_cdev", Icons.Rounded.AcUnit),
        PrefixRule("apc1_cdev", Icons.Rounded.AcUnit),
        PrefixRule("apc0-mx", Icons.Rounded.AcUnit),
        PrefixRule("apc1-mx", Icons.Rounded.AcUnit),
        PrefixRule("gpu_cdev", Icons.Rounded.AcUnit),
        PrefixRule("cdsp_cdev", Icons.Rounded.AcUnit),
        PrefixRule("display_cdev", Icons.Rounded.AcUnit),
        PrefixRule("lte_cdev", Icons.Rounded.AcUnit),
        PrefixRule("nr_cdev", Icons.Rounded.AcUnit),
        PrefixRule("qcom,userspace-cdev", Icons.Rounded.AcUnit),

        // ── CPU throttle prefixes ──
        PrefixRule("cpu000_", Icons.Rounded.AcUnit),
        PrefixRule("cpu010_", Icons.Rounded.AcUnit),
        PrefixRule("cpu100_", Icons.Rounded.AcUnit),
        PrefixRule("cpu101_", Icons.Rounded.AcUnit),
        PrefixRule("cpu110_", Icons.Rounded.AcUnit),
        PrefixRule("cpu111_", Icons.Rounded.AcUnit),
        PrefixRule("cpu120_", Icons.Rounded.AcUnit),
        PrefixRule("cpu121_", Icons.Rounded.AcUnit),
        PrefixRule("cpu130_", Icons.Rounded.AcUnit),
        PrefixRule("cpu131_", Icons.Rounded.AcUnit),
        PrefixRule("cpu140_", Icons.Rounded.AcUnit),
        PrefixRule("cpu141_", Icons.Rounded.AcUnit),
        PrefixRule("cpu200_", Icons.Rounded.AcUnit),
        PrefixRule("cpu201_", Icons.Rounded.AcUnit),
        PrefixRule("cpu202_", Icons.Rounded.AcUnit),

        // ── CPU hotplug / pause / emergency ──
        PrefixRule("cpu0-", Icons.Rounded.Memory),
        PrefixRule("cpu1-", Icons.Rounded.Memory),
        PrefixRule("cpu2-", Icons.Rounded.Memory),
        PrefixRule("cpu3-", Icons.Rounded.Memory),
        PrefixRule("cpu4-", Icons.Rounded.Memory),
        PrefixRule("cpu5-", Icons.Rounded.Memory),
        PrefixRule("cpu6-", Icons.Rounded.Memory),
        PrefixRule("cpu7-", Icons.Rounded.Memory),

        // ── Gold cluster sub-states ──
        PrefixRule("gold-", Icons.Rounded.Memory),
        PrefixRule("gold-plus", Icons.Rounded.Memory),

        // ── Security / TrustZone / Crypto ──
        PrefixRule("tz_", Icons.Rounded.Security),
        PrefixRule("tz-log", Icons.Rounded.Security),
        PrefixRule("qseecom", Icons.Rounded.Security),
        PrefixRule("qcom_scm", Icons.Rounded.Security),
        PrefixRule("qcom_smcinvoke", Icons.Rounded.Security),
        PrefixRule("qcom,qseecom", Icons.Rounded.Security),
        PrefixRule("qtee", Icons.Rounded.Security),
        PrefixRule("tme_", Icons.Rounded.Security),
        PrefixRule("rng@", Icons.Rounded.Security),
        PrefixRule("qcedev", Icons.Rounded.Security),
        PrefixRule("qcom_cedev", Icons.Rounded.Security),
        PrefixRule("qcom,tmecom", Icons.Rounded.Security),
        PrefixRule("secure_", Icons.Rounded.Security),
        PrefixRule("qcom,secure", Icons.Rounded.Security),

        // ── FarmsRPC ──
        PrefixRule("qcom,fastrpc", Icons.Rounded.Speed),

        // ── Timers ──
        PrefixRule("timer@", Icons.Rounded.Timer),
        PrefixRule("frame@", Icons.Rounded.Timer),
        PrefixRule("rtc@", Icons.Rounded.Timer),
        PrefixRule("sqm-timer", Icons.Rounded.Timer),
        PrefixRule("qcom,gh-qtimer", Icons.Rounded.Timer),

        // ── DMA ──
        PrefixRule("bamdma", Icons.Rounded.SwapHoriz),
        PrefixRule("dma", Icons.Rounded.SwapHoriz),
        PrefixRule("qcom,gpi-dma", Icons.Rounded.SwapHoriz),

        // ── Pinctrl / GPIO ──
        PrefixRule("pinctrl", Icons.Rounded.Tune),
        PrefixRule("tlmm", Icons.Rounded.Tune),

        // ── Syscon ──
        PrefixRule("syscon@", Icons.Rounded.Settings),

        // ── SDAM (Shared Direct Access Memory) ──
        PrefixRule("sdam@", Icons.Rounded.SdStorage),

        // ── QFProm (Fuses) ──
        PrefixRule("qfprom@", Icons.Rounded.Fingerprint),

        // ── Restart ──
        PrefixRule("restart", Icons.Rounded.RestartAlt),

        // ── Shared Memory / IPC ──
        PrefixRule("smem", Icons.Rounded.Share),
        PrefixRule("ipcc", Icons.Rounded.Share),
        PrefixRule("ipclite_signal", Icons.Rounded.Share),
        PrefixRule("qcom,glink", Icons.Rounded.Share),
        PrefixRule("qcom,glinkpkt", Icons.Rounded.Share),
        PrefixRule("qcom,qmp", Icons.Rounded.Share),
        PrefixRule("qcom,smp2p", Icons.Rounded.Share),
        PrefixRule("qcom,qrtr", Icons.Rounded.Share),

        // ── Ports ──
        PrefixRule("port@", Icons.Rounded.Cable),
        PrefixRule("in-ports", Icons.Rounded.Cable),
        PrefixRule("out-ports", Icons.Rounded.Cable),

        // ── Memory regions ──
        PrefixRule("ramoops", Icons.Rounded.Report),
        PrefixRule("sram@", Icons.Rounded.SdStorage),
        PrefixRule("mmio-sram", Icons.Rounded.SdStorage),
        PrefixRule("qcom,mem-buf", Icons.Rounded.SdStorage),
        PrefixRule("qcom,msm-imem", Icons.Rounded.SdStorage),
        PrefixRule("qcom_mem_object", Icons.Rounded.SdStorage),

        // ── BCM / RSC / DRV ──
        PrefixRule("bcm_voter", Icons.Rounded.HowToVote),
        PrefixRule("rsc@", Icons.Rounded.SettingsInputComponent),
        PrefixRule("drv@", Icons.Rounded.SettingsInputComponent),
        PrefixRule("channel@", Icons.Rounded.SettingsInputComponent),
        PrefixRule("crm@", Icons.Rounded.SettingsInputComponent),
        PrefixRule("csr@", Icons.Rounded.SettingsInputComponent),

        // ── SoC controller / CPUCP / SOCCP ──
        PrefixRule("soccp", Icons.Rounded.DeveloperBoard),
        PrefixRule("cpucp", Icons.Rounded.DeveloperBoard),
        PrefixRule("rpm_sw", Icons.Rounded.DeveloperBoard),
        PrefixRule("master-kernel", Icons.Rounded.DeveloperBoard),
        PrefixRule("slave-kernel", Icons.Rounded.DeveloperBoard),
        PrefixRule("aoss", Icons.Rounded.DeveloperBoard),

        // ── Heap / Pools ──
        PrefixRule("qcom,dma-heaps", Icons.Rounded.Inventory2),
        PrefixRule("qcom,gpu-mempool", Icons.Rounded.Inventory2),
        PrefixRule("qcom,gpu-mempools", Icons.Rounded.Inventory2),
        PrefixRule("mempool", Icons.Rounded.Inventory2),

        // ── GPU PWR levels ──
        PrefixRule("qcom,gpu-pwrlevel-bins", Icons.Rounded.Speed),
        PrefixRule("qcom,gpu-pwrlevels", Icons.Rounded.Speed),
        PrefixRule("qcom,gpu-pwrlevel@", Icons.Rounded.Speed),
        PrefixRule("qcom,kgsl", Icons.Rounded.Videocam),
        PrefixRule("qcom,gmu", Icons.Rounded.Videocam),

        // ── QCOM display ──
        PrefixRule("qcom,display", Icons.Rounded.Tv),

        // ── GDSC (power domains) ──
        PrefixRule("qcom,gdsc", Icons.Rounded.PowerSettingsNew),

        // ── QCOM misc services ──
        PrefixRule("qcom,battery", Icons.Rounded.BatteryChargingFull),
        PrefixRule("qcom,charger", Icons.Rounded.BatteryChargingFull),
        PrefixRule("qcom,ucsi", Icons.Rounded.Usb),
        PrefixRule("qcom,altmode", Icons.Rounded.Usb),
        PrefixRule("qcom,msm_cdsp", Icons.Rounded.Sensors),
        PrefixRule("qcom,mmrm", Icons.Rounded.Settings),
        PrefixRule("qcom,sps", Icons.Rounded.Settings),
        PrefixRule("qcom,msm-eud", Icons.Rounded.Usb),
        PrefixRule("qcom,scmi", Icons.Rounded.Settings),
        PrefixRule("qcom,cpu_mpam", Icons.Rounded.Speed),
        PrefixRule("qcom,mpam", Icons.Rounded.Speed),
        PrefixRule("qcom-mpam", Icons.Rounded.Speed),
        PrefixRule("qcom-slc", Icons.Rounded.Speed),
        PrefixRule("qcom,pmu", Icons.Rounded.Speed),
        PrefixRule("qcom,c1dcvs", Icons.Rounded.Speed),

        // ── IFE/IPE/OFE BW nodes ──
        PrefixRule("ife", Icons.Rounded.CameraAlt),
        PrefixRule("ipe", Icons.Rounded.CameraAlt),
        PrefixRule("ofe", Icons.Rounded.CameraAlt),
        PrefixRule("icp", Icons.Rounded.CameraAlt),
        PrefixRule("jpeg", Icons.Rounded.CameraAlt),
        PrefixRule("cre0", Icons.Rounded.CameraAlt),
        PrefixRule("rt-cdm", Icons.Rounded.CameraAlt),

        // ── Level BW nodes ──
        PrefixRule("level", Icons.Rounded.SwapVert),

        // ── Compute CB ──
        PrefixRule("compute-cb", Icons.Rounded.Sensors),

        // ── Context nodes ──
        PrefixRule("c0_context", Icons.Rounded.Settings),
        PrefixRule("c100_context", Icons.Rounded.Settings),
        PrefixRule("c200_context", Icons.Rounded.Settings),
        PrefixRule("c300_context", Icons.Rounded.Settings),
        PrefixRule("c400_context", Icons.Rounded.Settings),
        PrefixRule("c500_context", Icons.Rounded.Settings),
        PrefixRule("c600_context", Icons.Rounded.Settings),
        PrefixRule("c700_context", Icons.Rounded.Settings),

        // ── SPR (registers) ──
        PrefixRule("spr_", Icons.Rounded.Info),

        // ── VCM ──
        PrefixRule("vcm@", Icons.Rounded.CameraAlt),

        // ── Part IDs ──
        PrefixRule("part-id", Icons.Rounded.Info),
        PrefixRule("feat_conf", Icons.Rounded.Info),

        // ── DGM ──
        PrefixRule("dgm@", Icons.Rounded.Settings),

        // ── OPP table ──
        PrefixRule("sdhc2-opp", Icons.Rounded.Speed),

        // ── Active config ──
        PrefixRule("active-config", Icons.Rounded.Settings),

        // ── modem QMI / turing QMI ──
        PrefixRule("modem0-qmi", Icons.Rounded.SignalCellularAlt),
        PrefixRule("modem2-qmi", Icons.Rounded.SignalCellularAlt),
        PrefixRule("turing-qmi", Icons.Rounded.Sensors),
        PrefixRule("wpss-qmi", Icons.Rounded.Wifi),

        // ── QOS nodes ──
        PrefixRule("qos", Icons.Rounded.Speed),

        // ── SCID / Heuristics ──
        PrefixRule("scid_", Icons.Rounded.Info),

        // ── Usecase nodes ──
        PrefixRule("usecase", Icons.Rounded.Settings),

        // ── Algorithm nodes (audio) ──
        PrefixRule("alg", Icons.AutoMirrored.Rounded.VolumeUp),

        // ── PMX ts / touch pins ──
        PrefixRule("pmx_ts", Icons.Rounded.TouchApp),
        PrefixRule("ts_", Icons.Rounded.TouchApp),

        // ── fmd (fault management) ──
        PrefixRule("fmd-", Icons.Rounded.Warning),

        // ── Dummy ──
        PrefixRule("dummy-", Icons.Rounded.BugReport),

        // ── PMIC pon log ──
        PrefixRule("pmic-pon", Icons.Rounded.PowerSettingsNew),

        // ── Sleepstate ──
        PrefixRule("sleepstate", Icons.Rounded.PowerSettingsNew),

        // ── QMI sensors ──
        PrefixRule("qmi-tmd", Icons.Rounded.Thermostat),
        PrefixRule("qmi-ts", Icons.Rounded.Thermostat),

        // ── Misc ──
        PrefixRule("fsm_data", Icons.Rounded.Storage),
        PrefixRule("google,debug", Icons.Rounded.BugReport),
        PrefixRule("limits-stat", Icons.Rounded.Info),
        PrefixRule("fp", Icons.Rounded.Fingerprint),
    )

    // ─── Keyword fallback (if no prefix matched) ───────────────────
    private data class KeywordRule(val keyword: String, val icon: ImageVector)

    private val keywordRules: List<KeywordRule> = listOf(
        // Hardware bus keywords
        KeywordRule("i2c", Icons.Rounded.Cable),
        KeywordRule("spi", Icons.Rounded.Cable),
        KeywordRule("uart", Icons.Rounded.Cable),
        KeywordRule("usb", Icons.Rounded.Usb),
        KeywordRule("pcie", Icons.Rounded.Cable),

        // Memory / Region
        KeywordRule("region", Icons.Rounded.ViewModule),
        KeywordRule("_mem", Icons.Rounded.SdStorage),
        KeywordRule("mem_", Icons.Rounded.SdStorage),
        KeywordRule("heap", Icons.Rounded.Inventory2),
        KeywordRule("sram", Icons.Rounded.SdStorage),
        KeywordRule("dump", Icons.Rounded.BugReport),

        // Clocks / Timing
        KeywordRule("clk", Icons.Rounded.Schedule),
        KeywordRule("clock", Icons.Rounded.Schedule),
        KeywordRule("pll", Icons.Rounded.Schedule),
        KeywordRule("osc", Icons.Rounded.Schedule),

        // GPIO / Pin
        KeywordRule("gpio", Icons.Rounded.Tune),
        KeywordRule("pin", Icons.Rounded.Tune),

        // Power / Supply
        KeywordRule("supply", Icons.Rounded.ElectricalServices),
        KeywordRule("power", Icons.Rounded.PowerSettingsNew),
        KeywordRule("regulator", Icons.Rounded.ElectricalServices),
        KeywordRule("vreg", Icons.Rounded.ElectricalServices),
        KeywordRule("ldo", Icons.Rounded.ElectricalServices),
        KeywordRule("smp", Icons.Rounded.ElectricalServices),

        // Cooling
        KeywordRule("_cdev", Icons.Rounded.AcUnit),
        KeywordRule("hotplug", Icons.Rounded.AcUnit),

        // Cellular / Modem
        KeywordRule("modem", Icons.Rounded.SignalCellularAlt),
        KeywordRule("lte", Icons.Rounded.SignalCellularAlt),

        // Security
        KeywordRule("secure", Icons.Rounded.Security),
        KeywordRule("crypto", Icons.Rounded.Security),
        KeywordRule("tme", Icons.Rounded.Security),

        // Camera
        KeywordRule("camera", Icons.Rounded.CameraAlt),
        KeywordRule("cam_", Icons.Rounded.CameraAlt),

        // Display
        KeywordRule("display", Icons.Rounded.Tv),
        KeywordRule("dsi", Icons.Rounded.Tv),

        // Audio
        KeywordRule("audio", Icons.AutoMirrored.Rounded.VolumeUp),
        KeywordRule("codec", Icons.AutoMirrored.Rounded.VolumeUp),
        KeywordRule("sound", Icons.AutoMirrored.Rounded.VolumeUp),

        // Thermal
        KeywordRule("thermal", Icons.Rounded.Thermostat),
        KeywordRule("temp", Icons.Rounded.Thermostat),
        KeywordRule("therm", Icons.Rounded.Thermostat),

        // Reset / Restart
        KeywordRule("reset", Icons.Rounded.RestartAlt),
        KeywordRule("restart", Icons.Rounded.RestartAlt),

        // Debug / Test
        KeywordRule("test", Icons.Rounded.BugReport),
        KeywordRule("debug", Icons.Rounded.BugReport),
        KeywordRule("trace", Icons.Rounded.BugReport),
        KeywordRule("diag", Icons.Rounded.BugReport),
        KeywordRule("log", Icons.Rounded.BugReport),

        // LED
        KeywordRule("led", Icons.Rounded.LightMode),

        // Wifi / Bluetooth
        KeywordRule("wlan", Icons.Rounded.Wifi),
        KeywordRule("wifi", Icons.Rounded.Wifi),
        KeywordRule("bt_", Icons.Rounded.Bluetooth),
        KeywordRule("bluetooth", Icons.Rounded.Bluetooth),

        // IPC / shared
        KeywordRule("shared", Icons.Rounded.Share),
        KeywordRule("mailbox", Icons.Rounded.Share),
        KeywordRule("glink", Icons.Rounded.Share),
        KeywordRule("ipc", Icons.Rounded.Share),

        // Config / QMI
        KeywordRule("config", Icons.Rounded.Settings),
        KeywordRule("qmi", Icons.Rounded.Settings),

        // Default catch-alls last
        KeywordRule("swiotlb", Icons.Rounded.SdStorage),
        KeywordRule("ring", Icons.Rounded.Share),
        KeywordRule("vote", Icons.Rounded.HowToVote),
    )

    /** Default icon for unrecognized nodes */
    private val defaultNodeIcon: ImageVector = Icons.Rounded.Folder

    /** Default icon for property items */
    val propertyIcon: ImageVector = Icons.Rounded.Code

    /**
     * Get the icon for a DTS node name.
     * O(1) for exact match, O(P) for prefix scan, O(K) keyword fallback.
     */
    fun forNode(nodeName: String): ImageVector {
        // 1) Exact match (O(1))
        exactMatch[nodeName]?.let { return it }

        // 2) Lowercase for case-insensitive matching
        val lowerName = nodeName.lowercase()

        // 3) Prefix scan — first match wins
        for (rule in prefixRules) {
            if (lowerName.startsWith(rule.prefix.lowercase())) {
                return rule.icon
            }
        }

        // 4) Keyword fallback — first match wins
        for (rule in keywordRules) {
            if (lowerName.contains(rule.keyword)) {
                return rule.icon
            }
        }

        return defaultNodeIcon
    }
}
