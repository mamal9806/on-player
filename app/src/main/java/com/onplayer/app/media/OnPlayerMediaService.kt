package com.onplayer.app.media

import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Media playback service foundation.
 * A later UI integration must connect MediaController to this service
 * and replace the Activity-owned ExoPlayer to prevent duplicate playback.
 */
class OnPlayerMediaService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        mediaSession = MediaSession.Builder(this, exo).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY_URI) {
            val rawUri = intent.getStringExtra(EXTRA_URI)
            val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
            if (!rawUri.isNullOrBlank()) {
                val item = MediaItem.Builder()
                    .setUri(Uri.parse(rawUri))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                    .build()
                player?.setMediaItem(item)
                player?.prepare()
                player?.play()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_URI = "com.onplayer.app.media.PLAY_URI"
        const val EXTRA_URI = "media_uri"
        const val EXTRA_TITLE = "media_title"
    }
}
