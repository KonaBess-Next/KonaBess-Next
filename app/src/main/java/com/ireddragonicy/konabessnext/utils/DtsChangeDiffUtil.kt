package com.ireddragonicy.konabessnext.utils

object DtsChangeDiffUtil {

    private const val GENERAL_BIN_ID = -1
    private const val GENERAL_BIN_INDEX = -1
    private const val MAX_LINE_DIFF_ITEMS = 120
    private const val MAX_EXACT_LCS_CELLS = 750_000L
    private const val RESYNC_LOOKAHEAD = 24
    private val GPU_MODEL_REGEX = Regex("""^\s*qcom,gpu-model\s*=\s*"(.*)"\s*;.*$""")

    fun calculateGeneralDiff(
        originalLines: List<String>,
        currentLines: List<String>
    ): BinDiffResult? {
        if (originalLines == currentLines) return null

        val changes = ArrayList<DiffNode>()

        val oldGpuName = extractGpuModelName(originalLines)
        val newGpuName = extractGpuModelName(currentLines)
        if (oldGpuName != newGpuName && (!oldGpuName.isNullOrBlank() || !newGpuName.isNullOrBlank())) {
            changes.add(
                DiffNode(
                    type = DiffType.MODIFIED,
                    binIndex = GENERAL_BIN_INDEX,
                    levelIndex = -1,
                    oldDescription = "GPU Name: ${oldGpuName ?: "(none)"}",
                    newDescription = "GPU Name: ${newGpuName ?: "(none)"}"
                )
            )
        }

        val lineChanges = calculateLineDiffNodes(originalLines, currentLines, MAX_LINE_DIFF_ITEMS)
        changes.addAll(lineChanges)

        if (changes.isEmpty()) return null

        return BinDiffResult(
            binIndex = GENERAL_BIN_INDEX,
            binId = GENERAL_BIN_ID,
            changes = changes
        )
    }

    private fun calculateLineDiffNodes(
        oldLines: List<String>,
        newLines: List<String>,
        limit: Int
    ): List<DiffNode> {
        if (limit <= 0) return emptyList()
        if (oldLines.isEmpty() && newLines.isEmpty()) return emptyList()

        return if (shouldUseExactLcs(oldLines.size, newLines.size)) {
            calculateLineDiffNodesByLcs(oldLines, newLines, limit)
        } else {
            calculateLineDiffNodesWindowed(oldLines, newLines, limit)
        }
    }

    private fun calculateLineDiffNodesByLcs(
        oldLines: List<String>,
        newLines: List<String>,
        limit: Int
    ): List<DiffNode> {
        val matches = computeLcsMatchesSafe(oldLines, newLines)
        val nodes = ArrayList<DiffNode>()

        var oldCursor = 0
        var newCursor = 0

        for ((oldMatch, newMatch) in matches) {
            appendChangedRun(
                output = nodes,
                oldLines = oldLines,
                newLines = newLines,
                oldStart = oldCursor,
                oldEndExclusive = oldMatch,
                newStart = newCursor,
                newEndExclusive = newMatch,
                limit = limit
            )
            if (nodes.size >= limit) break
            oldCursor = oldMatch + 1
            newCursor = newMatch + 1
        }

        if (nodes.size < limit) {
            appendChangedRun(
                output = nodes,
                oldLines = oldLines,
                newLines = newLines,
                oldStart = oldCursor,
                oldEndExclusive = oldLines.size,
                newStart = newCursor,
                newEndExclusive = newLines.size,
                limit = limit
            )
        }

        return if (nodes.size <= limit) {
            nodes
        } else {
            nodes.take(limit)
        }
    }

    private fun calculateLineDiffNodesWindowed(
        oldLines: List<String>,
        newLines: List<String>,
        limit: Int
    ): List<DiffNode> {
        val nodes = ArrayList<DiffNode>()
        var oldIndex = 0
        var newIndex = 0

        while (oldIndex < oldLines.size && newIndex < newLines.size && nodes.size < limit) {
            if (oldLines[oldIndex] == newLines[newIndex]) {
                oldIndex++
                newIndex++
                continue
            }

            val oldAnchor = findWithinWindow(
                lines = oldLines,
                needle = newLines[newIndex],
                start = oldIndex + 1
            )
            val newAnchor = findWithinWindow(
                lines = newLines,
                needle = oldLines[oldIndex],
                start = newIndex + 1
            )

            when {
                oldAnchor != -1 && (newAnchor == -1 || (oldAnchor - oldIndex) <= (newAnchor - newIndex)) -> {
                    appendChangedRun(
                        output = nodes,
                        oldLines = oldLines,
                        newLines = newLines,
                        oldStart = oldIndex,
                        oldEndExclusive = oldAnchor,
                        newStart = newIndex,
                        newEndExclusive = newIndex,
                        limit = limit
                    )
                    oldIndex = oldAnchor
                }
                newAnchor != -1 -> {
                    appendChangedRun(
                        output = nodes,
                        oldLines = oldLines,
                        newLines = newLines,
                        oldStart = oldIndex,
                        oldEndExclusive = oldIndex,
                        newStart = newIndex,
                        newEndExclusive = newAnchor,
                        limit = limit
                    )
                    newIndex = newAnchor
                }
                else -> {
                    appendChangedRun(
                        output = nodes,
                        oldLines = oldLines,
                        newLines = newLines,
                        oldStart = oldIndex,
                        oldEndExclusive = oldIndex + 1,
                        newStart = newIndex,
                        newEndExclusive = newIndex + 1,
                        limit = limit
                    )
                    oldIndex++
                    newIndex++
                }
            }
        }

        if (nodes.size < limit && (oldIndex < oldLines.size || newIndex < newLines.size)) {
            appendChangedRun(
                output = nodes,
                oldLines = oldLines,
                newLines = newLines,
                oldStart = oldIndex,
                oldEndExclusive = oldLines.size,
                newStart = newIndex,
                newEndExclusive = newLines.size,
                limit = limit
            )
        }

        return if (nodes.size <= limit) {
            nodes
        } else {
            nodes.take(limit)
        }
    }

    private fun findWithinWindow(
        lines: List<String>,
        needle: String,
        start: Int
    ): Int {
        val safeStart = start.coerceAtLeast(0)
        val endExclusive = minOf(lines.size, safeStart + RESYNC_LOOKAHEAD)
        for (index in safeStart until endExclusive) {
            if (lines[index] == needle) return index
        }
        return -1
    }

    private fun shouldUseExactLcs(oldSize: Int, newSize: Int): Boolean {
        val cells = (oldSize.toLong() + 1L) * (newSize.toLong() + 1L)
        return cells in 1L..MAX_EXACT_LCS_CELLS
    }

    private fun appendChangedRun(
        output: MutableList<DiffNode>,
        oldLines: List<String>,
        newLines: List<String>,
        oldStart: Int,
        oldEndExclusive: Int,
        newStart: Int,
        newEndExclusive: Int,
        limit: Int
    ) {
        val oldCount = (oldEndExclusive - oldStart).coerceAtLeast(0)
        val newCount = (newEndExclusive - newStart).coerceAtLeast(0)
        val pairCount = minOf(oldCount, newCount)

        var index = 0
        while (index < pairCount && output.size < limit) {
            val oldLineIndex = oldStart + index
            val newLineIndex = newStart + index
            output.add(
                DiffNode(
                    type = DiffType.MODIFIED,
                    binIndex = GENERAL_BIN_INDEX,
                    levelIndex = newLineIndex + 1,
                    oldDescription = "Line ${oldLineIndex + 1}: ${compactLine(oldLines[oldLineIndex])}",
                    newDescription = "Line ${newLineIndex + 1}: ${compactLine(newLines[newLineIndex])}"
                )
            )
            index++
        }

        index = pairCount
        while (index < oldCount && output.size < limit) {
            val oldLineIndex = oldStart + index
            output.add(
                DiffNode(
                    type = DiffType.REMOVED,
                    binIndex = GENERAL_BIN_INDEX,
                    levelIndex = oldLineIndex + 1,
                    oldDescription = "Line ${oldLineIndex + 1}: ${compactLine(oldLines[oldLineIndex])}",
                    newDescription = null
                )
            )
            index++
        }

        index = pairCount
        while (index < newCount && output.size < limit) {
            val newLineIndex = newStart + index
            output.add(
                DiffNode(
                    type = DiffType.ADDED,
                    binIndex = GENERAL_BIN_INDEX,
                    levelIndex = newLineIndex + 1,
                    oldDescription = null,
                    newDescription = "Line ${newLineIndex + 1}: ${compactLine(newLines[newLineIndex])}"
                )
            )
            index++
        }
    }

    private fun computeLcsMatchesSafe(oldLines: List<String>, newLines: List<String>): List<Pair<Int, Int>> {
        val oldSize = oldLines.size
        val newSize = newLines.size
        val cells = (oldSize.toLong() + 1L) * (newSize.toLong() + 1L)
        if (cells > MAX_EXACT_LCS_CELLS) {
            return emptyList()
        }

        val dp = Array(oldSize + 1) { IntArray(newSize + 1) }

        for (oldIndex in oldSize - 1 downTo 0) {
            for (newIndex in newSize - 1 downTo 0) {
                dp[oldIndex][newIndex] = if (oldLines[oldIndex] == newLines[newIndex]) {
                    dp[oldIndex + 1][newIndex + 1] + 1
                } else {
                    maxOf(dp[oldIndex + 1][newIndex], dp[oldIndex][newIndex + 1])
                }
            }
        }

        val matches = ArrayList<Pair<Int, Int>>()
        var oldIndex = 0
        var newIndex = 0
        while (oldIndex < oldSize && newIndex < newSize) {
            if (oldLines[oldIndex] == newLines[newIndex]) {
                matches.add(oldIndex to newIndex)
                oldIndex++
                newIndex++
            } else if (dp[oldIndex + 1][newIndex] >= dp[oldIndex][newIndex + 1]) {
                oldIndex++
            } else {
                newIndex++
            }
        }
        return matches
    }

    private fun extractGpuModelName(lines: List<String>): String? {
        for (line in lines) {
            val match = GPU_MODEL_REGEX.matchEntire(line) ?: continue
            return match.groupValues.getOrNull(1)?.trim()
        }
        return null
    }

    private fun compactLine(raw: String, maxChars: Int = 110): String {
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        if (cleaned.length <= maxChars) return cleaned
        return "${cleaned.take(maxChars - 3)}..."
    }
}
