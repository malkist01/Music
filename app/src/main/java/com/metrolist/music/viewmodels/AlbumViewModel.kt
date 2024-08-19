package com.metrolist.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel
    @Inject
    constructor(
        database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val albumId = savedStateHandle.get<String>("albumId")!!
        val albumWithSongs =
            database
                .albumWithSongs(albumId)
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)
        var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())

        init {
            viewModelScope.launch {
                val album = database.album(albumId).first()
                YouTube
                    .album(albumId)
                    .onSuccess {
                        otherVersions.value = it.album.otherVersions
                        database.transaction {
                            if (album == null) {
                                insert(it)
                            } else {
                                println(it.album.artists)
                                update(album.album, it, album.artists)
                            }
                        }
                    }.onFailure {
                        reportException(it)
                        if (it.message?.contains("NOT_FOUND") == true) {
                            database.query {
                                album?.album?.let(::delete)
                            }
                        }
                    }
            }
        }
    }
