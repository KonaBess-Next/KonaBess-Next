package com.ireddragonicy.konabessnext.core.impl

import android.content.Context
import com.ireddragonicy.konabessnext.core.interfaces.FileDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FilenameFilter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFileDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) : FileDataSource {
    override fun getFilesDir(): File {
        return context.filesDir
    }

    override fun getFile(path: String): File {
        return File(path)
    }

    override fun listFiles(dir: File, filter: ((File, String) -> Boolean)?): Array<File>? {
        return if (filter == null) {
            dir.listFiles()
        } else {
            dir.listFiles { d, name -> filter(d, name) }
        }
    }

    override fun readLines(path: String): List<String> {
        return File(path).readLines()
    }

    override fun writeLines(path: String, lines: List<String>) {
        File(path).writeText(lines.joinToString("\n"))
    }
}
