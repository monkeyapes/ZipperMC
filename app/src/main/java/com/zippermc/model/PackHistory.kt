package com.zippermc.model

data class PackHistory(
    val fileName: String,
    val packs: List<PackInfo>,
    val mcPackage: String,
    val mcVersion: String?,
    val timestamp: Long = System.currentTimeMillis(),
)
