package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(
            value = ["albumId"],
        ),
    ],
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1, // in seconds
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val year: Int? = null,
    val date: LocalDateTime? = null, // ID3 tag property
    val dateModified: LocalDateTime? = null, // file property
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0, // in milliseconds
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = null, // doubles as "isDownloaded"
) {
    fun localToggleLike() = copy(
            liked = !liked,
            inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary
        )
        fun toggleLike() = localToggleLike().also {
            CoroutineScope(Dispatchers.IO).launch() {
                YouTube.likeVideo(id, !liked)
                this.cancel()
            }
        }
    
    fun toggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
        inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary
    )

    fun toggleLibrary() = copy(
        inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
        liked = if (inLibrary == null) liked else false,
        likedDate = if (inLibrary == null) likedDate else null
    )
}
