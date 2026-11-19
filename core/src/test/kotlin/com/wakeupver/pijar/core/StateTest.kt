package com.wakeupver.pijar.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StateTest {
    @Test
    fun roundTrip() {
        val state = AppState(
            platforms = listOf(
                PlatformConfig(
                    platformId = "psx",
                    folder = "/sdcard/ROMs/psx",
                    playerKey = "psx.duck|psx - Duckstation",
                    addedAt = 123,
                    fileHandling = FileHandling.STANDARD,
                    setupDone = true,
                ),
            ),
            games = listOf(
                GameRecord(
                    "id1",
                    "psx",
                    "/sdcard/ROMs/psx/A \"x\".iso",
                    "A",
                    1,
                    2,
                    3,
                    true,
                    4,
                    GameMetadata(
                        source = ScrapeSource.LIBRETRO,
                        title = "A",
                        overview = "Ringkasan",
                        releaseDate = "1995",
                        genres = listOf("Action", "Puzzle"),
                        coverUrl = "https://example.test/a.png",
                        coverPath = "/cover/a.png",
                        scrapedAt = 99,
                    ),
                ),
            ),
            prefs = UiPrefs(
                dynamicColor = false,
                accentHue = 120,
                scrape = ScrapeSettings(),
            ),
        )
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
    }

    @Test
    fun decodeIsTolerant() {
        val s = StateCodec.decode("""{"games":[{"id":"x","platform":"psx","path":"/a/b.iso"},{"id":""}]}""")
        assertEquals(1, s.games.size)
        assertEquals("b", s.games[0].title)
        assertEquals(UiPrefs(), s.prefs)
        assertEquals(0, s.platforms.size)
    }

    @Test
    fun legacyPlatformWithoutSetupFlagIsReady() {
        val s = StateCodec.decode("""{"platforms":[{"id":"psx"}]}""")
        assertEquals(true, s.platforms.single().setupDone)
    }

    @Test
    fun unknownFileHandlingFallsBackToDefault() {
        val s = StateCodec.decode("""{"platforms":[{"id":"psx","fileHandling":"unknown"}]}""")
        assertEquals(FileHandling.DEFAULT, s.platforms.single().fileHandling)
    }

    @Test
    fun scrapeStateIsTolerant() {
        val s = StateCodec.decode(
            """{"games":[{"id":"x","platform":"psx","path":"/a.iso","metadata":{"source":"igdb","genres":["Action"],"coverUrl":"https://example.test/x.png"}}],"prefs":{"scrape":{"enabledSources":["libretro","unknown"]}}}""",
        )
        assertEquals(ScrapeSettings(), s.prefs.scrape)
        val metadata = s.games.single().metadata
        assertNull(metadata.source)
        assertEquals(listOf("Action"), metadata.genres)
        assertEquals("https://example.test/x.png", metadata.coverUrl)
        assertTrue(metadata.hasData)
    }

    @Test
    fun invalidStateFails() {
        assertFailsWith<JsonException> { StateCodec.decode("[1,2]") }
        assertFailsWith<JsonException> { StateCodec.decode("{") }
    }
}
