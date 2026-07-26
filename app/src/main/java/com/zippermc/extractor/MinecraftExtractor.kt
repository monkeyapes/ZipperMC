package com.zippermc.extractor

import com.zippermc.model.AnalysisResult
import com.zippermc.model.ZipEntryType
import com.zippermc.util.FileUtils.sanitizeFileName
import com.zippermc.util.MinecraftPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

object MinecraftExtractor {

    suspend fun extract(
        zipFile: File,
        analysis: AnalysisResult,
        onProgress: (Float, String) -> Unit,
    ): Map<ZipEntryType, Int> = withContext(Dispatchers.IO) {
        val summary = mutableMapOf<ZipEntryType, Int>()
        val zip = ZipFile(zipFile)
        val entries = zip.entries().asSequence().toList()
        val total = entries.size
        var count = 0

        val primaryTarget = getTargetDir(analysis.primaryType)
        if (primaryTarget == null) {
            zip.close()
            return@withContext summary
        }

        val baseName = sanitizeFileName(analysis.detectedName)
        val targetDir = File(primaryTarget, baseName).also { it.mkdirs() }

        for (entry in entries) {
            if (entry.isDirectory) continue
            val entryPath = entry.name
            val outputFile = File(targetDir, entryPath)

            outputFile.parentFile?.mkdirs()
            try {
                zip.getInputStream(entry).use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (_: Exception) {
                // skip problematic entries
            }

            count++
            if (count % 5 == 0 || count == total) {
                onProgress(count.toFloat() / total, entryPath)
            }
        }

        zip.close()
        summary[analysis.primaryType] = entries.count { !it.isDirectory }
        return@withContext summary
    }

    private fun getTargetDir(type: ZipEntryType): File? {
        return when (type) {
            ZipEntryType.RESOURCE_PACK -> MinecraftPaths.resourcePacks
            ZipEntryType.BEHAVIOR_PACK -> MinecraftPaths.behaviorPacks
            ZipEntryType.WORLD -> MinecraftPaths.worlds
            ZipEntryType.SKIN_PACK -> MinecraftPaths.skinPacks
            ZipEntryType.UNKNOWN -> MinecraftPaths.resourcePacks
        }
    }
}
