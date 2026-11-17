package com.wakeupver.pijar.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StateTest {
    @Test
    fun roundTrip() {
        val state = AppState(
            platforms = listOf(PlatformConfig("psx", "/sdcard/ROMs/psx", "psx.duck|psx - Duckstation", 123)),
            games = listOf(
                GameRecord("id1", "psx", "/sdcard/ROMs/psx/A \"x\".iso", "A", 1, 2, 3, true, 4),
            ),
            prefs = UiPrefs(dynamicColor = false, accentHue = 120, showWallpaper = false),
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
    fun invalidStateFails() {
        assertFailsWith<JsonException> { StateCodec.decode("[1,2]") }
        assertFailsWith<JsonException> { StateCodec.decode("{") }
    }
}
