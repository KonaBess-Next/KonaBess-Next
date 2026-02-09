package com.ireddragonicy.konabessnext.core.interfaces

import java.io.File

interface FileDataSource {
    fun getFilesDir(): File
    fun getNativeLibDir(): File
    fun getFile(path: String): File
    fun listFiles(dir: File, filter: ((File, String) -> Boolean)? = null): Array<File>?
    fun readLines(path: String): List<String>
    fun writeLines(path: String, lines: List<String>)
}
