package com.ireddragonicy.konabessnext.domain

import com.ireddragonicy.konabessnext.model.display.TouchPanel
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TouchDomainManager @Inject constructor() {
    fun findTouchPanels(root: DtsNode): List<TouchPanel> {
        val panels = ArrayList<TouchPanel>()
        for (fragment in root.children) {
            if (!DtboDomainUtils.isFragmentNode(fragment)) continue
            val fragmentIndex = DtboDomainUtils.parseFragmentIndex(fragment.name)
            val overlay = fragment.getChild("__overlay__") ?: continue

            fun recurseSearch(node: DtsNode) {
                if (node.getProperty("spi-max-frequency") != null) {
                    val compatible = node.getProperty("compatible")?.originalValue?.removeSurrounding("\"") ?: ""
                    if (!node.name.lowercase().contains("ir-spi")) {
                        val freq = DtboDomainUtils.extractSingleLong(node.getProperty("spi-max-frequency")?.originalValue ?: "0")
                        panels.add(TouchPanel(fragmentIndex, node.name, compatible, freq))
                    }
                }
                node.children.forEach { recurseSearch(it) }
            }
            recurseSearch(overlay)
        }

        root.children.filter { it.name == "/" || it.name == "root" }.forEach { 
            panels.addAll(findTouchPanels(it)) 
        }
        return panels.distinctBy { it.nodeName to it.fragmentIndex }
    }

    fun findTouchNodeInTree(root: DtsNode, nodeName: String, fragmentIndex: Int = -1): DtsNode? {
        for (child in root.children) {
            if (DtboDomainUtils.isFragmentNode(child)) {
                if (fragmentIndex >= 0 && DtboDomainUtils.parseFragmentIndex(child.name) != fragmentIndex) continue
                var found: DtsNode? = null
                fun search(n: DtsNode) {
                    if (found != null) return
                    if (n.name == nodeName && n.getProperty("spi-max-frequency") != null) found = n
                    n.children.forEach { search(it) }
                }
                search(child.getChild("__overlay__") ?: continue)
                if (found != null) return found
            }
        }
        for (child in root.children) {
            if (child.name == "/" || child.name == "root") {
                findTouchNodeInTree(child, nodeName, fragmentIndex)?.let { return it }
            }
        }
        return null
    }

    fun updateTouchSpiFrequency(touchNode: DtsNode, newFrequency: String): Boolean {
        return DtboDomainUtils.updateNodePropertyHexSafe(touchNode, "spi-max-frequency", newFrequency)
    }
}
