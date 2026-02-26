package com.ireddragonicy.konabessnext.editor.sora

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.QuickQuoteHandler
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.StyleReceiver
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.util.BaseAnalyzeManager
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.util.regex.Pattern

/**
 * Sora Editor Language implementation for Device Tree Source (DTS) files.
 *
 * Provides regex-based syntax highlighting by porting the token patterns
 * from the legacy ComposeHighlighter into Sora's AnalyzeManager pipeline.
 * Analysis runs on a background thread triggered by rerun().
 */
class DtsSoraLanguage : Language {

    private val analyzeManager = DtsAnalyzeManager()

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_STRONG

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        // No auto-completion for DTS files
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        val lineText = content.getLine(line).toString()
        val trimmed = lineText.trimEnd()
        return if (trimmed.endsWith("{")) 4 else 0
    }

    override fun useTab(): Boolean = false

    override fun getFormatter(): Formatter = io.github.rosemoe.sora.lang.EmptyLanguage.EmptyFormatter.INSTANCE

    override fun getSymbolPairs(): SymbolPairMatch = SymbolPairMatch.DefaultSymbolPairs()

    override fun getNewlineHandlers(): Array<NewlineHandler>? = null

    override fun getQuickQuoteHandler(): QuickQuoteHandler? = null

    override fun destroy() {
        analyzeManager.destroy()
    }
}

/**
 * DTS-specific AnalyzeManager that tokenizes content line-by-line
 * using regex patterns and produces Sora-compatible span data.
 *
 * On any text change (insert/delete), triggers a full re-analysis on a
 * background thread. Sora handles the threading via rerun().
 */
private class DtsAnalyzeManager : BaseAnalyzeManager() {

    companion object {
        // --- Regex patterns ported from ComposeHighlighter.kt ---
        private val PATTERN_COMMENT_LINE = Pattern.compile("//.*")
        private val PATTERN_COMMENT_BLOCK_OPEN = Pattern.compile("/\\*")
        private val PATTERN_COMMENT_BLOCK_CLOSE = Pattern.compile("\\*/")
        private val PATTERN_PREPROCESSOR = Pattern.compile(
            "^\\s*(/dts-v1/|/plugin/|/include/|/omit-if-no-ref/|/delete-node/|/delete-property/|#include)"
        )
        private val PATTERN_STRING = Pattern.compile("\"(\\\\.|[^\"])*\"")
        private val PATTERN_HEX = Pattern.compile("\\b0x[0-9a-fA-F]+\\b")
        private val PATTERN_DECIMAL = Pattern.compile("\\b\\d+\\b")
        private val PATTERN_PHANDLE = Pattern.compile("<&[a-zA-Z_][a-zA-Z0-9_]*>")
        private val PATTERN_LABEL_REF = Pattern.compile("&[a-zA-Z_][a-zA-Z0-9_]*")
        private val PATTERN_NODE = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_,-]*)(?:@[0-9a-fA-F]+)?\\s*\\{"
        )
        private val PATTERN_PROPERTY = Pattern.compile(
            "^\\s*([a-zA-Z_#][a-zA-Z0-9_,.-]*)\\s*(?==|;)"
        )
        private val PATTERN_BRACKET = Pattern.compile("[{}<>;=,:]")
        private val PATTERN_KEYWORDS = Pattern.compile(
            "\\b(compatible|reg|status|phandle|interrupt-parent|interrupts|" +
                "interrupt-controller|#address-cells|#size-cells|#interrupt-cells|" +
                "ranges|dma-ranges|device_type|model|reg-names|clock-names|clocks|" +
                "resets|reset-names|power-domains|power-domain-names|iommus|" +
                "memory-region|no-map|reusable|alloc-ranges|alignment|size|" +
                "linux,cma-default|qcom,.*?)\\b"
        )
    }

    private data class TokenSpan(val start: Int, val end: Int, val colorId: Int)

    @Volatile
    private var analyzeThread: Thread? = null

    override fun insert(start: CharPosition, end: CharPosition, insertedContent: CharSequence) {
        rerun()
    }

    override fun delete(start: CharPosition, end: CharPosition, deletedContent: CharSequence) {
        rerun()
    }

    override fun rerun() {
        analyzeThread?.interrupt()
        analyzeThread = Thread {
            try {
                // Debounce to allow Sora's internal O(1) incremental span shifts to settle
                Thread.sleep(250) 
                runAnalysis()
            } catch (_: InterruptedException) {
                // Cancelled — expected
            }
        }.apply {
            isDaemon = true
            name = "DtsSoraAnalyzer"
            start()
        }
    }

    override fun destroy() {
        super.destroy()
        analyzeThread?.interrupt()
        analyzeThread = null
    }

    private fun runAnalysis() {
        val contentRef = contentRef ?: return
        val receiver = receiver ?: return

        val lineCount = contentRef.lineCount
        if (lineCount == 0) return

        val spans = MappedSpans.Builder()
        val blocks = mutableListOf<CodeBlock>()
        val blockStack = java.util.Stack<CodeBlock>()
        var inBlockComment = false

        for (line in 0 until lineCount) {
            if (Thread.currentThread().isInterrupted) return

            val lineText = contentRef.getLine(line).toString()
            val lineLength = lineText.length

            if (lineLength == 0) {
                // Empty line — add a normal text span
                val style = if (inBlockComment) {
                    TextStyle.makeStyle(DtsTokenColorIds.COMMENT)
                } else {
                    TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
                }
                spans.add(line, Span.obtain(0, style))
                continue
            }

            val tokenSpans = mutableListOf<TokenSpan>()

            if (inBlockComment) {
                val endMatcher = PATTERN_COMMENT_BLOCK_CLOSE.matcher(lineText)
                if (endMatcher.find()) {
                    tokenSpans.add(TokenSpan(0, endMatcher.end(), DtsTokenColorIds.COMMENT))
                    inBlockComment = false
                    if (endMatcher.end() < lineLength) {
                        tokenizeLine(lineText, tokenSpans, endMatcher.end())
                    }
                } else {
                    // Entire line is inside block comment
                    tokenSpans.add(TokenSpan(0, lineLength, DtsTokenColorIds.COMMENT))
                }
            } else {
                tokenizeLine(lineText, tokenSpans, 0)
            }

            // Check if a block comment starts on this line without closing
            if (!inBlockComment) {
                inBlockComment = hasUnclosedBlockComment(lineText)
            }

            // Build Sora spans
            val colorMap = emitSpansForLine(line, lineLength, tokenSpans, spans)
            
            // Extract code blocks from resolved brackets
            for (i in 0 until lineLength) {
                if (colorMap.isNotEmpty() && colorMap[i] == DtsTokenColorIds.BRACKET) {
                    val ch = lineText[i]
                    if (ch == '{') {
                        val b = CodeBlock()
                        b.startLine = line
                        b.startColumn = i
                        blockStack.push(b)
                    } else if (ch == '}') {
                        if (blockStack.isNotEmpty()) {
                            val b = blockStack.pop()
                            b.endLine = line
                            b.endColumn = i + 1
                            blocks.add(b)
                        }
                    }
                }
            }
        }

        // Finalize spans builder
        spans.determine(lineCount - 1)

        if (Thread.currentThread().isInterrupted) return

        val styles = Styles(spans.build(), true)
        for (block in blocks) {
            styles.addCodeBlock(block)
        }
        styles.finishBuilding()
        receiver.setStyles(this, styles)
    }

    /**
     * Check if the line has an unclosed block comment (open without matching close).
     */
    private fun hasUnclosedBlockComment(lineText: String): Boolean {
        var lastOpen = -1
        val openMatcher = PATTERN_COMMENT_BLOCK_OPEN.matcher(lineText)
        while (openMatcher.find()) {
            lastOpen = openMatcher.start()
        }
        if (lastOpen < 0) return false

        val closeMatcher = PATTERN_COMMENT_BLOCK_CLOSE.matcher(lineText)
        var lastCloseAfterOpen = -1
        while (closeMatcher.find()) {
            if (closeMatcher.start() > lastOpen) {
                lastCloseAfterOpen = closeMatcher.end()
            }
        }
        return lastCloseAfterOpen < 0
    }

    /**
     * Tokenize a single line starting from [startOffset], appending results to [tokens].
     */
    private fun tokenizeLine(
        lineText: CharSequence,
        tokens: MutableList<TokenSpan>,
        startOffset: Int
    ) {
        if (startOffset >= lineText.length) return
        val sub = if (startOffset > 0) lineText.subSequence(startOffset, lineText.length) else lineText
        val offset = startOffset

        // Line comment overrides everything after it
        val commentMatcher = PATTERN_COMMENT_LINE.matcher(sub)
        val textToTokenize: CharSequence
        if (commentMatcher.find()) {
            tokens.add(TokenSpan(
                commentMatcher.start() + offset,
                commentMatcher.end() + offset,
                DtsTokenColorIds.COMMENT
            ))
            textToTokenize = sub.subSequence(0, commentMatcher.start())
        } else {
            textToTokenize = sub
        }

        // Layer tokens bottom-to-top (later patterns override earlier ones in the color map)
        applyPattern(PATTERN_BRACKET, textToTokenize, offset, DtsTokenColorIds.BRACKET, tokens)
        applyPattern(PATTERN_HEX, textToTokenize, offset, DtsTokenColorIds.NUMBER, tokens)
        applyPattern(PATTERN_DECIMAL, textToTokenize, offset, DtsTokenColorIds.NUMBER, tokens)
        applyPattern(PATTERN_KEYWORDS, textToTokenize, offset, DtsTokenColorIds.KEYWORD, tokens)
        applyPatternGroup(PATTERN_NODE, textToTokenize, offset, DtsTokenColorIds.NODE, tokens, 1)
        applyPatternGroup(PATTERN_PROPERTY, textToTokenize, offset, DtsTokenColorIds.PROPERTY, tokens, 1)
        applyPattern(PATTERN_PHANDLE, textToTokenize, offset, DtsTokenColorIds.PHANDLE, tokens)
        applyPattern(PATTERN_LABEL_REF, textToTokenize, offset, DtsTokenColorIds.LABEL_REF, tokens)
        applyPattern(PATTERN_STRING, textToTokenize, offset, DtsTokenColorIds.STRING, tokens)
        applyPattern(PATTERN_PREPROCESSOR, textToTokenize, offset, DtsTokenColorIds.PREPROCESSOR, tokens)
    }

    private fun applyPattern(
        pattern: Pattern,
        text: CharSequence,
        offset: Int,
        colorId: Int,
        tokens: MutableList<TokenSpan>
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            tokens.add(TokenSpan(matcher.start() + offset, matcher.end() + offset, colorId))
        }
    }

    private fun applyPatternGroup(
        pattern: Pattern,
        text: CharSequence,
        offset: Int,
        colorId: Int,
        tokens: MutableList<TokenSpan>,
        group: Int
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val start = matcher.start(group)
            val end = matcher.end(group)
            if (start >= 0 && end >= 0) {
                tokens.add(TokenSpan(start + offset, end + offset, colorId))
            }
        }
    }

    /**
     * Converts TokenSpan list into Sora's MappedSpans.Builder format.
     * Uses a character-level color map for correct layering (last-writer-wins,
     * matching the legacy ComposeHighlighter behavior).
     */
    private fun emitSpansForLine(
        line: Int,
        lineLength: Int,
        tokenSpans: List<TokenSpan>,
        builder: MappedSpans.Builder
    ): IntArray {
        if (tokenSpans.isEmpty() || lineLength == 0) {
            builder.add(line, Span.obtain(
                0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
            ))
            return IntArray(0)
        }

        // Build per-character color map (0 = TEXT_NORMAL)
        val colorMap = IntArray(lineLength)
        for (span in tokenSpans) {
            val s = span.start.coerceIn(0, lineLength)
            val e = span.end.coerceIn(0, lineLength)
            for (i in s until e) {
                colorMap[i] = span.colorId
            }
        }

        // Emit initial span for column 0
        var currentColor = colorMap[0]
        builder.add(line, Span.obtain(0, TextStyle.makeStyle(resolveColorId(currentColor))))

        // Emit span transition points
        for (i in 1 until lineLength) {
            if (colorMap[i] != currentColor) {
                currentColor = colorMap[i]
                builder.add(line, Span.obtain(i, TextStyle.makeStyle(resolveColorId(currentColor))))
            }
        }
        
        return colorMap
    }

    private fun resolveColorId(colorId: Int): Int {
        return if (colorId == 0) EditorColorScheme.TEXT_NORMAL else colorId
    }
}
