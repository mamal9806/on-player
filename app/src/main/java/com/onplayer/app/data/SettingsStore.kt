package com.onplayer.app.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.onPlayerDataStore by preferencesDataStore(name = "onplayer_preferences")
val FAVORITES_KEY = stringPreferencesKey("favorites")
val THEME_KEY = stringPreferencesKey("theme")
val PLAYLISTS_KEY = stringPreferencesKey("playlists_json")
val CUSTOM_TITLES_KEY = stringPreferencesKey("custom_track_titles_json")

val LAST_PLAYED_URI_KEY = stringPreferencesKey("last_played_uri")
val LAST_PLAYED_NAME_KEY = stringPreferencesKey("last_played_name")
val LAST_PLAYED_MIME_KEY = stringPreferencesKey("last_played_mime")
val LAST_PLAYED_CATEGORY_KEY = stringPreferencesKey("last_played_category")

// URIs known by the fixed background audio-library scanner.
val KNOWN_AUDIO_URIS_KEY = stringSetPreferencesKey("known_audio_uris")
