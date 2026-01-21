package com.ireddragonicy.konabessnext.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/**
 * Helper to resolve real file paths from SAF URIs.
 * Primarily handles ExternalStorageProvider (primary:Download/...)
 */
object UriPathHelper {

    fun getPath(context: Context, uri: Uri): String? {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                if ("primary".contains(type, true)) {
                    return "/storage/emulated/0/" + split[1]
                } else if ("home".contains(type, true)) {
                    return "/storage/emulated/0/Documents/" + split[1]
                }
                // Handle non-primary volumes if needed (mapped to /storage/XXXX-XXXX)
                val volumes = File("/storage").listFiles()
                if (volumes != null) {
                    for (vol in volumes) {
                        if (vol.name.equals(type, ignoreCase = true)) {
                            return vol.absolutePath + "/" + split[1]
                        }
                    }
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                // ... can be complex, often just returning null or using contentResolver.openInputStream
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val contentUri = when (split[0]) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> null
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        }
        // MediaStore (and general)
        else if ("content".contains(uri.scheme ?: "", true)) {
            return getDataColumn(context, uri, null, null)
        }
        // File
        else if ("file".contains(uri.scheme ?: "", true)) {
            return uri.path
        }
        return null
    }

    private fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        if (uri == null) return null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(column)
                    return cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            // Handle error
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }
}
