package com.zippermc.extractor

import com.zippermc.model.AnalysisResult
import com.zippermc.model.PackInfo
import com.zippermc.model.ZipEntryType
import java.io.File
import java.util.zip.ZipFile

object ZipAnalyzer {

    fun analyze(file: File): AnalysisResult {
        return ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val entryNames = entries.map { it.name }

            val rootFiles = entryNames
                .filter { "/" !in it }
                .toSet()

            val subDirs = entryNames
                .map { it.substringBefore("/") }
                .distinct()
                .filter { it.isNotBlank() }

            val packs = mutableListOf<PackInfo>()

            if (rootFiles.contains("level.dat") || entryNames.any { it.startsWith("db/") }) {
                val name = entryNames.firstOrNull { it.endsWith("/levelname.txt") }
                    ?.let { readTextEntry(zip, it) }
                    ?: subDirs.firstOrNull { it != "db" } ?: "World"
                packs.add(PackInfo(ZipEntryType.WORLD, name, ""))
            }

            if (rootFiles.contains("skins.json")) {
                val name = readManifestName(zip, "") ?: subDirs.firstOrNull() ?: "Skin Pack"
                packs.add(PackInfo(ZipEntryType.SKIN_PACK, name, ""))
            }

            val manifestDirs = findManifestDirs(entryNames, rootFiles, subDirs)
            for ((subPath, manifestPath) in manifestDirs) {
                val mType = readManifestType(zip, manifestPath)
                val jsonText = readEntryText(zip, manifestPath)
                val mName = readManifestName(zip, manifestPath) ?: subPath.ifBlank { file.nameWithoutExtension }
                if (mType != null) {
                    val type = when (mType) {
                        "resources" -> ZipEntryType.RESOURCE_PACK
                        "data" -> ZipEntryType.BEHAVIOR_PACK
                        else -> ZipEntryType.UNKNOWN
                    }
                    packs.add(PackInfo(type, mName, subPath, jsonText))
                }
            }

            if (packs.isEmpty()) {
                val type = guessByStructure(entryNames)
                packs.add(PackInfo(type, file.nameWithoutExtension, ""))
            }

            AnalysisResult(
                packs = packs.distinctBy { it.subPath },
                totalEntryCount = entries.size,
                fileName = file.name,
            )
        }
    }

    private fun findManifestDirs(
        entryNames: List<String>,
        rootFiles: Set<String>,
        subDirs: List<String>,
    ): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()

        if ("manifest.json" in rootFiles) {
            results.add("" to "manifest.json")
        }

        for (dir in subDirs) {
            val manifestPath = "$dir/manifest.json"
            if (entryNames.any { it == manifestPath || it.startsWith("$dir/") && it.endsWith("/manifest.json") }) {
                val actualPath = entryNames.first { it.endsWith("/manifest.json") && it.startsWith("$dir/") }
                results.add(dir to actualPath)
            }
        }

        return results
    }

    private fun guessByStructure(entryNames: List<String>): ZipEntryType {
        val score = mutableMapOf<ZipEntryType, Int>()
        for (name in entryNames) {
            val lower = name.lowercase()
            when {
                lower.startsWith("textures/") || lower.startsWith("texts/") ||
                    lower.startsWith("sounds/") || lower.startsWith("models/") ||
                    name.endsWith(".lang") || name.endsWith(".mcmeta") ||
                    lower.startsWith("font/") || lower.startsWith("shaders/") ||
                    name.endsWith(".png") && !lower.contains("skins") -> {
                    score[ZipEntryType.RESOURCE_PACK] =
                        score.getOrDefault(ZipEntryType.RESOURCE_PACK, 0) + 1
                }
                lower.startsWith("entities/") || lower.startsWith("scripts/") ||
                    lower.startsWith("functions/") || lower.startsWith("structures/") ||
                    name.endsWith(".mcfunction") -> {
                    score[ZipEntryType.BEHAVIOR_PACK] =
                        score.getOrDefault(ZipEntryType.BEHAVIOR_PACK, 0) + 1
                }
            }
        }
        return score.maxByOrNull { it.value }?.let {
            if (it.value > 2) it.key else ZipEntryType.UNKNOWN
        } ?: ZipEntryType.UNKNOWN
    }

    private fun readManifestType(zip: ZipFile, manifestPath: String): String? {
        val entry = zip.getEntry(manifestPath) ?: return null
        return readJsonField(zip, entry, "type")
    }

    private fun readManifestName(zip: ZipFile, manifestPath: String): String? {
        val entry = zip.getEntry(manifestPath) ?: return null
        val header = readJsonField(zip, entry, "header")
        if (header != null) {
            return try {
                val nameKey = if (header.contains("\"name\"")) "name" else "Name"
                Regex(""""$nameKey"\s*:\s*"([^"]+)""")
                    .find(header)?.groupValues?.getOrNull(1)
            } catch (_: Exception) { null }
        }
        return readJsonField(zip, entry, "name")
    }

    private fun readEntryText(zip: ZipFile, path: String): String? {
        return try {
            val entry = zip.getEntry(path) ?: return null
            zip.getInputStream(entry).bufferedReader().use { it.readText() }
        } catch (_: Exception) { null }
    }

    private fun readJsonField(zip: ZipFile, entry: java.util.zip.ZipEntry, field: String): String? {
        return try {
            val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            Regex(""""$field"\s*:\s*"([^"]+)""").find(text)?.groupValues?.getOrNull(1)
                ?: Regex(""""$field"\s*:\s*([^,}\s]+)""").find(text)?.groupValues?.getOrNull(1)
        } catch (_: Exception) { null }
    }

    private fun readTextEntry(zip: ZipFile, name: String): String? {
        return try {
            val entry = zip.getEntry(name) ?: return null
            zip.getInputStream(entry).bufferedReader().use { it.readLine() }
        } catch (_: Exception) { null }
    }
}
