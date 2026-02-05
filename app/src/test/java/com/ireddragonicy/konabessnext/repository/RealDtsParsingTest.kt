package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RealDtsParsingTest {

    @Test
    fun testTunaBinParsing() {
        var file = File("src/test/Tuna0.txt")
        if (!file.exists()) {
            file = File("app/src/test/Tuna0.txt")
        }
        assertTrue("Tuna0.txt should exist at ${file.absolutePath}", file.exists())
        
        val content = file.readText()
        val root = DtsTreeHelper.parse(content)
        println("Root Children: ${root.children.size}")
        if (root.children.isNotEmpty()) {
             println("Root Child 0 Name: ${root.children[0].name}")
             println("Root Child 0 Children: ${root.children[0].children.size}")
        } else {
             println("Root is empty! Parsing likely failed.")
             // Print error property if exists
             val err = root.getProperty("error")
             if (err != null) println("Error: ${err.originalValue}")
        }
        
        // Emulate findAllBinNodes from GpuDomainManager
        val binNodes = ArrayList<com.ireddragonicy.konabessnext.model.dts.DtsNode>()
        fun recurse(node: com.ireddragonicy.konabessnext.model.dts.DtsNode) {
            val compatible = node.getProperty("compatible")?.originalValue
            val isCompatibleBin = compatible?.contains("qcom,gpu-pwrlevels") == true && compatible?.contains("bins") == false
            val isNameMatch = node.name.startsWith("qcom,gpu-pwrlevels")
            
            if (isCompatibleBin || isNameMatch) {
                binNodes.add(node)
                return 
            }
            node.children.forEach { recurse(it) }
        }
        recurse(root)
        
        println("Found ${binNodes.size} bin nodes")
        binNodes.forEach { println(" - ${it.name} (Compatible: ${it.getProperty("compatible")?.originalValue})") }
        
        // We expect Multi Bin (multiple bins found)
        // Based on Tuna0, we expect qcom,gpu-pwrlevels-0, -1, -2 etc.
        assertTrue("Should find multiple bins, found ${binNodes.size}", binNodes.size > 1)
        
        // Also verify they have levels
        val hasLevels = binNodes.any { bin -> 
            bin.children.any { it.name.startsWith("qcom,gpu-pwrlevel@") }
        }
        assertTrue("Bins should have levels", hasLevels)
        
        // Detailed Value Check (based on Tuna0.txt snippet)
        // qcom,gpu-pwrlevels-0 -> qcom,gpu-pwrlevel@0 -> qcom,gpu-freq = <0x45243200>; (1160000000 Hz)
        // Wait, snippet says:
        // qcom,gpu-pwrlevels-0 ... qcom,gpu-pwrlevel@0 ... qcom,gpu-freq = <0x45243200>;
        // 0x45243200 = 1160000000
        
        val firstBin = binNodes.find { it.name.endsWith("0") }
        assertTrue("Should find bin 0", firstBin != null)
        
        val level0 = firstBin!!.children.find { it.name == "qcom,gpu-pwrlevel@0" }
        assertTrue("Bin 0 should have level 0", level0 != null)
        
        val freqProp = level0!!.getProperty("qcom,gpu-freq")
        assertTrue("Level 0 should have qcom,gpu-freq", freqProp != null)
        println("Level 0 Freq Raw: ${freqProp?.originalValue}")
        
        val freqVal = level0.getLongValue("qcom,gpu-freq")
        println("Level 0 Freq Parsed: $freqVal")
        
        // Assert value is correct (0x45243200 = 1160000000)
        // Note: DtsHelper might return long.
        assertTrue("Frequency should match 1160000000 (0x45243200), got $freqVal", freqVal == 1160000000L)
    }
}
