package com.ireddragonicy.konabessnext.editor.highlight;

/**
 * Represents a highlighted span of text with position and color.
 */
public class Span {
    public final int start;
    public final int end;
    public final int color;

    public Span(int start, int end, int color) {
        this.start = start;
        this.end = end;
        this.color = color;
    }
}
