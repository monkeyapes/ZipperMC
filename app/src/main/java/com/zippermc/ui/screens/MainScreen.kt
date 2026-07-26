package com.zippermc.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zippermc.model.AnalysisResult
import com.zippermc.model.ExtractState
import com.zippermc.model.PackInfo
import com.zippermc.model.ZipEntryType
import com.zippermc.viewmodel.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onZipPicked(it) }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkPermission()
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermission()
    }

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
        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "state",
                    modifier = Modifier.fillMaxSize(),
                ) { currentState ->
                    when (currentState) {
                        is ExtractState.NeedsPermission -> PermissionPopup(
                            onGrant = {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                settingsLauncher.launch(intent)
                            },
                        )
                        is ExtractState.Idle -> IdleContent(
                            onPickZip = { filePicker.launch(arrayOf("*/*")) }
                        )
                        is ExtractState.Analyzing -> AnalyzingContent(currentState.fileName)
                        is ExtractState.Ready -> ReadyContent(
                            result = currentState.result,
                            onInstall = { viewModel.startExtract(currentState.result) },
                            onEditVersion = { viewModel.startVersionEdit(it) },
                            onPickAnother = { filePicker.launch(arrayOf("*/*")) },
                        )
                        is ExtractState.Extracting -> ExtractingContent(
                            progress = currentState.progress,
                            currentFile = currentState.currentFile,
                        )
                        is ExtractState.Success -> SuccessContent(
                            summary = currentState.summary,
                            onInstallAnother = { viewModel.reset(); filePicker.launch(arrayOf("*/*")) },
                            onOpenMinecraft = {
                                try {
                                    context.startActivity(
                                        context.packageManager.getLaunchIntentForPackage("com.mojang.minecraftpe")
                                            ?: Intent(Intent.ACTION_MAIN).apply {
                                                addCategory(Intent.CATEGORY_LAUNCHER)
                                                setPackage("com.mojang.minecraftpe")
                                            }
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, "Minecraft not installed", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        is ExtractState.Error -> ErrorContent(
                            message = currentState.message,
                            onRetry = { viewModel.reset(); filePicker.launch(arrayOf("*/*")) },
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPopup(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Storage Access Needed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "ZipperMC needs file access to write into Minecraft's folder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onGrant,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Grant Access", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You'll be taken to Settings → tap \"Allow all files\" → go back",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun IdleContent(onPickZip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Inventory2,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "ZipperMC",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Open a .mcaddon, .mcpack, or .zip\nfrom your file manager",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onPickZip,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Browse Files", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AnalyzingContent(fileName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Analyzing…", style = MaterialTheme.typography.titleMedium)
        Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadyContent(
    result: AnalysisResult,
    onInstall: () -> Unit,
    onEditVersion: (Int) -> Unit,
    onPickAnother: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Ready to Install", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(result.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                result.packs.forEachIndexed { i, pack ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    PackRow(pack, onEditVersion = { onEditVersion(i) })
                }
                if (result.packs.isEmpty()) {
                    Text("No content detected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onInstall,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = result.packs.isNotEmpty(),
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Install to Minecraft", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPickAnother, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text("Pick different file", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PackRow(pack: PackInfo, onEditVersion: () -> Unit) {
    val (icon, label) = iconAndLabel(pack.type)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pack.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (pack.manifestJson != null) {
                TextButton(onClick = onEditVersion) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Version", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ExtractingContent(progress: Float, currentFile: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeCap = StrokeCap.Round)
        Spacer(Modifier.height(16.dp))
        Text("Installing…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(8.dp))
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (currentFile.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(currentFile.takeLast(40), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SuccessContent(
    summary: List<PackInfo>,
    onInstallAnother: () -> Unit,
    onOpenMinecraft: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Done!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        if (summary.isNotEmpty()) {
            Text("${summary.size} pack${if (summary.size != 1) "s" else ""} installed", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            summary.forEach { pack ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    val (icon, label) = iconAndLabel(pack.type)
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(pack.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Text("Nothing was installed", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Text("The file might not contain Minecraft content.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenMinecraft, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.GridOn, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open Minecraft", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onInstallAnother, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text("Install another", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("Error", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text("Try again", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun iconAndLabel(type: ZipEntryType): Pair<ImageVector, String> = when (type) {
    ZipEntryType.RESOURCE_PACK -> Icons.Default.PhotoLibrary to "Resource Pack"
    ZipEntryType.BEHAVIOR_PACK -> Icons.Default.Language to "Behavior Pack"
    ZipEntryType.WORLD -> Icons.Default.GridOn to "World"
    ZipEntryType.SKIN_PACK -> Icons.Default.Person to "Skin Pack"
    ZipEntryType.UNKNOWN -> Icons.Default.Folder to "Files"
}
