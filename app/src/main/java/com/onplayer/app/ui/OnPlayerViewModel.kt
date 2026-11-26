package com.onplayer.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onplayer.app.data.MediaFile
import com.onplayer.app.data.MediaRepository
import com.onplayer.app.data.onPlayerDataStore
import com.onplayer.app.data.FAVORITES_KEY
import com.onplayer.app.data.THEME_KEY
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnPlayerViewModel(app:Application):AndroidViewModel(app){
    private val repo=MediaRepository(app)
    init { viewModelScope.launch { app.onPlayerDataStore.data.collect { prefs ->
        _favorites.value = prefs[FAVORITES_KEY].orEmpty().split("\n").filter{it.isNotBlank()}.toSet()
        _themeMode.value = prefs[THEME_KEY] ?: "Dark"
    } } }
    private val _files=MutableStateFlow<List<MediaFile>>(emptyList());val files:StateFlow<List<MediaFile>> = _files.asStateFlow()
    private val _current=MutableStateFlow<MediaFile?>(null);val current:StateFlow<MediaFile?> = _current.asStateFlow()
    private val _search=MutableStateFlow("");val search:StateFlow<String> = _search.asStateFlow()
    private val _scanning=MutableStateFlow(false);val scanning:StateFlow<Boolean> = _scanning.asStateFlow()
    private val _favorites=MutableStateFlow<Set<String>>(emptySet());val favorites:StateFlow<Set<String>> = _favorites.asStateFlow()
    private val _themeMode=MutableStateFlow("Dark");val themeMode:StateFlow<String> = _themeMode.asStateFlow()
    fun scan(){viewModelScope.launch(Dispatchers.IO){_scanning.value=true;try{_files.value=repo.scan()}catch(_:Exception){}finally{_scanning.value=false}}}
    fun open(file:MediaFile){_current.value=file}
    fun toggleFavorite(file:MediaFile){val key=file.uri.toString();val next=if(key in _favorites.value)_favorites.value-key else _favorites.value+key;_favorites.value=next;viewModelScope.launch{getApplication<Application>().onPlayerDataStore.edit{it[FAVORITES_KEY]=next.joinToString("\n")}}}
    fun setTheme(s:String){_themeMode.value=s;viewModelScope.launch{getApplication<Application>().onPlayerDataStore.edit{it[THEME_KEY]=s}}}
    fun setSearch(s:String){_search.value=s}
}
