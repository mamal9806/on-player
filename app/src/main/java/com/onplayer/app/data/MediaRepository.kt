package com.onplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

class MediaRepository(private val context: Context) {
    fun scan(): List<MediaFile> {
        val resolver = context.contentResolver
        val results = mutableListOf<MediaFile>()
        try {
            val columns = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM
            )
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                columns,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    results += MediaFile(
                        id = id,
                        name = cursor.getString(1) ?: "Audio",
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        mime = cursor.getString(2) ?: "audio/*",
                        size = cursor.getLong(3),
                        modified = cursor.getLong(4),
                        category = MediaCategory.Audio,
                        durationMs = cursor.getLong(5),
                        title = cursor.getString(6),
                        artist = cursor.getString(7),
                        album = cursor.getString(8)
                    )
                }
            }
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
        return results.distinctBy { it.uri.toString() }.sortedByDescending { it.modified }
    }
}
