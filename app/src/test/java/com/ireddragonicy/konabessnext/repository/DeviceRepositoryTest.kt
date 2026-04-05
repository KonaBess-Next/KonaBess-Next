package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.core.interfaces.AssetDataSource
import com.ireddragonicy.konabessnext.core.interfaces.FileDataSource
import com.ireddragonicy.konabessnext.core.interfaces.SystemPropertySource
import com.ireddragonicy.konabessnext.core.model.AppError
import com.ireddragonicy.konabessnext.core.model.DomainResult
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
        every { shellRepository.isRootMode } returns true
        coEvery { shellRepository.isRootAvailable() } returns false
        
        // When
        val result = deviceRepository.checkDevice()
        
        // Then
        assertTrue(result is DomainResult.Failure)
        assertTrue((result as DomainResult.Failure).error is AppError.RootAccessError)

        // Verify root check happened
        coVerify(exactly = 1) { shellRepository.isRootAvailable() }
        
        // Verify Error Emitted
        verify { userMessageManager.emitError("Root Access Required", any()) }
        
        // Verify NO extensive setup commands ran
        coVerify(exactly = 0) { shellRepository.execAndCheck(any()) }
    }

    @Test
    fun testWriteBootImage_ReturnsIoErrorIfImageTooLarge() = runTest {
        // Given
        val testDir = File("build/tmp/test_files")
        testDir.mkdirs()
        val bootNew = File(testDir, "boot_new.img")
        bootNew.writeBytes(ByteArray(1024)) // 1024 bytes
        
        every { fileDataSource.getFilesDir() } returns testDir
        // Mock system properties to resolve partition path
        every { systemPropertySource.get("ro.boot.slot_suffix", "") } returns "_a"
        every { shellRepository.isRootMode } returns true
        coEvery { shellRepository.isRootAvailable() } returns true
        
        // Mock execution of dd to get boot image (prefetch) and the write command
        coEvery { shellRepository.execAndCheck(any()) } returns true
        
        // Mock blockdev returning a SMALLER size(e.g. 512 bytes)
        coEvery { shellRepository.execForOutput(any()) } returns listOf("512")

        // First we must ensure bootName is set.
        val getBootResult = deviceRepository.getBootImage()
        assertTrue(getBootResult is DomainResult.Success)

        // When
        val writeResult = deviceRepository.writeBootImage()

        // Then
        assertTrue(writeResult is DomainResult.Failure)
        val error = (writeResult as DomainResult.Failure).error
        assertTrue(error is AppError.IoError)
        assertTrue(
            "Message should contain 'exceeds partition size' but was: ${error.message}",
            error.message.contains("exceeds partition size")
        )

        // Cleanup
        bootNew.delete()
    }
}
