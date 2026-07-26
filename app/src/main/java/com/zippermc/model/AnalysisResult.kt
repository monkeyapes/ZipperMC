package com.zippermc.model

data class AnalysisResult(
    val primaryType: ZipEntryType,
    val secondaryTypes: List<ZipEntryType> = emptyList(),
    val entryCount: Int = 0,
    val detectedName: String = "",
)
