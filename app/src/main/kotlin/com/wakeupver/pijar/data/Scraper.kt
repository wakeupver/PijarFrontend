package com.wakeupver.pijar.data

import com.wakeupver.pijar.core.GameMetadata
import com.wakeupver.pijar.core.GameRecord
import com.wakeupver.pijar.core.PlatformDef
import com.wakeupver.pijar.core.ScrapeSettings
import java.io.File

object Scraper {
    fun scrape(
        game: GameRecord,
        platform: PlatformDef?,
        settings: ScrapeSettings,
        filesDir: File,
        cacheDir: File,
    ): GameMetadata {
        val platformName = platform?.name.orEmpty()
        val platformAlias = platform?.scraperName.orEmpty()
        return ScreenScraper.scrape(game, platformName, platformAlias, settings, filesDir, cacheDir)
    }
}
