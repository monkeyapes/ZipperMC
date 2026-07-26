package com.zippermc.model

enum class ZipEntryType(val displayName: String, val subFolder: String) {
    RESOURCE_PACK("Resource Pack", "resource_packs"),
    BEHAVIOR_PACK("Behavior Pack", "behavior_packs"),
    WORLD("World", "minecraftWorlds"),
    SKIN_PACK("Skin Pack", "skin_packs"),
    UNKNOWN("Unknown", "");

    companion object {
        fun fromDisplayName(name: String): ZipEntryType =
            entries.find { it.displayName == name } ?: UNKNOWN
    }
}
