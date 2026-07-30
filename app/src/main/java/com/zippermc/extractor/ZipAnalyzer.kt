package com.zippermc.extractor

import com.zippermc.model.AnalysisResult
import com.zippermc.model.PackInfo
import com.zippermc.model.ZipEntryType
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ZipAnalyzer {

    fun analyze(file: File): AnalysisResult {
        val size = file.length()
        val magic = readMagic(file)

        val packsFromContent = readPacksFromContent(file, magic, size)
        if (packsFromContent != null) {
            return packsFromContent
        }

        val packsFromExtension = packsByExtension(file)
        if (packsFromExtension != null) return packsFromExtension

        return AnalysisResult(
            packs = listOf(PackInfo(ZipEntryType.UNKNOWN, file.nameWithoutExtension, "")),
            totalEntryCount = 0,
            fileName = file.name,
        )
    }

    private fun readPacksFromContent(file: File, magic: ByteArray, size: Long): AnalysisResult? {
        val attempts = listOf(
            { readViaZipFile(file) },
            { readViaJarFile(file) },
            { readViaZipStream(file) },
            { readViaManualScan(file, magic) },
        )
        for (attempt in attempts) {
            try {
                val result = attempt() ?: continue
                if (result.packs.isNotEmpty() || result.totalEntryCount > 0) {
                    return result
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun readMagic(file: File): ByteArray = try {
        RandomAccessFile(file, "r").use { raf ->
            val buf = ByteArray(8); val n = raf.read(buf)
            if (n > 0) buf.copyOf(n) else byteArrayOf()
        }
    } catch (_: Exception) { byteArrayOf() }

    private fun magicHex(buf: ByteArray): String =
        buf.joinToString("") { "%02X".format(it) }

    private fun isZipMagic(buf: ByteArray): Boolean =
        buf.size >= 2 && buf[0] == 0x50.toByte() && buf[1] == 0x4B.toByte()

    private fun readViaZipFile(file: File): AnalysisResult? {
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val entryNames = entries.map { it.name }
                val contents = readEntryContents(zip, entries)
                val result = buildResult(entryNames, contents, file, entries.size)
                if (result.packs.isNotEmpty()) return result
            }
        } catch (_: Exception) {}
        return null
    }

    private fun readViaJarFile(file: File): AnalysisResult? {
        try {
            JarFile(file).use { jar ->
                val entries = jar.entries().asSequence().toList()
                val entryNames = entries.map { it.name }
                val contents = mutableMapOf<String, String>()
                for (entry in entries) {
                    if (isManifestKey(entry.name)) {
                        try {
                            contents[entry.name] = jar.getInputStream(entry).bufferedReader().use { it.readText() }
                        } catch (_: Exception) {}
                    }
                }
                val result = buildResult(entryNames, contents, file, entries.size)
                if (result.packs.isNotEmpty()) return result
            }
        } catch (_: Exception) {}
        return null
    }

    private fun readViaZipStream(file: File): AnalysisResult? {
        try {
            FileInputStream(file).use { fis ->
                ZipInputStream(fis).use { zis ->
                    val entryNames = mutableListOf<String>()
                    val contents = mutableMapOf<String, String>()
                    var ze: ZipEntry? = zis.nextEntry
                    while (ze != null) {
                        val name = ze.name
                        entryNames.add(name)
                        if (isManifestKey(name)) {
                            try {
                                contents[name] = zis.bufferedReader().use { it.readText() }
                            } catch (_: Exception) {}
                        }
                        zis.closeEntry()
                        ze = zis.nextEntry
                    }
                    val result = buildResult(entryNames, contents, file, entryNames.size)
                    if (result.packs.isNotEmpty() || result.totalEntryCount > 0) return result
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun readViaManualScan(file: File, magic: ByteArray): AnalysisResult? {
        if (!isZipMagic(magic)) return null
        try {
            RandomAccessFile(file, "r").use { raf ->
                val entryNames = mutableListOf<String>()
                val fileLen = raf.length()
                var pos = 0L
                val buf = ByteArray(4)

                val localHeaderSig = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
                val centralDirSig = byteArrayOf(0x50, 0x4B, 0x01, 0x02)
                val eocdSig = byteArrayOf(0x50, 0x4B, 0x05, 0x06)

                while (pos < fileLen - 4) {
                    raf.seek(pos)
                    raf.readFully(buf)
                    if (buf.contentEquals(localHeaderSig)) {
                        raf.seek(pos + 26)
                        val nameLen = readLEShort(raf)
                        val extraLen = readLEShort(raf)
                        if (nameLen > 0 && nameLen < 65535) {
                            raf.seek(pos + 30)
                            val nameBytes = ByteArray(nameLen)
                            raf.readFully(nameBytes)
                            val name = try { String(nameBytes, Charsets.UTF_8) } catch (_: Exception) { null }
                            if (name != null && name.isNotBlank()) {
                                entryNames.add(name)
                            }
                        }
                        val headerSize = 30 + nameLen + extraLen
                        val compSize = try {
                            raf.seek(pos + 18); readLEInt(raf)
                        } catch (_: Exception) { 0 }
                        pos += headerSize + compSize
                    } else if (buf.contentEquals(centralDirSig) || buf.contentEquals(eocdSig)) {
                        break
                    } else {
                        pos++
                    }
                }

                if (entryNames.isNotEmpty()) {
                    val result = buildResult(entryNames, emptyMap(), file, entryNames.size)
                    if (result.packs.isNotEmpty()) return result
                    return result
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun readEntryContents(zip: ZipFile, entries: List<ZipEntry>): Map<String, String> {
        val contents = mutableMapOf<String, String>()
        for (entry in entries) {
            if (isManifestKey(entry.name)) {
                try {
                    contents[entry.name] = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                } catch (_: Exception) {}
            }
        }
        return contents
    }

    private fun isManifestKey(name: String): Boolean =
        name.endsWith("manifest.json") || name == "levelname.txt" || name == "skins.json"

    private fun readLEShort(raf: RandomAccessFile): Int {
        val lo = raf.readUnsignedByte()
        val hi = raf.readUnsignedByte()
        return (hi shl 8) or lo
    }

    private fun readLEInt(raf: RandomAccessFile): Long {
        val b0 = raf.readUnsignedByte().toLong()
        val b1 = raf.readUnsignedByte().toLong()
        val b2 = raf.readUnsignedByte().toLong()
        val b3 = raf.readUnsignedByte().toLong()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

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
            val nestedZips = entryNames.filter { e ->
                !e.endsWith("/") && (e.endsWith(".mcpack") || e.endsWith(".mcaddon") || e.endsWith(".zip") || e.endsWith(".mcworld") || e.endsWith(".mctemplate"))
            }
            if (nestedZips.isNotEmpty()) {
                for (n in nestedZips) {
                    val ext = n.substringAfterLast(".", "")
                    val guessedType = when (ext) {
                        "mcworld", "mctemplate" -> ZipEntryType.WORLD
                        "mcskin" -> ZipEntryType.SKIN_PACK
                        "mcaddon" -> ZipEntryType.UNKNOWN
                        else -> ZipEntryType.UNKNOWN
                    }
                    packs.add(PackInfo(guessedType, n.substringBeforeLast("."), n))
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

    private fun packsByExtension(file: File): AnalysisResult? {
        val ext = file.extension.lowercase()
        val type = when (ext) {
            "mcworld", "mctemplate" -> ZipEntryType.WORLD
            "mcskin" -> ZipEntryType.SKIN_PACK
            "mcaddon", "mcpack", "zip" -> ZipEntryType.UNKNOWN
            else -> return null
        }
        return AnalysisResult(
            packs = listOf(PackInfo(type, file.nameWithoutExtension, "")),
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
