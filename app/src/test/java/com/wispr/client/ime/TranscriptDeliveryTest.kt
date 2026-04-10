package com.wispr.client.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptDeliveryTest {

    @Test
    fun `deliver inserts into focused field when connection exists`() {
        var inserted: String? = null
        var copied: String? = null

        val outcome = TranscriptDelivery.deliver(
            text = "hello from ime",
            textInserter = TextInserter { value ->
                inserted = value
                true
            },
            clipboardWriter = ClipboardWriter { value ->
                copied = value
            },
        )

        assertEquals(DeliveryOutcome.INSERTED, outcome)
        assertEquals("hello from ime", inserted)
        assertEquals(null, copied)
    }

    @Test
    fun `deliver falls back to clipboard when insertion unavailable`() {
        var copied: String? = null

        val outcome = TranscriptDelivery.deliver(
            text = "fallback text",
            textInserter = null,
            clipboardWriter = ClipboardWriter { value ->
                copied = value
            },
        )

        assertEquals(DeliveryOutcome.COPIED_TO_CLIPBOARD, outcome)
        assertEquals("fallback text", copied)
    }

    @Test
    fun `deliver returns no text for blank transcript`() {
        var copied: String? = null

        val outcome = TranscriptDelivery.deliver(
            text = "   ",
            textInserter = TextInserter { true },
            clipboardWriter = ClipboardWriter { value ->
                copied = value
            },
        )

        assertEquals(DeliveryOutcome.NO_TEXT, outcome)
        assertEquals(null, copied)
    }
}
