package com.wakeupver.pijar.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
                ),
            ),
            games = listOf(
                GameRecord("id1", "psx", "/sdcard/ROMs/psx/A \"x\".iso", "A", 1, 2, 3, true, 4),
            ),
            prefs = UiPrefs(dynamicColor = false, accentHue = 120),
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
    fun unknownFileHandlingFallsBackToDefault() {
        val s = StateCodec.decode("""{"platforms":[{"id":"psx","fileHandling":"unknown"}]}""")
        assertEquals(FileHandling.DEFAULT, s.platforms.single().fileHandling)
    }

    @Test
    fun invalidStateFails() {
        assertFailsWith<JsonException> { StateCodec.decode("[1,2]") }
        assertFailsWith<JsonException> { StateCodec.decode("{") }
    }
}
