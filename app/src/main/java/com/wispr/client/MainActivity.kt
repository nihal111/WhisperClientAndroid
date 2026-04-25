package com.wispr.client

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wispr.client.ui.HomeScreen
import com.wispr.client.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppShell(
                    onOpenOverlaySettings = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")))
                    },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onStartBubbleService = {
                        com.wispr.client.overlay.WhisperFloatingBubbleService.sendCommand(
                            this,
                            com.wispr.client.overlay.WhisperFloatingBubbleService.ACTION_START,
                        )
                    },
                    onStopBubbleService = {
                        com.wispr.client.overlay.WhisperFloatingBubbleService.sendCommand(
                            this,
                            com.wispr.client.overlay.WhisperFloatingBubbleService.ACTION_STOP,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AppShell(
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartBubbleService: () -> Unit,
    onStopBubbleService: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeScreen()
                else -> SettingsScreen(
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onStartBubbleService = onStartBubbleService,
                    onStopBubbleService = onStopBubbleService,
                )
            }
        }
    }
}
