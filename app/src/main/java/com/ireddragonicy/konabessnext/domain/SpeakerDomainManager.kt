package com.ireddragonicy.konabessnext.domain

import com.ireddragonicy.konabessnext.model.display.SpeakerPanel
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeakerDomainManager @Inject constructor() {
    fun findSpeakerPanels(root: DtsNode): List<SpeakerPanel> {
        val panels = ArrayList<SpeakerPanel>()
        for (fragment in root.children) {
            if (!DtboDomainUtils.isFragmentNode(fragment)) continue
            val fragmentIndex = DtboDomainUtils.parseFragmentIndex(fragment.name)
            
            fun recurseSearch(node: DtsNode) {
                val compatibleStr = node.getProperty("compatible")?.originalValue?.removeSurrounding("\"") ?: ""
                if (compatibleStr.contains("awinic,aw882xx_smartpa", ignoreCase = true)) {
                    val min = DtboDomainUtils.extractSingleLong(node.getProperty("aw-re-min")?.originalValue ?: "0")
                    val max = DtboDomainUtils.extractSingleLong(node.getProperty("aw-re-max")?.originalValue ?: "0")
                    panels.add(SpeakerPanel(fragmentIndex, node.name, compatibleStr, min, max))
                }
                node.children.forEach { recurseSearch(it) }
            }
            recurseSearch(fragment.getChild("__overlay__") ?: continue)
        }
        
        root.children.filter { it.name == "/" || it.name == "root" }.forEach { 
            panels.addAll(findSpeakerPanels(it)) 
        }
        return panels.distinctBy { it.nodeName to it.fragmentIndex }
    }

    fun findSpeakerNodeInTree(root: DtsNode, nodeName: String, fragmentIndex: Int): DtsNode? {
        fun search(curr: DtsNode): DtsNode? {
            if (curr.name == nodeName) return curr
            curr.children.forEach { search(it)?.let { res -> return res } }
            return null
        }

        if (fragmentIndex == -1) {
            // Search in root hierarchy directly
            root.children.filter { it.name == "/" || it.name == "root" }.forEach { 
                search(it)?.let { res -> return res }
            }
            return null
        }

        // Robustly search fragments
        for (fragment in root.children) {
            if (DtboDomainUtils.isFragmentNode(fragment)) {
                val idx = DtboDomainUtils.parseFragmentIndex(fragment.name)
                if (idx == fragmentIndex) {
                    val overlay = fragment.getChild("__overlay__") ?: continue
                    search(overlay)?.let { return it }
                }
            }
        }
        
        // FIX: Recursively search inside "/" or "root" nodes if not found at top level
        for (child in root.children) {
            if (child.name == "/" || child.name == "root") {
                findSpeakerNodeInTree(child, nodeName, fragmentIndex)?.let { return it }
            }
        }
        
        return null
    }

    fun updateSpeakerReBounds(node: DtsNode, min: String, max: String): Boolean {
        val s1 = DtboDomainUtils.updateNodePropertyHexSafe(node, "aw-re-min", min)
        val s2 = DtboDomainUtils.updateNodePropertyHexSafe(node, "aw-re-max", max)
        return s1 && s2
    }
}
