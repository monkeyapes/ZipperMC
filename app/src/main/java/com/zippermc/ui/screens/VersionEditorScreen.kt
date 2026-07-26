package com.zippermc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zippermc.model.PackInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionEditorScreen(
    pack: PackInfo,
    currentMinEngine: String,
    currentPackVersion: String,
    onSave: (minEngineVer: String, packVer: String) -> Unit,
    onBack: () -> Unit,
) {
    var minEngine by remember { mutableStateOf(currentMinEngine) }
    var packVer by remember { mutableStateOf(currentPackVersion) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Version") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = pack.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = pack.type.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "Change these to make the addon compatible with your Minecraft version.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VersionField(
                label = "Min Engine Version",
                value = minEngine,
                onValueChange = { minEngine = it },
                hint = "e.g. 1.21.0",
            )

            VersionField(
                label = "Pack Version",
                value = packVer,
                onValueChange = { packVer = it },
                hint = "e.g. 1.0.0",
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onSave(minEngine, packVer) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save & Continue", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun VersionField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { newVal ->
                    val filtered = newVal.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 2) onValueChange(filtered)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(hint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
            )
        }
    }
}
