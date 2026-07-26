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
        val results = LinkedHashSet<ScannedFile>()

        if (Build.VERSION.SDK_INT >= 30) {
            results.addAll(scanMediaStoreDownloads(context))
            results.addAll(scanMediaStoreFiles(context))
            results.addAll(scanDirectPaths())
        } else {
            results.addAll(scanDirectPaths())
        }

        return results.toList().sortedByDescending { it.size }.take(50)
    }

    private fun scanMediaStoreDownloads(context: Context): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()
        try {
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
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0); val name = c.getString(1) ?: continue
                    val size = c.getLong(2); results.add(ScannedFile(name, Uri.withAppendedPath(collection, id.toString()), size, ""))
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private fun scanMediaStoreFiles(context: Context): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()
        try {
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATA,
            )
            val selection = "(${EXTENSIONS.joinToString(" OR ") { "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?" }}) AND ${MediaStore.MediaColumns.DISPLAY_NAME} IS NOT NULL"
            val args = EXTENSIONS.map { "%$it" }.toTypedArray()
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0); val name = c.getString(1) ?: continue
                    val size = c.getLong(2); val data = c.getString(3) ?: ""
                    results.add(ScannedFile(name, Uri.withAppendedPath(collection, id.toString()), size, data))
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private fun scanDirectPaths(): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()
        val dirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            try { File(Environment.getExternalStorageDirectory(), "APKs") } catch (_: Exception) { null },
            try { File(Environment.getExternalStorageDirectory(), "Download") } catch (_: Exception) { null },
            try { File(Environment.getExternalStorageDirectory(), "downloads") } catch (_: Exception) { null },
            try { File(Environment.getExternalStorageDirectory(), "Minecraft") } catch (_: Exception) { null },
            try { File(Environment.getExternalStorageDirectory(), "Games/Minecraft") } catch (_: Exception) { null },
            try { File(Environment.getExternalStorageDirectory(), "games/com.mojang/resource_packs") } catch (_: Exception) { null },
            try { File(Environment.getExternalStorageDirectory(), "games/com.mojang/behavior_packs") } catch (_: Exception) { null },
        )

        for (dir in dirs) {
            if (dir?.isDirectory != true) continue
            try {
                val files = dir.listFiles()
                if (files != null) for (file in files) {
                    if (file.isFile && EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) }) {
                        try {
                            val uri = Uri.fromFile(file)
                            results.add(ScannedFile(file.name, uri, file.length(), file.absolutePath))
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
        return results
    }
}
