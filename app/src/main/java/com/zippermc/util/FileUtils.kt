package com.zippermc.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream

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
        } catch (_: Throwable) { uri.lastPathSegment ?: "unknown.zip" }
    }

    fun copyToCache(context: Context, uri: Uri): File? {
        return try {
            val name = sanitizeFileName(getFileName(context, uri))
            val cacheFile = File(context.cacheDir, name)
            val ok = try {
                val afd = context.contentResolver.openTypedAssetFileDescriptor(uri, "*/*", null)
                if (afd == null) throw Exception("no afd")
                afd.use { fd ->
                    FileOutputStream(cacheFile).use { out ->
                        val buf = ByteArray(8192)
                        var read: Int
                        val fis = fd.createInputStream()
                        while (fis.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                        }
                    }
                }
                true
            } catch (_: Throwable) {
                try {
                    val input = context.contentResolver.openInputStream(uri)
                    if (input == null) { cacheFile.delete(); return null }
                    BufferedInputStream(input).use { inp ->
                        FileOutputStream(cacheFile).use { out ->
                            inp.copyTo(out)
                        }
                    }
                    true
                } catch (_: Throwable) { false }
            }
            if (ok && cacheFile.isFile && cacheFile.length() > 0) cacheFile else { cacheFile.delete(); null }
        } catch (_: Throwable) { null }
    }

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(200)
    }
}
