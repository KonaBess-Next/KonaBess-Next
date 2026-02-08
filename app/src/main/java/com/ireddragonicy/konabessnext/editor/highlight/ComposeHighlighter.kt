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

    // Pre-allocated SpanStyle singletons — eliminates per-call object allocation & GC pressure
    private val STYLE_COMMENT = SpanStyle(color = COLOR_COMMENT)
    private val STYLE_PREPROCESSOR = SpanStyle(color = COLOR_PREPROCESSOR)
    private val STYLE_STRING = SpanStyle(color = COLOR_STRING)
    private val STYLE_NUMBER = SpanStyle(color = COLOR_NUMBER)
    private val STYLE_KEYWORD = SpanStyle(color = COLOR_KEYWORD)
    private val STYLE_NODE = SpanStyle(color = COLOR_NODE)
    private val STYLE_PROPERTY = SpanStyle(color = COLOR_PROPERTY)
    private val STYLE_PHANDLE = SpanStyle(color = COLOR_PHANDLE)
    private val STYLE_BRACKET = SpanStyle(color = COLOR_BRACKET)
    private val STYLE_SEARCH = SpanStyle(background = Color(0xFF624C23), color = Color.White)

    fun highlight(text: String, searchQuery: String = ""): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        return buildAnnotatedString {
            append(text)

            // Apply pre-allocated style to all regex matches
            fun apply(pattern: Pattern, style: SpanStyle, group: Int = 0) {
                val matcher = pattern.matcher(text)
                while (matcher.find()) {
                    val start = matcher.start(group)
                    val end = matcher.end(group)
                    if (start in 0 until length && end <= length) {
                         addStyle(style, start, end)
                    }
                }
            }

            // Syntax Highlighting (layered — later styles override earlier ones)

            // 0. Punctuation (base layer)
            apply(PATTERN_BRACKET, STYLE_BRACKET)

            // 1. Numeric literals
            apply(PATTERN_HEX, STYLE_NUMBER)
            apply(PATTERN_DECIMAL, STYLE_NUMBER)
            apply(PATTERN_KEYWORDS, STYLE_KEYWORD)

            // 2. Structural elements
            apply(PATTERN_NODE, STYLE_NODE, 1)
            apply(PATTERN_PROPERTY, STYLE_PROPERTY, 1)
            apply(PATTERN_PHANDLE, STYLE_PHANDLE)
            apply(PATTERN_LABEL_REF, STYLE_PHANDLE)

            // 3. Strings & preprocessor directives
            apply(PATTERN_STRING, STYLE_STRING)
            apply(PATTERN_PREPROCESSOR, STYLE_PREPROCESSOR)

            // 4. Comments (highest priority — overlays all other styles)
            apply(PATTERN_COMMENT_LINE, STYLE_COMMENT)
            val blockMatcher = PATTERN_COMMENT_BLOCK.matcher(text)
            while(blockMatcher.find()) {
                addStyle(STYLE_COMMENT, blockMatcher.start(), blockMatcher.end())
            }

            // Search highlighting (topmost layer)
            if (searchQuery.isNotEmpty()) {
                var idx = text.indexOf(searchQuery, ignoreCase = true)
                while (idx != -1) {
                    addStyle(STYLE_SEARCH, idx, idx + searchQuery.length)
                    idx = text.indexOf(searchQuery, idx + 1, ignoreCase = true)
                }
            }
        }
    }
}
