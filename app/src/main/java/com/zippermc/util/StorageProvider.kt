package com.zippermc.util

import android.os.Environment
import java.io.File

object StorageProvider {

    private const val MC_ROOT = "games/com.mojang"

    private fun rootDir(): File {
        val ext = Environment.getExternalStorageDirectory()
        return File(ext, MC_ROOT)
    }

    fun ensureDirs(): Boolean {
        val root = rootDir()
        if (!root.exists() && !root.mkdirs()) return false
        for (sub in listOf("resource_packs", "behavior_packs", "minecraftWorlds", "skin_packs")) {
            val dir = File(root, sub)
            if (dir.exists() || dir.mkdir()) continue
            return false
        }
        return true
    }

    fun writePackFile(typeFolder: String, packName: String, relativePath: String, data: ByteArray) {
        val dir = File(File(rootDir(), typeFolder), sanitize(packName))
        val file = File(dir, relativePath)
        file.parentFile?.mkdirs()
        file.outputStream().use { it.write(data) }
    }

    fun ensureTypeDir(typeFolder: String) {
        File(rootDir(), typeFolder).mkdirs()
    }

    fun isAccessible(): Boolean {
        return try {
            val root = rootDir()
            root.exists() || root.mkdirs()
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitize(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(200)
}
