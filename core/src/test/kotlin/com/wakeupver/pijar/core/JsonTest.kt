package com.wakeupver.pijar.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonTest {
    @Test
    fun parsesNestedStructures() {
        val v = Json.parse("""{"a":1,"b":[true,null,"x"],"c":{"d":2.5}}""").asObject()!!
        assertEquals(1, v.int("a"))
        assertEquals(listOf<Any?>(true, null, "x"), v["b"])
        assertEquals(2.5, v["c"].asObject()!!["d"])
    }

    @Test
    fun decodesEscapesAndUnicode() {
        val s = Json.parse("\"a\\n\\\"b\\\" \\u00e9\\ud83d\\ude00\"") as String
        assertEquals("a\n\"b\" \u00e9\uD83D\uDE00", s)
    }

    @Test
    fun roundTripKeepsData() {
        val original = linkedMapOf<String, Any?>(
            "s" to "he said \"hi\"\n\ttab",
            "n" to 42L,
            "f" to 1.25,
            "b" to false,
            "z" to null,
            "l" to listOf(1L, "dua", linkedMapOf<String, Any?>("k" to true)),
        )
        val text = Json.stringify(original)
        assertEquals(original, Json.parse(text))
        assertEquals(original, Json.parse(Json.stringify(original, pretty = true)))
    }

    @Test
    fun rejectsInvalidJson() {
        assertFailsWith<JsonException> { Json.parse("{\"a\":") }
        assertFailsWith<JsonException> { Json.parse("[1,2") }
        assertFailsWith<JsonException> { Json.parse("{} x") }
        assertFailsWith<JsonException> { Json.parse("") }
    }

    @Test
    fun ignoresByteOrderMark() {
        assertEquals(7L, Json.parse("\uFEFF7"))
    }
}
