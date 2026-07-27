package com.zippermc.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
)

class GitHubUpdate {

    companion object {
        private const val REPO = "monkeyapes/ZipperMC"
        private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

        suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val tag = json.getString("tag_name").removePrefix("v")
                val body = json.optString("body", "")
                val assets = json.getJSONArray("assets")
                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                if (downloadUrl != null) {
                    UpdateInfo(tag, downloadUrl, body)
                } else null
            } catch (_: Exception) { null } finally {
                conn?.disconnect()
            }
        }

        fun openDownload(context: Context, url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
