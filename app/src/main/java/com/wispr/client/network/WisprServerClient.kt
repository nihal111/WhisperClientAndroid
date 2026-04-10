package com.wispr.client.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class WisprServerClient {

    suspend fun healthCheck(baseUrl: String, allowInsecureHttps: Boolean): Result<Int> = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeBaseUrl(baseUrl)
        if (normalizedBase.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Base URL is empty"))
        }

        val endpoint = "$normalizedBase/"
        try {
            val connection = openConnection(endpoint, allowInsecureHttps)
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

    suspend fun transcribeAudio(
        baseUrl: String,
        audioFile: File,
        allowInsecureHttps: Boolean,
    ): Result<String> = withContext(Dispatchers.IO) {
        val normalizedBase = normalizeBaseUrl(baseUrl)
        if (normalizedBase.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Base URL is empty"))
        }
        if (!audioFile.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Audio file does not exist"))
        }

        val endpoint = "$normalizedBase/inference"
        val boundary = "----WhisperClient${UUID.randomUUID()}"

        try {
            val connection = openConnection(endpoint, allowInsecureHttps)
            connection.requestMethod = "POST"
            connection.connectTimeout = 60_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.doInput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            connection.outputStream.use { output ->
                writeFormField(output, boundary, "temperature", "0.0")
                writeFormField(output, boundary, "temperature_inc", "0.2")
                writeFormField(output, boundary, "response_format", "json")
                writeFileField(output, boundary, "file", audioFile)
                output.write("--$boundary--\r\n".toByteArray())
            }

            val code = connection.responseCode
            val rawResponse = readFully(
                if (code in 200..299) connection.inputStream else connection.errorStream,
            )
            connection.disconnect()

            if (code !in 200..299) {
                return@withContext Result.failure(IOException("HTTP $code: $rawResponse"))
            }

            val text = parseTranscriptionText(rawResponse)

            if (text.isBlank()) {
                Result.failure(IOException("Server returned empty transcription"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeFormField(
        output: java.io.OutputStream,
        boundary: String,
        name: String,
        value: String,
    ) {
        output.write("--$boundary\r\n".toByteArray())
        output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
        output.write("$value\r\n".toByteArray())
    }

    private fun writeFileField(
        output: java.io.OutputStream,
        boundary: String,
        name: String,
        file: File,
    ) {
        output.write("--$boundary\r\n".toByteArray())
        output.write(
            "Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"\r\n".toByteArray(),
        )
        output.write("Content-Type: audio/webm\r\n\r\n".toByteArray())
        file.inputStream().use { input ->
            input.copyTo(output)
        }
        output.write("\r\n".toByteArray())
    }

    private fun readFully(stream: InputStream?): String {
        if (stream == null) {
            return ""
        }
        return stream.bufferedReader().use { it.readText() }
    }

    private fun openConnection(url: String, allowInsecureHttps: Boolean): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        if (allowInsecureHttps && connection is HttpsURLConnection) {
            connection.sslSocketFactory = trustAllSslContext.socketFactory
            connection.hostnameVerifier = trustAllHostnameVerifier
        }
        return connection
    }

    private val trustAllHostnameVerifier = HostnameVerifier { _, _ -> true }

    private val trustAllSslContext: SSLContext by lazy {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(trustManager), java.security.SecureRandom())
        context
    }

    internal fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().trimEnd('/')
    }

    internal fun parseTranscriptionText(rawResponse: String): String {
        val raw = run {
            val regexMatch = TEXT_JSON_REGEX.find(rawResponse)
            if (regexMatch != null) {
                return@run regexMatch.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim()
            }
            try {
                JSONObject(rawResponse).optString("text").trim()
            } catch (_: Throwable) {
                rawResponse.trim()
            }
        }
        return cleanTranscriptionText(raw)
    }

    internal fun cleanTranscriptionText(text: String): String {
        return text
            .replace("\\n", " ")
            .replace("\n", " ")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    private companion object {
        val TEXT_JSON_REGEX = Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
    }
}
