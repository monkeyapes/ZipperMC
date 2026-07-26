package com.zippermc.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object StorageProvider {
    private const val PREFS = "zippermc_saf"
    private const val KEY_TREE = "mc_tree_uri"

    fun saveTreeUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TREE, uri.toString()).apply()
    }

    fun getTreeUri(context: Context): Uri? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE, null)?.let { Uri.parse(it) }
    }

    fun hasTreeUri(context: Context): Boolean = getTreeUri(context) != null

    fun clearTreeUri(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_TREE).apply()
    }

    private fun getOrCreateDir(parent: DocumentFile, name: String): DocumentFile? {
        return parent.findFile(name) ?: parent.createDirectory(name)
    }

    fun writePackFile(
        context: Context,
        type: String,
        packName: String,
        relativePath: String,
        data: ByteArray,
    ): Boolean {
        val treeUri = getTreeUri(context) ?: return false
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false

        val typeDir = getOrCreateDir(root, type) ?: return false
        val packDir = getOrCreateDir(typeDir, sanitizePath(packName)) ?: return false

        val parts = relativePath.split("/")
        val fileName = parts.last()
        val dirs = parts.dropLast(1)

        var current = packDir
        for (dir in dirs) {
            current = getOrCreateDir(current, dir) ?: return false
        }

        val file = current.createFile("application/octet-stream", fileName) ?: return false
        return context.contentResolver.openOutputStream(file.uri)?.use { out ->
            out.write(data); true
        } ?: false
    }

    fun ensureTypeDirs(context: Context, type: String): Boolean {
        val treeUri = getTreeUri(context) ?: return false
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        getOrCreateDir(root, type) ?: return false
        return true
    }

    private fun sanitizePath(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(200)
    }
}
