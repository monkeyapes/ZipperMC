package com.zippermc.util

object MinecraftPaths {
    const val RESOURCE_PACKS = "resource_packs"
    const val BEHAVIOR_PACKS = "behavior_packs"
    const val WORLDS = "minecraftWorlds"
    const val SKIN_PACKS = "skin_packs"

    fun folderForType(type: String): String = when (type) {
        "Resource Pack" -> RESOURCE_PACKS
        "Behavior Pack" -> BEHAVIOR_PACKS
        "World" -> WORLDS
        "Skin Pack" -> SKIN_PACKS
        else -> RESOURCE_PACKS
    }
}
