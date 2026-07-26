package com.zippermc.model

data class VersionInfo(
    val minEngineVersion: List<Int>,
    val packVersion: List<Int>,
)

data class PackInfo(
    val type: ZipEntryType,
    val name: String,
    val subPath: String,
    val manifestJson: String? = null,
)

data class AnalysisResult(
    val packs: List<PackInfo>,
    val totalEntryCount: Int = 0,
    val fileName: String = "",
)
