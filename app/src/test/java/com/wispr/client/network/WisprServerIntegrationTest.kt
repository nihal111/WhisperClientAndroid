package com.wispr.client.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class WisprServerIntegrationTest {

    private val client = WisprServerClient()

    @Test
    fun `transcribeAudio with real wav returns non-empty text`() = runBlocking {
        val serverUrl = System.getenv("WISPR_SERVER_URL").orEmpty().trim()
        val wavPath = System.getenv("WISPR_WAV_PATH").orEmpty().trim()
        val allowInsecureHttps = System.getenv("WISPR_ALLOW_INSECURE_HTTPS")
            ?.trim()
            ?.lowercase()
            ?.let { it == "1" || it == "true" || it == "yes" }
            ?: serverUrl.startsWith("https://")

        assumeTrue("Set WISPR_SERVER_URL to run integration test", serverUrl.isNotBlank())
        assumeTrue("Set WISPR_WAV_PATH to run integration test", wavPath.isNotBlank())

        val wavFile = File(wavPath)
        assumeTrue("WAV file not found at: $wavPath", wavFile.exists())
        assumeTrue("Expected a .wav file", wavFile.extension.equals("wav", ignoreCase = true))

        val health = client.healthCheck(serverUrl, allowInsecureHttps)
        assertTrue("Health check failed: ${health.exceptionOrNull()?.message}", health.isSuccess)

        val result = client.transcribeAudio(serverUrl, wavFile, allowInsecureHttps)
        assertTrue("Transcription failed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val text = result.getOrNull().orEmpty().trim()
        assertTrue("Expected non-empty transcript", text.isNotBlank())

        println("[integration] transcript: $text")
    }
}
