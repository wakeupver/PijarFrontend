package com.wakeupver.pijar.core

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryTest {
    private fun touch(root: File, rel: String) {
        val f = File(root, rel)
        f.parentFile.mkdirs()
        f.writeText("x")
    }

    @Test
    fun scannerFiltersHiddenAndByRegex() {
        val root = Files.createTempDirectory("pijar-scan").toFile()
        try {
            touch(root, "Game A (USA).iso")
            touch(root, "sub/Game B.CHD")
            touch(root, ".hidden.iso")
            touch(root, ".trash/x.iso")
            touch(root, "readme.txt")
            val found = LibraryScanner.scan(root, Rx.ci("^(?!\\.).*\\.(iso|chd)$"))
            assertEquals(listOf("Game A (USA).iso", "Game B.CHD"), found.map { it.fileName })
            val limited = LibraryScanner.scan(root, Rx.ci(".*"), maxDepth = 0)
            assertTrue(limited.none { it.fileName == "Game B.CHD" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun mergeKeepsFavoritesAndHistory() {
        val a = ScannedFile("/r/Game A (USA).iso", "Game A (USA).iso", 10, 1)
        val b = ScannedFile("/r/Game B.chd", "Game B.chd", 20, 2)
        val idA = Ids.game("psx", a.path)
        val existing = listOf(
            GameRecord(idA, "psx", a.path, "Judul Saya", addedAt = 5, lastPlayedAt = 99, playCount = 3, favorite = true),
            GameRecord("old", "psx", "/r/gone.iso", "Gone", addedAt = 1),
            GameRecord("zzz", "nes", "/r/n.nes", "NES", addedAt = 1),
        )
        val merged = LibraryMerge.merge("psx", existing, listOf(a, b), now = 1000)
        assertEquals(2, merged.size)
        val ra = merged.first { it.id == idA }
        assertEquals("Judul Saya", ra.title)
        assertTrue(ra.favorite)
        assertEquals(3, ra.playCount)
        assertEquals(99L, ra.lastPlayedAt)
        val rb = merged.first { it.id != idA }
        assertEquals("Game B", rb.title)
        assertEquals(1000L, rb.addedAt)
        assertEquals(Ids.game("psx", a.path), Ids.game("psx", a.path))
        assertTrue(Ids.game("psx", a.path) != Ids.game("nes", a.path))
    }

    @Test
    fun titleCleaning() {
        assertEquals("Super Mario Bros", GameTitles.clean("Super_Mario_Bros_(USA)_[!].nes"))
        assertEquals("Final Fantasy VII", GameTitles.clean("Final Fantasy VII (USA) (Disc 1).chd"))
        assertEquals("(Only Tag)", GameTitles.clean("(Only Tag).iso"))
        assertEquals(listOf("USA", "Rev 1"), GameTitles.tags("Game (USA) (Rev 1).iso"))
        assertEquals("a.tar", GameTitles.baseName("a.tar.gz"))
        assertEquals("noext", GameTitles.baseName("noext"))
    }

    @Test
    fun badRegexNeverMatches() {
        assertTrue(!Rx.ci("([").matches("apa-saja"))
        assertTrue(Rx.ci("^abc$").matches("ABC"))
    }

    @Test
    fun folderGuessIncludesShortNameAndName() {
        val p = PlatformDef("psx", "Sony PlayStation", "psx", ".*", 1, emptyList())
        assertEquals(listOf("psx", "Sony PlayStation", "SonyPlayStation", "Sony_PlayStation"), FolderGuess.names(p))
    }
}
