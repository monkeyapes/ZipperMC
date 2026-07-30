package com.zippermc.extractor

import com.zippermc.model.AnalysisResult
import com.zippermc.model.PackInfo
import com.zippermc.model.ZipEntryType
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

object ZipAnalyzer {

    fun analyze(file: File): AnalysisResult? {
        return try {
            val entryMap = readAllEntries(file) ?: return null
            buildResult(entryMap, file)
        } catch (_: Throwable) { null }
    }

    private fun readAllEntries(file: File): Map<String, String?>? {
        try {
            ZipInputStream(FileInputStream(file)).use { zis ->
                val entries = mutableMapOf<String, String?>()
                var ze = zis.nextEntry
                while (ze != null && entries.size < 500) {
                    val name = safe(ze.name)
                    if (name != null) {
                        entries[name] = if (isManifestKey(name)) try {
                            zis.bufferedReader().use { it.readText() }
                        } catch (_: Throwable) { null } else null
                    }
                    zis.closeEntry()
                    ze = zis.nextEntry
                }
                if (entries.isEmpty()) return null
                return entries
            }
        } catch (_: Throwable) { return null }
    }

    private fun safe(name: String): String? {
        val s = name.replace(Regex("[\u0000-\u001f\u007f]"), "").take(200)
        return s.ifBlank { null }
    }

    private fun isManifestKey(name: String): Boolean =
        name.endsWith("manifest.json") || name == "levelname.txt" || name == "skins.json"

    private fun buildResult(entries: Map<String, String?>, file: File): AnalysisResult? {
        return try {
            val names = entries.keys.toList()
            val rootFiles = names.filter { "/" !in it }.toSet()
            val subDirs = names.map { it.substringBefore("/") }.distinct().filter { it.isNotBlank() }
            val packs = mutableListOf<PackInfo>()
            val readText = { path: String -> entries[path] }

            if (rootFiles.contains("level.dat") || names.any { it.startsWith("db/") }) {
                val wName = names.firstOrNull { it.endsWith("/levelname.txt") }?.let { readText(it) }?.trim()
                    ?: subDirs.firstOrNull { it != "db" } ?: "World"
                packs.add(PackInfo(ZipEntryType.WORLD, wName, ""))
            }

            if (rootFiles.contains("skins.json")) {
                val text = readText("skins.json")
                val sName = if (text != null) (jsonField(text, "name") ?: jsonField(text, "Name")) else null
                packs.add(PackInfo(ZipEntryType.SKIN_PACK, sName ?: subDirs.firstOrNull() ?: "Skin Pack", ""))
            }

            findManifests(names, rootFiles, subDirs).forEach { (sub, path) ->
                val json = readText(path)
                if (json != null) {
                    val type = jsonField(json, "type")
                    val mName = manifestName(json) ?: sub.ifBlank { file.nameWithoutExtension }
                    if (type != null) {
                        packs.add(PackInfo(
                            type = when (type) { "resources" -> ZipEntryType.RESOURCE_PACK; "data" -> ZipEntryType.BEHAVIOR_PACK; else -> ZipEntryType.UNKNOWN },
                            name = mName, subPath = sub, manifestJson = json,
                        ))
                    }
                }
            }

            if (packs.isEmpty()) {
                val nested = names.filter { e -> !e.endsWith("/") && e.contains(".") && !e.contains("/") }
                if (nested.isNotEmpty()) {
                    nested.take(10).forEach { n ->
                        val t = when (n.substringAfterLast(".")) { "mcworld", "mctemplate" -> ZipEntryType.WORLD; "mcskin" -> ZipEntryType.SKIN_PACK; else -> ZipEntryType.UNKNOWN }
                        packs.add(PackInfo(t, n.substringBeforeLast("."), n))
                    }
                } else {
                    val type = guessStructure(names)
                    packs.add(PackInfo(type, file.nameWithoutExtension, ""))
                }
            }

            if (packs.isEmpty()) return null
            AnalysisResult(packs = packs.distinctBy { it.subPath }.take(20), totalEntryCount = names.size, fileName = file.name)
        } catch (_: Throwable) { null }
    }

    fun fromExtension(file: File): AnalysisResult {
        val ext = file.extension.lowercase()
        val type = when (ext) {
            "mcworld", "mctemplate" -> ZipEntryType.WORLD
            "mcskin" -> ZipEntryType.SKIN_PACK
            else -> ZipEntryType.UNKNOWN
        }
        return AnalysisResult(listOf(PackInfo(type, file.nameWithoutExtension, "")), 0, file.name)
    }

    private fun findManifests(names: List<String>, rootFiles: Set<String>, subDirs: List<String>): List<Pair<String, String>> {
        val r = mutableListOf<Pair<String, String>>()
        if ("manifest.json" in rootFiles) r.add("" to "manifest.json")
        subDirs.forEach { dir ->
            val path = "$dir/manifest.json"
            val match = names.firstOrNull { it == path || (it.startsWith("$dir/") && it.endsWith("/manifest.json")) }
            if (match != null) r.add(dir to match)
        }
        return r
    }

    private fun guessStructure(names: List<String>): ZipEntryType {
        var rp = 0; var bp = 0
        names.forEach { n ->
            val l = n.lowercase()
            if (l.startsWith("textures/") || l.startsWith("texts/") || l.startsWith("sounds/") || l.startsWith("models/") || n.endsWith(".lang") || n.endsWith(".mcmeta") || l.startsWith("font/") || l.startsWith("shaders/") || (n.endsWith(".png") && !l.contains("skins"))) rp++
            if (l.startsWith("entities/") || l.startsWith("scripts/") || l.startsWith("functions/") || l.startsWith("structures/") || n.endsWith(".mcfunction")) bp++
        }
        return when { rp > bp && rp > 2 -> ZipEntryType.RESOURCE_PACK; bp > rp && bp > 2 -> ZipEntryType.BEHAVIOR_PACK; else -> ZipEntryType.UNKNOWN }
    }

    private fun jsonField(json: String, field: String): String? = try {
        Regex(""""$field"\s*:\s*"([^"]+)""").find(json)?.groupValues?.getOrNull(1)
            ?: Regex(""""$field"\s*:\s*([^,}\s]+)""").find(json)?.groupValues?.getOrNull(1)
    } catch (_: Throwable) { null }

    private fun manifestName(json: String): String? = try {
        val h = jsonField(json, "header")
        if (h != null) {
            val key = if (h.contains("\"name\"")) "name" else "Name"
            Regex(""""$key"\s*:\s*"([^"]+)""").find(h)?.groupValues?.getOrNull(1)
        } else jsonField(json, "name")
    } catch (_: Throwable) { null }
}
