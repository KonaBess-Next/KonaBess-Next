package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.core.interfaces.AssetDataSource
import com.ireddragonicy.konabessnext.core.interfaces.FileDataSource
import com.ireddragonicy.konabessnext.core.interfaces.SystemPropertySource
import com.ireddragonicy.konabessnext.core.processor.BootImageProcessor
import com.ireddragonicy.konabessnext.utils.UserMessageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import io.mockk.*
import android.content.SharedPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRepositoryTest {

    private lateinit var deviceRepository: DeviceRepository
    private lateinit var fileDataSource: FileDataSource
    private lateinit var systemPropertySource: SystemPropertySource
    private lateinit var assetDataSource: AssetDataSource
    private lateinit var shellRepository: ShellRepository
    private lateinit var bootImageProcessor: BootImageProcessor
    private lateinit var prefs: SharedPreferences
    private lateinit var chipRepository: ChipRepository
    private lateinit var userMessageManager: UserMessageManager

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        fileDataSource = mockk(relaxed = true)
        systemPropertySource = mockk(relaxed = true)
        assetDataSource = mockk(relaxed = true)
        shellRepository = mockk(relaxed = true)
        bootImageProcessor = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        chipRepository = mockk(relaxed = true)
        userMessageManager = mockk(relaxed = true) // Mock it to verify calls

        every { fileDataSource.getFilesDir() } returns File("build/tmp/test_files")

        deviceRepository = DeviceRepository(
            fileDataSource,
            systemPropertySource,
            assetDataSource,
            shellRepository,
            bootImageProcessor,
            prefs,
            chipRepository,
            userMessageManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCheckDevice_NoRoot_EmitsError() = runTest {
        // Given
        coEvery { shellRepository.isRootAvailable() } returns false
        
        // When
        deviceRepository.checkDevice()
        
        // Then
        // Verify root check happened
        coVerify(exactly = 1) { shellRepository.isRootAvailable() }
        
        // Verify Error Emitted
        verify { userMessageManager.emitError("Root Access Required", any()) }
        
        // Verify NO extensive setup commands ran
        coVerify(exactly = 0) { shellRepository.exec("chmod -R 777 build/tmp/test_files") }
    }
}
