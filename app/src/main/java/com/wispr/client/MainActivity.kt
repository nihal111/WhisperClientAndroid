package com.wispr.client

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wispr.client.data.ServerConfigStore
import com.wispr.client.network.WisprServerClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serverConfigStore = ServerConfigStore(this)
        val serverClient = WisprServerClient()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen(
                        serverConfigStore = serverConfigStore,
                        serverClient = serverClient,
                        onOpenImeSettings = {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    serverConfigStore: ServerConfigStore,
    serverClient: WisprServerClient,
    onOpenImeSettings: () -> Unit,
) {
    var baseUrl by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Idle") }

    LaunchedEffect(Unit) {
        baseUrl = serverConfigStore.getBaseUrl()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "WhisperClient", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            label = { Text("Server base URL") },
            singleLine = true,
        )

        Button(
            onClick = {
                serverConfigStore.setBaseUrl(baseUrl)
                statusText = "Saved URL"
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text("Save URL")
        }

        Button(
            onClick = {
                statusText = "Checking /health..."
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Check Health")
        }

        LaunchedEffect(statusText) {
            if (statusText != "Checking /health...") {
                return@LaunchedEffect
            }
            val result = serverClient.healthCheck(baseUrl)
            statusText = result.fold(
                onSuccess = { "Health OK (HTTP $it)" },
                onFailure = { "Health failed: ${it.message ?: "unknown error"}" },
            )
        }

        Text(
            text = statusText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        Button(
            onClick = onOpenImeSettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Text("Open Keyboard Settings")
        }
    }
}
