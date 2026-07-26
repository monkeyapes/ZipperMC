package com.zippermc.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zippermc.extractor.MinecraftExtractor
import com.zippermc.extractor.ZipAnalyzer
import com.zippermc.model.AnalysisResult
import com.zippermc.model.ExtractState
import com.zippermc.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ExtractState>(ExtractState.Idle)
    val state = _state.asStateFlow()

    private var cachedFile: java.io.File? = null
    private var currentAnalysis: AnalysisResult? = null
    private var versionOverrides = mutableMapOf<String, String>()

    fun hasStorageAccess(): Boolean =
        Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    fun checkPermission() {
        if (!hasStorageAccess()) {
            _state.value = ExtractState.NeedsPermission
        }
    }

    fun onZipPicked(uri: Uri) {
        if (!hasStorageAccess()) {
            _state.value = ExtractState.NeedsPermission
            return
        }

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
                currentAnalysis = null
                versionOverrides.clear()

                val analysis = ZipAnalyzer.analyze(file)
                currentAnalysis = analysis
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

    fun startVersionEdit(packIndex: Int) {
        val analysis = currentAnalysis ?: return
        _state.value = ExtractState.EditingVersion(
            result = analysis,
            zipUri = "",
            fileName = analysis.fileName,
            packIndex = packIndex,
        )
    }

    fun saveVersionOverride(minEngineVer: String, packVer: String) {
        versionOverrides["min_engine_version"] = minEngineVer
        versionOverrides["pack_version"] = packVer
        _state.value = ExtractState.Ready(
            result = currentAnalysis ?: return,
            zipUri = "",
            fileName = currentAnalysis?.fileName ?: "",
        )
    }

    fun cancelVersionEdit() {
        _state.value = ExtractState.Ready(
            result = currentAnalysis ?: return,
            zipUri = "",
            fileName = currentAnalysis?.fileName ?: "",
        )
    }

    fun startExtract(analysis: AnalysisResult) {
        val file = cachedFile ?: return

        if (!hasStorageAccess()) {
            _state.value = ExtractState.NeedsPermission
            return
        }

        _state.value = ExtractState.Extracting(0f, "")

        viewModelScope.launch {
            try {
                val installed = MinecraftExtractor.extract(
                    zipFile = file,
                    packs = analysis.packs,
                    versionOverrides = versionOverrides,
                    onProgress = { progress, current ->
                        _state.value = ExtractState.Extracting(progress, current)
                    },
                )
                _state.value = ExtractState.Success(
                    summary = installed,
                    totalFiles = installed.size,
                )
            } catch (e: Exception) {
                _state.value = ExtractState.Error("Extraction failed: ${e.message}")
            }
        }
    }

    fun reset() {
        cachedFile?.delete()
        cachedFile = null
        currentAnalysis = null
        versionOverrides.clear()
        _state.value = ExtractState.Idle
    }

    fun parseVersions(manifestJson: String?): Pair<String, String> {
        if (manifestJson == null) return "1.0.0" to "1.0.0"
        return try {
            val json = JSONObject(manifestJson)
            val header = json.getJSONObject("header")
            val mev = header.optJSONArray("min_engine_version")?.let { joinVersion(it) } ?: "1.0.0"
            val pv = header.optJSONArray("version")?.let { joinVersion(it) } ?: "1.0.0"
            mev to pv
        } catch (_: Exception) {
            "1.0.0" to "1.0.0"
        }
    }

    private fun joinVersion(arr: JSONArray): String =
        (0 until arr.length()).joinToString(".") { arr.optInt(it, 0).toString() }
}
