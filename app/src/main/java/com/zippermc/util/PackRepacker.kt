package com.zippermc.util

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object PackRepacker {

    fun repack(inputFile: File, overrides: Map<String, String>): File {
        if (overrides.isEmpty()) return inputFile

        val outputFile = File(inputFile.parent, "modified_${inputFile.name}")
        if (outputFile.exists()) outputFile.delete()

        ZipFile(inputFile).use { zip ->
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val modified = if (entry.name.endsWith("manifest.json")) {
                        val text = zip.getInputStream(entry).bufferedReader().readText()
                        applyVersionOverride(text, overrides)
                    } else null

                    zos.putNextEntry(ZipEntry(entry.name))
                    if (modified != null) {
                        zos.write(modified.toByteArray(Charsets.UTF_8))
                    } else {
                        zip.getInputStream(entry).copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }

        return outputFile
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
