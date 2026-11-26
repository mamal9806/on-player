package com.onplayer.app.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.edit
import com.onplayer.app.data.FAVORITES_KEY
import com.onplayer.app.data.THEME_KEY
import com.onplayer.app.data.MediaFile
import com.onplayer.app.data.MediaRepository
import com.onplayer.app.data.onPlayerDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnPlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val _files = MutableStateFlow<List<MediaFile>>(emptyList())
    val files: StateFlow<List<MediaFile>> = _files.asStateFlow()
    private val _current = MutableStateFlow<MediaFile?>(null)
    val current: StateFlow<MediaFile?> = _current.asStateFlow()
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
    init {
        viewModelScope.launch {
            app.onPlayerDataStore.data.collect { prefs ->
                _favorites.value = prefs[FAVORITES_KEY].orEmpty().split("\\n").filter { it.isNotBlank() }.toSet()
                _themeMode.value = prefs[THEME_KEY] ?: "Dark"
            }
        }
    }
    fun scan() = viewModelScope.launch(Dispatchers.IO) {
        _scanning.value = true
        _scanError.value = null
        try {
            val found = repo.scan()
            _files.value = found
            if (found.isEmpty()) _scanError.value = "No media found. Check storage permissions or use Browse device files."
        } catch (e: Exception) {
            _scanError.value = e.message ?: "Could not scan storage."
        } finally { _scanning.value = false }
    }
    fun open(file: MediaFile) { _current.value = file }

    // A queue request is consumed by the Media3 player screen and inserted
    // immediately after the currently playing item.
    fun queueNext(file: MediaFile) {
        if (_current.value == null) {
            _current.value = file
            _playNextRequest.value = null
        } else if (_current.value?.uri != file.uri) {
            _playNextRequest.value = file
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
            getApplication<Application>().onPlayerDataStore.edit { it[FAVORITES_KEY] = next.joinToString("\\n") }
        }
    }
    fun setTheme(value: String) {
        _themeMode.value = value
        viewModelScope.launch { getApplication<Application>().onPlayerDataStore.edit { it[THEME_KEY] = value } }
    }
    fun setSearch(value: String) { _search.value = value }
    fun setScanError(message: String?) { _scanError.value = message }
}
