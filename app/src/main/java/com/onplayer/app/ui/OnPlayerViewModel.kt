package com.onplayer.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.edit
import com.onplayer.app.data.CUSTOM_TITLES_KEY
import com.onplayer.app.data.FAVORITES_KEY
import com.onplayer.app.data.LAST_PLAYED_CATEGORY_KEY
import com.onplayer.app.data.LAST_PLAYED_MIME_KEY
import com.onplayer.app.data.LAST_PLAYED_NAME_KEY
import com.onplayer.app.data.LAST_PLAYED_URI_KEY
import com.onplayer.app.data.MediaCategory
import com.onplayer.app.data.MediaFile
import com.onplayer.app.data.MediaRepository
import com.onplayer.app.data.PLAYLISTS_KEY
import com.onplayer.app.data.THEME_KEY
import com.onplayer.app.data.onPlayerDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class UserPlaylist(
    val id: String,
    val name: String,
    val songUris: List<String>
)

class OnPlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val _files = MutableStateFlow<List<MediaFile>>(emptyList())
    val files: StateFlow<List<MediaFile>> = _files.asStateFlow()
    private val _current = MutableStateFlow<MediaFile?>(null)
    val current: StateFlow<MediaFile?> = _current.asStateFlow()
    private val _lastPlayed = MutableStateFlow<MediaFile?>(null)
    val lastPlayed: StateFlow<MediaFile?> = _lastPlayed.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _playNextRequest = MutableStateFlow<MediaFile?>(null)
    val playNextRequest: StateFlow<MediaFile?> = _playNextRequest.asStateFlow()
    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
    private val _themeMode = MutableStateFlow("Dark")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()
    private val _customTitles = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        viewModelScope.launch {
            app.onPlayerDataStore.data.collect { prefs ->
                _favorites.value = prefs[FAVORITES_KEY].orEmpty()
                    .split("\\n").filter { it.isNotBlank() }.toSet()
                _themeMode.value = prefs[THEME_KEY] ?: "Dark"
                _playlists.value = decodePlaylists(prefs[PLAYLISTS_KEY])
                _customTitles.value = decodeTitles(prefs[CUSTOM_TITLES_KEY])
                _files.value = _files.value.map(::withCustomTitle)
                _current.value = _current.value?.let(::withCustomTitle)
                _lastPlayed.value = _lastPlayed.value?.let(::withCustomTitle)

                val savedUri = prefs[LAST_PLAYED_URI_KEY]
                if (!savedUri.isNullOrBlank()) {
                    val savedName = prefs[LAST_PLAYED_NAME_KEY] ?: "Last played"
                    val savedMime = prefs[LAST_PLAYED_MIME_KEY] ?: "audio/*"
                    val savedCategory = runCatching {
                        MediaCategory.valueOf(prefs[LAST_PLAYED_CATEGORY_KEY] ?: MediaCategory.Audio.name)
                    }.getOrDefault(MediaCategory.Audio)
                    val uri = Uri.parse(savedUri)
                    _lastPlayed.value = _files.value.firstOrNull { it.uri == uri }
                        ?: withCustomTitle(MediaFile(
                            uri.hashCode().toLong(), savedName, uri, savedMime, 0L,
                            System.currentTimeMillis() / 1000L, savedCategory
                        ))
                }
            }
        }
    }

    private fun withCustomTitle(file: MediaFile): MediaFile {
        val title = _customTitles.value[file.uri.toString()] ?: return file
        return file.copy(title = title)
    }

    private fun decodePlaylists(raw: String?): List<UserPlaylist> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val songs = item.optJSONArray("songs") ?: JSONArray()
            UserPlaylist(
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = item.optString("name").ifBlank { "My playlist" },
                songUris = (0 until songs.length()).map { songs.optString(it) }.filter { it.isNotBlank() }
            )
        }
    }.getOrDefault(emptyList())

    private fun encodePlaylists(value: List<UserPlaylist>): String {
        val array = JSONArray()
        value.forEach { playlist ->
            array.put(JSONObject()
                .put("id", playlist.id)
                .put("name", playlist.name)
                .put("songs", JSONArray(playlist.songUris)))
        }
        return array.toString()
    }

    private fun decodeTitles(raw: String?): Map<String, String> = runCatching {
        val obj = JSONObject(raw ?: "{}")
        val result = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            obj.optString(key).takeIf { it.isNotBlank() }?.let { result[key] = it }
        }
        result
    }.getOrDefault(emptyMap())

    private fun persistPlaylists(value: List<UserPlaylist>) {
        viewModelScope.launch {
            getApplication<Application>().onPlayerDataStore.edit {
                it[PLAYLISTS_KEY] = encodePlaylists(value)
            }
        }
    }

    fun scan() = viewModelScope.launch(Dispatchers.IO) {
        _scanning.value = true
        _scanError.value = null
        try {
            val found = repo.scan().map(::withCustomTitle)
            _files.value = found
            _lastPlayed.value?.let { saved ->
                found.firstOrNull { it.uri == saved.uri }?.let { _lastPlayed.value = it }
            }
            if (found.isEmpty()) _scanError.value =
                "No music found. Check audio permission or choose a file manually."
        } catch (e: Exception) {
            _scanError.value = e.message ?: "Could not scan storage."
        } finally {
            _scanning.value = false
        }
    }

    fun open(file: MediaFile) {
        val prepared = withCustomTitle(file)
        _current.value = prepared
        if (prepared.category == MediaCategory.Audio) rememberLastPlayed(prepared)
    }

    fun rememberLastPlayed(file: MediaFile) {
        if (file.category != MediaCategory.Audio) return
        val prepared = withCustomTitle(file)
        _lastPlayed.value = prepared
        viewModelScope.launch {
            getApplication<Application>().onPlayerDataStore.edit { prefs ->
                prefs[LAST_PLAYED_URI_KEY] = prepared.uri.toString()
                prefs[LAST_PLAYED_NAME_KEY] = prepared.name
                prefs[LAST_PLAYED_MIME_KEY] = prepared.mime
                prefs[LAST_PLAYED_CATEGORY_KEY] = prepared.category.name
            }
        }
    }

    fun setPlaybackActive(active: Boolean) { _isPlaying.value = active }

    fun queueNext(file: MediaFile) {
        if (_current.value == null) {
            _current.value = withCustomTitle(file)
            _playNextRequest.value = null
        } else if (_current.value?.uri != file.uri) {
            _playNextRequest.value = withCustomTitle(file)
        }
    }

    fun clearPlayNextRequest(file: MediaFile) {
        if (_playNextRequest.value?.uri == file.uri) _playNextRequest.value = null
    }

    fun toggleFavorite(file: MediaFile) {
        val key = file.uri.toString()
        val next = if (key in _favorites.value) _favorites.value - key else _favorites.value + key
        _favorites.value = next
        viewModelScope.launch {
            getApplication<Application>().onPlayerDataStore.edit {
                it[FAVORITES_KEY] = next.joinToString("\\n")
            }
        }
    }

    fun clearFavorites() {
        _favorites.value = emptySet()
        viewModelScope.launch {
            getApplication<Application>().onPlayerDataStore.edit { it[FAVORITES_KEY] = "" }
        }
    }

    fun createPlaylist(name: String): String? {
        val cleanName = name.trim().take(40)
        if (cleanName.isBlank()) return null
        val id = UUID.randomUUID().toString()
        val next = _playlists.value + UserPlaylist(id, cleanName, emptyList())
        _playlists.value = next
        persistPlaylists(next)
        return id
    }

    fun addToPlaylist(playlistId: String, file: MediaFile) {
        val uri = file.uri.toString()
        val next = _playlists.value.map { playlist ->
            if (playlist.id == playlistId && uri !in playlist.songUris)
                playlist.copy(songUris = playlist.songUris + uri)
            else playlist
        }
        _playlists.value = next
        persistPlaylists(next)
    }

    fun removeFromPlaylist(playlistId: String, file: MediaFile) {
        val uri = file.uri.toString()
        val next = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(songUris = playlist.songUris - uri)
            else playlist
        }
        _playlists.value = next
        persistPlaylists(next)
    }

    fun deletePlaylist(playlistId: String) {
        val next = _playlists.value.filterNot { it.id == playlistId }
        _playlists.value = next
        persistPlaylists(next)
    }

    fun renameTrack(file: MediaFile, newTitle: String) {
        val cleanTitle = newTitle.trim().take(100)
        if (cleanTitle.isBlank()) return
        val key = file.uri.toString()
        val next = _customTitles.value + (key to cleanTitle)
        _customTitles.value = next
        _files.value = _files.value.map { if (it.uri.toString() == key) it.copy(title = cleanTitle) else it }
        _current.value = _current.value?.let { if (it.uri.toString() == key) it.copy(title = cleanTitle) else it }
        _lastPlayed.value = _lastPlayed.value?.let { if (it.uri.toString() == key) it.copy(title = cleanTitle) else it }
        viewModelScope.launch {
            val json = JSONObject()
            next.forEach { (uri, title) -> json.put(uri, title) }
            getApplication<Application>().onPlayerDataStore.edit { it[CUSTOM_TITLES_KEY] = json.toString() }
        }
    }

    fun removeDeletedTrack(file: MediaFile) {
        val key = file.uri.toString()
        _files.value = _files.value.filterNot { it.uri.toString() == key }
        _favorites.value = _favorites.value - key
        val next = _playlists.value.map { it.copy(songUris = it.songUris - key) }
        _playlists.value = next
        persistPlaylists(next)
        viewModelScope.launch {
            val app = getApplication<Application>()
            app.onPlayerDataStore.edit { prefs ->
                prefs[FAVORITES_KEY] = _favorites.value.joinToString("\\n")
                if (prefs[LAST_PLAYED_URI_KEY] == key) {
                    prefs.remove(LAST_PLAYED_URI_KEY)
                    prefs.remove(LAST_PLAYED_NAME_KEY)
                    prefs.remove(LAST_PLAYED_MIME_KEY)
                    prefs.remove(LAST_PLAYED_CATEGORY_KEY)
                }
                val titles = JSONObject()
                _customTitles.value.filterKeys { it != key }.forEach { (uri, title) -> titles.put(uri, title) }
                prefs[CUSTOM_TITLES_KEY] = titles.toString()
            }
        }
        _customTitles.value = _customTitles.value - key
        if (_current.value?.uri == file.uri) _current.value = null
        if (_lastPlayed.value?.uri == file.uri) _lastPlayed.value = null
    }

    fun setTheme(value: String) {
        _themeMode.value = value
        viewModelScope.launch {
            getApplication<Application>().onPlayerDataStore.edit { it[THEME_KEY] = value }
        }
    }

    fun setSearch(value: String) { _search.value = value }
    fun setScanError(message: String?) { _scanError.value = message }
}
