package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.DtsNode

/**
 * Text-level DTS patcher that modifies properties in raw DTS lines
 * without destroying formatting or comments.
 *
 * Uses the AST node hierarchy to locate the correct text block,
 * with DTBO-aware fallback for fragment number mismatches.
 */
object DtsTextPatcher {

    /**
     * Patches (replaces or inserts) a property in the raw DTS text.
     *
     * @return The modified lines, or the original list if no change was needed.
     */
    fun patchProperty(
        lines: List<String>,
        targetNode: DtsNode,
        propertyName: String,
        newValue: String
    ): List<String> {
        val path = buildPath(targetNode)
        if (path.isEmpty()) return lines

        val (start, end) = resolveBlock(lines, path, propertyName) ?: return lines
        val propLine = findPropertyLine(lines, start, end, propertyName)
        val indent = inferIndent(lines, start)
        val formatted = "$indent$propertyName = $newValue;"

        if (propLine != -1) {
            if (lines[propLine].trim() == formatted.trim()) return lines
            return lines.toMutableList().apply { this[propLine] = formatted }
        }

        val insertAt = findInsertPosition(lines, start, end)
        return lines.toMutableList().apply { add(insertAt, formatted) }
    }

    // ── Block Resolution ───────────────────────────────────────────────

    /**
     * Resolves the best text block for the given AST path.
     * Prefers a block that already contains the target property (for in-place
     * replacement); otherwise returns the largest block (for new insertions).
     */
    private fun resolveBlock(
        lines: List<String>,
        path: List<String>,
        propertyName: String
    ): Pair<Int, Int>? {
        val blocks = findMatchingBlocks(lines, path)
        if (blocks.isEmpty()) return null
        return blocks.firstOrNull { findPropertyLine(lines, it.first, it.second, propertyName) != -1 }
            ?: blocks.maxByOrNull { it.second - it.first }
    }

    /**
     * Finds all text blocks matching the path hierarchy in a single pass.
     *
     * `fragment@N` names are wildcarded to handle DTBO files where the AST
     * fragment index may differ from the text (e.g., stub vs. full definition).
     */
    private fun findMatchingBlocks(lines: List<String>, path: List<String>): List<Pair<Int, Int>> {
        val results = mutableListOf<Pair<Int, Int>>()
        var pathIdx = 0
        var depth = 0
        var targetStart = -1
        val matchedDepths = mutableListOf<Int>()

        for (i in lines.indices) {
            val line = lines[i]
            val opens = line.count { it == '{' }
            val closes = line.count { it == '}' }

            // Process closing braces first (exit blocks before entering new ones)
            if (closes > 0) {
                depth -= closes
                while (matchedDepths.isNotEmpty() && depth < matchedDepths.last()) {
                    matchedDepths.removeLast()
                    pathIdx--
                    if (pathIdx == path.size - 1 && targetStart != -1) {
                        results.add(targetStart to (i + 1))
                        targetStart = -1
                    }
                }
            }

            // Process opening braces (enter new blocks)
            if (opens > 0 && pathIdx < path.size) {
                val token = line.trim().substringBefore("{").trim()
                    .split("\\s+".toRegex()).lastOrNull().orEmpty()
                if (matchesPathElement(token, path[pathIdx])) {
                    matchedDepths.add(depth + 1)
                    pathIdx++
                    if (pathIdx == path.size) targetStart = i
                }
            }
            if (opens > 0) depth += opens
        }
        return results
    }

    /** `fragment@N` tokens wildcard-match any fragment number. */
    private fun matchesPathElement(token: String, expected: String): Boolean =
        token == expected ||
            (expected.startsWith("fragment@") && token.startsWith("fragment@"))

    // ── Property Search ────────────────────────────────────────────────

    /**
     * Finds a property line at the node's direct level (depth 1 inside the block).
     * @return line index, or -1 if not found.
     */
    private fun findPropertyLine(
        lines: List<String>,
        blockStart: Int,
        blockEnd: Int,
        propertyName: String
    ): Int {
        var depth = 0
        for (i in blockStart until blockEnd) {
            val line = lines[i]
            depth += line.count { it == '{' } - line.count { it == '}' }
            // Direct child: depth 1, no structural braces on this line
            if (depth == 1 && '{' !in line && '}' !in line) {
                if (matchesPropertyName(line.trim(), propertyName)) return i
            }
        }
        return -1
    }

    /**
     * Checks if a trimmed line starts with the property name followed by
     * whitespace (space/tab), '=', ';', or end-of-line.
     */
    private fun matchesPropertyName(trimmed: String, name: String): Boolean {
        if (!trimmed.startsWith(name)) return false
        if (trimmed.length == name.length) return true
        val c = trimmed[name.length]
        return c.isWhitespace() || c == '=' || c == ';'
    }

    // ── Insertion Helpers ──────────────────────────────────────────────

    /**
     * Inserts before the first child block, or before the closing brace.
     */
    private fun findInsertPosition(lines: List<String>, blockStart: Int, blockEnd: Int): Int {
        var depth = 0
        for (i in blockStart + 1 until blockEnd) {
            if (depth == 0 && lines[i].trim().endsWith("{")) {
                var pos = i
                while (pos > blockStart + 1 && lines[pos - 1].isBlank()) pos--
                return pos
            }
            depth += lines[i].count { it == '{' } - lines[i].count { it == '}' }
        }
        return blockEnd - 1
    }

    /** Infers indentation from the first non-blank line inside a block. */
    private fun inferIndent(lines: List<String>, blockStart: Int): String =
        lines.getOrNull(blockStart + 1)
            ?.takeIf { it.isNotBlank() }
            ?.takeWhile { it.isWhitespace() }
            ?: "\t\t"

    /** Builds the AST path from a node up to (but not including) the root. */
    private fun buildPath(node: DtsNode): List<String> {
        val path = mutableListOf<String>()
        var curr: DtsNode? = node
        while (curr != null && curr.name != "root" && curr.name != "/") {
            path.add(0, curr.name)
            curr = curr.parent
        }
        return path
    }
}
