package com.wispr.client.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class WisprServerClient {

    suspend fun healthCheck(baseUrl: String): Result<Int> = withContext(Dispatchers.IO) {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Base URL is empty"))
        }

        val endpoint = "$normalizedBase/health"
        try {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.doInput = true
            connection.connect()

            val responseCode = connection.responseCode
            connection.disconnect()
            Result.success(responseCode)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
