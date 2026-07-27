package com.zippermc.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    fun getFileName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "unknown.zip"
        return try {
            var name = "unknown.zip"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    val n = cursor.getString(idx)
                    if (!n.isNullOrBlank()) name = n
                }
            }
            name
        } catch (_: Exception) { uri.lastPathSegment ?: "unknown.zip" }
    }

    fun copyToCache(context: Context, uri: Uri): File? {
        return try {
            val name = getFileName(context, uri)
            val cacheFile = File(context.cacheDir, name)
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) { cacheFile.delete(); return null }
            input.use { inp ->
                FileOutputStream(cacheFile).use { out ->
                    inp.copyTo(out)
                }
            }
            if (cacheFile.isFile && cacheFile.length() > 0) cacheFile else { cacheFile.delete(); null }
        } catch (_: Exception) { null }
    }

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(200)
    }
}
