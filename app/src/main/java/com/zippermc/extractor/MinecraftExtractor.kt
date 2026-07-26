package com.zippermc.extractor

import android.content.Context
import com.zippermc.model.PackInfo
import com.zippermc.model.ZipEntryType
import com.zippermc.util.MinecraftPaths
import com.zippermc.util.StorageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

object MinecraftExtractor {

    suspend fun extract(
        context: Context,
        zipFile: File,
        packs: List<PackInfo>,
        versionOverrides: Map<String, String>,
        onProgress: (Float, String) -> Unit,
    ): List<PackInfo> = withContext(Dispatchers.IO) {
        val zip = ZipFile(zipFile)
        val allEntries = zip.entries().asSequence().toList()
        val total = allEntries.size
        var globalCount = 0
        val installed = mutableListOf<PackInfo>()

        for (pack in packs) {
            val typeFolder = MinecraftPaths.folderForType(pack.type.displayName)
            if (!StorageProvider.ensureTypeDirs(context, typeFolder)) {
                throw IOException("Cannot access Minecraft $typeFolder folder. Please re-select the Minecraft folder.")
            }

            val prefix = pack.subPath.let { if (it.isBlank()) null else "$it/" }

            val relevantEntries = if (prefix != null) {
                allEntries.filter { it.name.startsWith(prefix) && !it.isDirectory }
                    .map { it to it.name.removePrefix(prefix) }
            } else {
                allEntries.filter { !it.isDirectory }
                    .map { it to it.name }
            }

            for ((entry, relativePath) in relevantEntries) {
                val data = zip.getInputStream(entry).use { it.readBytes() }

                val finalData = if (relativePath == "manifest.json" && pack.manifestJson != null) {
                    applyVersionOverride(pack.manifestJson, versionOverrides).toByteArray(Charsets.UTF_8)
                } else {
                    data
                }

                if (!StorageProvider.writePackFile(context, typeFolder, pack.name, relativePath, finalData)) {
                    throw IOException("Failed to write: $relativePath")
                }

                globalCount++
                if (globalCount % 5 == 0 || globalCount == total) {
                    onProgress(globalCount.toFloat() / total, entry.name)
                }
            }

            installed.add(pack)
        }

        zip.close()
        installed
    }

    private fun applyVersionOverride(manifestJson: String, overrides: Map<String, String>): String {
        if (overrides.isEmpty()) return manifestJson
        try {
            val json = JSONObject(manifestJson)
            val header = json.optJSONObject("header") ?: return manifestJson
            var changed = false

            overrides["min_engine_version"]?.let { versionStr ->
                val parts = versionStr.split(".").mapNotNull { it.toIntOrNull() }
                if (parts.size == 3) {
                    header.put("min_engine_version", org.json.JSONArray(parts))
                    changed = true
                }
            }

            overrides["pack_version"]?.let { versionStr ->
                val parts = versionStr.split(".").mapNotNull { it.toIntOrNull() }
                if (parts.size == 3) {
                    header.put("version", org.json.JSONArray(parts))
                    changed = true
                }
            }

            if (changed) {
                json.put("header", header)
                return json.toString(2)
            }
        } catch (_: Exception) {}
        return manifestJson
    }
}
