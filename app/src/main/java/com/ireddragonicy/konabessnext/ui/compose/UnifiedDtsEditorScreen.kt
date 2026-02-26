package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ireddragonicy.konabessnext.viewmodel.TextEditorViewModel

@Composable
fun UnifiedDtsEditorScreen(
    textViewModel: TextEditorViewModel = hiltViewModel(),
    onSelectionDragStateChanged: (Boolean) -> Unit = {}
) {
    val dtsContent by textViewModel.dtsContent.collectAsState()
    val lintErrorCount by textViewModel.lintErrorCount.collectAsState()
    val lintErrors by textViewModel.lintErrors.collectAsState()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val soraEditorState = rememberSoraEditorState()

    Column {
        SearchAndToolsBar(
            query = soraEditorState.searchQuery,
            matchCount = soraEditorState.matchCount,
            currentMatchIndex = soraEditorState.currentMatchIndex,
            onQueryChange = { soraEditorState.search(it) },
            onNext = { soraEditorState.gotoNext() },
            onPrev = { soraEditorState.gotoPrevious() },
            onCopyAll = {
                clipboardManager.setText(AnnotatedString(soraEditorState.copyAllText()))
            },
            onReformat = { textViewModel.reformatCode() },
            isWordWrapEnabled = soraEditorState.isWordWrapEnabled,
            onToggleWordWrap = { soraEditorState.toggleWordWrap() },
            lintErrorCount = lintErrorCount,
            lintErrors = lintErrors,
            onLintErrorClick = { error ->
                // Navigate to error line in Sora editor
                soraEditorState.editor?.let { editor ->
                    val line = (error.line - 1).coerceAtLeast(0)
                    val col = (error.column - 1).coerceAtLeast(0)
                    val maxLine = editor.text.lineCount - 1
                    val safeL = line.coerceAtMost(maxLine)
                    val maxCol = editor.text.getColumnCount(safeL)
                    val safeC = col.coerceAtMost(maxCol)
                    editor.setSelection(safeL, safeC)
                }
            }
        )

        DtsEditor(
            content = dtsContent,
            soraEditorState = soraEditorState,
            onContentChanged = { newText ->
                textViewModel.updateFromText(newText, "Raw Edit")
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
