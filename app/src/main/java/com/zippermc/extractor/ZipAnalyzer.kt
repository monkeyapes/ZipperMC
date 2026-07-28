package com.zippermc.extractor

import com.zippermc.model.AnalysisResult
import com.zippermc.model.PackInfo
import com.zippermc.model.ZipEntryType
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ZipAnalyzer {

    fun analyze(file: File): AnalysisResult {
        val magic = try {
            FileInputStream(file).use { `in` ->
                val buf = ByteArray(4); val n = `in`.read(buf)
                if (n > 0) buf.copyOf(n) else byteArrayOf()
            }
        } catch (_: Exception) { byteArrayOf() }
        val magicHex = magic.joinToString("") { "%02X".format(it) }
        try {
            return ZipFile(file).use { zip -> fromZipFile(zip, file) }
        } catch (e1: Exception) {
            try {
                return FileInputStream(file).use { fis ->
                    ZipInputStream(fis).use { zis -> fromZipStream(zis, file) }
                }
            } catch (e2: Exception) {
                throw Exception("magic=$magicHex, size=${file.length()}, zipErr=${e1.message}, streamErr=${e2.message}")
            }
        }
    }

    private fun fromZipFile(zip: ZipFile, file: File): AnalysisResult {
        val entries = zip.entries().asSequence().toList()
        val entryNames = entries.map { it.name }
        val contents = mutableMapOf<String, String>()
        for (entry in entries) {
            val name = entry.name
            if (isManifestEntry(name)) {
                try {
                    contents[name] = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                } catch (_: Exception) {}
            }
        }
        return fromEntryData(entryNames, contents, file, entries.size)
    }

    private fun fromZipStream(zis: ZipInputStream, file: File): AnalysisResult {
        val entryNames = mutableListOf<String>()
        val contents = mutableMapOf<String, String>()
        var ze: ZipEntry? = zis.nextEntry
        while (ze != null) {
            val name = ze.name
            entryNames.add(name)
            if (isManifestEntry(name)) {
                try {
                    contents[name] = zis.bufferedReader().use { it.readText() }
                } catch (_: Exception) {}
            }
            zis.closeEntry()
            ze = zis.nextEntry
        }
        return fromEntryData(entryNames, contents, file, entryNames.size)
    }

    private fun isManifestEntry(name: String): Boolean =
        name.endsWith("manifest.json") || name == "levelname.txt" || name == "skins.json"

    private fun fromEntryData(
        entryNames: List<String>,
        contents: Map<String, String>,
        file: File,
        totalEntries: Int,
    ): AnalysisResult {
        val rootFiles = entryNames.filter { "/" !in it }.toSet()
        val subDirs = entryNames.map { it.substringBefore("/") }.distinct().filter { it.isNotBlank() }
        val packs = mutableListOf<PackInfo>()

        val manifestDirs = findManifestDirs(entryNames, rootFiles, subDirs)

        if (rootFiles.contains("level.dat") || entryNames.any { it.startsWith("db/") }) {
            val name = entryNames.firstOrNull { it.endsWith("/levelname.txt") }
                ?.let { contents[it]?.trim() } ?: subDirs.firstOrNull { it != "db" } ?: "World"
            packs.add(PackInfo(ZipEntryType.WORLD, name, ""))
        }

        if (rootFiles.contains("skins.json")) {
            val text = contents["skins.json"]
            val skinName = if (text != null) (parseJsonField(text, "name") ?: parseJsonField(text, "Name")) else null
            packs.add(PackInfo(ZipEntryType.SKIN_PACK, skinName ?: subDirs.firstOrNull() ?: "Skin Pack", ""))
        }

        for ((subPath, manifestPath) in manifestDirs) {
            val jsonText = contents[manifestPath]
            if (jsonText != null) {
                val mType = parseJsonField(jsonText, "type")
                val mName = parseManifestName(jsonText) ?: subPath.ifBlank { file.nameWithoutExtension }
                if (mType != null) {
                    val type = when (mType) {
                        "resources" -> ZipEntryType.RESOURCE_PACK
                        "data" -> ZipEntryType.BEHAVIOR_PACK
                        else -> ZipEntryType.UNKNOWN
                    }
                    packs.add(PackInfo(type, mName, subPath, jsonText))
                }
            }
        }

        if (packs.isEmpty()) {
            val nestedZips = entryNames.filter { e -> !e.endsWith("/") && (e.endsWith(".mcpack") || e.endsWith(".mcaddon") || e.endsWith(".zip")) }
            if (nestedZips.isNotEmpty()) {
                for (n in nestedZips) {
                    packs.add(PackInfo(ZipEntryType.UNKNOWN, n.substringBeforeLast("."), n))
                }
            } else {
                val type = guessByStructure(entryNames)
                packs.add(PackInfo(type, file.nameWithoutExtension, ""))
            }
        }

        return AnalysisResult(
            packs = packs.distinctBy { it.subPath },
            totalEntryCount = totalEntries,
            fileName = file.name,
        )
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
            if (entryNames.any { it == manifestPath || (it.startsWith("$dir/") && it.endsWith("/manifest.json")) }) {
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

    private fun parseJsonField(json: String, field: String): String? {
        return try {
            Regex(""""$field"\s*:\s*"([^"]+)""").find(json)?.groupValues?.getOrNull(1)
                ?: Regex(""""$field"\s*:\s*([^,}\s]+)""").find(json)?.groupValues?.getOrNull(1)
        } catch (_: Exception) { null }
    }

    private fun parseManifestName(json: String): String? {
        return try {
            val header = parseJsonField(json, "header")
            if (header != null) {
                val nameKey = if (header.contains("\"name\"")) "name" else "Name"
                Regex(""""$nameKey"\s*:\s*"([^"]+)""").find(header)?.groupValues?.getOrNull(1)
            } else {
                parseJsonField(json, "name")
            }
        } catch (_: Exception) { null }
    }
}
