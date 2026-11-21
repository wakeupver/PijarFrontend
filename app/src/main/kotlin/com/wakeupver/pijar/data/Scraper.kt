package com.wakeupver.pijar.data

import com.wakeupver.pijar.core.GameMetadata
import com.wakeupver.pijar.core.GameRecord
import com.wakeupver.pijar.core.GameTitles
import com.wakeupver.pijar.core.PlatformDef
import com.wakeupver.pijar.core.ScrapeSource
import java.io.File

object Scraper {
    private const val CACHE_TTL = 10L * 60 * 1000
    private val scrapeCache = HashMap<ScrapeKey, CachedMetadata>()

    fun scrape(game: GameRecord, platform: PlatformDef?, cacheDir: File): GameMetadata {
        val fileName = game.path.substringAfterLast('/', game.path)
        val query = GameTitles.clean(fileName).ifBlank { game.title }
        val key = ScrapeKey(
            query = query,
            platformId = game.platformId,
            libretroSystem = platform?.libretroName.orEmpty(),
            platformName = platform?.name.orEmpty(),
            regions = GameTitles.tags(fileName).sorted().joinToString(","),
        )
        val now = System.currentTimeMillis()
        val cached = synchronized(scrapeCache) {
            val entry = scrapeCache[key]
            entry?.takeIf { now - it.at < CACHE_TTL }?.data
        }
        val metadata = cached ?: scrapeUncached(platform, query, fileName, cacheDir).also { fresh ->
            synchronized(scrapeCache) { scrapeCache[key] = CachedMetadata(now, fresh) }
        }
        return metadata.copy(scrapedAt = now)
    }

    private fun scrapeUncached(
        platform: PlatformDef?,
        query: String,
        fileName: String,
        cacheDir: File,
    ): GameMetadata {
        val cover = libretroCover(platform, fileName, query, cacheDir) ?: return GameMetadata()
        return GameMetadata(
            source = ScrapeSource.LIBRETRO,
            coverUrl = cover,
            scrapedAt = System.currentTimeMillis(),
        )
    }

    private fun libretroCover(
        platform: PlatformDef?,
        fileName: String,
        query: String,
        cacheDir: File,
    ): String? {
        val system = platform?.libretroName?.takeIf { it.isNotBlank() } ?: platform?.name.orEmpty()
        if (system.isBlank()) return null
        return LibretroThumbnails.match(system, fileName, query, cacheDir)
    }

    private data class ScrapeKey(
        val query: String,
        val platformId: String,
        val libretroSystem: String,
        val platformName: String,
        val regions: String,
    )

    private data class CachedMetadata(val at: Long, val data: GameMetadata)
}
