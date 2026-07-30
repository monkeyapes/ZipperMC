package com.zippermc.extractor

import com.zippermc.model.AnalysisResult
import com.zippermc.model.PackInfo
import com.zippermc.model.ZipEntryType
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ZipAnalyzer {

    private const val MAX_ENTRIES = 2000
    private const val MAX_NAME_LENGTH = 256

    fun analyze(file: File): AnalysisResult {
        val magic = readMagic(file)
        val strategies = listOf(
            { readViaZipFile(file) },
            { readViaZipStream(file) },
        )
        for (strategy in strategies) {
            try {
                val result = strategy() ?: continue
                if (result.packs.isNotEmpty() || result.totalEntryCount > 0) {
                    return result
                }
            } catch (_: Exception) {}
        }

        return packsByExtension(file)
            ?: AnalysisResult(
                packs = listOf(PackInfo(ZipEntryType.UNKNOWN, safeName(file.nameWithoutExtension), "")),
                totalEntryCount = 0,
                fileName = file.name,
            )
    }

    private fun readMagic(file: File): ByteArray = try {
        RandomAccessFile(file, "r").use { raf ->
            val buf = ByteArray(4); val n = raf.read(buf)
            if (n > 0) buf.copyOf(n) else byteArrayOf()
        }
    } catch (_: Exception) { byteArrayOf() }

    private fun safeName(name: String): String =
        name.take(120).replace(Regex("[\u0000-\u001f]"), "").ifBlank { "Pack" }

    private fun safeEntryNames(names: List<String>): List<String> =
        names.map { it.take(MAX_NAME_LENGTH).replace(Regex("[\u0000-\u001f\u007f]"), "") }
            .filter { it.isNotBlank() }
            .take(MAX_ENTRIES)

    private fun readViaZipFile(file: File): AnalysisResult? {
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val entryNames = safeEntryNames(entries.map { it.name })
                val contents = readEntryContents(zip, entries, entryNames)
                return buildResult(entryNames, contents, file, entries.size)
            }
        } catch (_: Exception) {}
        return null
    }

    private fun readViaZipStream(file: File): AnalysisResult? {
        try {
            FileInputStream(file).use { fis ->
                ZipInputStream(fis).use { zis ->
                    val rawNames = mutableListOf<String>()
                    val contents = mutableMapOf<String, String>()
                    var ze: ZipEntry? = zis.nextEntry
                    while (ze != null && rawNames.size < MAX_ENTRIES) {
                        val name = ze.name
                        rawNames.add(name)
                        if (isManifestKey(name)) {
                            try {
                                contents[name] = zis.bufferedReader().use { it.readText() }
                            } catch (_: Exception) {}
                        }
                        zis.closeEntry()
                        ze = zis.nextEntry
                    }
                    val entryNames = safeEntryNames(rawNames)
                    return buildResult(entryNames, contents, file, rawNames.size)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun readEntryContents(
        zip: ZipFile,
        entries: List<ZipEntry>,
        validNames: List<String>,
    ): Map<String, String> {
        val validSet = validNames.toSet()
        val contents = mutableMapOf<String, String>()
        for (entry in entries) {
            val name = entry.name
            if (name in validSet && isManifestKey(name)) {
                try {
                    contents[name] = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                } catch (_: Exception) {}
            }
        }
        return contents
    }

    private fun isManifestKey(name: String): Boolean =
        name.endsWith("manifest.json") || name == "levelname.txt" || name == "skins.json"

    private fun buildResult(
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
            packs.add(PackInfo(ZipEntryType.WORLD, safeName(name), ""))
        }

        if (rootFiles.contains("skins.json")) {
            val text = contents["skins.json"]
            val skinName = if (text != null) (parseJsonField(text, "name") ?: parseJsonField(text, "Name")) else null
            packs.add(PackInfo(ZipEntryType.SKIN_PACK, safeName(skinName ?: subDirs.firstOrNull() ?: "Skin Pack"), ""))
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
                    packs.add(PackInfo(type, safeName(mName), subPath, jsonText))
                }
            }
        }

        if (packs.isEmpty()) {
            val nestedZips = entryNames.filter { e ->
                !e.endsWith("/") && (e.endsWith(".mcpack") || e.endsWith(".mcaddon") || e.endsWith(".zip") || e.endsWith(".mcworld") || e.endsWith(".mctemplate"))
            }
            if (nestedZips.isNotEmpty()) {
                for (n in nestedZips.take(10)) {
                    val ext = n.substringAfterLast(".", "")
                    val guessedType = when (ext) {
                        "mcworld", "mctemplate" -> ZipEntryType.WORLD
                        "mcskin" -> ZipEntryType.SKIN_PACK
                        else -> ZipEntryType.UNKNOWN
                    }
                    packs.add(PackInfo(guessedType, safeName(n.substringBeforeLast(".")), n))
                }
            } else {
                val type = guessByStructure(entryNames)
                packs.add(PackInfo(type, safeName(file.nameWithoutExtension), ""))
            }
        }

        return AnalysisResult(
            packs = packs.distinctBy { it.subPath }.take(20),
            totalEntryCount = totalEntries,
            fileName = file.name,
        )
    }

    private fun packsByExtension(file: File): AnalysisResult? {
        val ext = file.extension.lowercase()
        val type = when (ext) {
            "mcworld", "mctemplate" -> ZipEntryType.WORLD
            "mcskin" -> ZipEntryType.SKIN_PACK
            "mcaddon", "mcpack", "zip" -> ZipEntryType.UNKNOWN
            else -> return null
        }
        return AnalysisResult(
            packs = listOf(PackInfo(type, safeName(file.nameWithoutExtension), "")),
            totalEntryCount = 0,
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
