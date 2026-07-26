package com.zippermc.model

import android.content.Intent

sealed interface ExtractState {
    data object Idle : ExtractState
    data class Analyzing(val fileName: String) : ExtractState
    data class Ready(
        val result: AnalysisResult,
        val fileName: String,
        val mcVersion: String? = null,
    ) : ExtractState
    data class EditingVersion(
        val result: AnalysisResult,
        val fileName: String,
        val packIndex: Int,
    ) : ExtractState
    data class Installing(val progress: Float, val currentFile: String) : ExtractState
    data class Success(val summary: List<PackInfo>) : ExtractState
    data class SentToMinecraft(val intent: Intent) : ExtractState
    data class Error(val message: String) : ExtractState
}
