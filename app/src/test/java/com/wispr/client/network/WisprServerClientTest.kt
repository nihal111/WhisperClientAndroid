package com.wispr.client.network

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
