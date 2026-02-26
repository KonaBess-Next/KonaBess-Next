package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.domain.SpeakerDomainManager
import com.ireddragonicy.konabessnext.domain.TouchDomainManager
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.repository.DisplayDomainManager

object DtboDiffUtil {
    const val ID_DISPLAY = -100
    const val ID_TOUCH = -200
    const val ID_SPEAKER = -300

    fun calculateDiff(
        oldTree: DtsNode,
        newTree: DtsNode,
        displayMgr: DisplayDomainManager,
        touchMgr: TouchDomainManager,
        speakerMgr: SpeakerDomainManager
    ): List<BinDiffResult> {
        val results = mutableListOf<BinDiffResult>()

        val dispChanges = mutableListOf<DiffNode>()
        val oldDisp = displayMgr.findAllPanels(oldTree).associateBy { it.nodeName }
        val newDisp = displayMgr.findAllPanels(newTree).associateBy { it.nodeName }
        newDisp.forEach { (name, newPanel) ->
            val oldPanel = oldDisp[name]
            if (oldPanel != null) {
                if (oldPanel.dfpsList != newPanel.dfpsList) {
                    dispChanges.add(DiffNode(DiffType.MODIFIED, ID_DISPLAY, 0, "DFPS: ${oldPanel.dfpsList}", "DFPS: ${newPanel.dfpsList}"))
                }
                newPanel.timings.forEachIndexed { i, newTiming ->
                    val oldTiming = oldPanel.timings.getOrNull(i)
                    if (oldTiming != null) {
                        if (oldTiming.panelFramerate != newTiming.panelFramerate) {
                            dispChanges.add(DiffNode(DiffType.MODIFIED, ID_DISPLAY, 0, "$name (${newTiming.timingNodeName})\nFPS: ${oldTiming.panelFramerate}Hz", "FPS: ${newTiming.panelFramerate}Hz"))
                        }
                        if (oldTiming.panelClockRate != newTiming.panelClockRate) {
                            dispChanges.add(DiffNode(DiffType.MODIFIED, ID_DISPLAY, 0, "$name (${newTiming.timingNodeName})\nClock: ${oldTiming.panelClockRate}", "Clock: ${newTiming.panelClockRate}"))
                        }
                    }
                }
            }
        }
        if (dispChanges.isNotEmpty()) results.add(BinDiffResult(ID_DISPLAY, ID_DISPLAY, dispChanges))

        val touchChanges = mutableListOf<DiffNode>()
        val oldTouch = touchMgr.findTouchPanels(oldTree).associateBy { it.nodeName }
        val newTouch = touchMgr.findTouchPanels(newTree).associateBy { it.nodeName }
        newTouch.forEach { (name, newPanel) ->
            val oldPanel = oldTouch[name]
            if (oldPanel != null && oldPanel.spiMaxFrequency != newPanel.spiMaxFrequency) {
                touchChanges.add(DiffNode(DiffType.MODIFIED, ID_TOUCH, 0, "$name\nSPI Freq: ${oldPanel.spiMaxFrequency}", "SPI Freq: ${newPanel.spiMaxFrequency}"))
            }
        }
        if (touchChanges.isNotEmpty()) results.add(BinDiffResult(ID_TOUCH, ID_TOUCH, touchChanges))

        val spkChanges = mutableListOf<DiffNode>()
        val oldSpk = speakerMgr.findSpeakerPanels(oldTree).associateBy { it.nodeName }
        val newSpk = speakerMgr.findSpeakerPanels(newTree).associateBy { it.nodeName }
        newSpk.forEach { (name, newPanel) ->
            val oldPanel = oldSpk[name]
            if (oldPanel != null) {
                if (oldPanel.awReMin != newPanel.awReMin || oldPanel.awReMax != newPanel.awReMax) {
                    spkChanges.add(DiffNode(DiffType.MODIFIED, ID_SPEAKER, 0, "$name\nRE Range: ${oldPanel.awReMin} - ${oldPanel.awReMax}", "RE Range: ${newPanel.awReMin} - ${newPanel.awReMax}"))
                }
            }
        }
        if (spkChanges.isNotEmpty()) results.add(BinDiffResult(ID_SPEAKER, ID_SPEAKER, spkChanges))

        return results
    }
}
