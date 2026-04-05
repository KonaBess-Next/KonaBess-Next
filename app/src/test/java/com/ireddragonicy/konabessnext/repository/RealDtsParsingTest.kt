package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RealDtsParsingTest {

    @Test
    fun testParseGpuBinsFromTunaAndSd860Fixtures() {
        listOf("Tuna0.txt", "sd860.txt").forEach { fixture ->
            val content = loadFixture(fixture).readText()
            val root = DtsTreeHelper.parse(content)
            val binNodes = findGpuBinNodes(root)

            assertTrue("$fixture should contain at least one GPU bin node", binNodes.isNotEmpty())
            assertTrue(
                "$fixture should contain at least one GPU level node",
                binNodes.any { bin -> bin.children.any { it.name.startsWith("qcom,gpu-pwrlevel@") } }
            )
        }
    }

    @Test
    fun testAstEditingAndRoundTripOnTunaAndSd860Fixtures() {
        val expectedHex = "0x3b9ac9ff"

        listOf("Tuna0.txt", "sd860.txt").forEach { fixture ->
            val originalRoot = DtsTreeHelper.parse(loadFixture(fixture).readText())
            val bins = findGpuBinNodes(originalRoot)
            assertTrue("$fixture should have GPU bins for AST edit test", bins.isNotEmpty())

            val targetBin = bins.firstOrNull { bin ->
                bin.children.any { level ->
                    level.name.startsWith("qcom,gpu-pwrlevel@") && level.getProperty("qcom,gpu-freq") != null
                }
            } ?: throw AssertionError("Could not find target GPU bin in $fixture")

            val targetLevel = targetBin.children.firstOrNull { level ->
                level.name.startsWith("qcom,gpu-pwrlevel@") && level.getProperty("qcom,gpu-freq") != null
            } ?: throw AssertionError("Could not find target GPU level in ${targetBin.name} for $fixture")

            targetLevel.setProperty("qcom,gpu-freq", "999999999")

            val generated = DtsTreeHelper.generate(originalRoot)
            assertTrue(
                "$fixture generated output should contain updated hex value",
                generated.contains(expectedHex)
            )

            val reparsedRoot = DtsTreeHelper.parse(generated)
            val reparsedBin = findGpuBinNodes(reparsedRoot).firstOrNull { it.name == targetBin.name }
                ?: throw AssertionError("Could not find bin ${targetBin.name} after round-trip in $fixture")
            val reparsedLevel = reparsedBin.children.firstOrNull { it.name == targetLevel.name }
                ?: throw AssertionError("Could not find level ${targetLevel.name} after round-trip in $fixture")
            val reparsedProp = reparsedLevel.getProperty("qcom,gpu-freq")
                ?: throw AssertionError("qcom,gpu-freq missing after round-trip in $fixture")

            assertEquals(
                "$fixture should keep updated frequency after parse/generate/parse",
                "<$expectedHex>",
                reparsedProp.originalValue
            )
        }
    }

    private fun loadFixture(fileName: String): File {
        val moduleRelative = File("src/test/$fileName")
        if (moduleRelative.exists()) return moduleRelative

        val projectRelative = File("app/src/test/$fileName")
        if (projectRelative.exists()) return projectRelative

        throw AssertionError("Fixture $fileName not found in src/test or app/src/test")
    }

    private fun findGpuBinNodes(root: DtsNode): List<DtsNode> {
        val results = ArrayList<DtsNode>()
        fun recurse(node: DtsNode) {
            val compatible = node.getProperty("compatible")?.originalValue
            val isCompatibleBin =
                compatible?.contains("qcom,gpu-pwrlevels") == true && compatible.contains("bins").not()
            val isNameMatch = node.name.startsWith("qcom,gpu-pwrlevels")
            if (isCompatibleBin || isNameMatch) {
                results.add(node)
                return
            }
            node.children.forEach(::recurse)
        }
        recurse(root)
        return results
    }
}
