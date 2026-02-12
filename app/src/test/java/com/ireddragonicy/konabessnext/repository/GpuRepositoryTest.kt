package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.core.interfaces.FileDataSource
import com.ireddragonicy.konabessnext.model.ChipDefinition
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.utils.DtsTreeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class GpuRepositoryTest {

    private lateinit var repository: GpuRepository
    private lateinit var fakeDeviceRepository: FakeDeviceRepository
    private lateinit var fakeChipRepository: FakeChipRepository
    private lateinit var fakeFileDataSource: FakeFileDataSource
    
    private lateinit var dtsFileRepository: DtsFileRepository
    private lateinit var gpuDomainManager: GpuDomainManager
    private lateinit var historyManager: HistoryManager
    private lateinit var userMessageManager: com.ireddragonicy.konabessnext.utils.UserMessageManager
    
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDeviceRepository = FakeDeviceRepository()
        fakeChipRepository = FakeChipRepository()
        fakeFileDataSource = FakeFileDataSource()
        
        dtsFileRepository = DtsFileRepository(fakeFileDataSource, fakeDeviceRepository)
        gpuDomainManager = GpuDomainManager(fakeChipRepository)
        historyManager = HistoryManager()
        userMessageManager = com.ireddragonicy.konabessnext.utils.UserMessageManager() // Real instance logic is simple enough
        
        repository = GpuRepository(dtsFileRepository, gpuDomainManager, historyManager, fakeChipRepository, userMessageManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testUpdateParameterUndoRedo_UsesTunaFixture() = runBlocking {
        fakeChipRepository.setCurrentChip(createChipDefinition("tuna", "Tuna SoC"))
        val tunaLines = loadFixtureLines("Tuna0.txt")
        repository.updateContent(tunaLines, description = "Load Tuna fixture", addToHistory = false)

        repository.updateParameterInBin(
            binIndex = 0,
            levelIndex = 0,
            paramKey = "qcom,gpu-freq",
            newValue = "999999999"
        )

        val updatedText = repository.dtsLines.value.joinToString("\n")
        assertTrue(
            "Updated DTS should contain converted hex for 999999999",
            updatedText.contains("qcom,gpu-freq = <0x3b9ac9ff>;")
        )

        assertTrue("Undo should be available after update", repository.canUndo.value)
        repository.undo()
        assertEquals("Undo should restore exact original Tuna fixture", tunaLines, repository.dtsLines.value)

        assertTrue("Redo should be available after undo", repository.canRedo.value)
        repository.redo()
        val redoneText = repository.dtsLines.value.joinToString("\n")
        assertTrue(
            "Redo should re-apply edited frequency",
            redoneText.contains("qcom,gpu-freq = <0x3b9ac9ff>;")
        )
    }

    @Test
    fun testStructuralLevelOperations_UseSd860Fixture() = runBlocking {
        fakeChipRepository.setCurrentChip(createChipDefinition("sd860", "Snapdragon 860"))
        val sd860Lines = loadFixtureLines("sd860.txt")
        repository.updateContent(sd860Lines, description = "Load SD860 fixture", addToHistory = false)

        val initialRoot = parseCurrentTree()
        val initialBin0 = gpuDomainManager.findBinNode(initialRoot, 0)
            ?: throw AssertionError("Bin 0 not found in sd860 fixture")
        val initialLevelCount = levelNodes(initialBin0).size
        assertTrue("sd860 bin 0 should have multiple levels", initialLevelCount > 1)
        val initialPwrLevel = parseSingleCellIndex(
            initialBin0.getProperty("qcom,initial-pwrlevel")?.originalValue
                ?: throw AssertionError("Missing qcom,initial-pwrlevel in sd860 bin 0")
        )

        repository.addLevel(binIndex = 0, toTop = true)
        var root = parseCurrentTree()
        var bin0 = gpuDomainManager.findBinNode(root, 0)
            ?: throw AssertionError("Bin 0 missing after addLevel")
        var levels = levelNodes(bin0)
        assertEquals("Add top should insert one level", initialLevelCount + 1, levels.size)
        assertSequentialLevels(bin0)
        val pwrAfterAdd = parseSingleCellIndex(
            bin0.getProperty("qcom,initial-pwrlevel")?.originalValue
                ?: throw AssertionError("Missing qcom,initial-pwrlevel after addLevel")
        )
        assertEquals(
            "qcom,initial-pwrlevel should shift +1 after add at top",
            (initialPwrLevel + 1).coerceAtMost(levels.lastIndex),
            pwrAfterAdd
        )

        repository.duplicateLevelAt(binIndex = 0, levelIndex = 1)
        root = parseCurrentTree()
        bin0 = gpuDomainManager.findBinNode(root, 0)
            ?: throw AssertionError("Bin 0 missing after duplicateLevelAt")
        levels = levelNodes(bin0)
        assertEquals("Duplicate should add one level", initialLevelCount + 2, levels.size)
        assertSequentialLevels(bin0)
        val pwrAfterDuplicate = parseSingleCellIndex(
            bin0.getProperty("qcom,initial-pwrlevel")?.originalValue
                ?: throw AssertionError("Missing qcom,initial-pwrlevel after duplicateLevelAt")
        )
        val expectedAfterDuplicate = if (pwrAfterAdd >= 2) {
            (pwrAfterAdd + 1).coerceAtMost(levels.lastIndex)
        } else {
            pwrAfterAdd
        }
        assertEquals(
            "qcom,initial-pwrlevel should follow insertion shift when duplicating level 1",
            expectedAfterDuplicate,
            pwrAfterDuplicate
        )

        repository.deleteLevel(binIndex = 0, levelIndex = 0)
        root = parseCurrentTree()
        bin0 = gpuDomainManager.findBinNode(root, 0)
            ?: throw AssertionError("Bin 0 missing after deleteLevel")
        levels = levelNodes(bin0)
        assertEquals("Delete should remove one level", initialLevelCount + 1, levels.size)
        assertSequentialLevels(bin0)
        val pwrAfterDelete = parseSingleCellIndex(
            bin0.getProperty("qcom,initial-pwrlevel")?.originalValue
                ?: throw AssertionError("Missing qcom,initial-pwrlevel after deleteLevel")
        )
        val expectedAfterDelete = (pwrAfterDuplicate - 1)
            .coerceAtLeast(0)
            .coerceAtMost(levels.lastIndex)
        assertEquals(
            "qcom,initial-pwrlevel should shift down after deleting level 0",
            expectedAfterDelete,
            pwrAfterDelete
        )
    }

    @Test
    fun testUpdateGpuModelName_AstEdit_PreservesByteArraySyntax() = runBlocking {
        fakeChipRepository.setCurrentChip(createChipDefinition("tuna", "Tuna SoC"))
        val originalLines = loadFixtureLines("Tuna0.txt")
        repository.updateContent(originalLines, description = "Load Tuna fixture", addToHistory = false)

        repository.updateGpuModelName("Adreno840")
        val updatedText = repository.dtsLines.value.joinToString("\n")

        assertTrue(
            "AST rename should update qcom,gpu-model",
            updatedText.contains("qcom,gpu-model = \"Adreno840\";")
        )
        assertTrue(
            "Unrelated byte-array syntax must remain untouched",
            Regex("""qcom,eud-utmi-delay\s*=\s*\[[^\]]+\];""").containsMatchIn(updatedText)
        )
        assertTrue(
            "Another byte-array syntax should remain bracketed",
            Regex("""qcom,platform-strength-ctrl\s*=\s*\[[^\]]+\];""").containsMatchIn(updatedText)
        )
        assertTrue(
            "Hex bytes without 0x prefix should remain unsplit (1d, not '1 d')",
            Regex("""qcom,platform-regulator-settings\s*=\s*\[1d(?:\s+1d){4}\];""").containsMatchIn(updatedText)
        )
    }

    @Test
    fun testUpdateFrequency_AstEdit_PreservesByteArraySyntax() = runBlocking {
        fakeChipRepository.setCurrentChip(createChipDefinition("tuna", "Tuna SoC"))
        val originalLines = loadFixtureLines("Tuna0.txt")
        repository.updateContent(originalLines, description = "Load Tuna fixture", addToHistory = false)

        repository.updateParameterInBin(
            binIndex = 0,
            levelIndex = 0,
            paramKey = "qcom,gpu-freq",
            newValue = "999999999"
        )

        val updatedText = repository.dtsLines.value.joinToString("\n")
        assertTrue(
            "Frequency edit should be applied",
            updatedText.contains("qcom,gpu-freq = <0x3b9ac9ff>;")
        )
        assertTrue(
            "Byte-array syntax must remain untouched after AST frequency edit",
            Regex("""qcom,eud-utmi-delay\s*=\s*\[[^\]]+\];""").containsMatchIn(updatedText)
        )
        assertTrue(
            "Secondary byte-array should remain bracketed",
            Regex("""qcom,platform-strength-ctrl\s*=\s*\[[^\]]+\];""").containsMatchIn(updatedText)
        )
        assertTrue(
            "Regulator settings hex bytes must remain unsplit after AST frequency edit",
            Regex("""qcom,platform-regulator-settings\s*=\s*\[1d(?:\s+1d){4}\];""").containsMatchIn(updatedText)
        )
    }

    private fun createChipDefinition(id: String, name: String): ChipDefinition {
        return ChipDefinition(
            id = id,
            name = name,
            maxTableLevels = 16,
            ignoreVoltTable = false,
            minLevelOffset = 1,
            voltTablePattern = "qcom,gpu-pwrlevels",
            strategyType = "MULTI_BIN",
            levelCount = 16,
            levels = mapOf(),
            binDescriptions = null,
            needsCaTargetOffset = false,
            models = listOf(name)
        )
    }

    private fun loadFixtureLines(fileName: String): List<String> {
        val fixture = resolveFixture(fileName)
        return fixture.readLines()
    }

    private fun resolveFixture(fileName: String): File {
        val moduleRelative = File("src/test/$fileName")
        if (moduleRelative.exists()) return moduleRelative

        val projectRelative = File("app/src/test/$fileName")
        if (projectRelative.exists()) return projectRelative

        throw AssertionError("Fixture $fileName not found in src/test or app/src/test")
    }

    private fun parseCurrentTree(): DtsNode {
        return DtsTreeHelper.parse(repository.dtsLines.value.joinToString("\n"))
    }

    private fun levelNodes(binNode: DtsNode): List<DtsNode> {
        return binNode.children.filter { it.name.startsWith("qcom,gpu-pwrlevel@") }
    }

    private fun parseSingleCellIndex(rawValue: String): Int {
        val trimmed = rawValue.trim()
        val inner = trimmed.removePrefix("<").removeSuffix(">").trim()
        if (inner.isEmpty() || inner.contains(" ")) {
            throw AssertionError("Expected single-cell DTS value, got: $rawValue")
        }
        return if (inner.startsWith("0x", ignoreCase = true)) {
            inner.substring(2).toIntOrNull(16)
                ?: throw AssertionError("Invalid hex DTS value: $rawValue")
        } else {
            inner.toIntOrNull()
                ?: throw AssertionError("Invalid decimal DTS value: $rawValue")
        }
    }

    private fun assertSequentialLevels(binNode: DtsNode) {
        val levels = levelNodes(binNode)
        levels.forEachIndexed { index, levelNode ->
            assertEquals("Level name should be sequential", "qcom,gpu-pwrlevel@$index", levelNode.name)
            val regProp = levelNode.getProperty("reg")
                ?: throw AssertionError("Missing reg property at level index $index")
            val regIndex = parseSingleCellIndex(regProp.originalValue)
            assertEquals("reg should match level index", index, regIndex)
        }
    }
}

// --- Fakes ---

class FakeChipRepository : ChipRepositoryInterface {
    private val _definitions = MutableStateFlow<List<ChipDefinition>>(emptyList())
    override val definitions: StateFlow<List<ChipDefinition>> = _definitions.asStateFlow()

    private val _currentChip = MutableStateFlow<ChipDefinition?>(null)
    override val currentChip: StateFlow<ChipDefinition?> = _currentChip.asStateFlow()

    override fun loadDefinitions() {}
    override fun setCurrentChip(chip: ChipDefinition?) { _currentChip.value = chip }
    override fun getChipById(id: String): ChipDefinition? = null
    override fun getLevelsForCurrentChip(): IntArray = IntArray(0)
    override fun getLevelStringsForCurrentChip(): Array<String> = emptyArray()
}

class FakeDeviceRepository : DeviceRepositoryInterface {
    override var dtsPath: String? = "fake_path.dts"
    override fun tryRestoreLastChipset(): Boolean = false
    override fun getDtsFile(): File = File("fake_path.dts")
    override suspend fun getRunTimeGpuFrequencies(): List<Long> = emptyList()
}

class FakeFileDataSource : FileDataSource {
    override fun getFilesDir(): File = File("fake_files")
    override fun getNativeLibDir(): File = File("fake_files/lib")
    override fun getFile(path: String): File = File(path)
    override fun listFiles(dir: File, filter: ((File, String) -> Boolean)?): Array<File>? = emptyArray()
    override fun readLines(path: String): List<String> = emptyList()
    override fun writeLines(path: String, lines: List<String>) {}
}
