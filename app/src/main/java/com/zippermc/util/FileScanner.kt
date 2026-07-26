package com.zippermc.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

data class ScannedFile(
    val name: String,
    val uri: Uri,
    val size: Long,
    val path: String,
)

object FileScanner {

    private val EXTENSIONS = setOf(".mcaddon", ".mcpack", ".zip", ".mcworld", ".mctemplate", ".mcskin")

    fun scan(context: Context): List<ScannedFile> {
        return if (Build.VERSION.SDK_INT >= 30) {
            scanMediaStore(context)
        } else {
            scanDirectPaths(context)
        }
    }

    private fun scanMediaStore(context: Context): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
        )
        val selection = EXTENSIONS.joinToString(" OR ") {
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        }
        val args = EXTENSIONS.map { "%$it" }.toTypedArray()

        try {
            context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: continue
                    val size = cursor.getLong(2)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    results.add(ScannedFile(name, uri, size, ""))
                }
            }
        } catch (_: Exception) {}

        return results
    }

    private fun scanDirectPaths(@Suppress("UNUSED_PARAMETER") context: Context): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()
        val dirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).parentFile?.let { File(it, "APKs") },
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "APKs"),
            File(Environment.getExternalStorageDirectory(), "downloads"),
            Environment.getExternalStorageDirectory(),
        )

        for (dir in dirs) {
            if (!dir.isDirectory) continue
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) }) {
                        val uri = Uri.fromFile(file)
                        results.add(ScannedFile(file.name, uri, file.length(), file.absolutePath))
                    }
                }
            } catch (_: Exception) {}
        }

        return results
    }
}
