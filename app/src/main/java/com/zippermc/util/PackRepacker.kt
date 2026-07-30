package com.zippermc.util

import com.zippermc.model.AnalysisResult
import com.zippermc.model.ZipEntryType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object PackRepacker {

    private val MC_EXTS = setOf("mcaddon", "mcpack", "mcworld", "mctemplate", "mcskin", "mcres")

    fun repack(inputFile: File, overrides: Map<String, String>, analysis: AnalysisResult? = null): File {
        val ext = inputFile.extension.lowercase()

        if (ext in MC_EXTS && overrides.isEmpty()) return inputFile

        val targetExt = targetExtension(ext, analysis)

        if (overrides.isEmpty() && ext == targetExt) return inputFile

        if (overrides.isEmpty() && ext != targetExt && analysis?.packs?.isNotEmpty() == true) {
            if (targetExt == "mcaddon" || targetExt in MC_EXTS) {
                val renamed = File(inputFile.parent, "${inputFile.nameWithoutExtension}.$targetExt")
                if (renamed.exists()) renamed.delete()
                eventLog("Renaming ${inputFile.name} -> ${renamed.name}")
                return inputFile.copyTo(renamed)
            }
        }

        if (overrides.isEmpty() && ext != targetExt && (analysis == null || analysis.packs.isEmpty())) {
            return buildFallbackPack(inputFile, targetExt)
        }

        val outputName = if (ext == targetExt) "modified_${inputFile.name}" else "${inputFile.nameWithoutExtension}.$targetExt"
        val outputFile = File(inputFile.parent, outputName)
        if (outputFile.exists()) outputFile.delete()

        ZipFile(inputFile).use { zip ->
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val modified = if (entry.name.endsWith("manifest.json") && overrides.isNotEmpty()) {
                        val text = zip.getInputStream(entry).bufferedReader().readText()
                        applyVersionOverride(text, overrides)
                    } else null

                    zos.putNextEntry(ZipEntry(entry.name))
                    if (modified != null) zos.write(modified.toByteArray(Charsets.UTF_8))
                    else zip.getInputStream(entry).copyTo(zos)
                    zos.closeEntry()
                }
            }
        }

        return outputFile
    }

    private fun targetExtension(currentExt: String, analysis: AnalysisResult?): String {
        if (currentExt in MC_EXTS) return currentExt
        if (analysis == null) return "mcpack"
        val types = analysis.packs.map { it.type }
        if (types.any { it == ZipEntryType.WORLD }) return "mcworld"
        if (types.any { it == ZipEntryType.SKIN_PACK }) return "mcskin"
        val subPaths = analysis.packs.map { it.subPath }.filter { it.isNotBlank() }
        if (subPaths.size > 1 || (subPaths.size == 1 && subPaths[0] != "")) return "mcaddon"
        return "mcpack"
    }

    private fun buildFallbackPack(file: File, targetExt: String): File {
        val outputFile = File(file.parent, "${file.nameWithoutExtension}.$targetExt")
        if (outputFile.exists()) outputFile.delete()
        val uuid1 = java.util.UUID.randomUUID().toString()
        val uuid2 = java.util.UUID.randomUUID().toString()
        val name = file.nameWithoutExtension
        val manifest = JSONObject().apply {
            put("format_version", 2)
            put("header", JSONObject().apply {
                put("name", name)
                put("description", "Converted by ZipperMC")
                put("uuid", uuid1)
                put("version", JSONArray(intArrayOf(1, 0, 0)))
                put("min_engine_version", JSONArray(intArrayOf(1, 19, 0)))
            })
            put("modules", JSONArray().put(JSONObject().apply {
                put("type", "resources")
                put("uuid", uuid2)
                put("version", JSONArray(intArrayOf(1, 0, 0)))
            }))
        }
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    zos.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).copyTo(zos)
                    zos.closeEntry()
                }
            }
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        eventLog("Built fallback .$targetExt for ${file.name} -> $name")
        return outputFile
    }

    private fun eventLog(msg: String) {
        try {
            val dir = File(System.getProperty("java.io.tmpdir"))
            val log = File(dir, "packrepacker.log")
            log.appendText("[${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}] $msg\n")
        } catch (_: Exception) {}
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
                    header.put("min_engine_version", JSONArray(parts))
                    changed = true
                }
            }

            overrides["pack_version"]?.let { versionStr ->
                val parts = versionStr.split(".").mapNotNull { it.toIntOrNull() }
                if (parts.size == 3) {
                    header.put("version", JSONArray(parts))
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
