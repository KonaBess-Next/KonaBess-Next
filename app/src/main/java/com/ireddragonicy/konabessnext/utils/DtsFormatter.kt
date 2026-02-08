package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty

/**
 * Auto-formatter for DTS source text.
 *
 * Rules:
 *  - Tab indentation (one tab per nesting level).
 *  - Opening brace `{` on the same line as the node name.
 *  - One property per line, aligned with a single space around `=`.
 *  - Blank line between the last property and the first child node.
 *  - Node closing `};` on its own line at the parent indent.
 */
object DtsFormatter {

    private const val INDENT = "\t"

    /**
     * Formats raw DTS text by parsing it into an AST and regenerating it
     * with consistent style. Returns the original text unchanged if parsing fails.
     */
    fun format(rawDts: String): String {
        if (rawDts.isBlank()) return rawDts

        return try {
            val lexer = DtsLexer(rawDts)
            val tokens = lexer.tokenize()
            val parser = DtsParser(tokens)
            val root = parser.parse()
            generate(root, rawDts)
        } catch (_: Exception) {
            // If anything goes wrong, return original text untouched
            rawDts
        }
    }

    /**
     * Generates formatted DTS text from an AST root node.
     * Preserves the `/dts-v1/;` header if the original text contained it.
     */
    fun generate(root: DtsNode, originalText: String? = null): String {
        val sb = StringBuilder()

        // Preserve /dts-v1/; header if present in original text
        val hasVersionHeader = originalText?.trimStart()?.startsWith("/dts-v1/") == true
        if (hasVersionHeader) {
            sb.append("/dts-v1/;\n\n")
        }

        val startNodes = if (root.name == "root") root.children else listOf(root)

        for ((i, child) in startNodes.withIndex()) {
            generateNode(sb, child, "")
            if (i < startNodes.size - 1) sb.append("\n")
        }

        return sb.toString().trimEnd() + "\n"
    }

    private fun generateNode(sb: StringBuilder, node: DtsNode, indent: String) {
        // Node header: name {
        sb.append(indent).append(node.name).append(" {\n")

        val childIndent = indent + INDENT

        // Properties — one per line
        for (prop in node.properties) {
            appendProperty(sb, prop, childIndent)
        }

        // Separator between properties and child nodes
        if (node.properties.isNotEmpty() && node.children.isNotEmpty()) {
            sb.append("\n")
        }

        // Child nodes
        for ((i, child) in node.children.withIndex()) {
            generateNode(sb, child, childIndent)
            // Blank line between sibling nodes (but not after the last one)
            if (i < node.children.size - 1) {
                sb.append("\n")
            }
        }

        // Closing brace
        sb.append(indent).append("};\n")
    }

    private fun appendProperty(sb: StringBuilder, prop: DtsProperty, indent: String) {
        sb.append(indent).append(prop.name)
        if (prop.originalValue.isNotEmpty()) {
            sb.append(" = ").append(prop.originalValue)
        }
        sb.append(";\n")
    }
}
