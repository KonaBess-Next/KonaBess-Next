package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Level

enum class DiffType {
    ADDED,
    REMOVED,
    MODIFIED,
    UNCHANGED
}

data class DiffNode(
    val type: DiffType,
    val binIndex: Int,
    val levelIndex: Int,
    val oldDescription: String?,
    val newDescription: String?
)

data class BinDiffResult(
    val binIndex: Int,
    val binId: Int,
    val changes: List<DiffNode>
)

object BinDiffUtil {

    fun calculateDiff(
        originalBins: List<Bin>,
        currentBins: List<Bin>,
        includeUnchanged: Boolean = false
    ): List<BinDiffResult> {
        if (originalBins.isEmpty() && currentBins.isEmpty()) return emptyList()

        val orderedBinIds = LinkedHashSet<Int>().apply {
            originalBins.forEach { add(it.id) }
            currentBins.forEach { add(it.id) }
        }

        val originalById = originalBins.associateBy { it.id }
        val currentById = currentBins.associateBy { it.id }
        val results = ArrayList<BinDiffResult>(orderedBinIds.size)

        for (binId in orderedBinIds) {
            val oldBin = originalById[binId]
            val newBin = currentById[binId]
            val binIndex = resolveBinIndex(binId, originalBins, currentBins)

            val changes = when {
                oldBin == null && newBin != null -> {
                    newBin.levels.mapIndexed { levelIndex, level ->
                        DiffNode(
                            type = DiffType.ADDED,
                            binIndex = binIndex,
                            levelIndex = levelIndex,
                            oldDescription = null,
                            newDescription = describeLevel(toSnapshot(level))
                        )
                    }
                }
                oldBin != null && newBin == null -> {
                    oldBin.levels.mapIndexed { levelIndex, level ->
                        DiffNode(
                            type = DiffType.REMOVED,
                            binIndex = binIndex,
                            levelIndex = levelIndex,
                            oldDescription = describeLevel(toSnapshot(level)),
                            newDescription = null
                        )
                    }
                }
                oldBin != null && newBin != null -> {
                    diffLevels(
                        binIndex = binIndex,
                        oldLevels = oldBin.levels,
                        newLevels = newBin.levels,
                        includeUnchanged = includeUnchanged
                    )
                }
                else -> emptyList()
            }

            if (includeUnchanged || changes.any { it.type != DiffType.UNCHANGED }) {
                results.add(
                    BinDiffResult(
                        binIndex = binIndex,
                        binId = binId,
                        changes = changes
                    )
                )
            }
        }

        return results
    }

    private fun diffLevels(
        binIndex: Int,
        oldLevels: List<Level>,
        newLevels: List<Level>,
        includeUnchanged: Boolean
    ): List<DiffNode> {
        val oldSnapshots = oldLevels.map(::toSnapshot)
        val newSnapshots = newLevels.map(::toSnapshot)
        val matches = computeLcsMatches(oldSnapshots, newSnapshots)
        val result = ArrayList<DiffNode>(oldSnapshots.size + newSnapshots.size)

        var oldCursor = 0
        var newCursor = 0

        for ((oldMatchIndex, newMatchIndex) in matches) {
            appendChangedRun(
                output = result,
                binIndex = binIndex,
                oldSnapshots = oldSnapshots,
                newSnapshots = newSnapshots,
                oldRange = oldCursor until oldMatchIndex,
                newRange = newCursor until newMatchIndex
            )

            if (includeUnchanged) {
                result.add(
                    DiffNode(
                        type = DiffType.UNCHANGED,
                        binIndex = binIndex,
                        levelIndex = newMatchIndex,
                        oldDescription = describeLevel(oldSnapshots[oldMatchIndex]),
                        newDescription = describeLevel(newSnapshots[newMatchIndex])
                    )
                )
            }

            oldCursor = oldMatchIndex + 1
            newCursor = newMatchIndex + 1
        }

        appendChangedRun(
            output = result,
            binIndex = binIndex,
            oldSnapshots = oldSnapshots,
            newSnapshots = newSnapshots,
            oldRange = oldCursor until oldSnapshots.size,
            newRange = newCursor until newSnapshots.size
        )

        return result
    }

    private fun appendChangedRun(
        output: MutableList<DiffNode>,
        binIndex: Int,
        oldSnapshots: List<LevelSnapshot>,
        newSnapshots: List<LevelSnapshot>,
        oldRange: IntRange,
        newRange: IntRange
    ) {
        val oldIndices = oldRange.toList()
        val newIndices = newRange.toList()
        val pairCount = minOf(oldIndices.size, newIndices.size)

        for (i in 0 until pairCount) {
            val oldIndex = oldIndices[i]
            val newIndex = newIndices[i]
            output.add(
                DiffNode(
                    type = DiffType.MODIFIED,
                    binIndex = binIndex,
                    levelIndex = newIndex,
                    oldDescription = describeLevel(oldSnapshots[oldIndex]),
                    newDescription = describeLevel(newSnapshots[newIndex])
                )
            )
        }

        for (i in pairCount until oldIndices.size) {
            val oldIndex = oldIndices[i]
            output.add(
                DiffNode(
                    type = DiffType.REMOVED,
                    binIndex = binIndex,
                    levelIndex = oldIndex,
                    oldDescription = describeLevel(oldSnapshots[oldIndex]),
                    newDescription = null
                )
            )
        }

        for (i in pairCount until newIndices.size) {
            val newIndex = newIndices[i]
            output.add(
                DiffNode(
                    type = DiffType.ADDED,
                    binIndex = binIndex,
                    levelIndex = newIndex,
                    oldDescription = null,
                    newDescription = describeLevel(newSnapshots[newIndex])
                )
            )
        }
    }

    private fun computeLcsMatches(
        oldSnapshots: List<LevelSnapshot>,
        newSnapshots: List<LevelSnapshot>
    ): List<Pair<Int, Int>> {
        val oldSize = oldSnapshots.size
        val newSize = newSnapshots.size
        val dp = Array(oldSize + 1) { IntArray(newSize + 1) }

        for (oldIndex in oldSize - 1 downTo 0) {
            for (newIndex in newSize - 1 downTo 0) {
                dp[oldIndex][newIndex] = if (oldSnapshots[oldIndex] == newSnapshots[newIndex]) {
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
            if (oldSnapshots[oldIndex] == newSnapshots[newIndex]) {
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

    private fun describeLevel(snapshot: LevelSnapshot): String {
        val freqText = if (snapshot.frequencyHz > 0L) {
            "${snapshot.frequencyHz / 1_000_000L} MHz"
        } else {
            "Unknown MHz"
        }
        val voltText = if (snapshot.voltageLevel >= 0) {
            "Lvl ${snapshot.voltageLevel}"
        } else {
            "Lvl ?"
        }
        return "$freqText / $voltText"
    }

    private fun toSnapshot(level: Level): LevelSnapshot {
        return LevelSnapshot(
            frequencyHz = level.frequency,
            voltageLevel = level.voltageLevel
        )
    }

    private fun resolveBinIndex(
        binId: Int,
        originalBins: List<Bin>,
        currentBins: List<Bin>
    ): Int {
        val currentIndex = currentBins.indexOfFirst { it.id == binId }
        if (currentIndex >= 0) return currentIndex
        val oldIndex = originalBins.indexOfFirst { it.id == binId }
        return if (oldIndex >= 0) oldIndex else 0
    }

    private data class LevelSnapshot(
        val frequencyHz: Long,
        val voltageLevel: Int
    )
}
