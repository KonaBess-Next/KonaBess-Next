package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.core.interfaces.FileDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DtsFileRepository @Inject constructor(
    private val fileDataSource: FileDataSource,
    private val deviceRepository: DeviceRepositoryInterface
) {
    suspend fun loadDtsLines(): List<String> = withContext(Dispatchers.IO) {
        val path = deviceRepository.dtsPath ?: 
                   (if (deviceRepository.tryRestoreLastChipset()) deviceRepository.dtsPath else null) ?: 
                   throw IOException("DTS Path not set")
        
        return@withContext fileDataSource.readLines(path)
    }

    suspend fun saveDtsLines(lines: List<String>) = withContext(Dispatchers.IO) {
        val path = deviceRepository.dtsPath ?: throw IOException("DTS Path not set")
        fileDataSource.writeLines(path, lines)
    }
}
