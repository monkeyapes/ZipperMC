package com.zippermc.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zippermc.extractor.MinecraftExtractor
import com.zippermc.extractor.ZipAnalyzer
import com.zippermc.model.AnalysisResult
import com.zippermc.model.ExtractState
import com.zippermc.util.FileUtils
import com.zippermc.util.MinecraftPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ExtractState>(ExtractState.Idle)
    val state = _state.asStateFlow()

    private var cachedFile: java.io.File? = null

    fun onZipPicked(uri: Uri) {
        val ctx = getApplication<Application>()
        _state.value = ExtractState.Analyzing(FileUtils.getFileName(ctx, uri))

        viewModelScope.launch {
            try {
                val file = FileUtils.copyToCache(ctx, uri)
                if (file == null) {
                    _state.value = ExtractState.Error("Failed to read file")
                    return@launch
                }
                cachedFile = file

                val analysis = ZipAnalyzer.analyze(file)
                _state.value = ExtractState.Ready(
                    result = analysis,
                    zipUri = uri.toString(),
                    fileName = file.name,
                )
            } catch (e: Exception) {
                _state.value = ExtractState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun startExtract(analysis: AnalysisResult) {
        val file = cachedFile ?: return
        _state.value = ExtractState.Extracting(0f, "")

        viewModelScope.launch {
            try {
                val installed = MinecraftExtractor.extract(file, analysis.packs) { progress, current ->
                    _state.value = ExtractState.Extracting(progress, current)
                }
                val total = installed.size
                _state.value = ExtractState.Success(
                    summary = installed,
                    totalFiles = total,
                )
            } catch (e: Exception) {
                _state.value = ExtractState.Error(e.message ?: "Extraction failed")
            }
        }
    }

    fun reset() {
        cachedFile?.delete()
        cachedFile = null
        _state.value = ExtractState.Idle
    }

    fun isMcAccessible(): Boolean = MinecraftPaths.isMcDirAccessible()
}
