package com.onplayer.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.onplayer.app.MainActivity
import com.onplayer.app.R
import com.onplayer.app.data.KNOWN_AUDIO_URIS_KEY
import com.onplayer.app.data.MediaRepository
import com.onplayer.app.data.onPlayerDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

const val ACTION_LIBRARY_CHANGED = "com.onplayer.app.action.LIBRARY_CHANGED"

private const val PERIODIC_SCAN_NAME = "onplayer_fixed_music_library_scan"
private const val SCAN_INTERVAL_MINUTES = 15L
private const val NEW_MUSIC_CHANNEL_ID = "onplayer_new_music"

object AutoLibraryScanScheduler {
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoLibraryScanWorker>(
            SCAN_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_SCAN_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

class AutoLibraryScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!hasAudioPermission()) return@withContext Result.success()

        try {
            val tracks = MediaRepository(applicationContext).scan()
            val currentUris = tracks.mapTo(mutableSetOf()) { it.uri.toString() }
            val preferences = applicationContext.onPlayerDataStore.data.first()
            val knownUris = preferences[KNOWN_AUDIO_URIS_KEY]

            // First run establishes a baseline; it does not spam notifications
            // for the user's existing music collection.
            if (knownUris == null) {
                applicationContext.onPlayerDataStore.edit {
                    it[KNOWN_AUDIO_URIS_KEY] = currentUris
                }
                return@withContext Result.success()
            }

            val newlyAdded = tracks
                .filter { it.uri.toString() !in knownUris }
                .sortedByDescending { it.modified }

            if (newlyAdded.isEmpty()) return@withContext Result.success()

            // Keep discoveries pending until notifications are actually allowed.
            if (!canPostNotifications()) return@withContext Result.success()

            createNotificationChannel()
            newlyAdded.forEachIndexed { index, track ->
                postNewTrackNotification(track.displayTitle, track.displayArtist, track.modified, index)
            }

            applicationContext.onPlayerDataStore.edit {
                it[KNOWN_AUDIO_URIS_KEY] = currentUris
            }

            // Refresh the visible library immediately when the app is open.
            applicationContext.sendBroadcast(
                Intent(ACTION_LIBRARY_CHANGED).setPackage(applicationContext.packageName)
            )
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(applicationContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NEW_MUSIC_CHANNEL_ID,
            "New music found",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when On Player discovers new audio files on your device."
        }
        manager.createNotificationChannel(channel)
    }

    private fun postNewTrackNotification(title: String, artist: String, modifiedSeconds: Long, index: Int) {
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(
            Date(if (modifiedSeconds > 0L) modifiedSeconds * 1000L else System.currentTimeMillis())
        )
        val details = listOf(artist.takeIf { it.isNotBlank() && it != "Unknown artist" }, date)
            .filterNotNull()
            .joinToString(" • ")
        val notificationId = (title + details + index).hashCode().and(0x7fffffff).coerceAtLeast(1)
        val openApp = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, NEW_MUSIC_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("New song found: $title")
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$details"))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }
}
