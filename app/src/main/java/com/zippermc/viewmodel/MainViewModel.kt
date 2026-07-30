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

    private val _eventLog = MutableStateFlow<List<String>>(emptyList())
    val eventLog = _eventLog.asStateFlow()

    private val processLock = Mutex()

    init {
        loadHistory()
        checkForUpdate()
        event("App started")
    }

    private fun event(msg: String) {
        val t = try { java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()) } catch (_: Throwable) { "?" }
        val entry = "[$t] $msg"
        _eventLog.value = (_eventLog.value + entry).take(200)
        try {
            val ctx = getApplication<Application>()
            val dir = ctx.filesDir
            dir.mkdirs()
            val f = java.io.File(dir, "event.log")
            java.io.FileWriter(f, true).use { it.write("$entry\n") }
        } catch (_: Throwable) {}
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val current = withContext(Dispatchers.IO) {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.6.0"
                }
                val info = GitHubUpdate.check()
                if (info != null && compareVersions(info.latestVersion, current) > 0) {
                    _updateAvailable.value = info
                    event("Update available: ${info.latestVersion}")
                }
            } catch (_: Exception) {}
        }
    }

    fun dismissUpdate() { _updateAvailable.value = null }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.takeWhile { it.isDigit() || it == '.' }.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.takeWhile { it.isDigit() || it == '.' }.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val va = pa.getOrElse(i) { 0 }; val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    fun setAutoScan(enabled: Boolean) { _autoScan.value = enabled; prefs.edit().putBoolean("auto_scan", enabled).apply() }
    fun setDarkTheme(enabled: Boolean) { _darkTheme.value = enabled; prefs.edit().putBoolean("dark_theme", enabled).apply() }

    private fun loadHistory() {
        try {
            val arr = JSONArray(prefs.getString("history", "[]") ?: "[]")
            val list = mutableListOf<PackHistory>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pa = o.getJSONArray("packs"); val pk = mutableListOf<PackInfo>()
                for (j in 0 until pa.length()) { val p = pa.getJSONObject(j); pk.add(PackInfo(name = p.getString("name"), subPath = p.optString("subPath", ""), type = ZipEntryType.valueOf(p.getString("type")), manifestJson = if (p.isNull("manifestJson")) null else p.getString("manifestJson"))) }
                list.add(PackHistory(fileName = o.getString("fileName"), packs = pk, mcPackage = o.getString("mcPackage"), mcVersion = if (o.isNull("mcVersion")) null else o.getString("mcVersion"), timestamp = o.getLong("timestamp")))
            }
            _history.value = list
        } catch (_: Exception) {}
    }

    private fun saveHistory() {
        try {
            val arr = JSONArray()
            _history.value.forEach { h ->
                val pa = JSONArray(); h.packs.forEach { p -> pa.put(JSONObject().apply { put("name", p.name); put("subPath", p.subPath); put("type", p.type.name); put("manifestJson", p.manifestJson ?: JSONObject.NULL) }) }
                arr.put(JSONObject().apply { put("fileName", h.fileName); put("packs", pa); put("mcPackage", h.mcPackage); put("mcVersion", h.mcVersion ?: JSONObject.NULL); put("timestamp", h.timestamp) })
            }
            prefs.edit().putString("history", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun addToHistory(fileName: String, packs: List<PackInfo>) {
        val entry = PackHistory(fileName = fileName, packs = packs, mcPackage = selectedInstall?.packageName ?: "com.mojang.minecraftpe", mcVersion = selectedInstall?.versionName)
        val c = _history.value.toMutableList(); c.add(0, entry); if (c.size > 50) c.removeAt(c.lastIndex)
        _history.value = c; saveHistory()
    }

    fun onHistoryItemClicked(history: PackHistory) {}

    fun clearHistory() { _history.value = emptyList(); prefs.edit().remove("history").apply() }
    fun deleteHistoryItem(timestamp: Long) { val c = _history.value.toMutableList(); c.removeAll { it.timestamp == timestamp }; _history.value = c; saveHistory() }
    fun restoreHistoryItem(entry: PackHistory) { val c = _history.value.toMutableList(); c.add(0, entry); _history.value = c; saveHistory() }
    fun showSnackbar(msg: String) { _snackbarMessage.value = msg; event("Snackbar: $msg") }
    fun clearSnackbar() { _snackbarMessage.value = null }

    fun detectInstallations(): List<MinecraftInstall> {
        val ctx = getApplication<Application>(); val pm = ctx.packageManager; val results = mutableListOf<MinecraftInstall>(); val seen = mutableSetOf<String>()
        val known = listOf("com.mojang.minecraftpe", "com.mojang.minecraftedu", "com.mojang.minecraftedu_preview", "com.mojang.minecraftpreview", "com.mojang.minecrafttv", "com.mojang.minecrafttrial", "com.mojang.minecrafttrialpe", "com.mojang.minecraftpe.demo", "com.mojang.minecraftearth", "com.mojang.minecraftonline", "com.mojang.scrolls", "net.kdt.pojavlaunch", "net.kdt.pojavlaunch.debug", "com.mcpelauncher", "com.mcpelauncher.app", "io.mrarm.mcpelauncher", "io.mrarm.mctoolbox")
        for (pkg in known) try { val info = pm.getPackageInfo(pkg, 0); val label = info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg; results.add(MinecraftInstall(pkg, info.versionName ?: "?", label)); seen.add(pkg) } catch (_: PackageManager.NameNotFoundException) {}
        for (mime in listOf("application/octet-stream", "application/zip", "application/x-zip-compressed")) try { pm.queryIntentActivities(Intent(Intent.ACTION_VIEW).setType(mime), 0).forEach { ri -> val pkg = ri.activityInfo.packageName; if (pkg !in seen && (pkg.lowercase().contains("minecraft") || pkg.lowercase().contains("pojav") || pkg.lowercase().contains("mcpe"))) { seen.add(pkg); try { val info = pm.getPackageInfo(pkg, 0); val label = info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg; results.add(MinecraftInstall(pkg, info.versionName ?: "?", label)) } catch (_: Exception) {} } } } catch (_: Exception) {}
        try { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }, 0).forEach { ri -> val pkg = ri.activityInfo.packageName; if (pkg !in seen && (pkg.lowercase().contains("minecraft") || pkg.lowercase().contains("pojav"))) { seen.add(pkg); try { val info = pm.getPackageInfo(pkg, 0); val label = info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg; results.add(MinecraftInstall(pkg, info.versionName ?: "?", label)) } catch (_: Exception) {} } } } catch (_: Exception) {}
        results.sortByDescending { when (it.packageName) { "com.mojang.minecraftpe" -> 4; "com.mojang.minecraftpreview" -> 3; "net.kdt.pojavlaunch" -> 2; else -> if (it.packageName.contains("minecraft")) 1 else 0 } }
        _mcInstalls.value = results
        if (results.isNotEmpty() && selectedInstall == null) { selectedInstall = results.first(); _selectedMc.value = results.first() }
        event("Detected ${results.size} MC installations")
        return results
    }

    fun setSelectedInstall(install: MinecraftInstall) { selectedInstall = install; _selectedMc.value = install }

    fun scanFiles() {
        detectInstallations()
        viewModelScope.launch { try { _scannedFiles.value = withContext(Dispatchers.IO) { FileScanner.scan(getApplication()) }; event("Scanned ${_scannedFiles.value.size} files") } catch (_: Exception) {} }
    }

    fun scanAndAutoInstall() {
        if (!_autoScan.value) return
        detectInstallations()
        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { FileScanner.scan(getApplication()) }
                _scannedFiles.value = files
                if (files.isNotEmpty() && _state.value is ExtractState.Idle) processUri(files.first().uri)
            } catch (_: Exception) {}
        }
    }

    fun onZipPicked(uri: Uri) { event("File picked: $uri"); processUri(uri) }

    private fun processUri(uri: Uri) {
        viewModelScope.launch {
            try {
                processLock.withLock {
                    try {
                        val ctx = getApplication<Application>()
                        if (selectedInstall == null) detectInstallations()
                        _state.value = ExtractState.Analyzing("Copying...")
                        event("Copying file to cache")

                        val file = withContext(Dispatchers.IO) { FileUtils.copyToCache(ctx, uri) }
                        if (file == null) {
                            event("Copy failed: file is null")
                            _state.value = ExtractState.Error("Failed to read file")
                            return@withLock
                        }
                        event("Copied to cache: ${file.name} (${file.length()}b)")

                        cachedFile?.delete(); cachedFile = file
                        currentAnalysis = null; versionOverrides.clear()

                        val extResult = ZipAnalyzer.fromExtension(file)
                        _state.value = ExtractState.Ready(result = extResult, fileName = file.name, mcVersion = selectedInstall?.versionName)
                        event("Extension-based result: ${extResult.packs.size} pack(s)")

                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val fullResult = ZipAnalyzer.analyze(file)
                                if (fullResult != null && fullResult.packs.isNotEmpty()) {
                                    currentAnalysis = fullResult
                                    selectedInstall?.let { mc ->
                                        mc.versionName.split(".").mapNotNull { it.toIntOrNull() }.let { parts ->
                                            if (parts.size >= 2) {
                                                val mcPre = "${parts[0]}.${parts[1]}"
                                                fullResult.packs.forEach { p -> if (p.manifestJson != null) { parseVersions(p.manifestJson).let { (mev, _) -> mev.split(".").mapNotNull { it.toIntOrNull() }.let { if (it.size >= 2) { val ePre = "${it[0]}.${it[1]}"; if (ePre != mcPre) versionOverrides["min_engine_version"] = mc.versionName } } } } }
                                            }
                                        }
                                    }
                                    _state.value = ExtractState.Ready(result = fullResult, fileName = file.name, mcVersion = selectedInstall?.versionName)
                                    event("Analysis complete: ${fullResult.packs.size} pack(s)")
                                } else {
                                    event("Analysis returned null/empty, keeping extension result")
                                }
                            } catch (e: Throwable) {
                                event("Analysis failed: ${e.message}")
                            }
                        }
                    } catch (e: Throwable) {
                        event("processUri inner error: ${e.message}")
                        _state.value = ExtractState.Error(e.message ?: e::class.simpleName ?: "Unknown error")
                    }
                }
            } catch (e: Throwable) {
                event("processUri outer error: ${e.message}")
                _state.value = ExtractState.Error("Unexpected: ${e.message}")
            }
        }
    }

    fun sendToMinecraft(analysis: AnalysisResult) {
        val file = cachedFile ?: run { _state.value = ExtractState.Error("No file loaded"); return }
        val target = selectedInstall ?: run { _state.value = ExtractState.Error("No Minecraft installation selected"); return }
        _state.value = ExtractState.Installing(0f, file.name)
        viewModelScope.launch {
            try {
                val repacked = withContext(Dispatchers.IO) { PackRepacker.repack(file, versionOverrides) }
                val ctx = getApplication<Application>()
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", repacked)
                val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/octet-stream"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); setPackage(target.packageName) }
                addToHistory(file.name, analysis.packs)
                _state.value = ExtractState.SentToMinecraft(intent)
                event("Sent to ${target.packageName}")
            } catch (e: Throwable) { _state.value = ExtractState.Error("Failed: ${e.message}"); event("Send failed: ${e.message}") }
        }
    }

    fun startVersionEdit(packIndex: Int) {
        val a = currentAnalysis ?: run { _state.value = ExtractState.Error("No file loaded"); return }
        _state.value = ExtractState.EditingVersion(result = a, fileName = a.fileName, packIndex = packIndex)
    }

    fun saveVersionOverride(minEngineVer: String, packVer: String) {
        val a = currentAnalysis ?: return
        versionOverrides["min_engine_version"] = minEngineVer; versionOverrides["pack_version"] = packVer
        _state.value = ExtractState.Ready(result = a, fileName = a.fileName, mcVersion = selectedInstall?.versionName)
    }

    fun cancelVersionEdit() { val a = currentAnalysis ?: return; _state.value = ExtractState.Ready(result = a, fileName = a.fileName, mcVersion = selectedInstall?.versionName) }

    fun reset() { cachedFile?.delete(); cachedFile = null; currentAnalysis = null; versionOverrides.clear(); _state.value = ExtractState.Idle }

    fun parseVersions(manifestJson: String?): Pair<String, String> {
        if (manifestJson == null) return "1.0.0" to "1.0.0"
        return try { val j = JSONObject(manifestJson); val h = j.getJSONObject("header"); Pair(h.optJSONArray("min_engine_version")?.let { joinVersion(it) } ?: "1.0.0", h.optJSONArray("version")?.let { joinVersion(it) } ?: "1.0.0") } catch (_: Exception) { "1.0.0" to "1.0.0" }
    }

    private fun joinVersion(arr: JSONArray): String = (0 until arr.length()).joinToString(".") { arr.optInt(it, 0).toString() }
}
