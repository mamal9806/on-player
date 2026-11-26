package com.onplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

class MediaRepository(private val context: Context) {
    fun scan(): List<MediaFile> {
        val resolver = context.contentResolver
        val results = mutableListOf<MediaFile>()

        // Each media collection is scanned independently. A denied permission for one
        // collection must not prevent other permitted collections from appearing.
        safely {
            val p = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_MODIFIED, MediaStore.Audio.Media.DURATION)
            resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, p, null, null, "${MediaStore.Audio.Media.DATE_MODIFIED} DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    results += MediaFile(id, cursor.getString(1) ?: "Audio", ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), cursor.getString(2) ?: "audio/*", cursor.getLong(3), cursor.getLong(4), MediaCategory.Audio, cursor.getLong(5))
                }
            }
        }
        safely {
            val p = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_MODIFIED, MediaStore.Video.Media.DURATION)
            resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, p, null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    results += MediaFile(id, cursor.getString(1) ?: "Video", ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id), cursor.getString(2) ?: "video/*", cursor.getLong(3), cursor.getLong(4), MediaCategory.Videos, cursor.getLong(5))
                }
            }
        }
        safely {
            val p = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_MODIFIED)
            resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, p, null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    results += MediaFile(id, cursor.getString(1) ?: "Image", ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), cursor.getString(2) ?: "image/*", cursor.getLong(3), cursor.getLong(4), MediaCategory.Images)
                }
            }
        }
        safely {
            val filesUri = MediaStore.Files.getContentUri("external")
            val p = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.MIME_TYPE, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED)
            resolver.query(filesUri, p, "${MediaStore.Files.FileColumns.MEDIA_TYPE}=0", null, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val category = when (ext) {
                        "pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "csv", "ppt", "pptx", "odp" -> MediaCategory.Documents
                        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz" -> MediaCategory.Archives
                        "apk", "apks", "xapk" -> MediaCategory.APK
                        else -> MediaCategory.Others
                    }
                    val id = cursor.getLong(0)
                    results += MediaFile(id, name, ContentUris.withAppendedId(filesUri, id), cursor.getString(2) ?: "application/octet-stream", cursor.getLong(3), cursor.getLong(4), category)
                }
            }
        }
        return results.distinctBy { it.uri.toString() }.sortedByDescending { it.modified }
    }

    private inline fun safely(block: () -> Unit) {
        try { block() } catch (_: SecurityException) { } catch (_: IllegalArgumentException) { }
    }
}
