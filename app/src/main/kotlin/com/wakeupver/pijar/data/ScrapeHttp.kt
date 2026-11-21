package com.wakeupver.pijar.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object ScrapeHttp {
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 20_000
    private const val TOTAL_TIMEOUT = 45_000
    private const val MAX_BYTES = 16 * 1024 * 1024
    private const val THUMBNAILS = "https://thumbnails.libretro.com"
    private const val RAW_THUMBNAILS = "https://raw.githubusercontent.com/libretro-thumbnails"
    private const val CDN_THUMBNAILS = "https://cdn.jsdelivr.net/gh/libretro-thumbnails"
    private const val DATABASE = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat"
    private const val CDN_DATABASE = "https://cdn.jsdelivr.net/gh/libretro/libretro-database@master/metadat"
    private val ALIASES = mapOf(
        "atari - 8-bit" to "Atari - 8-bit Family",
        "nec - pc-8001 - pc-8801" to "NEC - PC-88",
        "microsoft - xbox" to "Microsoft - Xbox",
    )

    data class Download(val bytes: ByteArray, val url: String)

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun libretroNames(system: String): List<String> {
        val repo = libretroRepo(system)
        return listOf(system, repo).filter { it.isNotBlank() }.distinct()
    }

    fun libretroRepo(system: String): String =
        ALIASES[system.lowercase(Locale.ROOT)] ?: system

    fun libretroImageUrls(system: String, name: String): List<String> {
        val repo = libretroRepo(system).trim().replace(' ', '_')
        val tail = "Named_Boxarts/${encode(name)}"
        return listOf(
            "$RAW_THUMBNAILS/$repo/HEAD/$tail",
            "$CDN_THUMBNAILS/$repo@HEAD/$tail",
            "$THUMBNAILS/${encode(system)}/$tail",
        )
    }

    fun libretroTreeApi(system: String): String {
        val repo = libretroRepo(system).trim().replace(' ', '_')
        return "https://api.github.com/repos/libretro-thumbnails/$repo/git/trees/HEAD?recursive=1"
    }

    fun libretroContentsApi(system: String): String {
        val repo = libretroRepo(system).trim().replace(' ', '_')
        return "https://api.github.com/repos/libretro-thumbnails/$repo/contents/Named_Boxarts"
    }

    fun hashDatUrls(source: String, system: String): List<String> =
        libretroNames(system).flatMap { name ->
            val path = "$source/${encode(name)}.dat"
            listOf("$DATABASE/$path", "$CDN_DATABASE/$path")
        }

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
            if (!data.isNullOrEmpty()) return Download(data, url)
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
