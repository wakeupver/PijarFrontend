package com.wakeupver.pijar.data

import com.wakeupver.pijar.core.GameMetadata
import com.wakeupver.pijar.core.GameRecord
import com.wakeupver.pijar.core.GameTitles
import com.wakeupver.pijar.core.ScrapeMatcher
import com.wakeupver.pijar.core.ScrapeSettings
import com.wakeupver.pijar.core.ScrapeSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32

object ScreenScraper {
    private const val API = "https://api.screenscraper.fr/api2"
    private const val DEV_ID = ""
    private const val DEV_PASSWORD = ""
    private const val SOFT_NAME = "Pijar"
    private const val SYSTEM_TTL = 30L * 24 * 60 * 60 * 1000
    private const val MAX_HASH_BYTES = 2L * 1024 * 1024 * 1024

    private val REGIONS = listOf("wor", "us", "eu", "jp", "fr", "ss")
    private val LANGUAGES = listOf("en", "fr", "es", "de", "it", "pt", "ja", "zh", "ko")
    private val MEDIA_TYPES = listOf("box-2D", "box-texture-2D", "wheel-hd", "wheel", "sstitle", "ss", "fanart", "support-2D", "flyer")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val MARKS = Regex("\\p{M}")
    private val systems = ConcurrentHashMap<String, List<SystemInfo>>()
    private val systemLock = Any()
    private val hashCache = ConcurrentHashMap<String, Hashes>()

    data class Hashes(val md5: String, val sha1: String, val crc: String, val length: Long = 0L)

    private data class SystemInfo(val id: String, val names: List<String>)

    fun scrape(
        game: GameRecord,
        platformName: String,
        platformAlias: String,
        settings: ScrapeSettings,
        filesDir: File,
        cacheDir: File,
    ): GameMetadata {
        if (!settings.configured) return GameMetadata()
        val queries = linkedSetOf<String>()
        if (platformAlias.isNotBlank()) queries += platformAlias
        if (platformName.isNotBlank()) queries += platformName
        if (queries.isEmpty()) return GameMetadata()
        val systemId = queries.firstNotNullOfOrNull { systemId(it, settings, cacheDir) } ?: return GameMetadata()
        val hashes = hash(game.path)
        val fileName = game.path.substringAfterLast('/', game.path)
        val titles = linkedSetOf(fileName, GameTitles.clean(fileName), game.title)
        var jeu = byRom(hashes, systemId, fileName, settings)
        if (jeu == null) {
            for (title in titles) {
                if (title.isBlank()) continue
                jeu = byName(title, systemId, settings)
                if (jeu != null) break
            }
        }
        jeu ?: return GameMetadata()
        val gameId = jeu.optString("id").ifBlank { jeu.optString("romid") }
        val metadata = metadata(jeu)
        val urls = coverUrls(jeu, systemId, gameId, settings)
        val hit = if (gameId.isBlank()) null else CoverCache.fetch(filesDir, game.id, urls)
        return metadata.copy(
            source = ScrapeSource.SCREENSCRAPER,
            coverUrl = if (gameId.isBlank()) "" else "screenscraper:$gameId",
            coverPath = hit?.path.orEmpty(),
            scrapedAt = System.currentTimeMillis(),
        )
    }

    fun hash(path: String): Hashes {
        val file = File(path)
        val length = file.length()
        if (!file.isFile || length <= 0L || length > MAX_HASH_BYTES) return Hashes("", "", "", length)
        val key = "${file.path}\n$length\n${file.lastModified()}"
        hashCache[key]?.let { return it }
        return try {
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val crc = CRC32()
            val buffer = ByteArray(64 * 1024)
            file.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    md5.update(buffer, 0, read)
                    sha1.update(buffer, 0, read)
                    crc.update(buffer, 0, read)
                }
            }
            val result = Hashes(hex(md5.digest()), hex(sha1.digest()), hex(crc.value), length)
            hashCache[key] = result
            result
        } catch (_: Exception) {
            Hashes("", "", "", length)
        }
    }

    private fun systemId(
        query: String,
        settings: ScrapeSettings,
        cacheDir: File,
    ): String? {
        val key = "${appDevId(settings)}\n${appSoftName(settings)}"
        val list = systems[key]?.takeIf { it.isNotEmpty() } ?: synchronized(systemLock) {
            systems[key]?.takeIf { it.isNotEmpty() } ?: loadSystems(settings, cacheDir).also {
                if (it.isNotEmpty()) systems[key] = it
            }
        }
        if (list.isEmpty()) return null
        val candidates = linkedSetOf(query)
        SYSTEM_ALIASES[normalized(query)]?.let { candidates += it }
        val scored = candidates.mapNotNull { candidate ->
            val q = normalized(candidate)
            if (q.isEmpty()) return@mapNotNull null
            val best = list.maxOfOrNull { system -> systemScore(q, system) } ?: 0
            best to candidate
        }.sortedByDescending { it.first }
        val bestScore = scored.firstOrNull()?.first ?: 0
        if (bestScore < 260) return null
        val bestQuery = normalized(scored.first().second)
        return list.maxByOrNull { systemScore(bestQuery, it) }?.id?.takeIf { it.isNotBlank() }
    }

    private fun systemScore(query: String, system: SystemInfo): Int {
        var best = 0
        for (name in system.names) {
            val value = normalized(name)
            if (value.isEmpty()) continue
            if (value == query) return 1000
            if (value.contains(query) || query.contains(value)) best = maxOf(best, 850)
            val qt = ScrapeMatcher.tokens(query)
            val vt = ScrapeMatcher.tokens(value)
            if (qt.isEmpty() || vt.isEmpty()) continue
            val hits = qt.count { token -> vt.any { it == token || it.startsWith(token) || token.startsWith(it) } }
            if (hits == 0) continue
            val coverage = hits * 100 / qt.size
            val penalty = kotlin.math.abs(qt.size - vt.size) * 8
            best = maxOf(best, 250 + coverage - penalty)
        }
        return best
    }

    private fun loadSystems(settings: ScrapeSettings, cacheDir: File): List<SystemInfo> {
        val key = md5("${appDevId(settings)}\n${appSoftName(settings)}".toByteArray())
        val file = File(File(cacheDir, "screenscraper"), "systems-$key.json")
        if (file.isFile && System.currentTimeMillis() - file.lastModified() < SYSTEM_TTL) {
            parseSystems(file.readText().ifBlank { return emptyList() }).takeIf { it.isNotEmpty() }?.let { return it }
        }
        val text = ScrapeHttp.get(api("systemesListe.php", settings, emptyMap())) ?: return emptyList()
        val parsed = parseSystems(text)
        if (parsed.isEmpty()) return emptyList()
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(text)
        }
        return parsed
    }

    private fun parseSystems(text: String): List<SystemInfo> {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val response = root.optJSONObject("response") ?: return emptyList()
        val array = response.optJSONArray("systemes")
            ?: response.optJSONArray("systeme")
            ?: return emptyList()
        val result = ArrayList<SystemInfo>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            val names = ArrayList<String>()
            val noms = item.optJSONObject("noms")
            if (noms != null) {
                for (key in noms.keys()) {
                    val value = noms.optString(key)
                    if (value.isNotBlank()) names += value
                }
            }
            item.optString("nom").takeIf { it.isNotBlank() }?.let { names += it }
            if (names.isNotEmpty()) result += SystemInfo(id, names.distinct())
        }
        return result
    }

    private fun byRom(
        hashes: Hashes,
        systemId: String,
        fileName: String,
        settings: ScrapeSettings,
    ): JSONObject? {
        val params = linkedMapOf(
            "systemeid" to systemId,
            "romtype" to "rom",
            "romnom" to fileName,
            "romtaille" to hashes.length.toString(),
        )
        if (hashes.crc.isNotBlank()) params["crc"] = hashes.crc
        if (hashes.md5.isNotBlank()) params["md5"] = hashes.md5
        if (hashes.sha1.isNotBlank()) params["sha1"] = hashes.sha1
        val text = ScrapeHttp.get(api("jeuInfos.php", settings, params)) ?: return null
        return jeu(text)
    }

    private fun byName(title: String, systemId: String, settings: ScrapeSettings): JSONObject? {
        val params = linkedMapOf(
            "recherche" to GameTitles.clean(title).ifBlank { title },
            "systemeid" to systemId,
        )
        val text = ScrapeHttp.get(api("jeuRecherche.php", settings, params)) ?: return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val response = root.optJSONObject("response") ?: return null
        val array = response.optJSONArray("jeux") ?: return jeu(text)
        var best: JSONObject? = null
        var bestScore = 0
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val score = ScrapeMatcher.score(title, jeuTitle(item))
            if (score > bestScore) {
                bestScore = score
                best = item
            }
        }
        return best?.takeIf { bestScore >= 260 }
    }

    private fun jeu(text: String): JSONObject? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val value = root.optJSONObject("response")?.opt("jeu") ?: return null
        return when (value) {
            is JSONObject -> value
            is JSONArray -> value.optJSONObject(0)
            else -> null
        }
    }

    private fun metadata(jeu: JSONObject): GameMetadata {
        val title = localized(jeu.optJSONArray("noms"), "region", "text", REGIONS)
            .ifBlank { jeu.optString("nom") }
            .replace(Regex("^ZZZ\\(notgame\\):\\s*"), "")
            .trim()
        val overview = localized(jeu.optJSONArray("synopsis"), "langue", "text", LANGUAGES)
        val releaseDate = localized(jeu.optJSONArray("dates"), "region", "text", REGIONS)
        val genres = ArrayList<String>()
        val genreArray = jeu.optJSONArray("genres") ?: JSONArray()
        for (i in 0 until genreArray.length()) {
            val item = genreArray.optJSONObject(i) ?: continue
            val name = localized(item.optJSONArray("noms"), "langue", "text", LANGUAGES)
                .ifBlank { item.optString("nom_en") }
                .ifBlank { item.optString("nom_fr") }
                .ifBlank { item.optString("nomcourt") }
            if (name.isNotBlank()) genres += name
        }
        return GameMetadata(title = title, overview = overview, releaseDate = releaseDate, genres = genres.distinct())
    }

    private fun coverUrls(
        jeu: JSONObject,
        systemId: String,
        gameId: String,
        settings: ScrapeSettings,
    ): List<String> {
        val result = LinkedHashMap<String, Int>()
        val medias = jeu.optJSONArray("medias") ?: JSONArray()
        for (i in 0 until medias.length()) {
            val item = medias.optJSONObject(i) ?: continue
            val type = item.optString("type")
            val score = mediaScore(type, item.optString("region"))
            val url = absoluteMediaUrl(item.optString("url"))
            if (score > 0 && url.isNotBlank()) result[url] = maxOf(result[url] ?: 0, score)
        }
        if (gameId.isNotBlank()) {
            for (type in MEDIA_TYPES) {
                for (region in REGIONS) {
                    val url = mediaUrl(settings, systemId, gameId, type, region)
                    result[url] = mediaScore(type, region)
                }
            }
        }
        return result.entries.sortedByDescending { it.value }.map { it.key }.take(16)
    }

    private fun mediaScore(type: String, region: String): Int {
        val typeScore = when (type.lowercase(Locale.ROOT)) {
            "box-2d" -> 100
            "box-texture-2d" -> 90
            "wheel-hd" -> 80
            "wheel" -> 72
            "sstitle" -> 64
            "ss" -> 56
            "fanart" -> 32
            "support-2d" -> 24
            "flyer" -> 16
            else -> 0
        }
        if (typeScore == 0) return 0
        val regionScore = when (region.lowercase(Locale.ROOT)) {
            "wor" -> 12
            "us" -> 10
            "eu" -> 9
            "jp" -> 7
            "fr" -> 6
            "ss" -> 5
            "" -> 0
            else -> -6
        }
        return typeScore + regionScore
    }

    private fun absoluteMediaUrl(raw: String): String {
        val value = raw.replace("&amp;", "&").trim()
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("//")) return "https:$value"
        if (value.isEmpty() || value == "NOMEDIA") return ""
        return "https://www.screenscraper.fr/${value.trimStart('/')}"
    }

    private fun mediaUrl(
        settings: ScrapeSettings,
        systemId: String,
        gameId: String,
        type: String,
        region: String,
    ): String {
        val params = auth(settings) + linkedMapOf(
            "systemeid" to systemId,
            "jeuid" to gameId,
            "media" to "$type($region)",
            "outputformat" to "png",
        )
        return "$API/mediaJeu.php?${ScrapeHttp.query(params)}"
    }

    private fun api(
        path: String,
        settings: ScrapeSettings,
        params: Map<String, String>,
    ): String {
        val query = ScrapeHttp.query(auth(settings) + params + ("output" to "json"))
        return "$API/$path?$query"
    }

    private fun auth(settings: ScrapeSettings): LinkedHashMap<String, String> {
        val params = linkedMapOf(
            "devid" to appDevId(settings),
            "devpassword" to appDevPassword(settings),
            "softname" to appSoftName(settings),
        )
        if (settings.userId.isNotBlank()) params["ssid"] = settings.userId.trim()
        if (settings.userPassword.isNotBlank()) params["sspassword"] = settings.userPassword
        return params
    }

    private fun appDevId(settings: ScrapeSettings): String =
        settings.devId.trim().ifBlank { DEV_ID }.trim()

    private fun appDevPassword(settings: ScrapeSettings): String =
        settings.devPassword.ifBlank { DEV_PASSWORD }

    private fun appSoftName(settings: ScrapeSettings): String =
        settings.softName.trim().ifBlank { SOFT_NAME }

    private fun localized(
        array: JSONArray?,
        keyField: String,
        valueField: String,
        preference: List<String>,
    ): String {
        if (array == null) return ""
        val values = LinkedHashMap<String, String>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val key = item.optString(keyField).lowercase(Locale.ROOT)
            val value = item.optString(valueField).trim()
            if (key.isNotBlank() && value.isNotBlank()) values.putIfAbsent(key, value)
        }
        for (key in preference) values[key]?.let { return it }
        return values.values.firstOrNull().orEmpty()
    }

    private fun jeuTitle(jeu: JSONObject): String =
        localized(jeu.optJSONArray("noms"), "region", "text", REGIONS)
            .ifBlank { jeu.optString("nom") }

    private fun normalized(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return NON_ALNUM.replace(MARKS.replace(decomposed, ""), " ").trim()
    }

    private fun md5(bytes: ByteArray): String = hex(MessageDigest.getInstance("MD5").digest(bytes))

    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val value = bytes[i].toInt() and 0xFF
            out[i * 2] = digits[value ushr 4]
            out[i * 2 + 1] = digits[value and 0x0F]
        }
        return String(out)
    }

    private fun hex(value: Long): String {
        val digits = "0123456789abcdef"
        var current = value
        val out = CharArray(8)
        for (i in 7 downTo 0) {
            out[i] = digits[(current and 0xFL).toInt()]
            current = current ushr 4
        }
        return String(out)
    }

    private val SYSTEM_ALIASES = mapOf(
        "nes" to listOf("Nintendo Entertainment System", "Famicom"),
        "nintendo entertainment system" to listOf("NES", "Famicom"),
        "snes" to listOf("Super Nintendo Entertainment System", "Super Famicom"),
        "super nintendo entertainment system" to listOf("SNES", "Super Famicom"),
        "genesis" to listOf("Mega Drive", "Sega Genesis"),
        "megadrive" to listOf("Mega Drive", "Sega Genesis"),
        "mega drive" to listOf("Genesis", "Sega Mega Drive"),
        "psx" to listOf("PlayStation", "PSX"),
        "playstation" to listOf("PSX"),
        "ps2" to listOf("PlayStation 2"),
        "gb" to listOf("Game Boy"),
        "gba" to listOf("Game Boy Advance"),
        "nds" to listOf("Nintendo DS"),
        "3ds" to listOf("Nintendo 3DS"),
        "n64" to listOf("Nintendo 64"),
        "gamecube" to listOf("GameCube"),
        "wii" to listOf("Wii"),
        "arcade" to listOf("Arcade"),
        "mame" to listOf("Arcade", "MAME"),
    )
}
