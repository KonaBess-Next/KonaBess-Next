package com.ireddragonicy.konabessnext.utils

import android.util.Log

/**
 * Centralized debug logger for DTS Editor memory leak and performance diagnostics.
 *
 * Enable via: DtsEditorDebug.enabled = true
 * All logs use tag "DtsEditorDebug" — filter in Logcat with: DtsEditorDebug
 *
 * Categories:
 *   [EDITOR]    — DtsEditor composable (stableLines, save, recomposition)
 *   [VIEWMODEL] — SharedDtsViewModel (updateFromText, lint, reformat)
 *   [REPO]      — GpuRepository (updateContent, flows, tree parse)
 *   [SESSION]   — EditorSession (highlight cache)
 *   [MEMORY]    — Heap usage snapshots
 *   [LEAK]      — Detected leak indicators
 */
object DtsEditorDebug {
    
    /** Master switch — set to true to enable all debug logging */
    var enabled = true
    
    private const val TAG = "DtsEditorDebug"
    
    // Counters for leak detection
    private var treeParseJobCount = 0
    private var updateContentCallCount = 0
    private var updateFromTextCallCount = 0
    private var requestSaveCallCount = 0
    private var buildContentCallCount = 0
    private var onContentChangedCallCount = 0
    private var editorSessionUpdateCallCount = 0
    private var lintJobStartCount = 0
    private var lintJobCancelCount = 0
    private var externalSyncCount = 0
    private var externalSyncRebuildCount = 0
    
    // ==================== EDITOR ====================
    
    fun logRequestSave() {
        if (!enabled) return
        requestSaveCallCount++
        Log.d(TAG, "[EDITOR] requestSave() #$requestSaveCallCount")
    }
    
    fun logBuildContent(lineCount: Int, durationMs: Long, contentSize: Int) {
        if (!enabled) return
        buildContentCallCount++
        Log.d(TAG, "[EDITOR] buildContent() #$buildContentCallCount | lines=$lineCount | chars=$contentSize | ${durationMs}ms")
    }
    
    fun logOnContentChanged(contentSize: Int) {
        if (!enabled) return
        onContentChangedCallCount++
        Log.d(TAG, "[EDITOR] onContentChanged() #$onContentChangedCallCount | chars=$contentSize")
    }
    
    fun logExternalSync(contentSize: Int, willRebuild: Boolean) {
        if (!enabled) return
        externalSyncCount++
        if (willRebuild) externalSyncRebuildCount++
        val action = if (willRebuild) "REBUILDING stableLines" else "SKIPPED (content matches)"
        Log.w(TAG, "[EDITOR] externalSync #$externalSyncCount | chars=$contentSize | $action | totalRebuilds=$externalSyncRebuildCount")
    }
    
    fun logStableLines(count: Int, event: String) {
        if (!enabled) return
        Log.d(TAG, "[EDITOR] stableLines=$count | event=$event")
    }
    
    fun logEditorRecomposition(lineIndex: Int) {
        if (!enabled) return
        // Only log every 100th to avoid spam
        if (lineIndex % 100 == 0) {
            Log.v(TAG, "[EDITOR] EditorLineRow recomposing line=$lineIndex")
        }
    }
    
    // ==================== VIEWMODEL ====================
    
    fun logUpdateFromText(contentSize: Int, lineCount: Int) {
        if (!enabled) return
        updateFromTextCallCount++
        Log.d(TAG, "[VIEWMODEL] updateFromText() #$updateFromTextCallCount | chars=$contentSize | lines=$lineCount")
        logMemory("after updateFromText")
    }
    
    fun logLintJobStart() {
        if (!enabled) return
        lintJobStartCount++
        Log.d(TAG, "[VIEWMODEL] lintJob STARTED #$lintJobStartCount (active jobs: ${lintJobStartCount - lintJobCancelCount})")
    }
    
    fun logLintJobCancel() {
        if (!enabled) return
        lintJobCancelCount++
        Log.d(TAG, "[VIEWMODEL] lintJob CANCELLED #$lintJobCancelCount")
    }
    
    fun logLintJobComplete(errorCount: Int, durationMs: Long) {
        if (!enabled) return
        Log.d(TAG, "[VIEWMODEL] lintJob COMPLETE | errors=$errorCount | ${durationMs}ms")
    }
    
    fun logReformat(inputSize: Int, outputSize: Int, durationMs: Long) {
        if (!enabled) return
        Log.d(TAG, "[VIEWMODEL] reformat | input=$inputSize | output=$outputSize | ${durationMs}ms")
    }
    
    // ==================== REPOSITORY ====================
    
    fun logUpdateContent(lineCount: Int, description: String, addToHistory: Boolean) {
        if (!enabled) return
        updateContentCallCount++
        Log.d(TAG, "[REPO] updateContent() #$updateContentCallCount | lines=$lineCount | history=$addToHistory | desc=\"$description\"")
    }
    
    fun logUpdateContentSkipped(reason: String) {
        if (!enabled) return
        Log.d(TAG, "[REPO] updateContent() SKIPPED: $reason")
    }
    
    fun logTreeParseJobStart() {
        if (!enabled) return
        treeParseJobCount++
        Log.w(TAG, "[REPO] treeParse job STARTED #$treeParseJobCount ← check if previous was cancelled!")
    }
    
    fun logTreeParseJobComplete(durationMs: Long, nodeCount: Int) {
        if (!enabled) return
        Log.d(TAG, "[REPO] treeParse job COMPLETE | nodes=$nodeCount | ${durationMs}ms")
    }
    
    fun logTreeParseJobCancelled() {
        if (!enabled) return
        Log.d(TAG, "[REPO] treeParse job CANCELLED (old one replaced)")
    }
    
    fun logHistorySnapshot(oldLineCount: Int, newLineCount: Int) {
        if (!enabled) return
        val estimatedBytes = (oldLineCount + newLineCount) * 40 // rough estimate
        Log.d(TAG, "[REPO] historySnapshot | oldLines=$oldLineCount | newLines=$newLineCount | ~${estimatedBytes / 1024}KB")
    }
    
    fun logFlowTriggered(flowName: String, lineCount: Int) {
        if (!enabled) return
        Log.d(TAG, "[REPO] flow '$flowName' triggered | lines=$lineCount")
    }

    fun logDisplayUpdate(action: String, panelNode: String, timingIdx: Int, success: Boolean, details: String) {
        if (!enabled) return
        Log.w(TAG, "[REPO] DisplayUpdate: $action | panel=$panelNode | timing=$timingIdx | success=$success | $details")
    }

    fun logDomainSearch(action: String, panelNode: String, fragmentIndex: Int, resultNode: String?) {
        if (!enabled) return
        Log.v(TAG, "[DOMAIN] Search $action | queryPanel=$panelNode | queryFrag=$fragmentIndex | found=${resultNode ?: "NULL"}")
    }
    
    // ==================== EDITOR SESSION ====================
    
    fun logEditorSessionUpdate(lineCount: Int, searchQuery: String) {
        if (!enabled) return
        editorSessionUpdateCallCount++
        Log.d(TAG, "[SESSION] update() #$editorSessionUpdateCallCount | lines=$lineCount | query=\"$searchQuery\"")
    }
    
    fun logEditorSessionHighlightBatch(batchSize: Int, totalProcessed: Int, totalLines: Int) {
        if (!enabled) return
        Log.v(TAG, "[SESSION] highlight batch | batch=$batchSize | processed=$totalProcessed/$totalLines")
    }
    
    fun logEditorSessionComplete(linesHighlighted: Int, totalLines: Int, durationMs: Long) {
        if (!enabled) return
        Log.d(TAG, "[SESSION] update COMPLETE | highlighted=$linesHighlighted/$totalLines | ${durationMs}ms")
    }
    
    fun logEditorSessionCacheSize(highlightCacheSize: Int, signatureCacheSize: Int) {
        if (!enabled) return
        Log.d(TAG, "[SESSION] cacheSize | highlight=$highlightCacheSize | signature=$signatureCacheSize")
    }
    
    // ==================== MEMORY ====================
    
    fun logMemory(context: String) {
        if (!enabled) return
        val runtime = Runtime.getRuntime()
        val maxMem = runtime.maxMemory() / 1024 / 1024
        val totalMem = runtime.totalMemory() / 1024 / 1024
        val freeMem = runtime.freeMemory() / 1024 / 1024
        val usedMem = totalMem - freeMem
        Log.i(TAG, "[MEMORY] $context | used=${usedMem}MB | free=${freeMem}MB | total=${totalMem}MB | max=${maxMem}MB")
    }
    
    // ==================== LEAK DETECTION ====================
    
    fun logLeakWarning(source: String, message: String) {
        if (!enabled) return
        Log.e(TAG, "[LEAK] ⚠️ $source: $message")
    }
    
    /**
     * Call periodically or on-demand to dump all counters.
     * Helps identify if operations are firing too frequently.
     */
    fun dumpCounters() {
        if (!enabled) return
        Log.w(TAG, "╔══════════════════════════════════════════════")
        Log.w(TAG, "║ DtsEditorDebug COUNTER DUMP")
        Log.w(TAG, "╠══════════════════════════════════════════════")
        Log.w(TAG, "║ requestSave:          $requestSaveCallCount")
        Log.w(TAG, "║ buildContent:         $buildContentCallCount")
        Log.w(TAG, "║ onContentChanged:     $onContentChangedCallCount")
        Log.w(TAG, "║ updateFromText:       $updateFromTextCallCount")
        Log.w(TAG, "║ updateContent (repo): $updateContentCallCount")
        Log.w(TAG, "║ treeParseJobs:        $treeParseJobCount")
        Log.w(TAG, "║ lintJobStart:         $lintJobStartCount")
        Log.w(TAG, "║ lintJobCancel:        $lintJobCancelCount")
        Log.w(TAG, "║ editorSessionUpdate:  $editorSessionUpdateCallCount")
        Log.w(TAG, "║ externalSync:         $externalSyncCount")
        Log.w(TAG, "║ externalSyncRebuild:  $externalSyncRebuildCount")
        Log.w(TAG, "╚══════════════════════════════════════════════")
        logMemory("DUMP")
    }
    
    fun resetCounters() {
        treeParseJobCount = 0
        updateContentCallCount = 0
        updateFromTextCallCount = 0
        requestSaveCallCount = 0
        buildContentCallCount = 0
        onContentChangedCallCount = 0
        editorSessionUpdateCallCount = 0
        lintJobStartCount = 0
        lintJobCancelCount = 0
        externalSyncCount = 0
        externalSyncRebuildCount = 0
    }
}
