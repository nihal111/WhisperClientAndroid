package com.wispr.client.ime

fun interface TextInserter {
    fun insert(text: String): Boolean
}

fun interface ClipboardWriter {
    fun copy(text: String)
}

enum class DeliveryOutcome {
    INSERTED,
    COPIED_TO_CLIPBOARD,
    NO_TEXT,
}

val DeliveryOutcome.statusLabel: String
    get() = when (this) {
        DeliveryOutcome.INSERTED -> "Inserted transcript"
        DeliveryOutcome.COPIED_TO_CLIPBOARD -> "Copied transcript"
        DeliveryOutcome.NO_TEXT -> "No transcript yet"
    }

object TranscriptDelivery {
    fun deliver(
        text: String,
        textInserter: TextInserter?,
        clipboardWriter: ClipboardWriter,
    ): DeliveryOutcome {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            return DeliveryOutcome.NO_TEXT
        }

        val inserted = textInserter?.insert(normalized) ?: false
        if (inserted) {
            return DeliveryOutcome.INSERTED
        }

        clipboardWriter.copy(normalized)
        return DeliveryOutcome.COPIED_TO_CLIPBOARD
    }
}
