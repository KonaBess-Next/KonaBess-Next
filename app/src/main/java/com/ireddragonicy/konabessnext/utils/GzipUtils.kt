package com.ireddragonicy.konabessnext.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GzipUtils {

    @JvmStatic
    @Throws(IOException::class)
    fun compress(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) throw IOException("Empty byte array")
        
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            gzip.write(bytes)
        }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun uncompress(compressedStr: String?): String? {
        if (compressedStr == null) return null
        
        val compressed = Base64.getDecoder().decode(compressedStr)
        val out = ByteArrayOutputStream()
        
        ByteArrayInputStream(compressed).use { byteArrayInputStream ->
            GZIPInputStream(byteArrayInputStream).use { gzipInputStream ->
                val buffer = ByteArray(1024)
                var offset: Int
                while (gzipInputStream.read(buffer).also { offset = it } != -1) {
                    out.write(buffer, 0, offset)
                }
            }
        }
        return out.toString(StandardCharsets.UTF_8.name())
    }
    
    @JvmStatic
    fun uncompress(bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        
        // Try to decompress directly as gzip bytes (not Base64)
        return try {
            val out = ByteArrayOutputStream()
            ByteArrayInputStream(bytes).use { byteArrayInputStream ->
                GZIPInputStream(byteArrayInputStream).use { gzipInputStream ->
                    val buffer = ByteArray(1024)
                    var offset: Int
                    while (gzipInputStream.read(buffer).also { offset = it } != -1) {
                        out.write(buffer, 0, offset)
                    }
                }
            }
            out.toString(StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            // If gzip fails, try as Base64 encoded gzip string
            try {
                uncompress(String(bytes, StandardCharsets.ISO_8859_1))
            } catch (e2: Exception) {
                // Neither raw gzip nor Base64-encoded gzip — return null
                null
            }
        }
    }
}
