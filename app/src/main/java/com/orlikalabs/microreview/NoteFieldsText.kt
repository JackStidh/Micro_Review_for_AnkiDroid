package com.orlikalabs.microreview

/**
 * Pure part of "show the note's fields under the answer": which fields add something to what the card already
 * shows. A card's back may leave out a field (the meaning, say); the fields always have it.
 */
object NoteFieldsText {
    /** Anki's furigana syntax, `漢字[かんじ]` (a space may come before it): shown as `漢字 (かんじ)`. */
    private val FURIGANA = Regex(""" ?([^\s\[\]]+)\[([^\]\s][^\]]*)]""")
    private val SPACES = Regex("""\s+""")

    /** [text] with its furigana written out. */
    fun readable(text: String): String = text.replace(FURIGANA) { "${it.groupValues[1]} (${it.groupValues[2]})" }.trim()

    /**
     * The fields worth showing, as (name, text), in the note type's order: not empty, and not already in [shown]
     * (the question and answer as displayed). [values] are already turned into text; their furigana is written out.
     */
    fun extra(names: List<String>, values: List<String>, shown: String): List<Pair<String, String>> {
        val shownKey = key(shown)
        val seen = HashSet<String>()
        return values.indices.mapNotNull { i ->
            val text = readable(values[i])
            val k = key(text)
            when {
                k.isEmpty() || !seen.add(k) -> null
                // Already on the card: as is, or as the word followed by its reading (how ruby text reads).
                shownKey.contains(k) || shownKey.contains(key(values[i].replace(FURIGANA) { it.groupValues[1] + it.groupValues[2] })) -> null
                else -> (names.getOrNull(i) ?: "Field ${i + 1}") to text
            }
        }
    }

    private fun key(text: String): String = text.replace(SPACES, "").lowercase()
}
