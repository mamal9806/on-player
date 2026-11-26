package com.onplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

class MediaRepository(private val context: Context) {
    fun scan(): List<MediaFile> {
        val list = mutableListOf<MediaFile>(); val resolver=context.contentResolver
        val audioProjection=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.DISPLAY_NAME,MediaStore.Audio.Media.MIME_TYPE,MediaStore.Audio.Media.SIZE,MediaStore.Audio.Media.DATE_MODIFIED,MediaStore.Audio.Media.DURATION)
        resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,audioProjection,null,null,"${MediaStore.Audio.Media.DATE_MODIFIED} DESC")?.use { c -> while(c.moveToNext()) { val id=c.getLong(0); list+=MediaFile(id,c.getString(1) ?: "Audio",ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id),c.getString(2) ?: "audio/*",c.getLong(3),c.getLong(4),MediaCategory.Audio,c.getLong(5)) } }
        val videoProjection=arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,MediaStore.Video.Media.MIME_TYPE,MediaStore.Video.Media.SIZE,MediaStore.Video.Media.DATE_MODIFIED,MediaStore.Video.Media.DURATION)
        resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,videoProjection,null,null,"${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use { c -> while(c.moveToNext()) { val id=c.getLong(0); list+=MediaFile(id,c.getString(1) ?: "Video",ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,id),c.getString(2) ?: "video/*",c.getLong(3),c.getLong(4),MediaCategory.Videos,c.getLong(5)) } }
        val imageProjection=arrayOf(MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.MIME_TYPE,MediaStore.Images.Media.SIZE,MediaStore.Images.Media.DATE_MODIFIED)
        resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,imageProjection,null,null,"${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use { c -> while(c.moveToNext()) { val id=c.getLong(0); list+=MediaFile(id,c.getString(1) ?: "Image",ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,id),c.getString(2) ?: "image/*",c.getLong(3),c.getLong(4),MediaCategory.Images) } }
        // Document/file scan is intentionally via MediaStore.Files (scoped storage); no unrestricted filesystem access.
        val filesUri=MediaStore.Files.getContentUri("external"); val p=arrayOf(MediaStore.Files.FileColumns._ID,MediaStore.Files.FileColumns.DISPLAY_NAME,MediaStore.Files.FileColumns.MIME_TYPE,MediaStore.Files.FileColumns.SIZE,MediaStore.Files.FileColumns.DATE_MODIFIED)
        resolver.query(filesUri,p,"${MediaStore.Files.FileColumns.MEDIA_TYPE}=0",null,"${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { c -> while(c.moveToNext()) { val name=c.getString(1) ?: continue; val ext=name.substringAfterLast('.',"").lowercase(); val cat=when(ext){ "pdf","doc","docx","txt","rtf","xls","xlsx","ppt","pptx","csv" -> MediaCategory.Documents; "zip","rar","7z","tar","gz" -> MediaCategory.Archives; "apk" -> MediaCategory.APK; else -> MediaCategory.Others }; val id=c.getLong(0); list+=MediaFile(id,name,ContentUris.withAppendedId(filesUri,id),c.getString(2) ?: "application/octet-stream",c.getLong(3),c.getLong(4),cat) } }
        return list.distinctBy { it.uri }.sortedByDescending { it.modified }
    }
}
