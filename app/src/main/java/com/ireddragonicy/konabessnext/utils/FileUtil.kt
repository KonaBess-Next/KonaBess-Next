package com.ireddragonicy.konabessnext.utils

import java.io.File
import java.io.IOException

/**
 * Centralized File I/O utilities to replace scattered logic in Editors and Core.
 */
object FileUtil {

    @JvmStatic
    @Throws(IOException::class)
    fun readLines(path: String): List<String> {
        val file = File(path)
        return if (!file.exists()) ArrayList() else file.readLines()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeLines(path: String, lines: List<String>) {
        val file = File(path)
        prepareFileForWrite(file)
        file.bufferedWriter().use { writer ->
            for (line in lines) {
                writer.write(line)
                writer.newLine()
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeString(path: String, content: String) {
        val file = File(path)
        prepareFileForWrite(file)
        file.writeText(content)
    }

    @Throws(IOException::class)
    private fun prepareFileForWrite(file: File) {
        if (file.exists()) {
            if (!file.delete()) {
                file.setWritable(true)
                if (!file.delete()) {
                    // Try best effort
                }
            }
        }

        if (!file.exists() && !file.createNewFile()) {
            throw IOException("Failed to create file: " + file.absolutePath)
        }

        file.setReadable(true, false)
        file.setWritable(true, false)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyFile(source: File, dest: File) {
        source.copyTo(dest, overwrite = true)
    }
    
    // Helper to accept string paths
    @JvmStatic
    @Throws(IOException::class)
    fun copyFile(sourcePath: String, destPath: String) {
        copyFile(File(sourcePath), File(destPath))
    }
}
