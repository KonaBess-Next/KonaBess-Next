package com.ireddragonicy.konabessnext.editor.highlight;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax highlighter for Device Tree Source (DTS) files.
 * Tokenizes each line into colored spans for rendering.
 */
public class DtsHighlighter {
    private final EditorColorScheme scheme;

    // Pre-compiled patterns for performance
    private static final Pattern BLOCK_COMMENT_START = Pattern.compile("/\\*");
    private static final Pattern BLOCK_COMMENT_END = Pattern.compile("\\*/");
    private static final Pattern LINE_COMMENT = Pattern.compile("//.*$");
    private static final Pattern PREPROCESSOR = Pattern
            .compile("^\\s*(/dts-v1/|/plugin/|/include/|/omit-if-no-ref/|/delete-node/|/delete-property/|#include)");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"[^\"]*\"");
    private static final Pattern HEX_NUMBER = Pattern.compile("\\b0x[0-9a-fA-F]+\\b");
    private static final Pattern DECIMAL_NUMBER = Pattern.compile("\\b[0-9]+\\b");
    private static final Pattern PHANDLE_REF = Pattern.compile("<&[a-zA-Z_][a-zA-Z0-9_]*>");
    private static final Pattern LABEL_REF = Pattern.compile("&[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern PROPERTY_NAME = Pattern.compile("^\\s*([a-zA-Z_#][a-zA-Z0-9_,.-]*)\\s*(?==|;)");
    private static final Pattern NODE_NAME = Pattern
            .compile("^\\s*([a-zA-Z_][a-zA-Z0-9_,-]*)(?:@[0-9a-fA-F]+)?\\s*\\{");
    private static final Pattern BRACKETS = Pattern.compile("[{};<>]");

    // DTS Keywords
    private static final Pattern KEYWORDS = Pattern.compile(
            "\\b(compatible|reg|status|phandle|interrupt-parent|interrupts|" +
                    "interrupt-controller|#address-cells|#size-cells|#interrupt-cells|" +
                    "ranges|dma-ranges|device_type|model|reg-names|clock-names|clocks|" +
                    "resets|reset-names|power-domains|power-domain-names|iommus|" +
                    "memory-region|no-map|reusable|alloc-ranges|alignment|size|" +
                    "linux,cma-default|qcom,.*?)\\b");

    // Multi-line comment state (tracked per document)
    private boolean inBlockComment = false;

    public DtsHighlighter() {
        this.scheme = EditorColorScheme.getDefault();
    }

    public DtsHighlighter(EditorColorScheme scheme) {
        this.scheme = scheme;
    }

    /**
     * Reset the block comment state (call before processing a new document).
     */
    public void reset() {
        inBlockComment = false;
    }

    /**
     * Tokenize a single line into colored spans.
     * Call sequentially for each line to track multi-line comments.
     */
    public List<Span> tokenize(CharSequence line) {
        List<Span> spans = new ArrayList<>();
        int length = line.length();

        if (length == 0) {
            return spans;
        }

        String text = line.toString();
        boolean[] colored = new boolean[length];

        // Handle block comments (can span multiple lines)
        int pos = 0;
        while (pos < length) {
            if (inBlockComment) {
                // Look for end of block comment
                Matcher endMatcher = BLOCK_COMMENT_END.matcher(text);
                if (endMatcher.find(pos)) {
                    int end = endMatcher.end();
                    addSpan(spans, colored, pos, end, scheme.commentColor);
                    inBlockComment = false;
                    pos = end;
                } else {
                    // Entire rest of line is comment
                    addSpan(spans, colored, pos, length, scheme.commentColor);
                    pos = length;
                }
            } else {
                // Look for start of block comment
                Matcher startMatcher = BLOCK_COMMENT_START.matcher(text);
                if (startMatcher.find(pos)) {
                    int start = startMatcher.start();
                    // Check for end on same line
                    Matcher endMatcher = BLOCK_COMMENT_END.matcher(text);
                    if (endMatcher.find(startMatcher.end())) {
                        addSpan(spans, colored, start, endMatcher.end(), scheme.commentColor);
                        pos = endMatcher.end();
                    } else {
                        addSpan(spans, colored, start, length, scheme.commentColor);
                        inBlockComment = true;
                        pos = length;
                    }
                } else {
                    break; // No more block comments on this line
                }
            }
        }

        // If entire line is in block comment, we're done
        if (isFullyColored(colored)) {
            return spans;
        }

        // Line comments
        matchAndAdd(LINE_COMMENT, text, colored, spans, scheme.commentColor);

        // Preprocessor directives
        matchAndAdd(PREPROCESSOR, text, colored, spans, scheme.preprocessorColor);

        // String literals
        matchAndAdd(STRING_LITERAL, text, colored, spans, scheme.stringColor);

        // Phandle references <&name>
        matchAndAdd(PHANDLE_REF, text, colored, spans, scheme.phandleColor);

        // Label references &name
        matchAndAdd(LABEL_REF, text, colored, spans, scheme.phandleColor);

        // Hex numbers
        matchAndAdd(HEX_NUMBER, text, colored, spans, scheme.numberColor);

        // Keywords
        matchAndAdd(KEYWORDS, text, colored, spans, scheme.keywordColor);

        // Node names (before {)
        Matcher nodeMatcher = NODE_NAME.matcher(text);
        if (nodeMatcher.find()) {
            int start = nodeMatcher.start(1);
            int end = nodeMatcher.end(1);
            if (!isRangeColored(colored, start, end)) {
                addSpan(spans, colored, start, end, scheme.nodeColor);
            }
        }

        // Property names (before =)
        Matcher propMatcher = PROPERTY_NAME.matcher(text);
        if (propMatcher.find()) {
            int start = propMatcher.start(1);
            int end = propMatcher.end(1);
            if (!isRangeColored(colored, start, end)) {
                addSpan(spans, colored, start, end, scheme.propertyColor);
            }
        }

        // Brackets and operators
        matchAndAdd(BRACKETS, text, colored, spans, scheme.bracketColor);

        // Decimal numbers (after other tokens to avoid conflicts)
        matchAndAdd(DECIMAL_NUMBER, text, colored, spans, scheme.numberColor);

        // Fill remaining uncolored regions with default text color
        int start = -1;
        for (int i = 0; i <= length; i++) {
            if (i < length && !colored[i]) {
                if (start < 0)
                    start = i;
            } else {
                if (start >= 0) {
                    spans.add(new Span(start, i, scheme.textColor));
                    start = -1;
                }
            }
        }

        // Sort spans by start position
        spans.sort((a, b) -> Integer.compare(a.start, b.start));

        return spans;
    }

    private void matchAndAdd(Pattern pattern, String text, boolean[] colored,
            List<Span> spans, int color) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            // For patterns with groups, use group 1 if available
            if (matcher.groupCount() > 0 && matcher.group(1) != null) {
                start = matcher.start(1);
                end = matcher.end(1);
            }
            if (!isRangeColored(colored, start, end)) {
                addSpan(spans, colored, start, end, color);
            }
        }
    }

    private void addSpan(List<Span> spans, boolean[] colored, int start, int end, int color) {
        spans.add(new Span(start, end, color));
        for (int i = start; i < end && i < colored.length; i++) {
            colored[i] = true;
        }
    }

    private boolean isRangeColored(boolean[] colored, int start, int end) {
        for (int i = start; i < end && i < colored.length; i++) {
            if (colored[i])
                return true;
        }
        return false;
    }

    private boolean isFullyColored(boolean[] colored) {
        for (boolean b : colored) {
            if (!b)
                return false;
        }
        return true;
    }
}
