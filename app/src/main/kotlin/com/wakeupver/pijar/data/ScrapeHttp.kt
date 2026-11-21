package com.wakeupver.pijar.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ScrapeHttp {
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 20_000
    private const val TOTAL_TIMEOUT = 45_000
    private const val MAX_BYTES = 16 * 1024 * 1024

    data class Download(val bytes: ByteArray, val url: String)

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun query(params: Map<String, String>): String =
        params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

    fun getFirst(urls: List<String>, headers: Map<String, String> = emptyMap()): String? {
        for (url in urls) {
            val text = get(url, headers)
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? =
        bytes(url, "GET", headers, null)?.toString(Charsets.UTF_8)

    fun downloadFirst(urls: List<String>): Download? {
        for (url in urls) {
            val data = bytes(url, "GET", emptyMap(), null)
            if (data != null && data.isNotEmpty()) return Download(data, url)
        }
        return null
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
                val deadline = System.currentTimeMillis() + TOTAL_TIMEOUT
                while (true) {
                    if (System.currentTimeMillis() > deadline) throw IOException("timeout total")
                    val read = it.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) throw IOException("respons terlalu besar")
                    out.write(buffer, 0, read)
                }
                return out.toByteArray()
            }
        } catch (_: Exception) {
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
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
        }
        return connection
    }
}
