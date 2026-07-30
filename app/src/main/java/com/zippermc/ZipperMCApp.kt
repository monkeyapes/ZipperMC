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
                val dir = filesDir; dir.mkdirs()
                val msg = "${e::class.java.name}: ${e.message}"
                val ts = try { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()) } catch (_: Exception) { "?" }

                FileWriter(File(dir, "event.log"), true).use { it.write("[$ts] CRASH: $msg\n") }

                val existing = dir.listFiles()?.filter { it.name.startsWith("crash_") && it.name.endsWith(".log") }?.maxOfOrNull { it.name } ?: "crash_0"
                val idx = existing.substringAfter("crash_").substringBefore(".log").toIntOrNull() ?: 0
                val file = File(dir, "crash_${idx + 1}.log")
                FileWriter(file).use { it.write("$msg\n${e.stackTraceToString()}") }

                getSharedPreferences("zippermc_crash", 0).edit().putInt("crash_count", idx + 1).putString("last_crash", msg).apply()
            } catch (_: Exception) {}
            prev?.uncaughtException(thread, e)
        }
    }
}
