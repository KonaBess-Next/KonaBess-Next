package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DtsParserTest {
    @Test
    fun testParseRootProperties() {
        val dts = """
/dts-v1/;

/ {
	model = "Qualcomm Technologies, Inc. kona-7230-iot v2.1 SoC";
	compatible = "qcom,kona-iot";
	qcom,msm-id = <0x224 0x20001>;
	interrupt-parent = <0x1>;
	#address-cells = <0x2>;
	#size-cells = <0x2>;
	qcom,board-id = <0x0 0x0>;

    memory {
        device_type = "memory";
    };
};
        """.trimIndent()

        val root = DtsTreeHelper.parse(dts)
        assertNotNull(root)
        
        // Root should be dummy with one child "/"
        assertEquals(1, root.children.size)
        
        val slashNode = root.children[0]
        assertEquals("/", slashNode.name)
        
        // Check properties
        // We expect: model, compatible, qcom,msm-id, interrupt-parent, #address-cells, #size-cells, qcom,board-id
        // Total 7 properties.
        assertEquals("Properties count mismatch", 7, slashNode.properties.size)
        
        val propModel = slashNode.properties.find { it.name == "model" }
        assertNotNull("model property missing", propModel)
        assertEquals("\"Qualcomm Technologies, Inc. kona-7230-iot v2.1 SoC\"", propModel?.originalValue)
        
        val propAddr = slashNode.properties.find { it.name == "#address-cells" }
        assertNotNull("#address-cells property missing", propAddr)
        assertEquals("<0x2>", propAddr?.originalValue)
        
        // Check children
        assertEquals("Children count mismatch", 1, slashNode.children.size)
        assertEquals("memory", slashNode.children[0].name)
    }
}
