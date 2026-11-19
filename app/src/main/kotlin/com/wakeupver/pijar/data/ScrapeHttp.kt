package com.wakeupver.pijar.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ScrapeHttp {
    private const val CONNECT_TIMEOUT = 8_000
    private const val READ_TIMEOUT = 12_000
    private const val MAX_BYTES = 8 * 1024 * 1024

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? =
        bytes(url, "GET", headers, null)?.toString(Charsets.UTF_8)

    fun post(url: String, headers: Map<String, String>, body: String): String? =
        bytes(url, "POST", headers, body.toByteArray(Charsets.UTF_8))?.toString(Charsets.UTF_8)

    fun download(url: String): ByteArray? = bytes(url, "GET", emptyMap(), null)

    fun exists(url: String): Boolean {
        var connection: HttpURLConnection? = null
        try {
            connection = open(url, "HEAD", emptyMap(), null)
            val code = connection.responseCode
            if (code in 200..299) return true
            if (code == 405 || code == 501) return download(url) != null
            return false
        } catch (e: Exception) {
            return false
        } finally {
            connection?.disconnect()
        }
    }

    private fun bytes(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): ByteArray? {
        var connection: HttpURLConnection? = null
        try {
            connection = open(url, method, headers, body)
            if (connection.responseCode !in 200..299) return null
            val stream = connection.inputStream ?: return null
            stream.use {
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) throw IOException("respons terlalu besar")
                    out.write(buffer, 0, read)
                }
                return out.toByteArray()
            }
        } catch (e: Exception) {
            return null
        } finally {
            connection?.disconnect()
        }
    }

    private fun open(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Pijar/0.1")
        connection.setRequestProperty("Accept", "application/json,image/*,*/*")
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        if (body != null) {
            connection.doOutput = true
            connection.fixedLengthStreamingMode = body.size
            connection.outputStream.use { it.write(body) }
        }
        return connection
    }
}
