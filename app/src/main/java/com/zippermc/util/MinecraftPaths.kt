package com.zippermc.util

import android.os.Environment
import java.io.File

object MinecraftPaths {
    private const val MC_ROOT = "games/com.mojang"

    private val baseDir: File?
        get() {
            val ext = Environment.getExternalStorageDirectory()
            return File(ext, MC_ROOT).takeIf { it.exists() || it.mkdirs() }
        }

    val resourcePacks: File?
        get() = baseDir?.let { b ->
            File(b, "resource_packs").also { it.mkdirs() }
        }

    val behaviorPacks: File?
        get() = baseDir?.let { b ->
            File(b, "behavior_packs").also { it.mkdirs() }
        }

    val worlds: File?
        get() = baseDir?.let { b ->
            File(b, "minecraftWorlds").also { it.mkdirs() }
        }

    val skinPacks: File?
        get() = baseDir?.let { b ->
            File(b, "skin_packs").also { it.mkdirs() }
        }

    fun isMcDirAccessible(): Boolean {
        val ext = Environment.getExternalStorageDirectory()
        return File(ext, MC_ROOT).exists() || File(ext, MC_ROOT).mkdirs()
    }
}
