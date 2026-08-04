package app.spasiba.nativeapp2.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.spasiba.nativeapp2.data.model.Song

@Entity(tableName = "liked_songs", indices = [Index(value = ["addedAt"])])
data class LikedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val durationSeconds: Int,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSong() = Song(id, title, author, thumbnail, durationSeconds, isLiked = true)
}

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val username: String = "Spasiba Listener",
    val visitorId: String = "v_849201",
    val hardwareDeviceId: String = "",
    val macAddressOrImei: String = "",
    val photoUri: String? = null,
    val isSynced: Boolean = true,
    val registeredAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isShared: Boolean = false,
    val shareCode: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"], indices = [Index(value = ["playlistId"]), Index(value = ["songId"])])
data class PlaylistSongEntity(
    val playlistId: String,
    val songId: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val durationSeconds: Int,
    val position: Int = 0
) {
    fun toSong() = Song(songId, title, author, thumbnail, durationSeconds)
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "listening_history", indices = [Index(value = ["playedAt"])])
data class ListeningHistoryEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val durationSeconds: Int,
    val playedAt: Long = System.currentTimeMillis()
) {
    fun toSong() = Song(songId, title, author, thumbnail, durationSeconds)
}
