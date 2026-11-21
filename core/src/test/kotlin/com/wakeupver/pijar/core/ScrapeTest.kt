package com.wakeupver.pijar.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScrapeTest {
    @Test
    fun matcherHandlesRegionsAndCleanTitles() {
        assertEquals(1000, ScrapeMatcher.score("Tetris", "Tetris"))
        assertEquals(1000, ScrapeMatcher.score("Tetris", "Tetris (World)"))
        assertEquals(850, ScrapeMatcher.score("Mario", "Mario Bros"))
        assertEquals("Final Fantasy VII", ScrapeMatcher.best("Final Fantasy VII (USA)", listOf("Final Fantasy VII" to "Final Fantasy VII")))
        assertNull(ScrapeMatcher.best("Tetris", listOf("Yoshis Island" to "Yoshis Island")))
    }

    @Test
    fun matcherNormalizesAccentsAndSeparators() {
        assertEquals("pokemon yellow", ScrapeMatcher.normalize("Pokémon_Yellow_(USA)"))
        assertEquals("mario and luigi", ScrapeMatcher.normalize("Mario & Luigi"))
    }

    @Test
    fun platformReadsScreenScraperName() {
        val text = """{"platform":{"shortname":"a5200","uniqueId":"a5200","name":"Atari 5200","scraperSourceList":["SCREENSCRAPER:Atari - 5200","DSESS:BOX_ART:x"]},"playerList":[]}"""
        assertEquals("Atari - 5200", DaijishouFormat.parsePlatform(text).scraperName)
    }
}
