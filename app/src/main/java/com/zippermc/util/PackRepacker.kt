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

        if (overrides.isEmpty() && ext != targetExt) {
            val renamed = File(inputFile.parent, "${inputFile.nameWithoutExtension}.$targetExt")
            if (renamed.exists()) renamed.delete()
            return inputFile.copyTo(renamed)
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
        return "mcpack"
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
