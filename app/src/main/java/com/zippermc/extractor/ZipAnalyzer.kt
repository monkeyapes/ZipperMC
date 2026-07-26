package com.zippermc.extractor

import com.zippermc.model.AnalysisResult
import com.zippermc.model.ZipEntryType
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ZipAnalyzer {

    fun analyze(file: File): AnalysisResult {
        val zip = ZipFile(file)
        val entries = zip.entries().asSequence().toList()
        val entryNames = entries.map { it.name }

        val rootDirs = entryNames
            .map { it.substringBefore("/") }
            .distinct()
            .filter { it.isNotBlank() }

        val rootFiles = entryNames
            .filter { "/" !in it }
            .toSet()

        val detectResult = detectType(zip, entryNames, rootDirs, rootFiles)

        val secondary = mutableListOf<ZipEntryType>()

        if (detectResult.primaryType != ZipEntryType.RESOURCE_PACK &&
            hasManifestType(zip, "resources")
        ) {
            secondary.add(ZipEntryType.RESOURCE_PACK)
        }
        if (detectResult.primaryType != ZipEntryType.BEHAVIOR_PACK &&
            hasManifestType(zip, "data")
        ) {
            secondary.add(ZipEntryType.BEHAVIOR_PACK)
        }

        zip.close()

        val name = detectResult.detectedName.ifBlank {
            rootDirs.firstOrNull() ?: file.nameWithoutExtension
        }

        return detectResult.copy(
            secondaryTypes = secondary,
            entryCount = entries.size,
            detectedName = name,
        )
    }

    private fun detectType(
        zip: ZipFile,
        entryNames: List<String>,
        rootDirs: List<String>,
        rootFiles: Set<String>,
    ): AnalysisResult {
        if (rootFiles.contains("level.dat") || entryNames.any { it.startsWith("db/") }) {
            return AnalysisResult(
                primaryType = ZipEntryType.WORLD,
                detectedName = entryNames.firstOrNull { it.endsWith("/levelname.txt") }
                    ?.let { readTextEntry(zip, it) }
                    ?: rootDirs.firstOrNull { it != "db" } ?: "World",
            )
        }

        if (rootFiles.contains("skins.json")) {
            return AnalysisResult(
                primaryType = ZipEntryType.SKIN_PACK,
                detectedName = readManifestName(zip) ?: rootDirs.firstOrNull() ?: "Skin Pack",
            )
        }

        val manifestType = readManifestType(zip)
        if (manifestType != null) {
            val type = when (manifestType) {
                "resources" -> ZipEntryType.RESOURCE_PACK
                "data" -> ZipEntryType.BEHAVIOR_PACK
                else -> ZipEntryType.UNKNOWN
            }
            val name = readManifestName(zip) ?: rootDirs.firstOrNull() ?: "Addon"
            return AnalysisResult(primaryType = type, detectedName = name)
        }

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
                    name.endsWith(".mcfunction") || name.endsWith(".json") &&
                    !lower.contains("manifest") -> {
                    score[ZipEntryType.BEHAVIOR_PACK] =
                        score.getOrDefault(ZipEntryType.BEHAVIOR_PACK, 0) + 1
                }
            }
        }

        val best = score.maxByOrNull { it.value }
        return if (best != null && best.value > 2) {
            AnalysisResult(
                primaryType = best.key,
                detectedName = rootDirs.firstOrNull() ?: file.nameWithoutExtension,
            )
        } else {
            AnalysisResult(
                primaryType = ZipEntryType.UNKNOWN,
                detectedName = rootDirs.firstOrNull() ?: file.nameWithoutExtension,
            )
        }
    }

    private fun hasManifestType(zip: ZipFile, type: String): Boolean {
        return readManifestType(zip) == type
    }

    private fun readManifestType(zip: ZipFile): String? {
        val entry = zip.getEntry("manifest.json")
            ?: zip.entries().asSequence().firstOrNull {
                it.name.endsWith("/manifest.json") && !it.isDirectory
            } ?: return null
        return readJsonField(zip, entry, "type")
    }

    private fun readManifestName(zip: ZipFile): String? {
        val entry = zip.getEntry("manifest.json")
            ?: zip.entries().asSequence().firstOrNull {
                it.name.endsWith("/manifest.json") && !it.isDirectory
            } ?: return null

        val header = readJsonField(zip, entry, "header")
        if (header != null) {
            return try {
                val nameKey = if (header.contains("\"name\"")) "name" else "Name"
                Regex(""""$nameKey"\s*:\s*"([^"]+)""")
                    .find(header)?.groupValues?.getOrNull(1)
            } catch (_: Exception) { null }
        }
        return null
    }

    private fun readJsonField(zip: ZipFile, entry: ZipEntry, field: String): String? {
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
