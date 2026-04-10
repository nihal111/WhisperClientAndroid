package com.wispr.client.ime

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import com.wispr.client.R

class WisprInputMethodService : InputMethodService() {

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.ime_view, null)

        val insertButton = view.findViewById<Button>(R.id.insertSampleButton)
        val copyButton = view.findViewById<Button>(R.id.copySampleButton)

        insertButton.setOnClickListener {
            currentInputConnection?.commitText("Sample response from WhisperClient", 1)
            Log.i(TAG, "Inserted sample text into focused field")
        }

        copyButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("WhisperClient", "Sample response from WhisperClient")
            )
            Log.i(TAG, "Copied sample text to clipboard")
        }

        return view
    }

    companion object {
        private const val TAG = "WisprIme"
    }
}
