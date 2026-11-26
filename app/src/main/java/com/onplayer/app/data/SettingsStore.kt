package com.onplayer.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.onPlayerDataStore by preferencesDataStore(name = "onplayer_preferences")
val FAVORITES_KEY = stringPreferencesKey("favorites")
val THEME_KEY = stringPreferencesKey("theme")
