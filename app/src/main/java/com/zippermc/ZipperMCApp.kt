package com.zippermc

import android.app.Application
import android.content.SharedPreferences
import java.io.File
import java.io.FileWriter

class ZipperMCApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                val prefs = getSharedPreferences("zippermc_crash", 0)
                val count = prefs.getInt("crash_count", 0) + 1
                prefs.edit().putInt("crash_count", count).apply()
                val file = File(filesDir, "crash_$count.log")
                FileWriter(file).use { it.write("${e::class.java.name}: ${e.message}\n${e.stackTraceToString()}") }
            } catch (_: Exception) {}
            null
        }
    }
}
