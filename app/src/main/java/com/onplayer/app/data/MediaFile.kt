package com.onplayer.app.data

import android.net.Uri

enum class MediaCategory(val label: String) { Images("Photos"), Videos("Videos"), Audio("Music") }
data class MediaFile(
    val id: Long,
    val name: String,
    val uri: Uri,
    val mime: String,
    val size: Long,
    val modified: Long,
    val category: MediaCategory,
    val durationMs: Long = 0,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
) {
    val isPlayable get() = category == MediaCategory.Audio || category == MediaCategory.Videos
    private fun cleanMetadata(value: String?): String? = value?.trim()?.takeIf {
        it.isNotBlank() && !it.equals("unknown", true) && !it.equals("<unknown>", true) && !it.equals("null", true)
    }
    val displayTitle: String get() = cleanMetadata(title)
        ?: name.substringBeforeLast('.', name).trim().ifBlank { name }
    val displayArtist: String get() = cleanMetadata(artist) ?: "Unknown artist"
    val displayAlbum: String get() = cleanMetadata(album) ?: ""
    val sizeText: String get() {
        val mb = size / 1024.0 / 1024.0
        return if (mb >= 1024) "%.1f GB".format(mb / 1024)
        else if (mb >= 1) "%.1f MB".format(mb)
        else "%.0f KB".format(size / 1024.0)
    }
}