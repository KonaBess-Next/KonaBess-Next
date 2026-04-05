package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.DtsNode

fun main() {
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

    try {
        println("Parsing DTS...")
        val root = DtsTreeHelper.parse(dts)
        println("Root Name: " + root.name)
        println("Root Children: " + root.children.size)
        
        val slashNode = root.children.firstOrNull()
        if (slashNode != null) {
            println("Slash Node Name: " + slashNode.name)
            println("Slash Node Properties: " + slashNode.properties.size)
            for (prop in slashNode.properties) {
                println(" - Prop: ${prop.name} = ${prop.originalValue}")
            }
            println("Slash Node Children: " + slashNode.children.size)
            for (child in slashNode.children) {
                println(" - Child: ${child.name}")
            }
        } else {
            println("Error: / node not found")
        }
    } catch (e: Exception) {
         e.printStackTrace()
    }
}
