package com.zippermc

import android.app.Application
import java.io.File
import java.io.FileWriter

class ZipperMCApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val dir = filesDir
                dir.mkdirs()
                val existing = dir.listFiles()?.filter { it.name.startsWith("crash_") && it.name.endsWith(".log") }?.maxOfOrNull { it.name } ?: "crash_0"
                val idx = existing.substringAfter("crash_").substringBefore(".log").toIntOrNull() ?: 0
                val file = File(dir, "crash_${idx + 1}.log")
                FileWriter(file).use { it.write("${e::class.java.name}: ${e.message}\n${e.stackTraceToString()}") }
                val prefs = getSharedPreferences("zippermc_crash", 0)
                prefs.edit().putInt("crash_count", idx + 1).putString("last_crash", "${e::class.java.simpleName}: ${e.message}").apply()
            } catch (_: Exception) {}
            prev?.uncaughtException(thread, e)
        }
    }
}
