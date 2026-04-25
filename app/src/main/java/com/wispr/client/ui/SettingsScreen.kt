package com.wispr.client.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wispr.client.BuildConfig
import com.wispr.client.data.ServerConfigStore
import com.wispr.client.data.exportToUri
import com.wispr.client.data.importFromUri
import com.wispr.client.network.WhisperServerClient
import com.wispr.client.overlay.AccessibilityPermission
import com.wispr.client.overlay.OverlayConfigStore
import com.wispr.client.overlay.OverlayPermission
import com.wispr.client.overlay.WhisperFocusAccessibilityService
import com.wispr.client.ui.theme.GreenOk
import com.wispr.client.ui.theme.RedError
import com.wispr.client.ui.theme.TextTertiary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartBubbleService: () -> Unit,
    onStopBubbleService: () -> Unit,
    onShowSnackbar: suspend (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverConfigStore = remember { ServerConfigStore(context) }
    val overlayConfigStore = remember { OverlayConfigStore(context) }
    val serverClient = remember { WhisperServerClient() }

    var baseUrl by remember { mutableStateOf("") }
    var allowInsecureHttps by remember { mutableStateOf(true) }
    var canDrawOverlay by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var showBubbleWithoutKeyboard by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            scope.launch {
                val count = exportToUri(context, it)
                onShowSnackbar("Exported $count sessions")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            scope.launch {
                val count = importFromUri(context, it)
                onShowSnackbar("Imported $count sessions")
            }
        }
    }

    LaunchedEffect(Unit) {
        baseUrl = serverConfigStore.getBaseUrl()
        allowInsecureHttps = serverConfigStore.getAllowInsecureHttps()
        canDrawOverlay = OverlayPermission.canDraw(context)
        accessibilityEnabled = AccessibilityPermission.isServiceEnabled(
            context,
            WhisperFocusAccessibilityService::class.java,
        )
        showBubbleWithoutKeyboard = overlayConfigStore.getShowBubbleWithoutKeyboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

        // Server Configuration Card
        SettingsCard(title = "Server Configuration") {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                label = { Text("Server base URL") },
                singleLine = true,
            )

            SettingsSwitchRow(
                label = "Allow insecure HTTPS",
                checked = allowInsecureHttps,
                onCheckedChange = { allowInsecureHttps = it },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        serverConfigStore.setBaseUrl(baseUrl)
                        serverConfigStore.setAllowInsecureHttps(allowInsecureHttps)
                        scope.launch { onShowSnackbar("Config saved") }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val result = serverClient.healthCheck(baseUrl, allowInsecureHttps)
                            val msg = result.fold(
                                onSuccess = { "Server OK (HTTP $it)" },
                                onFailure = { "Failed: ${it.message ?: "unknown"}" },
                            )
                            onShowSnackbar(msg)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Check")
                }
            }
        }

        // Overlay & Accessibility Card
        SettingsCard(title = "Overlay & Accessibility") {
            PermissionRow("Overlay", canDrawOverlay)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PermissionRow("Accessibility", accessibilityEnabled)

            SettingsSwitchRow(
                label = "Show bubble without keyboard",
                checked = showBubbleWithoutKeyboard,
                onCheckedChange = { value ->
                    showBubbleWithoutKeyboard = value
                    overlayConfigStore.setShowBubbleWithoutKeyboard(value)
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenOverlaySettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Overlay", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                }

                OutlinedButton(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Accessibility", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                }

                OutlinedButton(
                    onClick = {
                        canDrawOverlay = OverlayPermission.canDraw(context)
                        accessibilityEnabled = AccessibilityPermission.isServiceEnabled(
                            context,
                            WhisperFocusAccessibilityService::class.java,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Refresh", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                }
            }
        }

        // Backup Card
        SettingsCard(title = "Backup") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        exportLauncher.launch("whisper-backup-${System.currentTimeMillis()}.json")
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Export")
                }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Import")
                }
            }

            Text(
                text = "Note: Importing appends to existing data.",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }

        // Debug Card (BuildConfig.DEBUG only)
        if (BuildConfig.DEBUG) {
            SettingsCard(title = "Debug") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onStartBubbleService,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Start Bubble", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                    }

                    OutlinedButton(
                        onClick = onStopBubbleService,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Stop Bubble", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            tint = if (granted) GreenOk else RedError,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = "$label: ${if (granted) "granted" else "not granted"}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
