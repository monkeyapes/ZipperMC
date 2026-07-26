package com.zippermc.model

sealed interface ExtractState {
    data object Idle : ExtractState
    data object PickingFile : ExtractState
    data class Analyzing(val fileName: String) : ExtractState
    data class Ready(
        val result: AnalysisResult,
        val zipUri: String,
        val fileName: String,
    ) : ExtractState
    data class Extracting(val progress: Float, val currentFile: String) : ExtractState
    data class Success(
        val summary: Map<ZipEntryType, Int>,
        val totalFiles: Int,
    ) : ExtractState
    data class Error(val message: String) : ExtractState
}
