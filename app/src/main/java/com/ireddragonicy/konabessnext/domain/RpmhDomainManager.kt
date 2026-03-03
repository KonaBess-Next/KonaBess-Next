package com.ireddragonicy.konabessnext.domain

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.power.RpmhRegulator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RpmhDomainManager @Inject constructor() {

    companion object {
        private val RPMH_COMPATIBLE_PATTERNS = listOf(
            "qcom,rpmh-vrm-regulator",
            "qcom,rpmh-arc-regulator"
        )
        private const val PROP_REGULATOR_NAME = "regulator-name"
        private const val PROP_MIN_MICROVOLT = "regulator-min-microvolt"
        private const val PROP_MAX_MICROVOLT = "regulator-max-microvolt"
    }

    /**
     * Recursively searches the DTS tree for RPMh regulator nodes and extracts
     * regulator-min-microvolt / regulator-max-microvolt from their children.
     */
    fun findRegulators(root: DtsNode): List<RpmhRegulator> {
        val results = mutableListOf<RpmhRegulator>()
        recurseSearch(root, results)
        return results
    }

    private fun recurseSearch(node: DtsNode, results: MutableList<RpmhRegulator>) {
        val compatible = node.getProperty("compatible")?.originalValue ?: ""

        if (RPMH_COMPATIBLE_PATTERNS.any { compatible.contains(it) }) {
            // This is a parent RPMh regulator node — scan its children for sub-regulators
            for (child in node.children) {
                val regName = child.getProperty(PROP_REGULATOR_NAME)?.originalValue
                    ?.trim()?.removeSurrounding("\"") ?: child.name

                val minProp = child.getProperty(PROP_MIN_MICROVOLT)
                val maxProp = child.getProperty(PROP_MAX_MICROVOLT)

                // Only include sub-nodes that have at least one voltage bound property
                if (minProp != null || maxProp != null) {
                    val minVal = if (minProp != null) {
                        DtboDomainUtils.extractSingleLong(minProp.originalValue)
                    } else 0L

                    val maxVal = if (maxProp != null) {
                        DtboDomainUtils.extractSingleLong(maxProp.originalValue)
                    } else 0L

                    results.add(
                        RpmhRegulator(
                            parentNodeName = node.name,
                            subNodeName = child.name,
                            regulatorName = regName,
                            minMicrovolt = minVal,
                            maxMicrovolt = maxVal
                        )
                    )
                }
            }
        }

        // Continue searching deeper (RPMh nodes may be nested inside soc, rsc, etc.)
        for (child in node.children) {
            recurseSearch(child, results)
        }
    }

    /**
     * Locates a specific sub-node by parent and sub-node names and updates its
     * voltage bounds using hex-safe formatting.
     */
    fun updateRegulatorBounds(
        root: DtsNode,
        parentNodeName: String,
        subNodeName: String,
        newMin: Long,
        newMax: Long
    ): Boolean {
        val subNode = findSubNode(root, parentNodeName, subNodeName) ?: return false

        var changed = false

        val minProp = subNode.getProperty(PROP_MIN_MICROVOLT)
        if (minProp != null) {
            val currentMin = DtboDomainUtils.extractSingleLong(minProp.originalValue)
            if (currentMin != newMin) {
                DtboDomainUtils.updateNodePropertyHexSafe(subNode, PROP_MIN_MICROVOLT, newMin.toString())
                changed = true
            }
        }

        val maxProp = subNode.getProperty(PROP_MAX_MICROVOLT)
        if (maxProp != null) {
            val currentMax = DtboDomainUtils.extractSingleLong(maxProp.originalValue)
            if (currentMax != newMax) {
                DtboDomainUtils.updateNodePropertyHexSafe(subNode, PROP_MAX_MICROVOLT, newMax.toString())
                changed = true
            }
        }

        return changed
    }

    /**
     * Finds a specific sub-node inside an RPMh parent regulator node.
     */
    private fun findSubNode(root: DtsNode, parentNodeName: String, subNodeName: String): DtsNode? {
        val queue = ArrayDeque<DtsNode>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.name == parentNodeName) {
                val compatible = node.getProperty("compatible")?.originalValue ?: ""
                if (RPMH_COMPATIBLE_PATTERNS.any { compatible.contains(it) }) {
                    return node.children.firstOrNull { it.name == subNodeName }
                }
            }
            queue.addAll(node.children)
        }
        return null
    }
}
