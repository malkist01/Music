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
            value = ["albumId"]
        )
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1, // in seconds
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val liked: Boolean = false,
    val totalPlayTime: Long = 0, // in milliseconds
    val inLibrary: LocalDateTime? = null,
) {
    fun localToggleLike() = copy(
        liked = !liked
    )

    fun toggleLike() = localToggleLike().also {
        CoroutineScope(Dispatchers.IO).launch() {
            YouTube.likeVideo(id, !liked)
            this.cancel()
        }
    }
}
