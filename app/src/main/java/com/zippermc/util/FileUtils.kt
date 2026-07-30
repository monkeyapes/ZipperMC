package com.zippermc.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object FileUtils {
    fun getFileName(context: Context, uri: Uri): String {
        return try {
            if (uri.scheme == "file") return uri.lastPathSegment ?: "pack.zip"
            var name = "pack.zip"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    val n = cursor.getString(idx)
                    if (!n.isNullOrBlank()) name = n
                }
            }
            name
        } catch (_: Throwable) { uri.lastPathSegment ?: "pack.zip" }
    }

    fun copyToCache(context: Context, uri: Uri): File? {
        return try {
            val name = sanitizeFileName(getFileName(context, uri))
            val cacheFile = File(context.cacheDir, name)
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) return null
            input.use { inp ->
                cacheFile.outputStream().use { out ->
                    inp.copyTo(out)
                }
            }
            if (cacheFile.isFile && cacheFile.length() > 0) cacheFile else { cacheFile.delete(); null }
        } catch (_: Throwable) { null }
    }

    fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(150).ifBlank { "pack.zip" }
}
