package com.onplayer.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.onPlayerDataStore by preferencesDataStore(name = "onplayer_preferences")
val FAVORITES_KEY = stringPreferencesKey("favorites")
val THEME_KEY = stringPreferencesKey("theme")

val LAST_PLAYED_URI_KEY = stringPreferencesKey("last_played_uri")
val LAST_PLAYED_NAME_KEY = stringPreferencesKey("last_played_name")
val LAST_PLAYED_MIME_KEY = stringPreferencesKey("last_played_mime")
val LAST_PLAYED_CATEGORY_KEY = stringPreferencesKey("last_played_category")
