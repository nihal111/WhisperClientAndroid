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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartBubbleService: () -> Unit,
    onStopBubbleService: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverConfigStore = remember { ServerConfigStore(context) }
    val overlayConfigStore = remember { OverlayConfigStore(context) }
    val serverClient = remember { WhisperServerClient() }

    var baseUrl by remember { mutableStateOf("") }
    var allowInsecureHttps by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Ready") }
    var canDrawOverlay by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var showBubbleWithoutKeyboard by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            scope.launch {
                val count = exportToUri(context, it)
                statusText = "Exported $count sessions"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            scope.launch {
                val count = importFromUri(context, it)
                statusText = "Imported $count sessions"
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
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

        Text(
            text = "Server Configuration",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp),
        )

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            label = { Text("Server base URL") },
            singleLine = true,
        )

        RowLine(
            label = "Allow insecure HTTPS (self-signed cert)",
            control = {
                Switch(
                    checked = allowInsecureHttps,
                    onCheckedChange = { allowInsecureHttps = it },
                )
            },
        )

        Button(
            onClick = {
                serverConfigStore.setBaseUrl(baseUrl)
                serverConfigStore.setAllowInsecureHttps(allowInsecureHttps)
                statusText = "Saved config"
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Save Config")
        }

        Button(
            onClick = {
                scope.launch {
                    statusText = "Checking server..."
                    val result = serverClient.healthCheck(baseUrl, allowInsecureHttps)
                    statusText = result.fold(
                        onSuccess = { "Server reachable (HTTP $it)" },
                        onFailure = { "Server check failed: ${it.message ?: "unknown error"}" },
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Check Server")
        }

        Text(
            text = "Overlay & Accessibility",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp),
        )

        Text(
            text = "Overlay: ${if (canDrawOverlay) "granted" else "not granted"} | Accessibility: ${if (accessibilityEnabled) "enabled" else "disabled"}",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        RowLine(
            label = "Show bubble without keyboard",
            control = {
                Switch(
                    checked = showBubbleWithoutKeyboard,
                    onCheckedChange = { value ->
                        showBubbleWithoutKeyboard = value
                        overlayConfigStore.setShowBubbleWithoutKeyboard(value)
                    },
                )
            },
        )

        Button(
            onClick = onOpenOverlaySettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Open Overlay Permission")
        }

        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Open Accessibility Settings")
        }

        Button(
            onClick = {
                canDrawOverlay = OverlayPermission.canDraw(context)
                accessibilityEnabled = AccessibilityPermission.isServiceEnabled(
                    context,
                    WhisperFocusAccessibilityService::class.java,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Refresh Status")
        }

        Text(
            text = "Backup",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp),
        )

        Button(
            onClick = {
                exportLauncher.launch("whisper-backup-${System.currentTimeMillis()}.json")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Export data")
        }

        Button(
            onClick = { importLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Import data")
        }

        Text(
            text = "Note: Importing appends to existing data. Exported backup is a JSON file.",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (BuildConfig.DEBUG) {
            Text(
                text = "Debug",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp),
            )

            Button(
                onClick = onStartBubbleService,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Start Bubble Service")
            }

            Button(
                onClick = onStopBubbleService,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Stop Bubble Service")
            }
        }

        Text(
            text = statusText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RowLine(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        control()
    }
}
