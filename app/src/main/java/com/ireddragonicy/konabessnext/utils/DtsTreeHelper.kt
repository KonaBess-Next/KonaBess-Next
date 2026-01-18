package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import java.util.Stack

object DtsTreeHelper {

    /**
     * Parses a raw DTS string into a root DtsNode.
     */
    fun parse(rawDts: String?): DtsNode {
        // Create an invisible root holder
        val root = DtsNode("root")
        root.isExpanded = true // Root must always be expanded
        if (rawDts.isNullOrEmpty()) return root

        // Strip comments first to avoid parsing errors
        var dts = rawDts.replace("//.*".toRegex(), "")
        // Remove /* */ comments
        dts = dts.replace("/\\*.*?\\*/".toRegex(), "")

        val stack = Stack<DtsNode>()
        stack.push(root)

        val chars = dts.toCharArray()
        val buffer = StringBuilder()

        var i = 0
        val len = chars.size

        while (i < len) {
            val c = chars[i]

            if (c == '{') {
                // Start of a node
                val label = buffer.toString().trim()
                val newNode = DtsNode(label)
                if (!stack.isEmpty()) {
                    stack.peek().addChild(newNode)
                }
                stack.push(newNode)
                buffer.setLength(0) // clear buffer
                i++
            } else if (c == '}') {
                // End of node
                if (!stack.isEmpty() && stack.size > 1) {
                    stack.pop()
                }

                // Peek next to consume ';'
                var next = i + 1
                while (next < len && Character.isWhitespace(chars[next])) next++
                if (next < len && chars[next] == ';') {
                    i = next // Skip the ;
                }
                buffer.setLength(0) // clear buffer
                i++
            } else if (c == ';') {
                // End of a property statement
                val statement = buffer.toString().trim()
                if (statement.isNotEmpty()) {
                    val eqIndex = statement.indexOf('=')
                    if (eqIndex != -1) {
                        val key = statement.substring(0, eqIndex).trim()
                        val `val` = statement.substring(eqIndex + 1).trim()
                        if (!stack.isEmpty()) {
                            stack.peek().addProperty(DtsProperty(key, `val`))
                        }
                    } else {
                        // Boolean property
                        if (!stack.isEmpty()) {
                            stack.peek().addProperty(DtsProperty(statement, ""))
                        }
                    }
                }
                buffer.setLength(0)
                i++
            } else {
                buffer.append(c)
                i++
            }
        }

        return root
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
        for (child in root.children) {
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
            if (!prop.originalValue.isNullOrEmpty()) {
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
