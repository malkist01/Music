package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.AlbumUtils
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.HomeAlbumRecommendation
import com.metrolist.innertube.pages.HomeArtistRecommendation
import com.metrolist.innertube.pages.HomePlayList
import com.metrolist.music.constants.QuickPicks
import com.metrolist.music.constants.QuickPicksKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        val database: MusicDatabase,
    ) : ViewModel() {
        val isRefreshing = MutableStateFlow(false)
        val isLoading = MutableStateFlow(false)
        private val quickPicksEnum =
            context.dataStore.data
                .map {
                    it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
                }.distinctUntilChanged()

        val quickPicks = MutableStateFlow<List<Song>?>(null)
        val explorePage = MutableStateFlow<ExplorePage?>(null)

        val forgottenFavorite = MutableStateFlow<List<Song>?>(null)
        val home = MutableStateFlow<List<HomePlayList>?>(null)
        val keepListeningSongs = MutableStateFlow<List<Song>?>(null)
        val keepListeningAlbums = MutableStateFlow<List<Song>?>(null)
        val keepListeningArtists = MutableStateFlow<List<Artist>?>(null)

        val keepListening = MutableStateFlow<List<Int>?>(null)

        private val continuation = MutableStateFlow<String?>(null)
        val homeFirstContinuation = MutableStateFlow<List<HomePlayList>?>(null)
        val homeSecondContinuation = MutableStateFlow<List<HomePlayList>?>(null)
        val homeThirdContinuation = MutableStateFlow<List<HomePlayList>?>(null)

        private val songsAlbumRecommendation = MutableStateFlow<List<Song>?>(null)
        val homeFirstAlbumRecommendation = MutableStateFlow<HomeAlbumRecommendation?>(null)
        val homeSecondAlbumRecommendation = MutableStateFlow<HomeAlbumRecommendation?>(null)

        private val artistRecommendation = MutableStateFlow<List<Artist>?>(null)
        val homeFirstArtistRecommendation = MutableStateFlow<HomeArtistRecommendation?>(null)
        val homeSecondArtistRecommendation = MutableStateFlow<HomeArtistRecommendation?>(null)
        val homeThirdArtistRecommendation = MutableStateFlow<HomeArtistRecommendation?>(null)

        val youtubePlaylists = MutableStateFlow<List<PlaylistItem>?>(null)

        private suspend fun getQuickPicks() {
            when (quickPicksEnum.first()) {
                QuickPicks.QUICK_PICKS ->
                    quickPicks.value =
                        database
                            .quickPicks()
                            .first()
                            .shuffled()
                            .take(20)
                QuickPicks.LAST_LISTEN -> songLoad()
            }
        }

        private suspend fun load() {
            isLoading.value = true
            
            getQuickPicks()
            val artists =
                database
                    .mostPlayedArtists(System.currentTimeMillis() - 86400000 * 7 * 2)
                    .first()
                    .shuffled()
                    .take(5)
            val filteredArtists = mutableListOf<Artist>()
            artists.forEach {
                if (it.artist.isYouTubeArtist) {
                    filteredArtists.add(it)
                }
            }
            keepListeningArtists.value = filteredArtists
            keepListeningAlbums.value =
                database
                    .getRecommendationAlbum(limit = 8, offset = 2)
                    .first()
                    .shuffled()
                    .take(5)
            keepListeningSongs.value =
                database
                    .mostPlayedSongs(System.currentTimeMillis() - 86400000 * 7 * 2, limit = 15, offset = 5)
                    .first()
                    .shuffled()
                    .take(10)
            val listenAgainBuilder = mutableListOf<Int>()
            var index = 0
            keepListeningArtists.value?.forEach { _ ->
                listenAgainBuilder.add(index)
                index += 1
            }
            index = 5
            keepListeningAlbums.value?.forEach { _ ->
                listenAgainBuilder.add(index)
                index += 1
            }
            index = 10
            keepListeningSongs.value?.forEach { _ ->
                listenAgainBuilder.add(index)
                index += 1
            }
            keepListening.value = listenAgainBuilder.shuffled()
            songsAlbumRecommendation.value =
                database
                    .getRecommendationAlbum(limit = 10)
                    .first()
                    .shuffled()
                    .take(2)

            artistRecommendation.value =
                database
                    .mostPlayedArtists(System.currentTimeMillis() - 86400000 * 7, limit = 10)
                    .first()
                    .shuffled()
                    .take(3)

            viewModelScope.launch {
                YouTube
                    .likedPlaylists()
                    .onSuccess {
                        youtubePlaylists.value = it
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
    
        private suspend fun homeLoad() {
            isLoading.value = true
            
            YouTube
                .home()
                .onSuccess { res ->
                    res.getOrNull(1)?.continuation?.let {
                        continuation.value = it
                    }
                    home.value = res
                }.onFailure {
                    reportException(it)
                }
            continuationsLoad()
        }

        private suspend fun continuation(
            continuationVal: String?,
            next: MutableStateFlow<List<HomePlayList>?>,
        ) {
            isLoading.value = true
            
            continuationVal?.run {
                YouTube
                    .browseContinuation(this)
                    .onSuccess { res ->
                        res.firstOrNull()?.continuation?.let {
                            continuation.value = it
                        }
                        next.value = res
                    }.onFailure {
                        reportException(it)
                    }
            }
        }

        private suspend fun continuationsLoad() {
            isLoading.value = true
            
            artistLoad(artistRecommendation.value?.getOrNull(0), homeFirstArtistRecommendation)
            forgottenFavorite.value =
                database
                    .forgottenFavorites()
                    .first()
                    .shuffled()
                    .take(20)
            continuation.value?.run {
                continuation(this, homeFirstContinuation)
            }
            albumLoad(songsAlbumRecommendation.value?.getOrNull(0), homeFirstAlbumRecommendation)

            continuation.value?.run {
                continuation(this, homeSecondContinuation)
            }
            artistLoad(artistRecommendation.value?.getOrNull(1), homeSecondArtistRecommendation)

            continuation.value?.run {
                continuation(this, homeThirdContinuation)
            }
            albumLoad(songsAlbumRecommendation.value?.getOrNull(1), homeSecondAlbumRecommendation)

            artistLoad(artistRecommendation.value?.getOrNull(2), homeThirdArtistRecommendation)
        }

        private suspend fun songLoad() {
            isLoading.value = true
            
            val song =
                database
                    .events()
                    .first()
                    .firstOrNull()
                    ?.song
            if (song != null) {
                if (database.hasRelatedSongs(song.id)) {
                    val relatedSongs =
                        database
                            .getRelatedSongs(song.id)
                            .first()
                            .shuffled()
                            .take(20)
                    quickPicks.value = relatedSongs
                }
            }
        }

        private suspend fun albumLoad(
            song: Song?,
            next: MutableStateFlow<HomeAlbumRecommendation?>,
        ) {
            isLoading.value = true
            
            val albumUtils = AlbumUtils(song?.song?.albumName, song?.song?.thumbnailUrl)
            YouTube.next(WatchEndpoint(videoId = song?.id)).onSuccess { res ->
                YouTube
                    .recommendAlbum(res.relatedEndpoint!!.browseId, albumUtils)
                    .onSuccess { page ->
                        next.value =
                            page.copy(
                                albums = page.albums,
                            )
                    }.onFailure {
                        reportException(it)
                    }
            }
        }

        private suspend fun artistLoad(
            artist: Artist?,
            next: MutableStateFlow<HomeArtistRecommendation?>,
        ) {
            isLoading.value = true
            
            val listItem = mutableListOf<YTItem>()
            artist?.id?.let {
                YouTube.artist(it).onSuccess { res ->
                    res.sections.getOrNull(res.sections.size - 2)?.items?.forEach { item ->
                        listItem.add(item)
                    }
                    res.sections.lastOrNull()?.items?.forEach { item ->
                        listItem.add(item)
                    }
                }
            }
            if (artist != null) {
                next.value =
                    HomeArtistRecommendation(
                        listItem = listItem.shuffled().take(9),
                        artistName = artist.artist.name,
                    )
            }
        }

        fun refresh() {
            if (isRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                isRefreshing.value = true
                load()
                isRefreshing.value = false
            }
            viewModelScope.launch(Dispatchers.IO) {
                homeLoad()
            }
        }

        init {
            viewModelScope.launch(Dispatchers.IO) {
                val mostPlayedArtists = database.mostPlayedArtists(System.currentTimeMillis() - 86400000 * 7 * 2)
                viewModelScope.launch {
                    mostPlayedArtists.collect { artists ->
                        artists
                            .map { it.artist }
                            .filter {
                                it.thumbnailUrl == null
                            }.forEach { artist ->
                                YouTube.artist(artist.id).onSuccess { artistPage ->
                                    database.query {
                                        update(artist, artistPage)
                                    }
                                }
                            }
                    }
                }
            }
            viewModelScope.launch(Dispatchers.IO) {
                isRefreshing.value = true
                load()
                isRefreshing.value = false
            }
            viewModelScope.launch(Dispatchers.IO) {
                homeLoad()
            }
        }
    }
