package com.wispr.client.data

import android.content.Context

class ServerConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty()
    }

    fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url.trim()).apply()
    }

    fun getAllowInsecureHttps(): Boolean {
        return prefs.getBoolean(KEY_ALLOW_INSECURE_HTTPS, true)
    }

    fun setAllowInsecureHttps(value: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_INSECURE_HTTPS, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "whisper_client"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ALLOW_INSECURE_HTTPS = "allow_insecure_https"
        private const val DEFAULT_BASE_URL = "https://192.168.1.100:3000"
    }
}
