package com.ireddragonicy.konabessnext.editor.sora

import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Sora Editor Color IDs for DTS syntax token highlighting.
 *
 * These extend beyond the built-in [EditorColorScheme] base IDs,
 * starting at 256 to avoid collisions.
 */
object DtsTokenColorIds {
    const val KEYWORD         = 256
    const val STRING          = 257
    const val COMMENT         = 258
    const val NUMBER          = 259
    const val PREPROCESSOR    = 260
    const val NODE            = 261
    const val PROPERTY        = 262
    const val PHANDLE         = 263
    const val BRACKET         = 264
    const val LABEL_REF       = 265
}

/**
 * DTS syntax highlighting colors — mirroring our existing
 * [ComposeHighlighter] palette for a consistent look.
 */
private object DtsSyntaxColors {
    val TEXT       = Color(0xFFE0E2E7)  // Neutral FG
    val COMMENT    = Color(0xFF7D8590)  // Gray
    val PREPROC    = Color(0xFFC678DD)  // Purple
    val STRING     = Color(0xFF98C379)  // Green
    val NUMBER     = Color(0xFFD19A66)  // Orange
    val KEYWORD    = Color(0xFFE5C07B)  // Yellow
    val NODE       = Color(0xFF61AFEF)  // Blue
    val PROPERTY   = Color(0xFFE06C75)  // Red
    val PHANDLE    = Color(0xFF56B6C2)  // Cyan
    val BRACKET    = Color(0xFFAABBCC)  // Light Gray
}

/**
 * Converts a Material 3 [ColorScheme] to a Sora [EditorColorScheme]
 * with full DTS syntax token colors applied.
 *
 * Usage:
 * ```
 * val soraScheme = MaterialTheme.colorScheme.toSoraColorScheme()
 * editor.colorScheme = soraScheme
 * ```
 */
fun ColorScheme.toSoraColorScheme(): EditorColorScheme {
    return EditorColorScheme().apply {
        // === Editor Chrome ===
        setColor(EditorColorScheme.WHOLE_BACKGROUND, surface.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, surfaceVariant.copy(alpha = 0.4f).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER, onSurfaceVariant.toArgb())
        setColor(EditorColorScheme.LINE_DIVIDER, AndroidColor.TRANSPARENT)
        setColor(EditorColorScheme.CURRENT_LINE, surfaceVariant.copy(alpha = 0.25f).toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB, onSurfaceVariant.copy(alpha = 0.3f).toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_TRACK, surfaceVariant.copy(alpha = 0.1f).toArgb())

        // === Selection & Cursor ===
        setColor(EditorColorScheme.SELECTION_INSERT, primary.toArgb())
        setColor(EditorColorScheme.SELECTION_HANDLE, primary.toArgb())
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, Color(0x66B39DDB).toArgb())
        setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, Color(0xFF624C23).toArgb())

        // === Normal Text ===
        setColor(EditorColorScheme.TEXT_NORMAL, DtsSyntaxColors.TEXT.toArgb())

        // === Completions & Diagnostics ===
        setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, surfaceContainer.toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, onSurface.toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, onSurfaceVariant.toArgb())
        setColor(EditorColorScheme.PROBLEM_ERROR, error.toArgb())
        setColor(EditorColorScheme.PROBLEM_WARNING, tertiary.toArgb())

        // === DTS Syntax Token Colors ===
        setColor(DtsTokenColorIds.KEYWORD, DtsSyntaxColors.KEYWORD.toArgb())
        setColor(DtsTokenColorIds.STRING, DtsSyntaxColors.STRING.toArgb())
        setColor(DtsTokenColorIds.COMMENT, DtsSyntaxColors.COMMENT.toArgb())
        setColor(DtsTokenColorIds.NUMBER, DtsSyntaxColors.NUMBER.toArgb())
        setColor(DtsTokenColorIds.PREPROCESSOR, DtsSyntaxColors.PREPROC.toArgb())
        setColor(DtsTokenColorIds.NODE, DtsSyntaxColors.NODE.toArgb())
        setColor(DtsTokenColorIds.PROPERTY, DtsSyntaxColors.PROPERTY.toArgb())
        setColor(DtsTokenColorIds.PHANDLE, DtsSyntaxColors.PHANDLE.toArgb())
        setColor(DtsTokenColorIds.BRACKET, DtsSyntaxColors.BRACKET.toArgb())
        setColor(DtsTokenColorIds.LABEL_REF, DtsSyntaxColors.PHANDLE.toArgb())
    }
}
