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
    @Test
    fun testAstEditingAndRegeneration() {
        var file = File("src/test/Tuna0.txt")
        if (!file.exists()) {
            file = File("app/src/test/Tuna0.txt")
        }
        val content = file.readText()
        val root = DtsTreeHelper.parse(content)
        
        fun findBin0(node: com.ireddragonicy.konabessnext.model.dts.DtsNode): com.ireddragonicy.konabessnext.model.dts.DtsNode? {
             if (node.name.startsWith("qcom,gpu-pwrlevels") && node.name.endsWith("0")) return node
             for (child in node.children) {
                 val found = findBin0(child)
                 if (found != null) return found
             }
             return null
        }
        
        val bin0 = findBin0(root) ?: throw AssertionError("Could not find bin 0 in original parse")
        
        val level0 = bin0.children.find { it.name == "qcom,gpu-pwrlevel@0" } 
            ?: throw AssertionError("Could not find level 0 in bin 0. Children: ${bin0.children.map { it.name }}")
        
        val targetKey = "qcom,gpu-freq"
        val newValue = "999999999" // distinct value
        
        val originalProp = level0.getProperty(targetKey) 
            ?: throw AssertionError("Original prop $targetKey missing in level 0. Props: ${level0.properties.map { it.name }}")
        
        level0.setProperty(targetKey, newValue)
        
        val generated = DtsTreeHelper.generate(root)
        
        // 4. Verify in String
        // It should be converted to hex: 999999999 -> 0x3b9ac9ff
        val expectedHex = "0x3b9ac9ff"
        assertTrue("Generated string should contain new hex value", generated.contains(expectedHex))
        
        // 5. Parse again and verify
        val newRoot = DtsTreeHelper.parse(generated)
        val newBin0 = findBin0(newRoot)
        assertTrue("Should find new bin 0", newBin0 != null)
        
        val newLevel0 = newBin0!!.children.find { it.name == "qcom,gpu-pwrlevel@0" }
        val newProp = newLevel0!!.getProperty(targetKey)
        
        assertTrue("New prop should exist", newProp != null)
        assertEquals("New value should be hex formatted", "<$expectedHex>", newProp!!.originalValue)
        
        println("AST Editing Verification Passed!")
    }

    private fun assertEquals(msg: String, expected: Any, actual: Any) {
         if (expected != actual) {
             throw AssertionError("$msg. Expected <$expected> but was <$actual>")
         }
    }
}
