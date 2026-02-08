package com.ireddragonicy.konabessnext.editor.highlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.util.regex.Pattern

internal object ComposeHighlighter {

    // Colors mapping (based on existing DtsHighlighter/EditorColorScheme)
    private val COLOR_TEXT = Color(0xFFE0E2E7) // Neutral FG
    private val COLOR_COMMENT = Color(0xFF7D8590) // Gray
    private val COLOR_PREPROCESSOR = Color(0xFFC678DD) // Purple
    private val COLOR_STRING = Color(0xFF98C379) // Green
    private val COLOR_NUMBER = Color(0xFFD19A66) // Orange
    private val COLOR_KEYWORD = Color(0xFFE5C07B) // Yellow
    private val COLOR_NODE = Color(0xFF61AFEF) // Blue
    private val COLOR_PROPERTY = Color(0xFFE06C75) // Red
    private val COLOR_PHANDLE = Color(0xFF56B6C2) // Cyan
    private val COLOR_BRACKET = Color(0xFFAABBCC) // Light Gray

    // Regex Patterns
    private val PATTERN_COMMENT_BLOCK = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL)
    private val PATTERN_COMMENT_LINE = Pattern.compile("//.*")
    private val PATTERN_PREPROCESSOR = Pattern.compile("^\\s*(/dts-v1/|/plugin/|/include/|/omit-if-no-ref/|/delete-node/|/delete-property/|#include)")
    private val PATTERN_STRING = Pattern.compile("\"(\\\\.|[^\"])*\"")
    private val PATTERN_HEX = Pattern.compile("\\b0x[0-9a-fA-F]+\\b")
    private val PATTERN_DECIMAL = Pattern.compile("\\b\\d+\\b")
    private val PATTERN_PHANDLE = Pattern.compile("<&[a-zA-Z_][a-zA-Z0-9_]*>")
    private val PATTERN_LABEL_REF = Pattern.compile("&[a-zA-Z_][a-zA-Z0-9_]*")
    private val PATTERN_NODE = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_,-]*)(?:@[0-9a-fA-F]+)?\\s*\\{")
    private val PATTERN_PROPERTY = Pattern.compile("^\\s*([a-zA-Z_#][a-zA-Z0-9_,.-]*)\\s*(?==|;)")
    private val PATTERN_BRACKET = Pattern.compile("[{}<>;=,:]") // Punctuation
    private val PATTERN_KEYWORDS = Pattern.compile(
        "\\b(compatible|reg|status|phandle|interrupt-parent|interrupts|" +
                "interrupt-controller|#address-cells|#size-cells|#interrupt-cells|" +
                "ranges|dma-ranges|device_type|model|reg-names|clock-names|clocks|" +
                "resets|reset-names|power-domains|power-domain-names|iommus|" +
                "memory-region|no-map|reusable|alloc-ranges|alignment|size|" +
                "linux,cma-default|qcom,.*?)\\b"
    )

    fun highlight(text: String, searchQuery: String = ""): AnnotatedString {
        return buildAnnotatedString {
            append(text)

            // Helper to apply style to matches
            fun apply(pattern: Pattern, color: Color, group: Int = 0) {
                val matcher = pattern.matcher(text)
                while (matcher.find()) {
                    val start = matcher.start(group)
                    val end = matcher.end(group)
                    if (start in 0 until length && end <= length) {
                         addStyle(SpanStyle(color = color), start, end)
                    }
                }
            }

            // Syntax Highlighting
            
            // 0. Punctuation (Base layer for symbols)
            apply(PATTERN_BRACKET, COLOR_BRACKET)
            
            // 1. Base tokens
            apply(PATTERN_HEX, COLOR_NUMBER)
            // ... (rest is same)
            apply(PATTERN_DECIMAL, COLOR_NUMBER)
            apply(PATTERN_KEYWORDS, COLOR_KEYWORD)
            
            // 2. Structural
            apply(PATTERN_NODE, COLOR_NODE, 1) // group 1 is name
            apply(PATTERN_PROPERTY, COLOR_PROPERTY, 1) // group 1 is name
            apply(PATTERN_PHANDLE, COLOR_PHANDLE)
            apply(PATTERN_LABEL_REF, COLOR_PHANDLE)
            
            // 3. Strings & Preprocessor
            apply(PATTERN_STRING, COLOR_STRING)
            apply(PATTERN_PREPROCESSOR, COLOR_PREPROCESSOR)

            // 4. Comments (Highest priority, should overlay others)
            apply(PATTERN_COMMENT_LINE, COLOR_COMMENT)
            // Block comments require tracking state across lines which is hard for "per line" highlighter.
            // For now, simple inline block comments:
            val blockMatcher = PATTERN_COMMENT_BLOCK.matcher(text)
            while(blockMatcher.find()) {
                addStyle(SpanStyle(color = COLOR_COMMENT), blockMatcher.start(), blockMatcher.end())
            }
            
            // Search Highlighting
            if (searchQuery.isNotEmpty()) {
                var idx = text.indexOf(searchQuery, ignoreCase = true)
                while (idx != -1) {
                    addStyle(
                        SpanStyle(background = Color(0xFF624C23), color = Color.White), 
                        idx, 
                        idx + searchQuery.length
                    )
                    idx = text.indexOf(searchQuery, idx + 1, ignoreCase = true)
                }
            }
        }
    }
}
