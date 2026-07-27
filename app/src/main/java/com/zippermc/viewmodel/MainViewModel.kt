package com.zippermc.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zippermc.extractor.ZipAnalyzer
import com.zippermc.model.AnalysisResult
import com.zippermc.model.ExtractState
import com.zippermc.model.MinecraftInstall
import com.zippermc.model.PackHistory
import com.zippermc.model.PackInfo
import com.zippermc.model.ZipEntryType
import com.zippermc.util.FileScanner
import com.zippermc.util.FileUtils
import com.zippermc.util.GitHubUpdate
import com.zippermc.util.PackRepacker
import com.zippermc.util.ScannedFile
import com.zippermc.util.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ExtractState>(ExtractState.Idle)
    val state = _state.asStateFlow()

    private val prefs = application.getSharedPreferences("zippermc", 0)

    private var cachedFile: java.io.File? = null
    private var currentAnalysis: AnalysisResult? = null
    private var versionOverrides = mutableMapOf<String, String>()
    private var selectedInstall: MinecraftInstall? = null

    private val _scannedFiles = MutableStateFlow<List<ScannedFile>>(emptyList())
    val scannedFiles = _scannedFiles.asStateFlow()

    private val _mcInstalls = MutableStateFlow<List<MinecraftInstall>>(emptyList())
    val mcInstalls = _mcInstalls.asStateFlow()

    private val _selectedMc = MutableStateFlow<MinecraftInstall?>(null)
    val selectedMc = _selectedMc.asStateFlow()

    private val _history = MutableStateFlow<List<PackHistory>>(emptyList())
    val history = _history.asStateFlow()

    private val _autoScan = MutableStateFlow(prefs.getBoolean("auto_scan", true))
    val autoScan = _autoScan.asStateFlow()

    private val _darkTheme = MutableStateFlow(prefs.getBoolean("dark_theme", false))
    val darkTheme = _darkTheme.asStateFlow()

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable = _updateAvailable.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()

    private val processLock = Mutex()

    init {
        loadHistory()
        checkForUpdate()
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val current = withContext(Dispatchers.IO) {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.5.2"
                }
                val info = GitHubUpdate.check()
                if (info != null && compareVersions(info.latestVersion, current) > 0) {
                    _updateAvailable.value = info
                }
            } catch (_: Exception) {}
        }
    }

    fun dismissUpdate() {
        _updateAvailable.value = null
    }

    private fun compareVersions(a: String, b: String): Int {
        val cleanA = a.takeWhile { it.isDigit() || it == '.' }
        val cleanB = b.takeWhile { it.isDigit() || it == '.' }
        val pa = cleanA.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = cleanB.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    fun setAutoScan(enabled: Boolean) {
        _autoScan.value = enabled
        prefs.edit().putBoolean("auto_scan", enabled).apply()
    }

    fun setDarkTheme(enabled: Boolean) {
        _darkTheme.value = enabled
        prefs.edit().putBoolean("dark_theme", enabled).apply()
    }

    private fun loadHistory() {
        try {
            val json = prefs.getString("history", "[]") ?: "[]"
            val arr = JSONArray(json)
            val list = mutableListOf<PackHistory>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val packsArr = obj.getJSONArray("packs")
                val packs = mutableListOf<PackInfo>()
                for (j in 0 until packsArr.length()) {
                    val p = packsArr.getJSONObject(j)
                    packs.add(PackInfo(
                        name = p.getString("name"),
                        subPath = p.optString("subPath", ""),
                        type = ZipEntryType.valueOf(p.getString("type")),
                        manifestJson = if (p.isNull("manifestJson")) null else p.getString("manifestJson"),
                    ))
                }
                list.add(PackHistory(
                    fileName = obj.getString("fileName"),
                    packs = packs,
                    mcPackage = obj.getString("mcPackage"),
                    mcVersion = if (obj.isNull("mcVersion")) null else obj.getString("mcVersion"),
                    timestamp = obj.getLong("timestamp"),
                ))
            }
            _history.value = list
        } catch (_: Exception) {}
    }

    private fun saveHistory() {
        try {
            val arr = JSONArray()
            for (h in _history.value) {
                val packsArr = JSONArray()
                for (p in h.packs) {
                    packsArr.put(JSONObject().apply {
                        put("name", p.name)
                        put("subPath", p.subPath)
                        put("type", p.type.name)
                        put("manifestJson", p.manifestJson ?: JSONObject.NULL)
                    })
                }
                arr.put(JSONObject().apply {
                    put("fileName", h.fileName)
                    put("packs", packsArr)
                    put("mcPackage", h.mcPackage)
                    put("mcVersion", h.mcVersion ?: JSONObject.NULL)
                    put("timestamp", h.timestamp)
                })
            }
            prefs.edit().putString("history", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun addToHistory(fileName: String, packs: List<PackInfo>) {
        val entry = PackHistory(
            fileName = fileName,
            packs = packs,
            mcPackage = selectedInstall?.packageName ?: "com.mojang.minecraftpe",
            mcVersion = selectedInstall?.versionName,
        )
        val current = _history.value.toMutableList()
        current.add(0, entry)
        if (current.size > 50) current.removeAt(current.lastIndex)
        _history.value = current
        saveHistory()
    }

    fun onHistoryItemClicked(history: PackHistory) {}

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("history").apply()
    }

    fun deleteHistoryItem(timestamp: Long) {
        val current = _history.value.toMutableList()
        current.removeAll { it.timestamp == timestamp }
        _history.value = current
        saveHistory()
    }

    fun restoreHistoryItem(entry: PackHistory) {
        val current = _history.value.toMutableList()
        current.add(0, entry)
        _history.value = current
        saveHistory()
    }

    fun showSnackbar(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun detectInstallations(): List<MinecraftInstall> {
        val ctx = getApplication<Application>()
        val results = mutableListOf<MinecraftInstall>()
        val pm = ctx.packageManager
        val seen = mutableSetOf<String>()

        val knownPackages = listOf(
            "com.mojang.minecraftpe",
            "com.mojang.minecraftedu",
            "com.mojang.minecraftedu_preview",
            "com.mojang.minecraftpreview",
            "com.mojang.minecrafttv",
            "com.mojang.minecrafttrial",
            "com.mojang.minecrafttrialpe",
            "com.mojang.minecraftpe.demo",
            "com.mojang.minecraftearth",
            "com.mojang.minecraftonline",
            "com.mojang.scrolls",
            "net.kdt.pojavlaunch",
            "net.kdt.pojavlaunch.debug",
            "com.mcpelauncher",
            "com.mcpelauncher.app",
            "io.mrarm.mcpelauncher",
            "io.mrarm.mctoolbox",
        )
        for (pkg in knownPackages) {
            try {
                val info = pm.getPackageInfo(pkg, 0)
                val label = info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
                results.add(MinecraftInstall(pkg, info.versionName ?: "?", label))
                seen.add(pkg)
            } catch (_: PackageManager.NameNotFoundException) {}
        }

        val intentTypes = listOf("application/octet-stream", "application/zip", "application/x-zip-compressed")
        for (mime in intentTypes) {
            try {
                val intents = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW).setType(mime), 0)
                for (ri in intents) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg in seen) continue
                    seen.add(pkg)
                    try {
                        val info = pm.getPackageInfo(pkg, 0)
                        val label = info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
                        val lowerPkg = pkg.lowercase()
                        val lowerLabel = label.lowercase()
                        if (lowerPkg.contains("minecraft") || lowerPkg.contains("mcpe") ||
                            lowerPkg.contains("pojav") || lowerPkg.contains("mcpelauncher") ||
                            lowerLabel.contains("minecraft") || lowerLabel.contains("pojav") ||
                            lowerLabel.contains("mcpe")
                        ) {
                            results.add(MinecraftInstall(pkg, info.versionName ?: "?", label))
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        try {
            val launcherIntents = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }, 0)
            for (ri in launcherIntents) {
                val pkg = ri.activityInfo.packageName
                if (pkg in seen) continue
                seen.add(pkg)
                try {
                    val info = pm.getPackageInfo(pkg, 0)
                    val lowerPkg = pkg.lowercase()
                    val label = info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
                    val lowerLabel = label.lowercase()
                    if (lowerPkg.contains("minecraft") || lowerPkg.contains("pojav") || lowerLabel.contains("minecraft")) {
                        results.add(MinecraftInstall(pkg, info.versionName ?: "?", label))
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        results.sortByDescending {
            when (it.packageName) {
                "com.mojang.minecraftpe" -> 4
                "com.mojang.minecraftpreview" -> 3
                "net.kdt.pojavlaunch" -> 2
                else -> if (it.packageName.contains("minecraft")) 1 else 0
            }
        }
        _mcInstalls.value = results
        if (results.isNotEmpty() && selectedInstall == null) {
            selectedInstall = results.first()
            _selectedMc.value = results.first()
        }
        return results
    }

    fun setSelectedInstall(install: MinecraftInstall) {
        selectedInstall = install
        _selectedMc.value = install
    }

    fun scanFiles() {
        detectInstallations()
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { FileScanner.scan(ctx) }
                _scannedFiles.value = files
            } catch (_: Exception) {}
        }
    }

    fun scanAndAutoInstall() {
        if (!_autoScan.value) return
        detectInstallations()
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { FileScanner.scan(ctx) }
                _scannedFiles.value = files
                if (files.isNotEmpty() && _state.value is ExtractState.Idle) {
                    processUri(files.first().uri)
                }
            } catch (_: Exception) {}
        }
    }

    fun onZipPicked(uri: Uri) {
        processUri(uri)
    }

    private fun processUri(uri: Uri) {
        viewModelScope.launch {
            processLock.withLock {
                val ctx = getApplication<Application>()
                if (selectedInstall == null) detectInstallations()
                _state.value = ExtractState.Analyzing("")

                try {
                    val fileName = withContext(Dispatchers.IO) { FileUtils.getFileName(ctx, uri) }
                    _state.value = ExtractState.Analyzing(fileName)
                    val file = withContext(Dispatchers.IO) { FileUtils.copyToCache(ctx, uri) }
                    if (file == null) {
                        _state.value = ExtractState.Error("Failed to read file")
                        return@withLock
                    }
                    cachedFile?.delete()
                    cachedFile = file
                    currentAnalysis = null
                    versionOverrides.clear()

                    val analysis = withContext(Dispatchers.IO) { ZipAnalyzer.analyze(file) }
                    currentAnalysis = analysis

                    selectedInstall?.let { mc ->
                        val mcParts = mc.versionName.split(".").mapNotNull { it.toIntOrNull() }
                        if (mcParts.size >= 2) {
                            val mcPrefix = "${mcParts[0]}.${mcParts[1]}"
                            for (pack in analysis.packs) {
                                if (pack.manifestJson != null) {
                                    val (minEng, _) = parseVersions(pack.manifestJson)
                                    val engParts = minEng.split(".").mapNotNull { it.toIntOrNull() }
                                    if (engParts.size >= 2) {
                                        val engPrefix = "${engParts[0]}.${engParts[1]}"
                                        if (engPrefix != mcPrefix) {
                                            versionOverrides["min_engine_version"] = mc.versionName
                                        }
                                    }
                                }
                            }
                        }
                    }

                    _state.value = ExtractState.Ready(
                        result = analysis,
                        fileName = file.name,
                        mcVersion = selectedInstall?.versionName,
                    )
                } catch (e: java.lang.Exception) {
                    val msg = e.message ?: e::class.simpleName ?: "Unknown error"
                    _state.value = ExtractState.Error(msg)
                }
            }
        }
    }

    fun sendToMinecraft(analysis: AnalysisResult) {
        val file = cachedFile ?: run {
            _state.value = ExtractState.Error("No file loaded"); return
        }
        val target = selectedInstall ?: run {
            _state.value = ExtractState.Error("No Minecraft installation selected"); return
        }
        val fileName = file.name
        _state.value = ExtractState.Installing(0f, "")
        viewModelScope.launch {
            try {
                val repacked = withContext(Dispatchers.IO) { PackRepacker.repack(file, versionOverrides) }
                val ctx = getApplication<Application>()
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", repacked)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/octet-stream")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage(target.packageName)
                }
                addToHistory(fileName, analysis.packs)
                _state.value = ExtractState.SentToMinecraft(intent)
            } catch (e: Exception) {
                _state.value = ExtractState.Error("Failed: ${e.message}")
            }
        }
    }

    fun startVersionEdit(packIndex: Int) {
        val analysis = currentAnalysis ?: run { _state.value = ExtractState.Error("No file loaded"); return }
        _state.value = ExtractState.EditingVersion(result = analysis, fileName = analysis.fileName, packIndex = packIndex)
    }

    fun saveVersionOverride(minEngineVer: String, packVer: String) {
        val analysis = currentAnalysis ?: return
        versionOverrides["min_engine_version"] = minEngineVer
        versionOverrides["pack_version"] = packVer
        _state.value = ExtractState.Ready(result = analysis, fileName = analysis.fileName, mcVersion = selectedInstall?.versionName)
    }

    fun cancelVersionEdit() {
        val analysis = currentAnalysis ?: return
        _state.value = ExtractState.Ready(result = analysis, fileName = analysis.fileName, mcVersion = selectedInstall?.versionName)
    }

    fun reset() {
        cachedFile?.delete(); cachedFile = null
        currentAnalysis = null; versionOverrides.clear()
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
        } catch (_: Exception) { "1.0.0" to "1.0.0" }
    }

    private fun joinVersion(arr: JSONArray): String =
        (0 until arr.length()).joinToString(".") { arr.optInt(it, 0).toString() }
}
