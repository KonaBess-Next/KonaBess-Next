package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.core.interfaces.FileDataSource
import com.ireddragonicy.konabessnext.core.strategy.ChipArchitecture
import com.ireddragonicy.konabessnext.core.strategy.MultiBinStrategy
import com.ireddragonicy.konabessnext.model.ChipDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class GpuRepositoryTest {

    private lateinit var repository: GpuRepository
    private lateinit var fakeDeviceRepository: FakeDeviceRepository
    private lateinit var fakeChipRepository: FakeChipRepository
    private lateinit var fakeFileDataSource: FakeFileDataSource
    
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDeviceRepository = FakeDeviceRepository()
        fakeChipRepository = FakeChipRepository()
        fakeFileDataSource = FakeFileDataSource()
        
        repository = GpuRepository(fakeDeviceRepository, fakeChipRepository, fakeFileDataSource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSSOT_Synchronization_And_UndoRedo() = kotlinx.coroutines.runBlocking {
        println("Test Started")
        
        // 1. Setup Fake Chip Definition (MultiBin Strategy)
        val chipDef = ChipDefinition(
            id = "tuna",
            name = "Tuna SoC",
            maxTableLevels = 10,
            ignoreVoltTable = false,
            minLevelOffset = 1,
            voltTablePattern = "qcom,gpu-pwrlevels",
            strategyType = "MULTI_BIN",
            levelCount = 10,
            levels = mapOf(), 
            binDescriptions = null,
            needsCaTargetOffset = false,
            models = listOf("Tuna")
        )
        fakeChipRepository.setCurrentChip(chipDef)
        
        // 2. Load Fallback Content directly (Bypass file read issues)
        val lines = listOf(
            "/dts-v1/;",
            "/ {",
            "\tmodel = \"Tuna SoC\";",
            "\tcompatible = \"qcom,tuna\";",
            "\tqcom,gpu-pwrlevels-0 {",
            "\t\t#address-cells = <1>;",
            "\t\t#size-cells = <0>;",
            "\t\tcompatible = \"qcom,gpu-pwrlevels\";",
            "\t\tqcom,gpu-pwrlevel@0 {",
            "\t\t\treg = <0>;",
            "\t\t\tqcom,gpu-freq = <0x3bbd3000>;", // 1002229760 Hz
            "\t\t\tqcom,bus-freq = <0xa>;",
            "\t\t};",
            "\t};",
            "};"
        )
        
        // initializing repo with content
        repository.updateContent(lines)

        // --- Initial State Check ---
        val initialBins = repository.bins.filter { it.isNotEmpty() }.first()
        println("Initial Bins Count: ${initialBins.size}")
        assertTrue("Should have parsed bins", initialBins.isNotEmpty())
        
        val targetBinIndex = 0
        val targetLevelIndex = 0
        val targetBin = initialBins[0]
        val targetLevel = targetBin.levels.firstOrNull()
        assertNotNull("Bin 0 Level 0 should exist", targetLevel)
        
        val initialFreq = targetLevel!!.frequency
        
        // --- SCENARIO 1: Change Frequency (GUI Edit) ---
        val newFreqHz = 999000000L
        val newFreqHex = "0x" + java.lang.Long.toHexString(newFreqHz)
        
        repository.updateParameterInBin(targetBinIndex, targetLevelIndex, "qcom,gpu-freq", newFreqHex)

        // Verify Text (SSOT)
        val updatedLines = repository.dtsLines.first()
        val freqLineFound = updatedLines.any { it.contains(newFreqHex) }
        assertTrue("Text should contain new freq hex", freqLineFound)
        
        // Verify GUI Model (Derived Sync)
        val updatedBins = repository.bins.filter { 
            it.isNotEmpty() && it[0].levels[0].frequency == newFreqHz 
        }.first()
        val updatedFreq = updatedBins[0].levels[0].frequency
        assertEquals("GUI Model should reflect new frequency", newFreqHz, updatedFreq)

        // --- SCENARIO 2: Change Bus (Parameter Update) ---
        val newBusHex = "0x4d2" // 1234
        repository.updateParameterInBin(targetBinIndex, targetLevelIndex, "qcom,bus-freq", newBusHex)
        
        val updatedLines2 = repository.dtsLines.first()
        assertTrue("Text should contain new bus hex", updatedLines2.any { it.contains(newBusHex) })

        // --- SCENARIO 3: Undo/Redo ---
        assertTrue("Can Undo should be true", repository.canUndo.value)
        
        // Undo 1 (Revert Bus)
        repository.undo()
        val undo1Lines = repository.dtsLines.first()
        assertFalse("Text should NOT contain bus hex after undo", undo1Lines.any { it.contains(newBusHex) })
        assertTrue("Text should still contain freq hex", undo1Lines.any { it.contains(newFreqHex) })
        
        // Undo 2 (Revert Freq)
        repository.undo()
        val undo2Lines = repository.dtsLines.first()
        assertFalse("Text should NOT contain freq hex after undo 2", undo2Lines.any { it.contains(newFreqHex) })
        
        // Verify we are back to initial content
        val currentBins = repository.bins.filter { 
            it.isNotEmpty() && it[0].levels[0].frequency == initialFreq 
        }.first()
        assertEquals("Should be back to initial freq", initialFreq, currentBins[0].levels[0].frequency)
        
        // Redo 1 (Re-apply Freq)
        assertTrue("Can Redo should be true", repository.canRedo.value)
        repository.redo()
        val redo1Lines = repository.dtsLines.first()
        assertTrue("Text should contain freq hex after redo", redo1Lines.any { it.contains(newFreqHex) })
    }
}

// --- Fakes ---

class FakeChipRepository : ChipRepositoryInterface {
    private val _definitions = MutableStateFlow<List<ChipDefinition>>(emptyList())
    override val definitions: StateFlow<List<ChipDefinition>> = _definitions.asStateFlow()

    private val _currentChip = MutableStateFlow<ChipDefinition?>(null)
    override val currentChip: StateFlow<ChipDefinition?> = _currentChip.asStateFlow()
    
    private val multiBinStrategy = MultiBinStrategy()

    override fun loadDefinitions() {}
    override fun setCurrentChip(chip: ChipDefinition?) { _currentChip.value = chip }
    override fun getChipById(id: String): ChipDefinition? = null
    override fun getArchitecture(def: ChipDefinition?): ChipArchitecture = multiBinStrategy
    override fun getLevelsForCurrentChip(): IntArray = IntArray(0)
    override fun getLevelStringsForCurrentChip(): Array<String> = emptyArray()
}

class FakeDeviceRepository : DeviceRepositoryInterface {
    override var dtsPath: String? = "fake_path.dts"
    override fun tryRestoreLastChipset(): Boolean = false
    override fun getDtsFile(): File = File("fake_path.dts")
}

class FakeFileDataSource : FileDataSource {
    override fun getFilesDir(): File = File("fake_files")
    override fun getFile(path: String): File = File(path)
    override fun listFiles(dir: File, filter: ((File, String) -> Boolean)?): Array<File>? = emptyArray()
    override fun readLines(path: String): List<String> = emptyList()
    override fun writeLines(path: String, lines: List<String>) {}
}
