package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.DtsNode

object DtsTextPatcher {
    /**
     * Patches a property in the raw DTS lines without destroying formatting,
     * following the AST hierarchy to find the exact block.
     * 
     * @param lines The original DTS text lines
     * @param targetNode The AST Node where the property belongs
     * @param propertyName The property to replace or add
     * @param newValue The raw exact new string value to set (e.g. "<0x0 0x0>")
     * @return The patched list of strings, or the original if not found
     */
    fun patchProperty(lines: List<String>, targetNode: DtsNode, propertyName: String, newValue: String): List<String> {
        val path = buildPath(targetNode)
        if (path.isEmpty()) return lines
        
        // Find the node block boundaries
        val blockBounds = findNodeBlock(lines, path)
        if (blockBounds == null) {
            return lines // couldn't find the exact node block
        }
        
        val startLineInclusive = blockBounds.first
        val endLineExclusive = blockBounds.second
        
        val outLines = lines.toMutableList()
        
        var scopeDepth = 0
        var foundLineIndex = -1

        // Search inside the block for the property
        // We only look at lines that don't belong to a sub-block
        // Match property ONLY when we are inside the target node but not inside a sub-node
        // If startLineInclusive is "node {", after parsing that line scopeDepth becomes 1.
        for (i in startLineInclusive until endLineExclusive) {
            val lineStr = lines[i]
            val hasOpen = lineStr.count { it == '{' }
            val hasClose = lineStr.count { it == '}' }
            
            val prevDepth = scopeDepth
            scopeDepth += hasOpen
            scopeDepth -= hasClose

            // A property belonging to THIS node is on a line where we are at depth 1
            // and the line itself doesn't start a new block or end one (unless it's a single line property)
            // Simpler: if we were at depth 1 before or after the line adjustment (depending on style)
            // Standard: prop = val; is at depth 1.
            if (prevDepth == 1 || (prevDepth == 0 && hasOpen > 0 && !lineStr.trim().startsWith("}"))) {
                // If the line has no sub-blocks or we were at depth 1
                // Actually, just check if we are at depth 1 and it's not a node start
                val currentDepth = if (hasOpen > 0) prevDepth + 1 else scopeDepth
                if (currentDepth == 1 && !lineStr.trim().endsWith("{")) {
                    val trimmed = lineStr.trim()
                    if (trimmed.startsWith("$propertyName ") || trimmed.startsWith("$propertyName=") || trimmed.startsWith("$propertyName;")) {
                        foundLineIndex = i
                        break
                    }
                }
            }
        }
        
        val indent = getIndent(outLines[startLineInclusive + 1].takeIf { it.isNotBlank() } ?: "\t\t")
        
        if (foundLineIndex != -1) {
            // Replace existing property
            val oldLine = outLines[foundLineIndex]
            val newLine = "$indent$propertyName = $newValue;"
            if (oldLine.trim() == newLine.trim()) return lines
            outLines[foundLineIndex] = newLine
        } else {
            // Append property right before the first child, or before the closing brace if no children
            var insertPos = -1
            scopeDepth = 0
            for (i in startLineInclusive + 1 until endLineExclusive) {
                 val lineStr = lines[i]
                 val trimmed = lineStr.trim()
                 
                 // if this line opens a child block, insert before it
                 if (scopeDepth == 0 && trimmed.endsWith("{")) {
                     insertPos = i
                     break
                 }
                 
                 val hasOpen = lineStr.contains("{")
                 val hasClose = lineStr.contains("}")
                 if (hasClose) scopeDepth -= lineStr.count { it == '}' }
                 if (hasOpen) scopeDepth += lineStr.count { it == '{' }
            }
            
            if (insertPos != -1) {
                // backtrack to before blank lines leading up to the child block
                while (insertPos > startLineInclusive + 1 && lines[insertPos - 1].trim().isEmpty()) {
                    insertPos--
                }
                outLines.add(insertPos, "$indent$propertyName = $newValue;")
            } else {
                // insert right before the closing brace of the target node
                outLines.add(endLineExclusive - 1, "$indent$propertyName = $newValue;")
            }
        }
        
        return outLines
    }

    private fun getIndent(referenceLine: String): String {
        val whitespace = referenceLine.takeWhile { it.isWhitespace() }
        return if (whitespace.isEmpty()) "\t\t" else whitespace
    }

    private fun buildPath(node: DtsNode): List<String> {
        val path = mutableListOf<String>()
        var curr: DtsNode? = node
        while (curr != null && curr.name != "root" && curr.name != "/") {
            path.add(0, curr.name)
            curr = curr.parent
        }
        return path
    }

    private fun findNodeBlock(lines: List<String>, path: List<String>): Pair<Int, Int>? {
        var currentPathIndex = 0
        var currentScopeDepth = 0
        
        var targetStart = -1
        var targetEnd = -1
        
        // We use a small stack to keep track of matched breadcrumbs
        // When we enter a node, if it matches the next breadcrumb, we advance currentPathIndex
        // We also need to remember what depth a breadcrumb was matched at.
        val matchedDepths = mutableListOf<Int>()
        
        var i = 0
        while (i < lines.size) {
            val lineStr = lines[i]
            val trimmed = lineStr.trim()
            val hasOpen = lineStr.contains("{")
            val hasClose = lineStr.contains("}")
            
            if (hasClose) {
                currentScopeDepth -= lineStr.count { it == '}' }
                if (matchedDepths.isNotEmpty() && currentScopeDepth < matchedDepths.last()) {
                    // we exited a matched block
                    matchedDepths.removeLast()
                    currentPathIndex--
                    
                    if (currentPathIndex == path.size - 1 && targetStart != -1 && targetEnd == -1) {
                        targetEnd = i + 1
                        return Pair(targetStart, targetEnd)
                    }
                }
            }
            
            if (hasOpen) {
                // Look at the node name
                // "fragment@0 {" or "fragment@0{"
                val nodeNameBeforeBrace = trimmed.substringBefore("{").trim()
                if (currentPathIndex < path.size) {
                    val expectedName = path[currentPathIndex]
                    if (nodeNameBeforeBrace == expectedName || nodeNameBeforeBrace.endsWith(" $expectedName") || 
                        lineStr.contains(expectedName) && lineStr.contains("{")) {
                        
                        // We do a naive check: if the line contains our expected name and opens a block
                        // A more robust check is to make sure the token before { matches
                        val tokens = nodeNameBeforeBrace.split("\\s+".toRegex())
                        if (tokens.lastOrNull() == expectedName) {
                            matchedDepths.add(currentScopeDepth + 1)
                            currentPathIndex++
                            if (currentPathIndex == path.size) {
                                targetStart = i
                            }
                        }
                    }
                }
                currentScopeDepth += lineStr.count { it == '{' }
            }
            i++
        }
        
        return null
    }
}
