package com.wispr.client.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptStoreInstrumentedTest {
    @Test
    fun persistsLastTranscript() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TranscriptStore(context)

        store.setLastTranscript("hello transcript")
        assertEquals("hello transcript", store.getLastTranscript())
    }
}
