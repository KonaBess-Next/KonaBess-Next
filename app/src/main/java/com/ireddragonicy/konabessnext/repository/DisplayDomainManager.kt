package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.display.DisplayPanel
import com.ireddragonicy.konabessnext.model.display.DisplayProperty
import com.ireddragonicy.konabessnext.model.display.DisplayTiming
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stateless domain logic for DTBO display panel parsing and manipulation.
 *
 * This is the display-overclock counterpart of [GpuDomainManager].
 * It knows how to walk a DTBO AST to locate DSI panel overlay fragments,
 * extract [DisplayPanel] / [DisplayTiming] models, and perform targeted
 * property mutations for display overclocking.
 *
 * ### DTBO overlay structure
 * ```
 * / {
 *     fragment@92 {
 *         target = <0xffffffff>;
 *         __overlay__ {
 *             qcom,mdss_dsi_<panel_name> {
 *                 qcom,mdss-dsi-panel-name = "…";
 *                 qcom,dsi-supported-dfps-list = <0x78 0x5a 0x3c 0x1e>;
 *                 …
 *                 qcom,mdss-dsi-display-timings {
 *                     timing@0 {
 *                         qcom,mdss-dsi-panel-framerate = <0x78>;
 *                         …
 *                     };
 *                 };
 *             };
 *         };
 *     };
 * };
 * ```
 */
@Singleton
class DisplayDomainManager @Inject constructor() {

    // ---- Panel discovery -------------------------------------------------------

    /**
     * Walks the full DTBO AST and returns every DSI panel overlay it finds.
     *
     * A "panel node" is any node whose name starts with `qcom,mdss_dsi_`
     * that lives inside a `fragment@N / __overlay__` tree.
     */
    fun findAllPanels(root: DtsNode): List<DisplayPanel> {
        val panels = ArrayList<DisplayPanel>()
        for (fragment in root.children) {
            if (!isFragmentNode(fragment)) continue
            val fragmentIndex = parseFragmentIndex(fragment.name)
            val overlay = fragment.getChild("__overlay__") ?: continue
            for (child in overlay.children) {
                if (isPanelNode(child)) {
                    panels.add(buildPanel(child, fragmentIndex))
                }
            }
        }
        // Also walk nested root nodes (e.g. "/" node at top-level)
        for (topLevel in root.children) {
            if (topLevel.name == "/" || topLevel.name == "root") {
                panels.addAll(findAllPanels(topLevel))
            }
        }
        return panels
    }

    /**
     * Finds the first DSI panel that contains a `qcom,mdss-dsi-display-timings` child.
     */
    fun findFirstPanelWithTimings(root: DtsNode): DisplayPanel? {
        return findAllPanels(root).firstOrNull { it.timings.isNotEmpty() }
    }

    // ---- Fast line-scan parsing ------------------------------------------------

    /**
     * Builds a [DisplayPanel] from a DTS AST panel node.
     */
    private fun buildPanel(panelNode: DtsNode, fragmentIndex: Int): DisplayPanel {
        val panelName = panelNode.getProperty("qcom,mdss-dsi-panel-name")
            ?.originalValue?.removeSurrounding("\"") ?: ""
        val panelType = panelNode.getProperty("qcom,mdss-dsi-panel-type")
            ?.originalValue?.removeSurrounding("\"") ?: ""

        val dfpsRaw = panelNode.getProperty("qcom,dsi-supported-dfps-list")
            ?.originalValue ?: ""
        val dfpsList = extractIntList(dfpsRaw)

        // Collect panel-level properties
        val properties = panelNode.properties.map {
            DisplayProperty(it.name, it.originalValue)
        }

        // Parse timing sub-nodes
        val timingsContainer = panelNode.getChild("qcom,mdss-dsi-display-timings")
        val timings = if (timingsContainer != null) {
            timingsContainer.children.map { timingNode ->
                buildTiming(timingNode)
            }
        } else emptyList()

        return DisplayPanel(
            fragmentIndex = fragmentIndex,
            nodeName = panelNode.name,
            panelName = panelName,
            panelType = panelType,
            dfpsList = dfpsList,
            timings = timings,
            properties = properties
        )
    }

    /**
     * Builds a [DisplayTiming] from a timing AST node (e.g. `timing@0`).
     */
    private fun buildTiming(timingNode: DtsNode): DisplayTiming {
        fun intProp(name: String): Int =
            extractSingleInt(timingNode.getProperty(name)?.originalValue ?: "0")
        fun longProp(name: String): Long =
            extractSingleLong(timingNode.getProperty(name)?.originalValue ?: "0")

        val timingProperties = timingNode.properties.map {
            DisplayProperty(it.name, it.originalValue)
        }

        return DisplayTiming(
            timingNodeName = timingNode.name,
            panelFramerate = intProp("qcom,mdss-dsi-panel-framerate"),
            panelWidth = intProp("qcom,mdss-dsi-panel-width"),
            panelHeight = intProp("qcom,mdss-dsi-panel-height"),
            panelClockRate = longProp("qcom,mdss-dsi-panel-clockrate"),
            hFrontPorch = intProp("qcom,mdss-dsi-h-front-porch"),
            hBackPorch = intProp("qcom,mdss-dsi-h-back-porch"),
            hPulseWidth = intProp("qcom,mdss-dsi-h-pulse-width"),
            vFrontPorch = intProp("qcom,mdss-dsi-v-front-porch"),
            vBackPorch = intProp("qcom,mdss-dsi-v-back-porch"),
            vPulseWidth = intProp("qcom,mdss-dsi-v-pulse-width"),
            hLeftBorder = intProp("qcom,mdss-dsi-h-left-border"),
            hRightBorder = intProp("qcom,mdss-dsi-h-right-border"),
            vTopBorder = intProp("qcom,mdss-dsi-v-top-border"),
            vBottomBorder = intProp("qcom,mdss-dsi-v-bottom-border"),
            hSyncPulse = intProp("qcom,mdss-dsi-h-sync-pulse"),
            properties = timingProperties
        )
    }

    /**
     * O(n) line scan to extract [DisplayPanel] models without full AST.
     * Mirrors [GpuDomainManager.parseBins] strategy for performance.
     */
    fun parsePanelsFromLines(lines: List<String>): List<DisplayPanel> {
        if (lines.isEmpty()) return emptyList()
        val panels = ArrayList<DisplayPanel>()
        var i = 0

        while (i < lines.size) {
            val trimmed = lines[i].trim()

            // Detect panel node: "qcom,mdss_dsi_<name> {"
            if (trimmed.startsWith("qcom,mdss_dsi_") && trimmed.endsWith("{")) {
                val nodeName = trimmed.removeSuffix("{").trim()
                val panel = parseSinglePanelBlock(lines, i, nodeName)
                if (panel != null) panels.add(panel)

                // Skip past this block
                var braceCount = 1
                i++
                while (i < lines.size && braceCount > 0) {
                    val line = lines[i].trim()
                    braceCount += line.count { it == '{' }
                    braceCount -= line.count { it == '}' }
                    i++
                }
                continue
            }
            i++
        }
        return panels
    }

    private fun parseSinglePanelBlock(
        lines: List<String>,
        startIndex: Int,
        nodeName: String
    ): DisplayPanel? {
        var i = startIndex + 1
        var braceCount = 1
        var panelName = ""
        var panelType = ""
        val dfpsList = ArrayList<Int>()
        val panelProperties = ArrayList<DisplayProperty>()
        val timings = ArrayList<DisplayTiming>()
        var fragmentIndex = -1

        // Try to find fragment index by scanning backwards
        for (j in startIndex downTo 0) {
            val line = lines[j].trim()
            if (line.startsWith("fragment@")) {
                fragmentIndex = parseFragmentIndex(line.substringBefore("{").trim())
                break
            }
        }

        while (i < lines.size && braceCount > 0) {
            val line = lines[i].trim()
            braceCount += line.count { it == '{' }
            braceCount -= line.count { it == '}' }

            if (braceCount <= 0) break

            // Check for timing sub-block
            if (line.startsWith("qcom,mdss-dsi-display-timings") && line.endsWith("{")) {
                val timingResult = parseTimingsBlock(lines, i)
                timings.addAll(timingResult.first)
                i = timingResult.second
                continue
            }

            // Panel-level properties
            if (line.contains("=") && !line.startsWith("//")) {
                val propName = line.substringBefore("=").trim()
                val propValue = line.substringAfter("=").trim().removeSuffix(";").trim()

                when (propName) {
                    "qcom,mdss-dsi-panel-name" -> panelName = propValue.removeSurrounding("\"")
                    "qcom,mdss-dsi-panel-type" -> panelType = propValue.removeSurrounding("\"")
                    "qcom,dsi-supported-dfps-list" -> {
                        dfpsList.addAll(extractIntList(propValue))
                    }
                }
                panelProperties.add(DisplayProperty(propName, propValue))
            } else if (!line.contains("{") && !line.contains("}") && line.endsWith(";")) {
                // Boolean property (no = sign)
                val boolProp = line.removeSuffix(";").trim()
                if (boolProp.isNotEmpty()) {
                    panelProperties.add(DisplayProperty(boolProp, ""))
                }
            }

            i++
        }

        if (panelName.isEmpty() && timings.isEmpty()) return null

        return DisplayPanel(
            fragmentIndex = fragmentIndex,
            nodeName = nodeName,
            panelName = panelName,
            panelType = panelType,
            dfpsList = dfpsList,
            timings = timings,
            properties = panelProperties
        )
    }

    /**
     * Parses all `timing@N` children within a `qcom,mdss-dsi-display-timings` block.
     * Returns pair of (timings, lastLineIndex).
     */
    private fun parseTimingsBlock(
        lines: List<String>,
        startIndex: Int
    ): Pair<List<DisplayTiming>, Int> {
        val timings = ArrayList<DisplayTiming>()
        var i = startIndex + 1
        var braceCount = 1

        while (i < lines.size && braceCount > 0) {
            val line = lines[i].trim()
            braceCount += line.count { it == '{' }
            braceCount -= line.count { it == '}' }

            if (braceCount <= 0) break

            // Individual timing node
            if (line.startsWith("timing@") && line.endsWith("{")) {
                val timingName = line.removeSuffix("{").trim()
                val timing = parseSingleTimingBlock(lines, i, timingName)
                if (timing != null) timings.add(timing)

                // Skip past timing block
                var innerBrace = 1
                i++
                while (i < lines.size && innerBrace > 0) {
                    val innerLine = lines[i].trim()
                    innerBrace += innerLine.count { it == '{' }
                    innerBrace -= innerLine.count { it == '}' }
                    i++
                }
                continue
            }
            i++
        }
        return timings to i
    }

    private fun parseSingleTimingBlock(
        lines: List<String>,
        startIndex: Int,
        timingNodeName: String
    ): DisplayTiming? {
        var i = startIndex + 1
        var braceCount = 1
        var framerate = 0
        var width = 0
        var height = 0
        var clockRate = 0L
        var hFP = 0; var hBP = 0; var hPW = 0
        var vFP = 0; var vBP = 0; var vPW = 0
        var hLB = 0; var hRB = 0; var vTB = 0; var vBB = 0; var hSync = 0
        val timingProps = ArrayList<DisplayProperty>()

        while (i < lines.size && braceCount > 0) {
            val line = lines[i].trim()
            braceCount += line.count { it == '{' }
            braceCount -= line.count { it == '}' }

            if (braceCount <= 0) break

            if (line.contains("=") && !line.startsWith("//")) {
                val propName = line.substringBefore("=").trim()
                val propValue = line.substringAfter("=").trim().removeSuffix(";").trim()

                when (propName) {
                    "qcom,mdss-dsi-panel-framerate" -> framerate = extractSingleInt(propValue)
                    "qcom,mdss-dsi-panel-width" -> width = extractSingleInt(propValue)
                    "qcom,mdss-dsi-panel-height" -> height = extractSingleInt(propValue)
                    "qcom,mdss-dsi-panel-clockrate" -> clockRate = extractSingleLong(propValue)
                    "qcom,mdss-dsi-h-front-porch" -> hFP = extractSingleInt(propValue)
                    "qcom,mdss-dsi-h-back-porch" -> hBP = extractSingleInt(propValue)
                    "qcom,mdss-dsi-h-pulse-width" -> hPW = extractSingleInt(propValue)
                    "qcom,mdss-dsi-v-front-porch" -> vFP = extractSingleInt(propValue)
                    "qcom,mdss-dsi-v-back-porch" -> vBP = extractSingleInt(propValue)
                    "qcom,mdss-dsi-v-pulse-width" -> vPW = extractSingleInt(propValue)
                    "qcom,mdss-dsi-h-left-border" -> hLB = extractSingleInt(propValue)
                    "qcom,mdss-dsi-h-right-border" -> hRB = extractSingleInt(propValue)
                    "qcom,mdss-dsi-v-top-border" -> vTB = extractSingleInt(propValue)
                    "qcom,mdss-dsi-v-bottom-border" -> vBB = extractSingleInt(propValue)
                    "qcom,mdss-dsi-h-sync-pulse" -> hSync = extractSingleInt(propValue)
                }
                timingProps.add(DisplayProperty(propName, propValue))
            } else if (!line.contains("{") && !line.contains("}") && line.endsWith(";")) {
                val boolProp = line.removeSuffix(";").trim()
                if (boolProp.isNotEmpty()) {
                    timingProps.add(DisplayProperty(boolProp, ""))
                }
            }
            i++
        }

        return DisplayTiming(
            timingNodeName = timingNodeName,
            panelFramerate = framerate,
            panelWidth = width,
            panelHeight = height,
            panelClockRate = clockRate,
            hFrontPorch = hFP,
            hBackPorch = hBP,
            hPulseWidth = hPW,
            vFrontPorch = vFP,
            vBackPorch = vBP,
            vPulseWidth = vPW,
            hLeftBorder = hLB,
            hRightBorder = hRB,
            vTopBorder = vTB,
            vBottomBorder = vBB,
            hSyncPulse = hSync,
            properties = timingProps
        )
    }

    // ---- AST-based node finders -----------------------------------------------

    /**
     * Finds the first timing node matching the given panel and timing index
     * in the AST tree. Used for AST-based mutations.
     * 
     * @param fragmentIndex If >= 0, restricts search to that fragment. If -1, finds first match.
     */
    fun findTimingNode(
        root: DtsNode, 
        panelNodeName: String, 
        timingIndex: Int, 
        fragmentIndex: Int = -1
    ): DtsNode? {
        val panelNode = findPanelNodeInTree(root, panelNodeName, fragmentIndex) ?: return null
        val timingsContainer = panelNode.getChild("qcom,mdss-dsi-display-timings") ?: return null
        return timingsContainer.getChild("timing@$timingIndex")
    }

    /**
     * Finds a panel node by name across all overlay fragments.
     * 
     * @param fragmentIndex If >= 0, restricts search to that fragment. If -1, finds first match.
     */
    fun findPanelNodeInTree(
        root: DtsNode, 
        panelNodeName: String,
        fragmentIndex: Int = -1
    ): DtsNode? {
        // 1. Search direct fragments first (Standard DTBO structure)
        for (child in root.children) {
            if (isFragmentNode(child)) {
                if (fragmentIndex >= 0) {
                    val idx = parseFragmentIndex(child.name)
                    if (idx != fragmentIndex) continue
                }
                
                val overlay = child.getChild("__overlay__") ?: continue
                val panel = overlay.getChild(panelNodeName)
                if (panel != null) {
                    com.ireddragonicy.konabessnext.utils.DtsEditorDebug.logDomainSearch("found", panelNodeName, fragmentIndex, "frag=${child.name}")
                    return panel
                }
            }
        }
        
        // 2. Recurse ONLY into known container nodes ("/" and "root")
        // This matches findAllPanels logic and prevents finding phantom nodes in other structures
        for (child in root.children) {
            if (child.name == "/" || child.name == "root") {
                val found = findPanelNodeInTree(child, panelNodeName, fragmentIndex)
                if (found != null) return found
            }
        }

        com.ireddragonicy.konabessnext.utils.DtsEditorDebug.logDomainSearch("FAIL", panelNodeName, fragmentIndex, null)
        return null
    }

    /**
     * Finds all panel DTS nodes (not models, but raw AST nodes) across fragments.
     */
    fun findAllPanelNodes(root: DtsNode): List<DtsNode> {
        val results = ArrayList<DtsNode>()
        fun recurse(node: DtsNode) {
            if (isPanelNode(node)) {
                results.add(node)
                return
            }
            node.children.forEach { recurse(it) }
        }
        recurse(root)
        return results
    }

    // ---- Property mutation helpers -------------------------------------------

    /**
     * Updates a timing property in the AST, preserving hex/decimal formatting.
     * Returns true if the property was found and updated.
     */
    fun updateTimingProperty(
        timingNode: DtsNode,
        propertyName: String,
        newValue: String
    ): Boolean {
        val existingProp = timingNode.getProperty(propertyName)
        if (existingProp == null) {
            // Property doesn't exist — add it
            timingNode.setProperty(propertyName, formatAsCell(newValue))
            return true
        }

        if (existingProp.isHexArray) {
            existingProp.updateFromDisplayValue(newValue)
        } else {
            val original = existingProp.originalValue.trim()
            // Preserve <0x…> formatting
            val open = original.indexOf('<')
            val close = original.indexOf('>', open + 1)
            if (open != -1 && close != -1) {
                val currentCellToken = original
                    .substring(open + 1, close)
                    .trim()
                    .split(Regex("\\s+"))
                    .firstOrNull()
                val formatted = formatCellByStyle(newValue, currentCellToken)
                existingProp.originalValue =
                    original.substring(0, open + 1) + formatted + original.substring(close)
            } else {
                existingProp.originalValue = newValue
            }
        }
        return true
    }

    /**
     * Updates a panel-level property (not inside timing) in the AST.
     */
    fun updatePanelProperty(
        panelNode: DtsNode,
        propertyName: String,
        newValue: String
    ): Boolean {
        return updateTimingProperty(panelNode, propertyName, newValue)
    }

    /**
     * Updates the DFPS list on a panel node.
     * Takes a sorted list of FPS values and converts to hex cell array.
     */
    fun updateDfpsList(panelNode: DtsNode, fpsList: List<Int>) {
        val cellValues = fpsList.joinToString(" ") { "0x${it.toString(16)}" }
        panelNode.setProperty("qcom,dsi-supported-dfps-list", "<$cellValues>")
    }

    // ---- Internal helpers ---------------------------------------------------

    private fun isFragmentNode(node: DtsNode): Boolean {
        return node.name.startsWith("fragment@")
    }

    private fun isPanelNode(node: DtsNode): Boolean {
        return node.name.startsWith("qcom,mdss_dsi_")
    }

    private fun parseFragmentIndex(name: String): Int {
        val suffix = name.substringAfter("fragment@", "")
        // Try decimal first (user report: fragment@92 is being read as 0x92/146)
        // If it fails (e.g. contains a-f), fallback to hex.
        return suffix.toIntOrNull() ?: suffix.toIntOrNull(16) ?: -1
    }

    private fun extractSingleInt(rawValue: String): Int {
        return extractSingleLong(rawValue).toInt()
    }

    private fun extractSingleLong(rawValue: String): Long {
        val trimmed = rawValue.trim()
        // Handle <0xNN> format
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            val parts = inner.split(Regex("\\s+"))
            return try {
                if (parts.size == 1) {
                    val v = parts[0].trim()
                    if (v.startsWith("0x", ignoreCase = true))
                        java.lang.Long.decode(v)
                    else v.toLongOrNull() ?: 0L
                } else {
                    // Multi-cell big-endian
                    var result = 0L
                    for (part in parts) {
                        val cell = part.trim()
                        val cellVal = if (cell.startsWith("0x", ignoreCase = true))
                            java.lang.Long.decode(cell) else cell.toLong()
                        result = (result shl 32) or (cellVal and 0xFFFFFFFFL)
                    }
                    result
                }
            } catch (_: Exception) { 0L }
        }
        // Plain decimal or hex
        return try {
            if (trimmed.startsWith("0x", ignoreCase = true))
                java.lang.Long.decode(trimmed)
            else trimmed.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun extractIntList(rawValue: String): List<Int> {
        val trimmed = rawValue.trim()
        val inner = if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            trimmed.substring(1, trimmed.length - 1).trim()
        } else trimmed

        if (inner.isEmpty()) return emptyList()
        return inner.split(Regex("\\s+")).mapNotNull { token ->
            try {
                if (token.startsWith("0x", ignoreCase = true))
                    java.lang.Long.decode(token).toInt()
                else token.toIntOrNull()
            } catch (_: Exception) { null }
        }
    }

    private fun formatAsCell(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("<")) return trimmed
        val numeric = trimmed.toLongOrNull()
        return if (numeric != null) "<0x${numeric.toString(16)}>" else trimmed
    }

    private fun formatCellByStyle(newValue: String, currentCellToken: String?): String {
        val normalized = newValue.trim()
        if (normalized.startsWith("0x", ignoreCase = true)) return normalized
        val numeric = normalized.toLongOrNull() ?: return normalized

        return if (currentCellToken?.startsWith("0x", ignoreCase = true) == true) {
            "0x${numeric.toString(16)}"
        } else {
            numeric.toString()
        }
    }

    // ---- Touch Overclock ---------------------------------------------------

    /**
     * Walks the DTBO AST and extracts touch panel nodes that have `spi-max-frequency`.
     */
    fun findTouchPanels(root: DtsNode): List<com.ireddragonicy.konabessnext.model.display.TouchPanel> {
        val panels = ArrayList<com.ireddragonicy.konabessnext.model.display.TouchPanel>()
        for (fragment in root.children) {
            if (!isFragmentNode(fragment)) continue
            val fragmentIndex = parseFragmentIndex(fragment.name)
            val overlay = fragment.getChild("__overlay__") ?: continue

            fun recurseSearch(node: DtsNode) {
                if (node.getProperty("spi-max-frequency") != null) {
                    val compatible = node.getProperty("compatible")?.originalValue?.removeSurrounding("\"") ?: ""
                    val nodeNameLower = node.name.lowercase()
                    
                    // Exclude known non-touch SPI devices like Infrared Blasters
                    if (!nodeNameLower.contains("ir-spi")) {
                        val spiMaxFreq = extractSingleLong(node.getProperty("spi-max-frequency")?.originalValue ?: "0")
                        panels.add(
                            com.ireddragonicy.konabessnext.model.display.TouchPanel(
                                fragmentIndex = fragmentIndex,
                                nodeName = node.name,
                                compatible = compatible,
                                spiMaxFrequency = spiMaxFreq
                            )
                        )
                    }
                }
                node.children.forEach { recurseSearch(it) }
            }
            recurseSearch(overlay)
        }

        for (topLevel in root.children) {
            if (topLevel.name == "/" || topLevel.name == "root") {
                panels.addAll(findTouchPanels(topLevel))
            }
        }
        return panels.distinctBy { it.nodeName to it.fragmentIndex }
    }

    /**
     * Walks the DTBO AST and extracts speaker amplifier nodes like AW882XX.
     */
    fun findSpeakerPanels(root: DtsNode): List<com.ireddragonicy.konabessnext.model.display.SpeakerPanel> {
        val panels = ArrayList<com.ireddragonicy.konabessnext.model.display.SpeakerPanel>()
        for (fragment in root.children) {
            if (!isFragmentNode(fragment)) continue
            val fragmentIndex = parseFragmentIndex(fragment.name)
            val overlay = fragment.getChild("__overlay__") ?: continue

            fun recurseSearch(node: DtsNode) {
                val compatibleStr = node.getProperty("compatible")?.originalValue?.removeSurrounding("\"") ?: ""
                
                // Specifically target AW882xx for Speaker OC
                if (compatibleStr.contains("awinic,aw882xx_smartpa", ignoreCase = true)) {
                    val awReMin = extractSingleLong(node.getProperty("aw-re-min")?.originalValue ?: "0")
                    val awReMax = extractSingleLong(node.getProperty("aw-re-max")?.originalValue ?: "0")
                    
                    panels.add(
                        com.ireddragonicy.konabessnext.model.display.SpeakerPanel(
                            fragmentIndex = fragmentIndex,
                            nodeName = node.name,
                            compatible = compatibleStr,
                            awReMin = awReMin,
                            awReMax = awReMax
                        )
                    )
                }
                node.children.forEach { recurseSearch(it) }
            }
            recurseSearch(overlay)
        }

        for (topLevel in root.children) {
            if (topLevel.name == "/" || topLevel.name == "root") {
                panels.addAll(findSpeakerPanels(topLevel))
            }
        }
        return panels.distinctBy { it.nodeName to it.fragmentIndex }
    }

    /**
     * Finds a specific speaker node by name and fragment.
     */
    fun findSpeakerNodeInTree(
        root: DtsNode,
        nodeName: String,
        fragmentIndex: Int
    ): DtsNode? {
        val fragmentNodeName = "fragment@$fragmentIndex"
        val fragmentNode = root.getChild(fragmentNodeName) ?: return null
        val overlayNode = fragmentNode.getChild("__overlay__") ?: return null

        fun findNode(current: DtsNode): DtsNode? {
            if (current.name == nodeName) return current
            for (child in current.children) {
                val res = findNode(child)
                if (res != null) return res
            }
            return null
        }
        return findNode(overlayNode)
    }

    /**
     * Updates the aw-re-min and aw-re-max properties on a speaker node.
     */
    fun updateSpeakerReBounds(speakerNode: DtsNode, newReMin: String, newReMax: String): Boolean {
        var success = updateTimingProperty(speakerNode, "aw-re-min", newReMin)
        success = success && updateTimingProperty(speakerNode, "aw-re-max", newReMax)
        return success
    }

    /**
     * Finds a touch node in the AST by name across all overlay fragments.
     */
    fun findTouchNodeInTree(
        root: DtsNode,
        nodeName: String,
        fragmentIndex: Int = -1
    ): DtsNode? {
        for (child in root.children) {
            if (isFragmentNode(child)) {
                if (fragmentIndex >= 0) {
                    val idx = parseFragmentIndex(child.name)
                    if (idx != fragmentIndex) continue
                }

                val overlay = child.getChild("__overlay__") ?: continue
                var foundTarget: DtsNode? = null
                fun recurseSearch(node: DtsNode) {
                    if (foundTarget != null) return
                    if (node.name == nodeName && node.getProperty("spi-max-frequency") != null) {
                        foundTarget = node
                        return
                    }
                    node.children.forEach { recurseSearch(it) }
                }
                recurseSearch(overlay)
                if (foundTarget != null) return foundTarget
            }
        }

        for (child in root.children) {
            if (child.name == "/" || child.name == "root") {
                val found = findTouchNodeInTree(child, nodeName, fragmentIndex)
                if (found != null) return found
            }
        }

        return null
    }

    /**
     * Updates the spi-max-frequency property on a touch node.
     */
    fun updateTouchSpiFrequency(touchNode: DtsNode, newFrequencyHex: String): Boolean {
        return updateTimingProperty(touchNode, "spi-max-frequency", newFrequencyHex)
    }
}
