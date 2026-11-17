package com.wakeupver.pijar.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmArgsTest {
    private fun ctx(path: String, uri: String = "content://x/f", tags: Map<String, String> = emptyMap()) =
        TemplateContext(path, "application/octet-stream", tags) { uri }

    @Test
    fun parsesDuckstationTemplate() {
        val cmd = AmArgs.parse(
            "-n com.github.stenzek.duckstation/.EmulationActivity\n-e bootPath {file.path}\n --activity-clear-task\n --activity-clear-top",
        )
        assertEquals("com.github.stenzek.duckstation/.EmulationActivity", cmd.component)
        assertEquals("com.github.stenzek.duckstation", cmd.targetPackage())
        assertTrue(cmd.warnings.isEmpty())
        val r = cmd.resolve(ctx("/sdcard/ROMs/My Game (USA).iso"))
        assertEquals("com.github.stenzek.duckstation", r.componentPackage)
        assertEquals("com.github.stenzek.duckstation.EmulationActivity", r.componentClass)
        assertEquals(listOf(Extra("bootPath", ExtraValue.Text("/sdcard/ROMs/My Game (USA).iso"))), r.extras)
        assertEquals(0x00008000 or 0x04000000, r.flags)
        assertFalse(r.usesFileUri)
    }

    @Test
    fun fileUriTemplateMarksUriAsUsed() {
        val cmd = AmArgs.parse("-n a.b/.C\n -a android.intent.action.VIEW\n -d {file.uri}")
        val r = cmd.resolve(ctx("/r/g.iso", "content://p/root/r/g.iso"))
        assertEquals("android.intent.action.VIEW", r.action)
        assertEquals("content://p/root/r/g.iso", r.data)
        assertTrue(r.usesFileUri)
    }

    @Test
    fun quotedArrayExtraFromDaijishouDocs() {
        val cmd = AmArgs.parse(
            "-n org.vita3k.emulator/org.vita3k.emulator.Emulator --esa \"AppStartParameters\" \"-r\",\"{tags.vita_game_id}\"",
        )
        val r = cmd.resolve(ctx("/r/Golden Abyss.dpt", tags = mapOf("vita_game_id" to "PCSF00001")))
        assertEquals(listOf(Extra("AppStartParameters", ExtraValue.TextList(listOf("-r", "PCSF00001")))), r.extras)
    }

    @Test
    fun typedExtras() {
        val cmd = AmArgs.parse(
            "-n a/.B --ez off 0 --ez on true --ei n 42 --el big 123456789012 --ef f 1.5 --esn nothing --eu u content://a/b --eia ids 1,2,3",
        )
        val extras = cmd.resolve(ctx("/x.bin")).extras.associate { it.key to it.value }
        assertEquals(ExtraValue.Bool(false), extras["off"])
        assertEquals(ExtraValue.Bool(true), extras["on"])
        assertEquals(ExtraValue.IntNum(42), extras["n"])
        assertEquals(ExtraValue.LongNum(123456789012L), extras["big"])
        assertEquals(ExtraValue.FloatNum(1.5f), extras["f"])
        assertEquals(ExtraValue.NullText, extras["nothing"])
        assertEquals(ExtraValue.UriText("content://a/b"), extras["u"])
        assertEquals(ExtraValue.IntList(listOf(1, 2, 3)), extras["ids"])
    }

    @Test
    fun invalidNumberFailsAtResolve() {
        val cmd = AmArgs.parse("-n a/.B --ei n abc")
        assertFailsWith<IllegalArgumentException> { cmd.resolve(ctx("/x.bin")) }
    }

    @Test
    fun unknownOptionProducesWarning() {
        val cmd = AmArgs.parse("-n a/.B --foo-bar")
        assertEquals(1, cmd.warnings.size)
        assertTrue(cmd.warnings[0].contains("--foo-bar"))
    }

    @Test
    fun positionalPackageBecomesLauncherIntent() {
        val r = AmArgs.parse("com.example.app").resolve(ctx("/x"))
        assertEquals("com.example.app", r.packageName)
        assertEquals("android.intent.action.MAIN", r.action)
        assertEquals(listOf("android.intent.category.LAUNCHER"), r.categories)
    }

    @Test
    fun tokenizerHandlesNewlinesAndQuotes() {
        assertEquals(
            listOf("-e", "k", "a b", "c d", "-c", "x", ""),
            AmArgs.tokenize("  -e   k  'a b'  \"c d\"\n -c x \"\""),
        )
    }

    @Test
    fun hexFlagsAreSupported() {
        val cmd = AmArgs.parse("-n a/.B -f 0x10000000")
        assertEquals(0x10000000, cmd.flags)
    }

    @Test
    fun templateRules() {
        val c = ctx("/a/b/Game.iso")
        assertEquals("{foo} Game.iso ", Templates.expand("{foo} {file.name} {tags.Missing}", c))
        assertEquals("Game", Templates.expand("{file.basename}", c))
        assertEquals("/a/b", Templates.expand("{file.dir}", c))
        assertFalse(c.uriUsed)
        assertNull(Components.split("tanpa-slash"))
    }

    @Test
    fun dptFormat() {
        val tags = Dpt.parse("# Daijishou Player Template\n[vita_game_id] PCSF00001\n[task_name] exampleTask\n...\n")
        assertEquals(mapOf("vita_game_id" to "PCSF00001", "task_name" to "exampleTask"), tags)
        assertEquals(mapOf("a" to "b"), Dpt.parse("# DST\n[a] b"))
        assertNull(Dpt.parse("bukan dpt\n[a] b"))
        assertTrue(Dpt.isDptFile("Golden Abyss.DPT"))
    }
}
