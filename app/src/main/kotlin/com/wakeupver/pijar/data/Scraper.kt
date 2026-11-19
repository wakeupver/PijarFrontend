package com.wakeupver.pijar.data

import com.wakeupver.pijar.core.GameMetadata
import com.wakeupver.pijar.core.GameRecord
import com.wakeupver.pijar.core.GameTitles
import com.wakeupver.pijar.core.PlatformDef
import com.wakeupver.pijar.core.ScrapeMatcher
import com.wakeupver.pijar.core.ScrapeSettings
import com.wakeupver.pijar.core.ScrapeSource
import com.wakeupver.pijar.core.Json
import com.wakeupver.pijar.core.asArray
import com.wakeupver.pijar.core.asObject
import com.wakeupver.pijar.core.long
import com.wakeupver.pijar.core.objects
import com.wakeupver.pijar.core.string
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Scraper {
    private const val LIBRETRO_ROOT = "https://thumbnails.libretro.com"
    private const val TGDB_ENDPOINT = "https://api.thegamesdb.net/v1/Games/ByGameName"
    private const val IGDB_TOKEN_URL = "https://id.twitch.tv/oauth2/token"
    private const val IGDB_ENDPOINT = "https://api.igdb.com/v4/games"
    private val ILLEGAL = Regex("""[&*/:<>?|"]""")
    private val DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var cachedToken: Pair<String, Long>? = null

    fun scrape(game: GameRecord, platform: PlatformDef?, settings: ScrapeSettings): GameMetadata {
        val fileName = game.path.substringAfterLast('/', game.path)
        val query = GameTitles.clean(fileName).ifBlank { game.title }
        var result = GameMetadata()
        val now = System.currentTimeMillis()

        if (ScrapeSource.LIBRETRO in settings.enabledSources) {
            val cover = libretroCover(platform, fileName, query)
            if (cover != null) {
                result = result.copy(source = ScrapeSource.LIBRETRO, coverUrl = cover, scrapedAt = now)
            }
        }
        if (ScrapeSource.THE_GAMES_DB in settings.enabledSources && settings.theGamesDbApiKey.isNotBlank()) {
            runCatching { theGamesDb(query, settings.theGamesDbApiKey) }.getOrNull()?.let { result = combine(result, it) }
        }
        if (ScrapeSource.IGDB in settings.enabledSources) {
            runCatching { igdb(query, settings) }.getOrNull()?.let { result = combine(result, it) }
        }
        return result.copy(scrapedAt = now)
    }

    private fun libretroCover(platform: PlatformDef?, fileName: String, query: String): String? {
        val system = platform?.libretroName?.takeIf { it.isNotBlank() } ?: platform?.name.orEmpty()
        if (system.isBlank()) return null
        val names = linkedSetOf<String>()
        names += GameTitles.baseName(fileName)
        names += query
        GameTitles.tags(fileName).forEach { names += it }
        names += fileName
        for (name in names.take(6)) {
            val safe = ILLEGAL.replace(name.trim(), "_")
            if (safe.isBlank()) continue
            val url = "$LIBRETRO_ROOT/${ScrapeHttp.encode(system)}/Named_Boxarts/${ScrapeHttp.encode(safe)}.png"
            if (ScrapeHttp.exists(url)) return url
        }
        return null
    }

    private fun theGamesDb(query: String, apiKey: String): GameMetadata? {
        val url = TGDB_ENDPOINT +
            "?apikey=${ScrapeHttp.encode(apiKey)}" +
            "&name=${ScrapeHttp.encode(query)}" +
            "&fields=overview,release_date,genres" +
            "&include=boxart"
        val root = ScrapeHttp.get(url)?.let { Json.parse(it).asObject() } ?: return null
        val data = root["data"].asObject() ?: return null
        val games = data.objects("games")
        val candidates = games.map { game ->
            game.string("game_title").ifBlank { game.string("name") } to game
        }
        val best = ScrapeMatcher.best(query, candidates) ?: return null
        val genreNames = data["genres"].asObject().orEmpty().mapValues { entry -> entry.value?.toString().orEmpty() }
        val genres = (best["genres"] as? List<*>).orEmpty().mapNotNull { value ->
            genreNames[value?.toString().orEmpty()]?.takeIf { it.isNotBlank() }
        }
        val boxart = data["boxart"].asObject()
        val arts = boxart?.get(best.long("id").toString())?.asArray().orEmpty().mapNotNull { it.asObject() }
        val art = arts.firstOrNull { it.string("type").contains("front", ignoreCase = true) } ?: arts.firstOrNull()
        val filename = art?.string("filename").orEmpty()
        val uri = art?.string("uri").orEmpty()
        val fallback = filename.takeIf { it.isNotBlank() }
            ?.let { "https://cdn.thegamesdb.net/images/original/$it" }
            .orEmpty()
        val cover = uri.ifBlank { fallback }
        return GameMetadata(
            source = ScrapeSource.THE_GAMES_DB,
            title = best.string("game_title").ifBlank { best.string("name") },
            overview = best.string("overview"),
            releaseDate = releaseDate(best["release_date"]),
            genres = genres,
            coverUrl = cover,
        )
    }

    private fun igdb(query: String, settings: ScrapeSettings): GameMetadata? {
        val token = accessToken(settings) ?: return null
        val body = "fields name,summary,first_release_date,genres.name,cover.url;" +
            "search \"${escapeQuery(query)}\";limit 8;"
        val headers = mapOf(
            "Client-ID" to settings.igdbClientId,
            "Authorization" to "Bearer $token",
            "Content-Type" to "text/plain",
        )
        val games = ScrapeHttp.post(IGDB_ENDPOINT, headers, body)
            ?.let { Json.parse(it).asArray() }
            ?.mapNotNull { it.asObject() }
            .orEmpty()
        val candidates = games.map { game -> game.string("name") to game }
        val best = ScrapeMatcher.best(query, candidates) ?: return null
        val genres = (best["genres"] as? List<*>).orEmpty().mapNotNull { value ->
            value.asObject()?.string("name")?.takeIf { it.isNotBlank() }
        }
        val rawCover = best["cover"].asObject()?.string("url").orEmpty()
        val cover = if (rawCover.isBlank()) {
            ""
        } else {
            "https:${rawCover.replace("t_thumb", "t_original")}"
        }
        val released = best.long("first_release_date")
        return GameMetadata(
            source = ScrapeSource.IGDB,
            title = best.string("name"),
            overview = best.string("summary"),
            releaseDate = if (released > 0L) {
                synchronized(DATE) { DATE.format(Date(released * 1000L)) }
            } else {
                ""
            },
            genres = genres,
            coverUrl = cover,
        )
    }

    private fun accessToken(settings: ScrapeSettings): String? {
        if (settings.igdbClientId.isBlank() || settings.igdbClientSecret.isBlank()) return null
        val cached = cachedToken
        if (cached != null && cached.second > System.currentTimeMillis() + 60_000L) return cached.first
        val body = "client_id=${ScrapeHttp.encode(settings.igdbClientId)}" +
            "&client_secret=${ScrapeHttp.encode(settings.igdbClientSecret)}" +
            "&grant_type=client_credentials"
        val headers = mapOf("Content-Type" to "application/x-www-form-urlencoded")
        val text = ScrapeHttp.post(IGDB_TOKEN_URL, headers, body) ?: return null
        val root = Json.parse(text).asObject() ?: return null
        val token = root.string("access_token")
        if (token.isBlank()) return null
        val expires = root.long("expires_in", 3600L).coerceIn(60L, 604800L)
        cachedToken = token to System.currentTimeMillis() + expires * 1000L
        return token
    }

    private fun releaseDate(value: Any?): String = when (value) {
        is String -> value
        is Map<*, *> -> value["year"]?.toString().orEmpty()
        is Number -> value.toString()
        else -> ""
    }

    private fun escapeQuery(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun combine(primary: GameMetadata, next: GameMetadata): GameMetadata = GameMetadata(
        source = if (next.hasText) next.source else primary.source,
        title = primary.title.ifBlank { next.title },
        overview = primary.overview.ifBlank { next.overview },
        releaseDate = primary.releaseDate.ifBlank { next.releaseDate },
        genres = primary.genres.ifEmpty { next.genres },
        coverUrl = primary.coverUrl.ifBlank { next.coverUrl },
        coverPath = primary.coverPath.ifBlank { next.coverPath },
        scrapedAt = maxOf(primary.scrapedAt, next.scrapedAt),
    )
}
