package com.wispr.client.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class WisprServerClientTest {

    private val client = WisprServerClient()

    @Test
    fun `normalizeBaseUrl trims whitespace and trailing slash`() {
        val normalized = client.normalizeBaseUrl("  https://127.0.0.1:3000/  ")
        assertEquals("https://127.0.0.1:3000", normalized)
    }

    @Test
    fun `parseTranscriptionText extracts text from json`() {
        val parsed = client.parseTranscriptionText("{\"text\":\"hello world\"}")
        assertEquals("hello world", parsed)
    }

    @Test
    fun `parseTranscriptionText falls back to raw text`() {
        val parsed = client.parseTranscriptionText("  plain text response  ")
        assertEquals("plain text response", parsed)
    }

    @Test
    fun `parseTranscriptionText handles escaped characters`() {
        val parsed = client.parseTranscriptionText("{\"text\":\"line one\\\\nline \\\"two\\\"\"}")
        assertEquals("line one\\nline \"two\"", parsed)
    }

    @Test
    fun `transcribeAudio fails when base url is empty`() = runBlocking {
        val temp = createTempFile(prefix = "wispr-test", suffix = ".wav")
        try {
            val result = client.transcribeAudio("   ", temp, allowInsecureHttps = true)
            assertFalse(result.isSuccess)
            assertEquals("Base URL is empty", result.exceptionOrNull()?.message)
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `transcribeAudio fails when audio file is missing`() = runBlocking {
        val missing = File("build/does-not-exist/sample.wav")
        val result = client.transcribeAudio("https://127.0.0.1:3000", missing, allowInsecureHttps = true)
        assertFalse(result.isSuccess)
        assertEquals("Audio file does not exist", result.exceptionOrNull()?.message)
    }
}
