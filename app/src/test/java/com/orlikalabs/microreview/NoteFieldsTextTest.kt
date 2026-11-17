package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteFieldsTextTest {
    @Test
    fun furiganaIsWrittenOut() {
        assertEquals("木曜日 (もくようび)", NoteFieldsText.readable("木曜日[もくようび]"))
        // As in Anki, the space before a word with furigana only separates it.
        assertEquals("今日 (きょう)は木曜日 (もくようび)です", NoteFieldsText.readable("今日[きょう]は 木曜日[もくようび]です"))
        assertEquals("no furigana", NoteFieldsText.readable("no furigana"))
        assertEquals("[]", NoteFieldsText.readable("[]"))
    }

    @Test
    fun onlyFieldsThatAddSomethingAreShown() {
        val names = listOf("Word", "Reading", "Meaning", "Notes", "Audio")
        val values = listOf("木曜日", "木曜日[もくようび]", "Thursday", "", "")
        // The card showed the word and its reading: only the meaning is missing.
        assertEquals(listOf("Meaning" to "Thursday"), NoteFieldsText.extra(names, values, "木曜日\nもくようび"))
        // The card showed the word alone: the reading is missing too.
        assertEquals(
            listOf("Reading" to "木曜日 (もくようび)", "Meaning" to "Thursday"),
            NoteFieldsText.extra(names, values, "木曜日"),
        )
    }

    @Test
    fun aFieldAlreadyShownWholeIsLeftOut() {
        val extra = NoteFieldsText.extra(listOf("Front", "Back"), listOf("木曜日", "Thursday"), "木曜日 Thursday")
        assertEquals(emptyList<Pair<String, String>>(), extra)
    }

    @Test
    fun duplicatesAndMissingNames() {
        val extra = NoteFieldsText.extra(listOf("A"), listOf("x", "x", "y"), "")
        assertEquals(listOf("A" to "x", "Field 3" to "y"), extra)
    }
}
