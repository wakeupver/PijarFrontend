package com.wakeupver.pijar.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DaijishouFormatTest {
    private val sample = """
        {
          "databaseVersion": 14,
          "revisionNumber": 16,
          "platform": {
            "name": "Sony PlayStation", "uniqueId": "psx", "shortname": "psx", "description": null,
            "acceptedFilenameRegex": "^(?!(?:\\._|\\.).*).*(?<!bin)$"
          },
          "playerList": [
            {"name": "psx - Duckstation", "uniqueId": "psx.duck", "description": "x",
             "acceptedFilenameRegex": "^(.*).(?:bin|chd|cue|iso)$",
             "amStartArguments": "-n com.github.stenzek.duckstation/.EmulationActivity\n-e bootPath {file.path}",
             "killPackageProcesses": true},
            {"name": "psx - Duckstation (Legacy)", "uniqueId": "psx.duck", "description": null,
             "acceptedFilenameRegex": "^(.*).(?:chd)$",
             "amStartArguments": "-n com.github.stenzek.duckstation/.EmulationActivity",
             "killPackageProcesses": false}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesPlatformJson() {
        val p = DaijishouFormat.parsePlatform(sample)
        assertEquals("psx", p.uniqueId)
        assertEquals("Sony PlayStation", p.name)
        assertEquals(16, p.revision)
        assertEquals(2, p.players.size)
        assertEquals("com.github.stenzek.duckstation", p.players[0].packageName)
        assertTrue(p.players[0].killPackageProcesses)
        // uniqueId ganda di data Daijishō: kunci harus tetap unik.
        assertEquals(2, p.players.map { it.key }.toSet().size)
    }

    @Test
    fun playerPickerFollowsRegexAndPreference() {
        val p = DaijishouFormat.parsePlatform(sample)
        assertEquals(2, PlayerPicker.compatible(p, "game.CHD").size)
        assertEquals(1, PlayerPicker.compatible(p, "game.iso").size)
        assertEquals("psx - Duckstation (Legacy)", PlayerPicker.choose(p, p.players[1].key, "game.chd")?.name)
        assertEquals("psx - Duckstation", PlayerPicker.choose(p, p.players[1].key, "game.iso")?.name)
        assertEquals(null, PlayerPicker.choose(p, "", "game.zip"))
    }

    @Test
    fun parsesIndex() {
        val text = """{"baseUri":"x","platformList":[{"filename":"3DO.json","platformName":"3DO","platformShortname":"3do","platformUniqueId":"3do","revisionNumber":8}]}"""
        val list = DaijishouFormat.parseIndex(text)
        assertEquals(1, list.size)
        assertEquals("3DO.json", list[0].filename)
        assertEquals(8, list[0].revision)
    }

    private fun assetsDir(): File? {
        val rel = "app/src/main/assets/daijishou/platforms"
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(4) {
            val candidate = File(dir, rel)
            if (candidate.isDirectory) return candidate
            dir = dir?.parentFile
        }
        return null
    }

    /** Uji integrasi: seluruh data platform asli milik Daijishō harus terbaca dan terjemah ke Intent. */
    @Test
    fun allBundledPlatformsParse() {
        val dir = assetsDir() ?: return
        val files = dir.listFiles { f -> f.extension == "json" && f.name != "index.json" }!!.sortedBy { it.name }
        assertTrue(files.size >= 100, "Terlalu sedikit berkas platform: ${files.size}")
        val ids = HashSet<String>()
        var players = 0
        for (f in files) {
            val platform = DaijishouFormat.parsePlatform(f.readText())
            assertTrue(ids.add(platform.uniqueId), "uniqueId ganda: ${platform.uniqueId} (${f.name})")
            Regex(platform.acceptedFilenameRegex)
            for (pl in platform.players) {
                players++
                Regex(pl.acceptedFilenameRegex)
                val cmd = AmArgs.parse(pl.amStartArguments)
                assertTrue(cmd.warnings.isEmpty(), "${f.name} / ${pl.name}: ${cmd.warnings}")
                assertNotNull(pl.packageName, "${f.name} / ${pl.name}: tanpa paket target")
                if (!pl.amStartArguments.contains("{tags.")) {
                    val ctx = TemplateContext("/storage/emulated/0/ROMs/Test Game.bin", "application/octet-stream") { "content://t/f" }
                    val r = cmd.resolve(ctx)
                    assertNotNull(r.targetPackage)
                    val texts = r.extras.map { it.value.toString() } + listOfNotNull(r.data, r.mimeType)
                    assertTrue(texts.none { it.contains("{file.") }, "${f.name} / ${pl.name}: tag file belum terisi")
                }
            }
        }
        assertTrue(players >= 900, "Pemain terlalu sedikit: $players")
        val index = DaijishouFormat.parseIndex(File(dir, "index.json").readText())
        assertTrue(index.size >= 100)
        assertTrue(index.all { File(dir, it.filename).isFile }, "index.json menunjuk berkas yang tidak ada")
    }
}
