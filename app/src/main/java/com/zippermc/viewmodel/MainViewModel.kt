package com.zippermc.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zippermc.extractor.MinecraftExtractor
import com.zippermc.extractor.ZipAnalyzer
import com.zippermc.model.AnalysisResult
import com.zippermc.model.ExtractState
import com.zippermc.util.FileScanner
import com.zippermc.util.FileUtils
import com.zippermc.util.PackRepacker
import com.zippermc.util.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ExtractState>(ExtractState.Idle)
    val state = _state.asStateFlow()

    private var cachedFile: java.io.File? = null
    private var currentAnalysis: AnalysisResult? = null
    private var versionOverrides = mutableMapOf<String, String>()

    private val outputIntent = MutableStateFlow<android.content.Intent?>(null)
    val pendingIntent = outputIntent.asStateFlow()

    private val _scannedFiles = MutableStateFlow<List<ScannedFile>>(emptyList())
    val scannedFiles = _scannedFiles.asStateFlow()

    fun scanDeviceFiles() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                FileScanner.scan(ctx)
            }
            _scannedFiles.value = files
        }
    }

    fun onZipPicked(uri: Uri) {
        processUri(uri)
    }

    private fun processUri(uri: Uri) {
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
                    fileName = file.name,
                )
            } catch (e: Exception) {
                _state.value = ExtractState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun startVersionEdit(packIndex: Int) {
        val analysis = currentAnalysis ?: run {
            _state.value = ExtractState.Error("No file loaded")
            return
        }
        _state.value = ExtractState.EditingVersion(
            result = analysis,
            fileName = analysis.fileName,
            packIndex = packIndex,
        )
    }

    fun saveVersionOverride(minEngineVer: String, packVer: String) {
        val analysis = currentAnalysis ?: return
        versionOverrides["min_engine_version"] = minEngineVer
        versionOverrides["pack_version"] = packVer
        _state.value = ExtractState.Ready(
            result = analysis,
            fileName = analysis.fileName,
        )
    }

    fun cancelVersionEdit() {
        val analysis = currentAnalysis ?: return
        _state.value = ExtractState.Ready(
            result = analysis,
            fileName = analysis.fileName,
        )
    }

    fun installOrSendToMinecraft(analysis: AnalysisResult) {
        val file = cachedFile ?: run {
            _state.value = ExtractState.Error("No file loaded")
            return
        }

        _state.value = ExtractState.Installing(0f, "")

        viewModelScope.launch {
            try {
                val installed = MinecraftExtractor.extract(
                    zipFile = file,
                    packs = analysis.packs,
                    versionOverrides = versionOverrides,
                    onProgress = { progress, current ->
                        _state.value = ExtractState.Installing(progress, current)
                    },
                )
                _state.value = ExtractState.Success(summary = installed)
            } catch (_: Exception) {
                // Direct write failed — fallback to sending to Minecraft via intent
                try {
                    val repacked = withContext(Dispatchers.IO) {
                        PackRepacker.repack(file, versionOverrides)
                    }
                    val ctx = getApplication<Application>()
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        ctx, "${ctx.packageName}.provider", repacked
                    )
                    outputIntent.value = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/octet-stream")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.mojang.minecraftpe")
                    }
                    _state.value = ExtractState.Success(summary = analysis.packs)
                } catch (e: Exception) {
                    _state.value = ExtractState.Error("Failed: ${e.message}")
                }
            }
        }
    }

    fun clearPendingIntent() {
        outputIntent.value = null
    }

    fun reset() {
        cachedFile?.delete()
        cachedFile = null
        currentAnalysis = null
        versionOverrides.clear()
        outputIntent.value = null
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
