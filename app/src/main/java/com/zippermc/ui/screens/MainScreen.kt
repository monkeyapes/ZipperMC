package com.zippermc.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zippermc.model.AnalysisResult
import com.zippermc.model.ExtractState
import com.zippermc.model.MinecraftInstall
import com.zippermc.model.PackInfo
import com.zippermc.model.ZipEntryType
import com.zippermc.ui.theme.DiamondBlue
import com.zippermc.ui.theme.GrassGreen
import com.zippermc.ui.theme.SkyBlue
import com.zippermc.util.GitHubUpdate
import com.zippermc.util.ScannedFile
import com.zippermc.util.UpdateInfo
import com.zippermc.viewmodel.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel, onToggleTheme: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val updateInfo by viewModel.updateAvailable.collectAsState()
    val context = LocalContext.current

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("Update Available", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("v${info.latestVersion} is ready to download.", style = MaterialTheme.typography.bodyMedium)
                    if (info.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(info.releaseNotes.take(300), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { GitHubUpdate.openDownload(context, info.downloadUrl) }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) {
                    Text("Later")
                }
            },
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.History, null) }, label = { Text("History") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeTab(viewModel)
                1 -> HistoryScreen(viewModel)
                2 -> SettingsScreen(viewModel, onToggleTheme)
            }
        }
    }
}

@Composable
private fun HomeTab(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val scannedFiles by viewModel.scannedFiles.collectAsState()
    val mcInstalls by viewModel.mcInstalls.collectAsState()
    val selectedMc by viewModel.selectedMc.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.onZipPicked(it) } }

    LaunchedEffect(Unit) { viewModel.scanAndAutoInstall() }

    when (val s = state) {
        is ExtractState.EditingVersion -> {
            val packs = s.result.packs
            if (s.packIndex in packs.indices) {
                VersionEditorScreen(
                    pack = packs[s.packIndex],
                    currentMinEngine = viewModel.parseVersions(packs[s.packIndex].manifestJson).first,
                    currentPackVersion = viewModel.parseVersions(packs[s.packIndex].manifestJson).second,
                    onSave = { me, pv -> viewModel.saveVersionOverride(me, pv) },
                    onBack = { viewModel.cancelVersionEdit() },
                )
            }
        }
        is ExtractState.SentToMinecraft -> SentToMinecraftContent(s.intent)
        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(targetState = state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "state") { currentState ->
                    when (currentState) {
                        is ExtractState.Idle -> IdleContent(scannedFiles, mcInstalls, selectedMc, onPickZip = { filePicker.launch(arrayOf("*/*")) }, onPickScanned = { viewModel.onZipPicked(it) }, onSelectMc = { viewModel.setSelectedInstall(it) })
                        is ExtractState.Analyzing -> AnalyzingContent(currentState.fileName)
                        is ExtractState.Ready -> ReadyContent(
                            result = currentState.result,
                            mcVersion = currentState.mcVersion,
                            selectedMc = selectedMc,
                            hasOverrides = viewModel.parseVersions(currentState.result.packs.firstOrNull()?.manifestJson).first != (currentState.mcVersion ?: ""),
                            onSendToMinecraft = { viewModel.sendToMinecraft(currentState.result) },
                            onEditVersion = { viewModel.startVersionEdit(it) },
                            onPickAnother = { filePicker.launch(arrayOf("*/*")) },
                        )
                        is ExtractState.Installing -> InstallingContent(currentState.progress, currentState.currentFile)
                        is ExtractState.Success -> SuccessContent(currentState.summary, onInstallAnother = { viewModel.reset(); filePicker.launch(arrayOf("*/*")) }, onOpenMinecraft = { openMinecraft(context) })
                        is ExtractState.Error -> ErrorContent(currentState.message, onRetry = { viewModel.reset(); filePicker.launch(arrayOf("*/*")) })
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun SentToMinecraftContent(intent: Intent) {
    val context = LocalContext.current
    val msg = stringResource(com.zippermc.R.string.minecraft_not_installed)
    LaunchedEffect(Unit) {
        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun IdleContent(
    scannedFiles: List<ScannedFile>,
    mcInstalls: List<MinecraftInstall>,
    selectedMc: MinecraftInstall?,
    onPickZip: () -> Unit,
    onPickScanned: (Uri) -> Unit,
    onSelectMc: (MinecraftInstall) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Column(
                modifier = Modifier.padding(top = 48.dp, bottom = 28.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Inventory2, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("ZipperMC", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                Text(stringResource(com.zippermc.R.string.mc_addon_installer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (mcInstalls.isNotEmpty()) {
                Text("Minecraft Installation", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(4.dp)) {
                        mcInstalls.forEach { mc ->
                            val isSelected = mc.packageName == selectedMc?.packageName
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onSelectMc(mc) }.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.size(22.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(mc.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                                    Text("v${mc.versionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isSelected) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("Selected", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            if (mc != mcInstalls.last()) {
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (scannedFiles.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(com.zippermc.R.string.found_files), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text("(${scannedFiles.size})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                scannedFiles.forEach { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPickScanned(file.uri) },
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Folder, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(formatSize(file.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = onPickZip,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                Icon(Icons.Default.Folder, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(com.zippermc.R.string.browse_files), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnalyzingContent(fileName: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(56.dp), strokeCap = StrokeCap.Round)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(com.zippermc.R.string.analyzing), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(fileName, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ReadyContent(
    result: AnalysisResult,
    mcVersion: String?,
    selectedMc: MinecraftInstall?,
    hasOverrides: Boolean,
    onSendToMinecraft: () -> Unit,
    onEditVersion: (Int) -> Unit,
    onPickAnother: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Column(
                modifier = Modifier.padding(top = 48.dp, bottom = 24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(com.zippermc.R.string.ready_to_install), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Inventory2, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(result.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            selectedMc?.let { mc ->
                                Text("${mc.label} v${mc.versionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (mcVersion != null && hasOverrides) {
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = DiamondBlue.copy(alpha = 0.12f)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, Modifier.size(16.dp), tint = DiamondBlue)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(com.zippermc.R.string.auto_adjusted, mcVersion), style = MaterialTheme.typography.bodySmall, color = DiamondBlue, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    result.packs.forEachIndexed { i, pack ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        PackRow(pack, onEditVersion = { onEditVersion(i) })
                    }
                    if (result.packs.isEmpty()) {
                        Text(stringResource(com.zippermc.R.string.no_content), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSendToMinecraft,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = result.packs.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(com.zippermc.R.string.send_to_minecraft), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPickAnother,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(com.zippermc.R.string.pick_different), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PackRow(pack: PackInfo, onEditVersion: () -> Unit) {
    val (icon, label) = iconAndLabel(pack.type)
    val chipColor = when (pack.type) {
        ZipEntryType.RESOURCE_PACK -> SkyBlue
        ZipEntryType.BEHAVIOR_PACK -> GrassGreen
        ZipEntryType.WORLD -> DiamondBlue
        ZipEntryType.SKIN_PACK -> Color(0xFFE040FB)
        ZipEntryType.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = chipColor.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp), tint = chipColor)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pack.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = chipColor.copy(alpha = 0.12f)) {
                    Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = chipColor, fontWeight = FontWeight.Medium)
                }
            }
            if (pack.manifestJson != null) {
                TextButton(onClick = onEditVersion) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun InstallingContent(progress: Float, currentFile: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(52.dp), strokeCap = StrokeCap.Round)
        Spacer(Modifier.height(20.dp))
        Text("Installing\u2026", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(10.dp))
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (currentFile.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(currentFile.takeLast(50), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SuccessContent(summary: List<PackInfo>, onInstallAnother: () -> Unit, onOpenMinecraft: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(88.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(com.zippermc.R.string.done), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (summary.isNotEmpty()) {
            Text(stringResource(com.zippermc.R.string.packs_installed, summary.size), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            summary.forEach { pack ->
                val (icon, label) = iconAndLabel(pack.type)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column { Text(pack.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium); Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenMinecraft,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        ) {
            Icon(Icons.Default.GridOn, null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(com.zippermc.R.string.open_minecraft), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onInstallAnother, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
            Text(stringResource(com.zippermc.R.string.install_another), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.size(72.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(com.zippermc.R.string.error), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        ) {
            Text(stringResource(com.zippermc.R.string.try_again), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun openMinecraft(context: android.content.Context) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage("com.mojang.minecraftpe")
            ?: Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER); setPackage("com.mojang.minecraftpe") }
        if (intent?.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Minecraft not installed", Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, "Minecraft not installed", Toast.LENGTH_SHORT).show()
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}

private fun iconAndLabel(type: ZipEntryType): Pair<ImageVector, String> = when (type) {
    ZipEntryType.RESOURCE_PACK -> Icons.Default.PhotoLibrary to "Resource Pack"
    ZipEntryType.BEHAVIOR_PACK -> Icons.Default.Language to "Behavior Pack"
    ZipEntryType.WORLD -> Icons.Default.GridOn to "World"
    ZipEntryType.SKIN_PACK -> Icons.Default.Person to "Skin Pack"
    ZipEntryType.UNKNOWN -> Icons.Default.Folder to "Files"
}
