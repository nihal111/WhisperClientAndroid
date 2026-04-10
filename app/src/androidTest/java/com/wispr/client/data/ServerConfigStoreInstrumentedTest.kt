package com.wispr.client.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerConfigStoreInstrumentedTest {
    @Test
    fun persistsBaseUrlAndTlsFlag() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ServerConfigStore(context)

        store.setBaseUrl("https://127.0.0.1:3000")
        store.setAllowInsecureHttps(false)
        assertEquals("https://127.0.0.1:3000", store.getBaseUrl())
        assertFalse(store.getAllowInsecureHttps())

        store.setAllowInsecureHttps(true)
        assertTrue(store.getAllowInsecureHttps())
    }
}
