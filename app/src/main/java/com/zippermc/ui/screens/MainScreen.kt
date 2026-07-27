package com.zippermc.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onToggleTheme: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val updateInfo by viewModel.updateAvailable.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("Update Available", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("v${info.latestVersion} is ready to download.")
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
                TextButton(onClick = { viewModel.dismissUpdate() }) { Text("Later") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val title = when (tab) { 0 -> "ZipperMC"; 1 -> "History"; else -> "Settings" }
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    if (tab == 0) {
                        IconButton(onClick = { viewModel.scanFiles() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                },
            )
        },
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
                0 -> HomeTab(viewModel, snackbarHostState, scope)
                 1 -> HistoryScreen(viewModel, snackbarHostState)
                2 -> SettingsScreen(viewModel, onToggleTheme)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTab(viewModel: MainViewModel, snackbarHostState: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    val state by viewModel.state.collectAsState()
    val scannedFiles by viewModel.scannedFiles.collectAsState()
    val mcInstalls by viewModel.mcInstalls.collectAsState()
    val selectedMc by viewModel.selectedMc.collectAsState()
    val context = LocalContext.current
    var showMcSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.onZipPicked(it) } }

    LaunchedEffect(Unit) { viewModel.scanAndAutoInstall() }

    LaunchedEffect(state) {
        when (state) {
            is ExtractState.Error -> {
                val msg = (state as ExtractState.Error).message
                scope.launch { snackbarHostState.showSnackbar(msg) }
                viewModel.reset()
            }
            is ExtractState.Success -> {
                scope.launch { snackbarHostState.showSnackbar("Done! Packs ready.") }
            }
            else -> {}
        }
    }

    if (showMcSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMcSheet = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text("Select Minecraft", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                mcInstalls.forEach { mc ->
                    val isSelected = mc.packageName == selectedMc?.packageName
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.setSelectedInstall(mc); showMcSheet = false
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape).background(
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) Icon(Icons.Default.CheckCircle, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mc.label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                            Text("v${mc.versionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("Selected", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (mc != mcInstalls.last()) Divider(Modifier.padding(start = 40.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
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
        is ExtractState.SentToMinecraft -> SentToMinecraftContent(s.intent)
        else -> {
            val cur = state
            when (cur) {
                is ExtractState.Idle -> IdleContent(
                    scannedFiles, mcInstalls, selectedMc,
                    onPickZip = { filePicker.launch(arrayOf("*/*")) },
                    onPickScanned = { viewModel.onZipPicked(it) },
                    onSelectMc = { showMcSheet = true },
                    onChangeMc = { showMcSheet = true },
                )
                is ExtractState.Analyzing -> AnalyzingContent(cur.fileName)
                is ExtractState.Ready -> ReadyContent(
                    result = cur.result,
                    mcVersion = cur.mcVersion,
                    selectedMc = selectedMc,
                    hasOverrides = viewModel.parseVersions(cur.result.packs.firstOrNull()?.manifestJson).first != (cur.mcVersion ?: ""),
                    onSendToMinecraft = { viewModel.sendToMinecraft(cur.result) },
                    onEditVersion = { viewModel.startVersionEdit(it) },
                    onPickAnother = { filePicker.launch(arrayOf("*/*")) },
                    onChangeMc = { showMcSheet = true },
                )
                is ExtractState.Installing -> InstallingContent(cur.progress, cur.currentFile)
                else -> IdleContent(
                    scannedFiles, mcInstalls, selectedMc,
                    onPickZip = { filePicker.launch(arrayOf("*/*")) },
                    onPickScanned = { viewModel.onZipPicked(it) },
                    onSelectMc = { showMcSheet = true },
                    onChangeMc = { showMcSheet = true },
                )
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
    onSelectMc: () -> Unit,
    onChangeMc: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)))
            ),
        ) {
            Column(
                modifier = Modifier.padding(top = 32.dp, bottom = 28.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(80.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Inventory2, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("ZipperMC", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                Text(stringResource(com.zippermc.R.string.mc_addon_installer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (mcInstalls.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectMc() },
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.GridOn, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedMc?.label ?: "Select Minecraft", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("v${selectedMc?.versionName ?: "?"} \u2022 ${mcInstalls.size} installed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (scannedFiles.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(com.zippermc.R.string.found_files), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${scannedFiles.size}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(scannedFiles.take(6)) { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onPickScanned(file.uri) },
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        val ext = file.name.substringAfterLast('.', "")
                                        val icon = when (ext) {
                                            "mcaddon", "mcpack" -> Icons.Default.Inventory2
                                            "mcworld" -> Icons.Default.GridOn
                                            "mcskin" -> Icons.Default.Person
                                            else -> Icons.Default.Folder
                                        }
                                        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, textAlign = TextAlign.Center)
                                Text(formatSize(file.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent(
    result: AnalysisResult,
    mcVersion: String?,
    selectedMc: MinecraftInstall?,
    hasOverrides: Boolean,
    onSendToMinecraft: () -> Unit,
    onEditVersion: (Int) -> Unit,
    onPickAnother: () -> Unit,
    onChangeMc: () -> Unit,
) {
    var filterType by remember { mutableStateOf<ZipEntryType?>(null) }
    val filteredPacks = result.packs.filter { filterType == null || it.type == filterType }
    val types = result.packs.map { it.type }.distinct()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)))
            ),
        ) {
            Column(
                modifier = Modifier.padding(top = 32.dp, bottom = 24.dp).fillMaxWidth(),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Inventory2, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.clickable(onClick = onChangeMc)) {
                        Text(selectedMc?.let { "${it.label} v${it.versionName}" } ?: "Select Minecraft", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

            if (types.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filterType == null, onClick = { filterType = null }, label = { Text("All") })
                    types.forEach { t ->
                        val (_, label) = iconAndLabel(t)
                        FilterChip(selected = filterType == t, onClick = { filterType = if (filterType == t) null else t }, label = { Text(label) })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            result.packs.forEachIndexed { i, pack ->
                if (filterType == null || pack.type == filterType) {
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    PackCard(pack, onEditVersion = { onEditVersion(result.packs.indexOf(pack)) })
                }
            }
            if (filteredPacks.isEmpty()) {
                Text(stringResource(com.zippermc.R.string.no_content), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSendToMinecraft,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = filteredPacks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(com.zippermc.R.string.send_to_minecraft), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPickAnother, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Pick Another")
                }
                OutlinedButton(onClick = onChangeMc, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Change MC")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PackCard(pack: PackInfo, onEditVersion: () -> Unit) {
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
            Surface(shape = RoundedCornerShape(8.dp), color = chipColor.copy(alpha = 0.15f), modifier = Modifier.size(44.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(24.dp), tint = chipColor)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pack.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = chipColor.copy(alpha = 0.12f)) {
                    Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = chipColor, fontWeight = FontWeight.Medium)
                }
            }
            if (pack.manifestJson != null) {
                TextButton(onClick = onEditVersion) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
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
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), strokeCap = StrokeCap.Round)
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
