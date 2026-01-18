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

@Singleton
class GpuRepository @Inject constructor(
    private val deviceRepository: DeviceRepository
) {

    private val _bins = MutableStateFlow<List<Bin>>(emptyList())
    val bins: StateFlow<List<Bin>> = _bins.asStateFlow()

    private val _opps = MutableStateFlow<List<Opp>>(emptyList())
    val opps: StateFlow<List<Opp>> = _opps.asStateFlow()

    private val _linesInDts = MutableStateFlow<List<String>>(emptyList())
    val linesInDts: StateFlow<List<String>> = _linesInDts.asStateFlow()

    private var binPosition: Int = -1
    private var oppPosition: Int = -1

    // History
    private val MAX_HISTORY_SIZE = 50
    private val undoStack: Deque<Pair<EditorState, String>> = ArrayDeque()
    private val redoStack: Deque<Pair<EditorState, String>> = ArrayDeque()
    
    // History strings for UI
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    suspend fun loadTable() = withContext(Dispatchers.IO) {
        val path = deviceRepository.dtsPath ?: throw IOException("DTS Path not set")
        val lines = FileUtil.readLines(path)
        
        // Use a mutable list to strip lines as we decode
        val linesMutable = ArrayList(lines)
        
        // Reset positions
        binPosition = -1
        oppPosition = -1
        
        // Decode Freq Table (Bins) - This removes lines from linesMutable
        val newBins = decodeBins(linesMutable)
        _bins.value = newBins
        
        // Decode Voltage Table (Opps) - This removes lines from linesMutable
        val newOpps = decodeOpps(linesMutable)
        _opps.value = newOpps
        
        _linesInDts.value = linesMutable
        
        // Reset history
        undoStack.clear()
        redoStack.clear()
        updateHistoryState()
        _isDirty.value = false
    }

    private fun decode(lines: List<String>) {
        val newBins = ArrayList<Bin>()
        var i = -1
        // Make a mutable copy for decoding if architecture modifies it (it shouldn't, but logic in GpuTableEditor passed lines directly)
        val linesMutable = ArrayList(lines)
        
        while (++i < linesMutable.size) {
            val thisLine = linesMutable[i].trim()
            try {
                if (ChipInfo.which.architecture.isStartLine(thisLine)) {
                    if (binPosition < 0) binPosition = i
                    ChipInfo.which.architecture.decode(linesMutable, newBins, i)
                    // Logic in original code: i-- because decode consumes lines? 
                    // Wait, ChipArchitecture.decode usually parses multiple lines.
                    // The loop continues. If decode advanced logic, great.
                    // Original code: i-- inside the if block. This implies decode doesn't advance 'i' or we need to re-evaluate?
                    // "ChipInfo.which.architecture.decode(lines_in_dts, bins, i);"
                    // Looking at Java code:
                    /*
                    if (ChipInfo.which.architecture.isStartLine(this_line)) {
                        if (bin_position < 0)
                            bin_position = i;
                        ChipInfo.which.architecture.decode(lines_in_dts, bins, i);
                        i--;
                    }
                    */
                    // This creates an infinite loop if decode doesn't modify lines or some state, 
                    // OR 'i' is only decremented to re-check something?
                    // Actually, 'decode' probably modifies 'bins'. 
                    // AND 'decode' doesn't return new index. 
                    // Wait, if it decrements 'i', it means it wants to re-process current line? No.
                    // Ah, maybe 'decode' removes lines? No.
                    // Let's assume the Java logic is correct and just replicate it carefully or trust the loop handles it.
                    // Actually, if 'decode' parses the block starting at 'i', it should skip those lines in the loop.
                    // But 'i' is the loop variable. IF 'decode' doesn't update 'i' (it's passed as value), then loop increments 'i'.
                    // So decrementing 'i' means next loop iteration 'i' is same as before. That would be infinite loop unless block specific logic.
                    // MAYBE 'decode' modifies 'lines_in_dts'?? Unlikely.
                    // Let's assume standard parsing.
                    // I will replicate the "i--" for now, assuming Side Effects or strict porting.
                    i-- 
                    // Wait, if I do i--, next loop ++i brings it back to same line.
                    // Does ChipInfo.which.architecture.decode consume lines from the list? 
                    // Java: decode(lines_in_dts, bins, i)
                    // If it modifies lines_in_dts (removes them), then 'i' points to next line.
                    // But 'decode' usually reads.
                    // I will NOT execute this loop blindly.
                    // But I have to replace GpuTableEditor.
                    // I will assume for now that simply populating 'bins' is the goal.
                    // Let's protect against infinite loop by breaking if bins don't increase or i doesn't move.
                    break // Assuming only one table block or handled internally.
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _bins.value = newBins
        
        // Reset history
        undoStack.clear()
        redoStack.clear()
        updateHistoryState()
        _isDirty.value = false
    }

    suspend fun saveTable() = withContext(Dispatchers.IO) {
        val path = deviceRepository.dtsPath ?: throw IOException("DTS Path not set")
        val newDts = genBack()
        FileUtil.writeLines(path, newDts)
        _isDirty.value = false
    }

    private fun genBack(): List<String> {
        val newDts = ArrayList(_linesInDts.value)
        val sortedInsertions = ArrayList<Pair<Int, List<String>>>()
        
        // Collect insertions
        if (_bins.value.isNotEmpty()) {
             // If binPosition is valid use it, else append? Or 0?
             val pos = if (binPosition >= 0 && binPosition <= newDts.size) binPosition else newDts.size
             sortedInsertions.add(pos to genTableBins())
        }
        
        if (_opps.value.isNotEmpty()) {
             val pos = if (oppPosition >= 0 && oppPosition <= newDts.size) oppPosition else newDts.size
             sortedInsertions.add(pos to genTableOpps())
        }
        
        // Sort by position descending to avoid shifting indices issues if we insert multiple
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

    // --- State Management ---

    fun captureState(): EditorState {
        return EditorState(
            ArrayList(_linesInDts.value),
            ArrayList(_bins.value.map { it.copyBin() }),
            binPosition,
            ArrayList(_opps.value.map { it.copy() }),
            oppPosition
        )
    }

    fun saveState(description: String) {
        pushUndoState(captureState(), description)
        redoStack.clear()
        updateHistoryState()
    }

    fun modifyBins(newBins: List<Bin>, description: String = "Modify Bins") {
        pushUndoState(captureState(), description)
        _bins.value = ArrayList(newBins)
        _isDirty.value = true
        redoStack.clear()
        updateHistoryState()
    }

    fun modifyOpps(newOpps: List<Opp>, description: String = "Modify Opps") {
        pushUndoState(captureState(), description)
        _opps.value = ArrayList(newOpps)
        _isDirty.value = true
        redoStack.clear()
        updateHistoryState()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val currentSnapshot = captureState()
        // If we undo, we push current state to redo stack with "Undo" description?
        // Usually redo stack keeps the description of the action we undid.
        // Let's pop from undo stack (which has state BEFORE action).
        
        val previous = undoStack.pop()
        val description = previous.second
        
        // Push current to redo with description
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
         // Update history list for UI
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
        while (++i < linesMutable.size) {
            val thisLine = linesMutable[i].trim()
            try {
                if (ChipInfo.which.architecture.isStartLine(thisLine)) {
                    if (binPosition < 0) binPosition = i
                    ChipInfo.which.architecture.decode(linesMutable, newBins, i)
                    // Since decode modifies list (removes), we need to step back to process next line correctly
                    i-- // Decrement i to stay on the match as next loop increments it
                    // break // Removed to allow parsing multiple bins 
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return newBins
    }

    private fun decodeOpps(linesMutable: ArrayList<String>): List<Opp> {
        val newOpps = ArrayList<Opp>()
        val pattern = ChipInfo.which.voltTablePattern ?: return emptyList()
        
        var i = -1
        var isInGpuTable = false
        var bracket = 0
        var start = -1
        var foundPosition = -1
        
        // Iterate and remove
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
                     // Found a closed opp block
                     try {
                         val subList = linesMutable.subList(start, i + 1)
                         // Create copy for parsing because we clear subList
                         val subListCopy = ArrayList(subList)
                         newOpps.add(parseOpp(subListCopy))
                         subList.clear() // Removes from linesMutable
                         i = start - 1 // Reset index
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
        return ChipInfo.which.architecture.generateTable(_bins.value as ArrayList<Bin>)
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
