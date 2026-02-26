package com.ireddragonicy.konabessnext.model.display

/**
 * Represents a DSI display panel found in a DTBO overlay fragment.
 *
 * Each panel lives inside a `fragment@N / __overlay__ / qcom,mdss_dsi_…` node
 * and contains one or more [DisplayTiming] entries under
 * `qcom,mdss-dsi-display-timings`.
 *
 * @property fragmentIndex  The `fragment@N` index that owns this panel.
 * @property nodeName       Full DTS node name, e.g. `qcom,mdss_dsi_o10u_36_02_0b_dsc_vid`.
 * @property panelName      Human-readable name from `qcom,mdss-dsi-panel-name`.
 * @property panelType      `dsi_video_mode` or `dsi_cmd_mode`.
 * @property dfpsList       Supported DFPS rates from `qcom,dsi-supported-dfps-list`.
 * @property timings        The timing sub-nodes (typically `timing@0`, `timing@1`, …).
 * @property properties     All panel-level properties (key → raw DTS value).
 */
data class DisplayPanel(
    val fragmentIndex: Int,
    val nodeName: String,
    val panelName: String,
    val panelType: String,
    val dfpsList: List<Int>,
    val timings: List<DisplayTiming>,
    val properties: List<DisplayProperty>
)

/**
 * Represents a single display timing entry (e.g. `timing@0`)
 * inside `qcom,mdss-dsi-display-timings`.
 *
 * Contains the core overclock-relevant parameters extracted from the
 * timing node's properties.
 */
data class DisplayTiming(
    val timingNodeName: String,
    val panelFramerate: Int,
    val panelWidth: Int,
    val panelHeight: Int,
    val panelClockRate: Long,
    val hFrontPorch: Int,
    val hBackPorch: Int,
    val hPulseWidth: Int,
    val vFrontPorch: Int,
    val vBackPorch: Int,
    val vPulseWidth: Int,
    val hLeftBorder: Int,
    val hRightBorder: Int,
    val vTopBorder: Int,
    val vBottomBorder: Int,
    val hSyncPulse: Int,
    val properties: List<DisplayProperty>
)

/**
 * A single display-related DTS property (name + raw string value).
 */
data class DisplayProperty(
    val name: String,
    val value: String
)
