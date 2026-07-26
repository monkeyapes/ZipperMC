package com.zippermc.model

data class PackInfo(
    val type: ZipEntryType,
    val name: String,
    val subPath: String,
)

data class AnalysisResult(
    val packs: List<PackInfo>,
    val totalEntryCount: Int = 0,
    val fileName: String = "",
)
