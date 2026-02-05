package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import java.util.Stack

object DtsTreeHelper {

    /**
     * Parses a raw DTS string into a root DtsNode using the new Recursive Descent Parser.
     */
    fun parse(rawDts: String?): DtsNode {
        if (rawDts.isNullOrEmpty()) {
            val root = DtsNode("root")
            root.isExpanded = true
            return root
        }

        try {
            val lexer = DtsLexer(rawDts)
            val tokens = lexer.tokenize()
            val parser = DtsParser(tokens)
            return parser.parse()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback or returned failed root?
            // For now return a dummy root with error or empty
            val root = DtsNode("root")
            root.isExpanded = true
            // Maybe add a property saying "Parse Error"
            root.addProperty(DtsProperty("error", "Failed to parse: ${e.message}"))
            return root
        }
    }

    /**
     * Generates DTS string from the node tree.
     */
    fun generate(root: DtsNode): String {
        val sb = StringBuilder()

        // Always add the DTS version header
        sb.append("/dts-v1/;\n\n")

        // Root usually has children which are the top-level nodes of the file.
        // If root itself is "root" (our dummy), we iterate its children.
        val startNodes = if (root.name == "root") root.children else listOf(root)

        for (child in startNodes) {
            generateNode(sb, child, "")
            sb.append("\n")
        }

        return sb.toString().trim()
    }

    private fun generateNode(sb: StringBuilder, node: DtsNode, indent: String) {
        // Node Header: name {
        sb.append(indent).append(node.name).append(" {\n")

        val childIndent = "$indent\t"

        // Properties
        for (prop in node.properties) {
            sb.append(childIndent).append(prop.name)
            if (prop.originalValue.isNotEmpty()) {
                sb.append(" = ").append(prop.originalValue)
            }
            sb.append(";\n")
        }

        // Children
        for (child in node.children) {
            generateNode(sb, child, childIndent)
        }

        // Node Footer: };
        sb.append(indent).append("};\n")
    }
}
