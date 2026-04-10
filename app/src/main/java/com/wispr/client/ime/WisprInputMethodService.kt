package com.wispr.client.ime

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import com.wispr.client.R
import com.wispr.client.data.TranscriptStore

class WisprInputMethodService : InputMethodService() {

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.ime_view, null)
        val transcriptStore = TranscriptStore(this)

        val insertButton = view.findViewById<Button>(R.id.insertSampleButton)
        val copyButton = view.findViewById<Button>(R.id.copySampleButton)

        insertButton.setOnClickListener {
            val text = transcriptStore.getLastTranscript().ifBlank { "No transcript yet" }
            currentInputConnection?.commitText(text, 1)
            Log.i(TAG, "Inserted transcript into focused field")
        }

        copyButton.setOnClickListener {
            val text = transcriptStore.getLastTranscript().ifBlank { "No transcript yet" }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("WhisperClient", text)
            )
            Log.i(TAG, "Copied transcript to clipboard")
        }

        return view
    }

    companion object {
        private const val TAG = "WisprIme"
    }
}
