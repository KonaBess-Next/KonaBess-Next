package com.ireddragonicy.konabessnext.core.scanner

import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

import kotlinx.coroutines.runBlocking

class ReproTest {
    @Test
    fun testDiscovery() {
        assert(true)
    }

    @Test
    fun testSd860SnippetParsing() = runBlocking {
        val snippet = """
/dts-v1/;
/ {
    model = "Qualcomm Technologies, Inc. SM8150 v2 SoC";
    
    soc {
        qcom,kgsl-3d0@2C00000 {
            compatible = "qcom,kgsl-3d0";
            
            qcom,gpu-pwrlevel-bins {
                compatible = "qcom,gpu-pwrlevel-bins";

                qcom,gpu-pwrlevels-0 {
                    qcom,speed-bin = <0x0>;
                    qcom,initial-pwrlevel = <0x4>;

                    qcom,gpu-pwrlevel@0 {
                        reg = <0x0>;
                        qcom,gpu-freq = <0x22de6440>;
                    };
                    
                    qcom,gpu-pwrlevel@1 {
                        reg = <0x1>;
                        qcom,gpu-freq = <0x1dc13000>;
                    };
                };
            };
        };
    };
};
        """.trimIndent()

        val root = DtsTreeHelper.parse(snippet)
        
        // Debug print
        println("Root children: " + root.children.map { it.name })
        
        // The parser creates a virtual "root" node which contains the DTS root node "/"
        // The "soc" node is a child of "/", not of the virtual root
        val dtsRoot = root.children.find { it.name == "/" }
        assert(dtsRoot != null) { "Expected to find '/' node as a child of virtual root" }
        
        println("DTS root (/) children: " + dtsRoot!!.children.map { it.name })
        
        val soc = dtsRoot.children.find { it.name == "soc" }
        assert(soc != null) { "Expected to find 'soc' node as a child of '/'" }
        
        println("SoC children: " + soc!!.children.map { it.name })
        
        val gpu = soc.children.find { it.name.startsWith("qcom,kgsl-3d0") }
        assert(gpu != null)
        
        println("GPU children: " + gpu!!.children.map { it.name })
        
        val bins = gpu.children.find { it.name == "qcom,gpu-pwrlevel-bins" }
        assert(bins != null)
        
        println("Bins children: " + bins!!.children.map { it.name })
        
        val levels0 = bins.children.find { it.name == "qcom,gpu-pwrlevels-0" }
        assert(levels0 != null)
        
        val levelNodes = levels0!!.children.filter { it.name.startsWith("qcom,gpu-pwrlevel@") }
        println("Found level nodes: " + levelNodes.size)
        
        assertEquals(2, levelNodes.size)
        
        // Run Scanner logic
        val scannerResult = DtsScanner.scan(createTempFileWithContent(snippet), 0)
        println("Scanner Result: isValid=\${scannerResult.isValid}, maxLevels=\${scannerResult.maxLevels}, detectedModel=\${scannerResult.detectedModel}")
        
        assert(scannerResult.isValid)
        assertEquals(2, scannerResult.levelCount)
    }

    fun createTempFileWithContent(content: String): File {
        val file = File.createTempFile("test", ".dts")
        file.writeText(content)
        return file
    }
}
