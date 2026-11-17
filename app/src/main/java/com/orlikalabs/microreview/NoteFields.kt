package com.orlikalabs.microreview

import android.content.ContentUris
import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * The note's fields that the card doesn't show ([NoteFieldsText]), read from AnkiDroid: shown under the answer of
 * the full-screen card, so that a definition left out of the card's back is still there.
 */
object NoteFields {
    /** Field names per note type: they rarely change, and each card would ask again. */
    private val names = ConcurrentHashMap<Long, List<String>>()

    /** Does ContentProvider I/O: off the main thread. Empty if AnkiDroid can't be read. */
    fun extra(context: Context, noteId: Long, shown: String): List<Pair<String, String>> = try {
        val note = context.contentResolver.query(
            ContentUris.withAppendedId(AnkiDroidContract.Note.CONTENT_URI, noteId),
            arrayOf(AnkiDroidContract.Note.MID, AnkiDroidContract.Note.FLDS),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getLong(0) to cursor.getString(1).orEmpty()
        }
        if (note == null) {
            emptyList()
        } else {
            val (modelId, fields) = note
            val values = fields.split(AnkiDroidContract.FIELD_SEPARATOR).map(CardText::clean)
            NoteFieldsText.extra(fieldNames(context, modelId), values, shown)
        }
    } catch (e: Exception) {
        FocusLog.i(context, "note fields unavailable: $e")
        emptyList()
    }

    private fun fieldNames(context: Context, modelId: Long): List<String> {
        names[modelId]?.let { return it }
        val read = context.contentResolver.query(
            ContentUris.withAppendedId(AnkiDroidContract.Model.CONTENT_URI, modelId),
            arrayOf(AnkiDroidContract.Model.FIELD_NAMES),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.split(AnkiDroidContract.FIELD_SEPARATOR) else null
        } ?: return emptyList()
        names[modelId] = read
        return read
    }
}
