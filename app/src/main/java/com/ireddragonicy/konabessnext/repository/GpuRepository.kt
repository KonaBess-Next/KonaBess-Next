package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.core.ChipInfo
import com.ireddragonicy.konabessnext.model.Bin
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.model.EditorState
import com.ireddragonicy.konabessnext.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.ArrayDeque
import java.util.Deque
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flowOn

@Singleton
class GpuRepository @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val chipRepository: ChipRepository
) {
    sealed class ParseResult {
        object Loading : ParseResult()
        data class Success(val bins: List<Bin>) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }

    private val repoScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _dtsContent = MutableStateFlow("")
    val dtsContent: StateFlow<String> = _dtsContent.asStateFlow()

    private val _dtsLines = MutableStateFlow<List<String>>(emptyList())
    val dtsLines: StateFlow<List<String>> = _dtsLines.asStateFlow()

    private val _parsedResult = MutableStateFlow<ParseResult>(ParseResult.Loading)
    val parsedResult: StateFlow<ParseResult> = _parsedResult.asStateFlow()

    private val _bins = MutableStateFlow<List<Bin>>(emptyList())
    val bins: StateFlow<List<Bin>> = _bins.asStateFlow()
    
    private val _opps = MutableStateFlow<List<Opp>>(emptyList())
    val opps: StateFlow<List<Opp>> = _opps.asStateFlow()

    // Flag to ignore debounce trigger when content is updated programmatically
    @Volatile
    private var ignoreNextContentUpdate = false

    init {
        // Live Synchronization: Text -> Bins
        _dtsContent
            .debounce(500) // Increased debounce to 500ms to avoid rapid flicker
            .onEach { content ->
                if (ignoreNextContentUpdate) {
                    ignoreNextContentUpdate = false
                    return@onEach
                }
                
                if (content.isNotEmpty()) {
                    parseContentPartial(content)
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(repoScope)
    }

    suspend fun parseContentPartial(content: String) {
        // Don't set Loading state for partial text updates to avoid UI flickering
        // _parsedResult.value = ParseResult.Loading 
        
        try {
            val lines = content.split("\n")
            val linesMutable = ArrayList(lines)
            
            val currentChip = chipRepository.currentChip.value
            if (currentChip != null) {
                // Keep current positions or reset?
                // For text editing, we just re-parse structure.
                val newBins = decodeBins(linesMutable)
                
                _bins.value = newBins
                _parsedResult.value = ParseResult.Success(newBins)
                
                 val newOpps = decodeOpps(ArrayList(lines))
                 _opps.value = newOpps
                 
                 _linesInDts.value = linesMutable
            }
        } catch (e: Exception) {
            _parsedResult.value = ParseResult.Error(e.message ?: "Parse error")
        }
    }


    private val _linesInDts = MutableStateFlow<List<String>>(emptyList())
    val linesInDts: StateFlow<List<String>> = _linesInDts.asStateFlow()

    private var binPosition: Int = -1
    private var oppPosition: Int = -1

    // History
    private val MAX_HISTORY_SIZE = 50
    private val undoStack: Deque<Pair<EditorState, String>> = ArrayDeque()
    private val redoStack: Deque<Pair<EditorState, String>> = ArrayDeque()
    
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private var initialBins: List<Bin> = emptyList()
    private var initialOpps: List<Opp> = emptyList()
    private var initialDtsLines: List<String> = emptyList()

    private fun refreshIsDirty() {
        val binsChanged = _bins.value != initialBins
        val oppsChanged = _opps.value != initialOpps
        _isDirty.value = binsChanged || oppsChanged
    }

    private val _stateVersion = MutableStateFlow(0L)
    val stateVersion: StateFlow<Long> = _stateVersion.asStateFlow()

    suspend fun loadTable() = withContext(Dispatchers.IO) {
        _parsedResult.value = ParseResult.Loading
        try {
            val path = deviceRepository.dtsPath ?: throw IOException("DTS Path not set")
            val lines = FileUtil.readLines(path)
            
            // Set flag to ignore the next debounce trigger since we are loading valid data
            ignoreNextContentUpdate = true
            _dtsContent.value = lines.joinToString("\n")
            _dtsLines.value = lines

            val linesMutable = ArrayList(lines)
            
            binPosition = -1
            oppPosition = -1
            
            val newBins = decodeBins(linesMutable)
            _bins.value = newBins
            binPosition = if (binPosition < 0) 0 else binPosition
            
            val newOpps = decodeOpps(linesMutable)
            _opps.value = newOpps
            
            _linesInDts.value = linesMutable
            
            undoStack.clear()
            redoStack.clear()
            updateHistoryState()
            
            initialBins = EditorState.deepCopyBins(newBins)
            initialOpps = EditorState.deepCopyOpps(newOpps)
            initialDtsLines = ArrayList(linesMutable)
            
            _isDirty.value = false
            
            _parsedResult.value = ParseResult.Success(newBins)
        } catch (e: Exception) {
            _parsedResult.value = ParseResult.Error(e.message ?: "Failed to load table")
            throw e
        }
    }

    private fun decode(lines: List<String>) {
        val newBins = ArrayList<Bin>()
        var i = -1
        val linesMutable = ArrayList(lines)
        
        while (++i < linesMutable.size) {
            val thisLine = linesMutable[i].trim()
            try {
                val currentChip = chipRepository.currentChip.value
                val arch = ChipInfo.getArchitecture(currentChip)
                if (arch.isStartLine(thisLine)) {
                    if (binPosition < 0) binPosition = i
                    arch.decode(linesMutable, newBins, i)
                    break 
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _bins.value = newBins
        
        undoStack.clear()
        redoStack.clear()
        updateHistoryState()
        _isDirty.value = false
    }

    suspend fun saveTable() = withContext(Dispatchers.IO) {
        val path = deviceRepository.dtsPath ?: throw IOException("DTS Path not set")
        if (dtsIsStale) syncDts()
        
        val newDts = genBack()
        FileUtil.writeLines(path, newDts)
        
        initialBins = EditorState.deepCopyBins(_bins.value)
        initialOpps = EditorState.deepCopyOpps(_opps.value)
        initialDtsLines = ArrayList(_linesInDts.value)
        
        _isDirty.value = false
    }

    private fun genBack(): List<String> {
        val newDts = ArrayList(_linesInDts.value)
        val sortedInsertions = ArrayList<Pair<Int, List<String>>>()
        
        if (_bins.value.isNotEmpty()) {
             val pos = if (binPosition >= 0 && binPosition <= newDts.size) binPosition else newDts.size
             sortedInsertions.add(pos to genTableBins())
        }
        
        if (_opps.value.isNotEmpty()) {
             val pos = if (oppPosition >= 0 && oppPosition <= newDts.size) oppPosition else newDts.size
             sortedInsertions.add(pos to genTableOpps())
        }
        
        sortedInsertions.sortByDescending { it.first }
        
        for ((pos, lines) in sortedInsertions) {
             if (pos <= newDts.size) {
                 newDts.addAll(pos, lines)
             } else {
                 newDts.addAll(lines)
             }
        }
        
        return newDts
    }

    private fun updateGeneratedContent() {
        val newDts = genBack()
        _dtsLines.value = newDts
        
        ignoreNextContentUpdate = true
        _dtsContent.value = newDts.joinToString("\n")
    }

    fun captureState(): EditorState {
        return EditorState(
            ArrayList(_linesInDts.value),
            ArrayList(_bins.value.map { it.copyBin() }),
            binPosition,
            ArrayList(_opps.value.map { it.copy() }),
            oppPosition
        )
    }

    private var dtsIsStale = false

    fun syncDts() {
        if (dtsIsStale) {
            updateGeneratedContent()
            dtsIsStale = false
        }
    }

    fun saveState(description: String) {
        pushUndoState(captureState(), description)
        redoStack.clear()
        updateHistoryState()
        syncDts() 
    }

    fun modifyOpps(newOpps: List<Opp>, description: String = "Modify Opps") {
        pushUndoState(captureState(), description)
        _opps.value = ArrayList(newOpps)
        _isDirty.value = true
        redoStack.clear()
        updateHistoryState()
        dtsIsStale = true
        syncDts()
    }

    fun updateDtsContent(content: String, description: String) {
        pushUndoState(captureState(), description)
        
        ignoreNextContentUpdate = true // Avoid self-triggering debounce
        _dtsContent.value = content
        
        _isDirty.value = true
        redoStack.clear()
        updateHistoryState()
        dtsIsStale = false
    }
    
    fun updateBins(newBins: List<Bin>, description: String, regenerateDts: Boolean = true) {
        modifyBins(newBins, description, regenerateDts)
    }

    fun modifyBins(newBins: List<Bin>, description: String = "Modify Bins", regenerateDts: Boolean = true) {
        pushUndoState(captureState(), description)
        
        _bins.value = ArrayList(newBins)
        _parsedResult.value = ParseResult.Success(_bins.value)
        
        if (regenerateDts) {
            updateGeneratedContent()
            dtsIsStale = false
        } else {
            dtsIsStale = true
        }
        
        redoStack.clear()
        updateHistoryState()
        refreshIsDirty()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val currentSnapshot = captureState()
        
        val previous = undoStack.pop()
        val description = previous.second
        
        redoStack.push(currentSnapshot to description)
        
        restoreState(previous.first)
        updateHistoryState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val currentSnapshot = captureState()
        
        val next = redoStack.pop()
        val description = next.second
        
        undoStack.push(currentSnapshot to description)
        
        restoreState(next.first)
        updateHistoryState()
    }

    private fun restoreState(state: EditorState) {
        _linesInDts.value = state.linesInDts
        _bins.value = state.binsSnapshot
        binPosition = state.binPosition
        _opps.value = state.oppsSnapshot
        oppPosition = state.oppPosition
        
        // When restoring state, we trust it's valid, update UI immediately
        ignoreNextContentUpdate = true
        updateGeneratedContent()
        
        _parsedResult.value = ParseResult.Success(_bins.value)
        refreshIsDirty()
    }
    
    private fun pushUndoState(state: EditorState, description: String) {
        undoStack.push(state to description)
        while (undoStack.size > MAX_HISTORY_SIZE) {
            undoStack.removeLast()
        }
    }

    private fun updateHistoryState() {
         _canUndo.value = !undoStack.isEmpty()
         _canRedo.value = !redoStack.isEmpty()
         val list = ArrayList<String>()
         for (pair in undoStack) {
             list.add(pair.second)
         }
         _history.value = list
    }

    // --- Helpers ---

    private fun decodeBins(linesMutable: ArrayList<String>): List<Bin> {
        val newBins = ArrayList<Bin>()
        var i = -1
        // Safety Break for infinite loops
        var loopCount = 0
        val maxLoops = linesMutable.size * 2
        
        while (++i < linesMutable.size) {
            loopCount++
            if (loopCount > maxLoops) break
            
            val thisLine = linesMutable[i].trim()
            try {
                val currentChip = chipRepository.currentChip.value
                val arch = ChipInfo.getArchitecture(currentChip)
                if (arch.isStartLine(thisLine)) {
                    if (binPosition < 0) binPosition = i
                    arch.decode(linesMutable, newBins, i)
                    i-- 
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return newBins
    }

    private fun decodeOpps(linesMutable: ArrayList<String>): List<Opp> {
        val newOpps = ArrayList<Opp>()
        val currentChip = chipRepository.currentChip.value
        val pattern = currentChip?.voltTablePattern ?: return emptyList()
        
        var i = -1
        var isInGpuTable = false
        var bracket = 0
        var start = -1
        var foundPosition = -1
        
        while (++i < linesMutable.size) {
            val line = linesMutable[i].trim()
            if (line.isEmpty()) continue

            if (line.contains(pattern) && line.contains("{")) {
                isInGpuTable = true
                bracket++
                continue
            }

            if (!isInGpuTable) continue

            if (line.contains("opp-") && line.contains("{")) {
                start = i
                if (foundPosition < 0) foundPosition = i
                bracket++
                continue
            }

            if (line.contains("}")) {
                bracket--
                if (bracket == 0) break
                
                if (start != -1 && bracket == 1) {
                     try {
                         val subList = linesMutable.subList(start, i + 1)
                         val subListCopy = ArrayList(subList)
                         newOpps.add(parseOpp(subListCopy))
                         subList.clear() 
                         i = start - 1 
                         start = -1
                     } catch(e: Exception) {
                         e.printStackTrace()
                     }
                }
            }
        }
        if (foundPosition >= 0) oppPosition = foundPosition
        return newOpps
    }
    
    private fun parseOpp(lines: List<String>): Opp {
        val opp = Opp()
        for (line in lines) {
            if (line.contains("opp-hz")) {
                try {
                     opp.frequency = com.ireddragonicy.konabessnext.utils.DtsHelper.decode_int_line_hz(line).value
                } catch(e: Exception) {}
            }
            if (line.contains("opp-microvolt")) {
                try {
                    opp.volt = com.ireddragonicy.konabessnext.utils.DtsHelper.decode_int_line(line).value
                } catch(e: Exception) {}
            }
        }
        return opp
    }

    private fun genTableBins(): List<String> {
        val currentChip = chipRepository.currentChip.value
        return ChipInfo.getArchitecture(currentChip).generateTable(_bins.value as ArrayList<Bin>)
    }
    
    fun importFrequencyTable(lines: List<String>, description: String = "Import Frequency Table") {
        val mutableLines = ArrayList(lines)
        val newBins = decodeBins(mutableLines)
        if (newBins.isNotEmpty()) {
            modifyBins(newBins, description)
        }
    }
    
    fun importVoltageTable(lines: List<String>, description: String = "Import Voltage Table") {
        val mutableLines = ArrayList(lines)
        val newOpps = decodeOpps(mutableLines)
        if (newOpps.isNotEmpty()) {
            modifyOpps(newOpps, description)
        }
    }

    private fun genTableOpps(): List<String> {
        val table = ArrayList<String>()
        for (opp in _opps.value) {
            table.add("opp-${opp.frequency} {")
            table.add("opp-hz = <0x0 ${opp.frequency}>;")
            table.add("opp-microvolt = <${opp.volt}>;")
            table.add("};")
        }
        return table
    }
}