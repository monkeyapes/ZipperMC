package com.zippermc.extractor

import com.zippermc.model.PackInfo
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
        packs: List<PackInfo>,
        onProgress: (Float, String) -> Unit,
    ): List<PackInfo> = withContext(Dispatchers.IO) {
        val zip = ZipFile(zipFile)
        val allEntries = zip.entries().asSequence().toList()
        val total = allEntries.size
        var globalCount = 0
        val installed = mutableListOf<PackInfo>()

        for (pack in packs) {
            val targetDir = getTargetDir(pack.type)
            if (targetDir == null) continue

            val baseName = sanitizeFileName(pack.name)
            val packTarget = File(targetDir, baseName).also { it.mkdirs() }
            val prefix = pack.subPath.let { if (it.isBlank()) null else "$it/" }

            val relevantEntries = if (prefix != null) {
                allEntries.filter { it.name.startsWith(prefix) && !it.isDirectory }
                    .map { it to it.name.removePrefix(prefix) }
            } else {
                allEntries.filter { "/" !in it.name || !it.isDirectory }
                    .map { it to it.name }
            }

            for ((entry, relativePath) in relevantEntries) {
                val outputFile = File(packTarget, relativePath)
                outputFile.parentFile?.mkdirs()
                try {
                    zip.getInputStream(entry).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {}

                globalCount++
                if (globalCount % 5 == 0 || globalCount == total) {
                    onProgress(globalCount.toFloat() / total, entry.name)
                }
            }

            installed.add(pack)
        }

        zip.close()
        return@withContext installed
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
