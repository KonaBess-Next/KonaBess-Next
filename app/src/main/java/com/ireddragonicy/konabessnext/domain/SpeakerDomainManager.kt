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
        val overlay = root.getChild("fragment@$fragmentIndex")?.getChild("__overlay__") ?: return null
        fun search(curr: DtsNode): DtsNode? {
            if (curr.name == nodeName) return curr
            curr.children.forEach { search(it)?.let { res -> return res } }
            return null
        }
        return search(overlay)
    }

    fun updateSpeakerReBounds(node: DtsNode, min: String, max: String): Boolean {
        val s1 = DtboDomainUtils.updateNodePropertyHexSafe(node, "aw-re-min", min)
        val s2 = DtboDomainUtils.updateNodePropertyHexSafe(node, "aw-re-max", max)
        return s1 && s2
    }
}
