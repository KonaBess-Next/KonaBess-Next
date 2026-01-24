package com.ireddragonicy.konabessnext.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetsUtil {
    @Throws(IOException::class)
    fun exportFiles(context: Context, src: String, out: String) {
        val fileNames = context.assets.list(src)
        if (fileNames != null && fileNames.isNotEmpty()) {
            val file = File(out)
            file.mkdirs()
            for (fileName in fileNames) {
                exportFiles(context, "$src/$fileName", "$out/$fileName")
            }
        } else {
            val parentVal = File(out).parentFile
            if (parentVal != null && !parentVal.exists()) {
                parentVal.mkdirs()
            }
            context.assets.open(src).use { inputStream ->
                FileOutputStream(File(out)).use { fos ->
                    val buffer = ByteArray(1024)
                    var byteCount: Int
                    while (inputStream.read(buffer).also { byteCount = it } != -1) {
                        fos.write(buffer, 0, byteCount)
                    }
                    fos.flush()
                }
            }
        }
    }
}
