package com.ireddragonicy.konabessnext.editor.text

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import kotlin.math.max
import kotlin.math.min

@Immutable
data class TextCursor(
    val line: Int,
    val column: Int
) : Comparable<TextCursor> {
    override fun compareTo(other: TextCursor): Int {
        return if (line == other.line) {
            column.compareTo(other.column)
        } else {
            line.compareTo(other.line)
        }
    }
}

@Immutable
data class TextSelection(
    val start: TextCursor,
    val end: TextCursor
) {
    val normalizedStart: TextCursor
        get() = if (start <= end) start else end

    val normalizedEnd: TextCursor
        get() = if (start <= end) end else start

    val isCollapsed: Boolean
        get() = start == end
}

@Immutable
data class TextBufferLineSnapshot(
    val id: Long,
    val text: String
)

@Immutable
data class TextBufferSnapshot(
    val lines: List<TextBufferLineSnapshot>,
    val cursor: TextCursor,
    val nextLineId: Long
)

@Stable
class BufferLine internal constructor(
    val id: Long,
    initialText: String
) {
    private val storage = GapLine(initialText)
    private var version by mutableIntStateOf(0)

    val length: Int
        get() = storage.length

    val text: String
        get() {
            // Compose tracks this state read for granular line recomposition.
            version
            return storage.asString()
        }

    internal fun insert(column: Int, char: Char) {
        storage.insert(column.coerceIn(0, length), char)
        markDirty()
    }

    internal fun insertRange(column: Int, source: CharSequence, startIndex: Int, endIndex: Int) {
        storage.insertRange(column.coerceIn(0, length), source, startIndex, endIndex)
        markDirty()
    }

    internal fun deleteBackward(column: Int, count: Int = 1): Int {
        val removed = storage.deleteBackward(column.coerceIn(0, length), count)
        if (removed > 0) {
            markDirty()
        }
        return removed
    }

    internal fun deleteForward(column: Int, count: Int = 1): Int {
        val removed = storage.deleteForward(column.coerceIn(0, length), count)
        if (removed > 0) {
            markDirty()
        }
        return removed
    }

    internal fun takeSuffix(fromColumn: Int): String {
        val suffix = storage.takeSuffix(fromColumn.coerceIn(0, length))
        if (suffix.isNotEmpty()) {
            markDirty()
        }
        return suffix
    }

    internal fun append(other: BufferLine) {
        if (other.length == 0) return
        storage.append(other.storage)
        markDirty()
    }

    internal fun snapshotText(): String = storage.asString()

    private fun markDirty() {
        version++
    }
}

@Stable
interface TextBuffer {
    val lines: SnapshotStateList<BufferLine>
    val cursor: TextCursor
    val lineCount: Int

    fun setText(text: String)
    fun setLines(lines: List<String>)
    fun moveCursor(line: Int, column: Int)

    // Amortized O(1) insertion when gap is already at cursor.
    fun insert(char: Char)

    // Backspace semantics. O(1) in-line, O(L) when merging lines.
    fun delete(): Boolean

    // O(K) where K is inserted text length.
    fun insertText(text: String)

    // O(total_chars) when serializing for persistence.
    fun getText(): String

    // O(line_count) copy for repository updates without split().
    fun copyLines(): List<String>

    fun snapshot(): TextBufferSnapshot
    fun restore(snapshot: TextBufferSnapshot)
}

@Stable
class GapBufferState(initialText: String = "") : TextBuffer {
    private var nextLineId: Long = 1L
    private val lineList = mutableListOf<BufferLine>()

    override val lines: SnapshotStateList<BufferLine> = lineList.toMutableStateList()

    private var cursorState by mutableStateOf(TextCursor(0, 0))
    override val cursor: TextCursor
        get() = cursorState

    override val lineCount: Int
        get() = lines.size

    init {
        setText(initialText)
    }

    override fun setText(text: String) {
        setLines(splitToLines(text))
    }

    override fun setLines(lines: List<String>) {
        this.lines.clear()
        if (lines.isEmpty()) {
            this.lines.add(BufferLine(id = nextLineId++, initialText = ""))
        } else {
            for (line in lines) {
                this.lines.add(BufferLine(id = nextLineId++, initialText = line))
            }
        }
        cursorState = TextCursor(0, 0)
    }

    override fun moveCursor(line: Int, column: Int) {
        if (lines.isEmpty()) {
            lines.add(BufferLine(id = nextLineId++, initialText = ""))
        }
        val clampedLine = line.coerceIn(0, lines.lastIndex)
        val clampedColumn = column.coerceIn(0, lines[clampedLine].length)
        cursorState = TextCursor(clampedLine, clampedColumn)
    }

    override fun insert(char: Char) {
        ensureAtLeastOneLine()
        if (char == '\n') {
            splitLineAtCursor()
            return
        }

        val current = lines[cursor.line]
        current.insert(cursor.column, char)
        cursorState = cursor.copy(column = cursor.column + 1)
    }

    override fun delete(): Boolean {
        ensureAtLeastOneLine()
        val lineIndex = cursor.line
        val column = cursor.column
        if (lineIndex == 0 && column == 0) {
            return false
        }

        if (column > 0) {
            val current = lines[lineIndex]
            current.deleteBackward(column, 1)
            cursorState = cursor.copy(column = column - 1)
            return true
        }

        // Cursor is at line start: merge with previous line.
        val current = lines[lineIndex]
        val previous = lines[lineIndex - 1]
        val previousLength = previous.length
        previous.append(current)
        lines.removeAt(lineIndex)
        cursorState = TextCursor(line = lineIndex - 1, column = previousLength)
        return true
    }

    override fun insertText(text: String) {
        if (text.isEmpty()) return
        ensureAtLeastOneLine()

        var segmentStart = 0
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            val isLineBreak = ch == '\n' || ch == '\r'
            if (isLineBreak) {
                if (index > segmentStart) {
                    insertRangeAtCursor(text, segmentStart, index)
                }
                splitLineAtCursor()

                // Collapse CRLF into a single newline.
                if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                    index++
                }
                segmentStart = index + 1
            }
            index++
        }

        if (segmentStart < text.length) {
            insertRangeAtCursor(text, segmentStart, text.length)
        }
    }

    override fun getText(): String {
        if (lines.isEmpty()) return ""
        var totalChars = max(0, lineCount - 1) // account for '\n' between lines
        for (line in lines) {
            totalChars += line.length
        }
        val out = StringBuilder(totalChars)
        for (i in lines.indices) {
            if (i > 0) out.append('\n')
            out.append(lines[i].snapshotText())
        }
        return out.toString()
    }

    override fun copyLines(): List<String> {
        if (lines.isEmpty()) return listOf("")
        val out = ArrayList<String>(lines.size)
        for (line in lines) {
            out.add(line.snapshotText())
        }
        return out
    }

    override fun snapshot(): TextBufferSnapshot {
        val lineSnapshots = ArrayList<TextBufferLineSnapshot>(lines.size)
        for (line in lines) {
            lineSnapshots.add(TextBufferLineSnapshot(id = line.id, text = line.snapshotText()))
        }
        return TextBufferSnapshot(
            lines = lineSnapshots,
            cursor = cursor,
            nextLineId = nextLineId
        )
    }

    override fun restore(snapshot: TextBufferSnapshot) {
        lines.clear()
        if (snapshot.lines.isEmpty()) {
            lines.add(BufferLine(id = nextLineId++, initialText = ""))
        } else {
            for (line in snapshot.lines) {
                lines.add(BufferLine(id = line.id, initialText = line.text))
            }
        }
        nextLineId = max(nextLineId, snapshot.nextLineId)
        moveCursor(snapshot.cursor.line, snapshot.cursor.column)
    }

    private fun splitLineAtCursor() {
        val lineIndex = cursor.line
        val column = cursor.column
        val current = lines[lineIndex]
        val suffix = current.takeSuffix(column)
        lines.add(lineIndex + 1, BufferLine(id = nextLineId++, initialText = suffix))
        cursorState = TextCursor(line = lineIndex + 1, column = 0)
    }

    private fun insertRangeAtCursor(source: CharSequence, start: Int, end: Int) {
        if (start >= end) return
        val current = lines[cursor.line]
        current.insertRange(cursor.column, source, start, end)
        cursorState = cursor.copy(column = cursor.column + (end - start))
    }

    private fun ensureAtLeastOneLine() {
        if (lines.isEmpty()) {
            lines.add(BufferLine(id = nextLineId++, initialText = ""))
            cursorState = TextCursor(0, 0)
        }
    }

    private fun splitToLines(text: String): List<String> {
        if (text.isEmpty()) return listOf("")

        val out = ArrayList<String>(max(1, text.length / 40))
        var segmentStart = 0
        var index = 0
        while (index < text.length) {
            if (text[index] == '\n') {
                out.add(text.substring(segmentStart, index))
                segmentStart = index + 1
            }
            index++
        }
        out.add(text.substring(segmentStart))
        return out
    }
}

/**
 * Per-line gap buffer.
 *
 * Insertion/deletion near the gap is O(1) amortized.
 * Moving the gap is O(distance) within the current line.
 */
private class GapLine(initialText: String) {
    companion object {
        private const val DEFAULT_GAP_SIZE = 32
        private const val MIN_CAPACITY = 64
    }

    private var buffer: CharArray
    private var gapStart: Int = 0
    private var gapEnd: Int
    private var cachedString: String? = null

    val length: Int
        get() = buffer.size - gapSize

    private val gapSize: Int
        get() = gapEnd - gapStart

    init {
        val textLength = initialText.length
        val capacity = max(MIN_CAPACITY, textLength + DEFAULT_GAP_SIZE)
        buffer = CharArray(capacity)
        gapEnd = capacity - textLength
        if (textLength > 0) {
            initialText.toCharArray(buffer, gapEnd, 0, textLength)
        }
    }

    fun asString(): String {
        cachedString?.let { return it }
        val output = StringBuilder(length)
        if (gapStart > 0) {
            output.append(buffer, 0, gapStart)
        }
        val suffixLength = buffer.size - gapEnd
        if (suffixLength > 0) {
            output.append(buffer, gapEnd, suffixLength)
        }
        return output.toString().also { cachedString = it }
    }

    fun insert(index: Int, char: Char) {
        moveGapTo(index)
        ensureGapCapacity(1)
        buffer[gapStart] = char
        gapStart += 1
        invalidateCache()
    }

    fun insertRange(index: Int, source: CharSequence, startIndex: Int, endIndex: Int) {
        val safeStart = startIndex.coerceAtLeast(0)
        val safeEnd = endIndex.coerceAtMost(source.length)
        val count = (safeEnd - safeStart).coerceAtLeast(0)
        if (count == 0) return

        moveGapTo(index)
        ensureGapCapacity(count)
        var write = gapStart
        for (i in safeStart until safeEnd) {
            buffer[write++] = source[i]
        }
        gapStart = write
        invalidateCache()
    }

    fun deleteBackward(index: Int, count: Int): Int {
        if (count <= 0) return 0
        val safeIndex = index.coerceIn(0, length)
        val actual = min(count, safeIndex)
        if (actual == 0) return 0

        moveGapTo(safeIndex)
        gapStart -= actual
        invalidateCache()
        return actual
    }

    fun deleteForward(index: Int, count: Int): Int {
        if (count <= 0) return 0
        val safeIndex = index.coerceIn(0, length)
        val actual = min(count, length - safeIndex)
        if (actual == 0) return 0

        moveGapTo(safeIndex)
        gapEnd += actual
        invalidateCache()
        return actual
    }

    fun takeSuffix(fromIndex: Int): String {
        val safeFrom = fromIndex.coerceIn(0, length)
        val suffixLength = length - safeFrom
        if (suffixLength <= 0) return ""

        moveGapTo(safeFrom)
        val suffix = String(buffer, gapEnd, suffixLength)
        gapEnd += suffixLength
        invalidateCache()
        return suffix
    }

    fun append(other: GapLine) {
        val otherLength = other.length
        if (otherLength == 0) return

        moveGapTo(length)
        ensureGapCapacity(otherLength)

        val otherPrefixLength = other.gapStart
        if (otherPrefixLength > 0) {
            System.arraycopy(other.buffer, 0, buffer, gapStart, otherPrefixLength)
        }
        val otherSuffixLength = other.buffer.size - other.gapEnd
        if (otherSuffixLength > 0) {
            System.arraycopy(
                other.buffer,
                other.gapEnd,
                buffer,
                gapStart + otherPrefixLength,
                otherSuffixLength
            )
        }
        gapStart += otherLength
        invalidateCache()
    }

    private fun moveGapTo(targetIndex: Int) {
        val target = targetIndex.coerceIn(0, length)
        if (target == gapStart) return

        if (target < gapStart) {
            val moveCount = gapStart - target
            System.arraycopy(buffer, target, buffer, gapEnd - moveCount, moveCount)
            gapStart = target
            gapEnd -= moveCount
            return
        }

        val moveCount = target - gapStart
        System.arraycopy(buffer, gapEnd, buffer, gapStart, moveCount)
        gapStart += moveCount
        gapEnd += moveCount
    }

    private fun ensureGapCapacity(required: Int) {
        if (gapSize >= required) return

        val textLength = length
        var newCapacity = buffer.size
        val minRequiredCapacity = textLength + required + DEFAULT_GAP_SIZE
        while (newCapacity < minRequiredCapacity) {
            newCapacity *= 2
        }
        val newBuffer = CharArray(newCapacity)
        if (gapStart > 0) {
            System.arraycopy(buffer, 0, newBuffer, 0, gapStart)
        }

        val suffixLength = buffer.size - gapEnd
        val newGapEnd = newCapacity - suffixLength
        if (suffixLength > 0) {
            System.arraycopy(buffer, gapEnd, newBuffer, newGapEnd, suffixLength)
        }

        buffer = newBuffer
        gapEnd = newGapEnd
    }

    private fun invalidateCache() {
        cachedString = null
    }
}
