package com.zippermc.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.zippermc.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onToggleTheme: () -> Unit) {
    val autoScan by viewModel.autoScan.collectAsState()
    val darkTheme by viewModel.darkTheme.collectAsState()
    val eventLog by viewModel.eventLog.collectAsState()
    val context = LocalContext.current

    val crashDir = context.filesDir
    val crashFiles = crashDir.listFiles()?.filter { it.name.startsWith("crash_") && it.name.endsWith(".log") } ?: emptyList()
    val eventFile = File(crashDir, "event.log")

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary),
        )

        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Auto-scan files", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Scan Downloads for Minecraft files on launch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoScan, onCheckedChange = { viewModel.setAutoScan(it) })
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.DarkMode, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Dark theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Use dark color scheme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = darkTheme, onCheckedChange = { viewModel.setDarkTheme(it); onToggleTheme() })
                    }
                }
            }

            if (eventFile.exists()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).clickable {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", eventFile)
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Share event log"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Share event log", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("${eventLog.size} events recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (crashFiles.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).clickable {
                            val latest = crashFiles.maxByOrNull { it.lastModified() } ?: return@clickable
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", latest)
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Share crash log"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.BugReport, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Share crash logs", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                            Text("${crashFiles.size} crash log(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            val ver = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }
            Text("ZipperMC v$ver", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
