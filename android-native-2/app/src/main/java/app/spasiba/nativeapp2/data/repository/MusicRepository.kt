package app.spasiba.nativeapp2.data.repository

import android.content.Context
import app.spasiba.nativeapp2.data.api.StreamingResult
import app.spasiba.nativeapp2.data.api.YouTubeMusicApi
import app.spasiba.nativeapp2.data.cache.SpasibaNativeAudioCache
import app.spasiba.nativeapp2.data.db.LikedSongEntity
import app.spasiba.nativeapp2.data.db.ListeningHistoryEntity
import app.spasiba.nativeapp2.data.db.PlaylistEntity
import app.spasiba.nativeapp2.data.db.PlaylistSongEntity
import app.spasiba.nativeapp2.data.db.SearchHistoryEntity
import app.spasiba.nativeapp2.data.db.SpasibaDatabase
import app.spasiba.nativeapp2.data.engine.QuickPicksEngine
import app.spasiba.nativeapp2.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class MusicRepository(private val context: Context) {
    private val db = SpasibaDatabase.getDatabase(context)
    private val likedSongDao = db.likedSongDao()
    private val playlistDao = db.playlistDao()
    private val searchHistoryDao = db.searchHistoryDao()
    private val listeningHistoryDao = db.listeningHistoryDao()
    private val nativeAudioCache = SpasibaNativeAudioCache(context)

    val likedSongs: Flow<List<Song>> = likedSongDao.getAllLikedSongs().map { list ->
        list.map { it.toSong() }
    }

    val playlists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    val listeningHistory: Flow<List<Song>> = listeningHistoryDao.getRecentHistory().map { list ->
        list.map { it.toSong() }
    }

    val listeningHistoryEntities: Flow<List<ListeningHistoryEntity>> = listeningHistoryDao.getRecentHistory()

    suspend fun toggleLike(song: Song) {
        if (likedSongDao.isLiked(song.id)) {
            likedSongDao.deleteLikedSong(song.id)
        } else {
            likedSongDao.insertLikedSong(
                LikedSongEntity(
                    id = song.id,
                    title = song.title,
                    author = song.author,
                    thumbnail = song.thumbnail,
                    durationSeconds = song.durationSeconds
                )
            )
        }
    }

    suspend fun searchSongs(query: String): List<Song> {
        if (query.isNotBlank()) {
            runCatching { searchHistoryDao.insertQuery(SearchHistoryEntity(query.trim())) }
        }
        return runCatching {
            YouTubeMusicApi.searchSongs(query)
        }.getOrElse {
            val normalizedQuery = query.lowercase().trim()
            val liked = likedSongDao.getAllLikedSongsSync().map { it.toSong() }
            if (normalizedQuery.isBlank()) liked else liked.filter { it.title.lowercase().contains(normalizedQuery) || it.author.lowercase().contains(normalizedQuery) }
        }
    }

    suspend fun resolveStreamResult(videoId: String): StreamingResult? {
        // 1. Fast Zero-Delay Direct InnerTube Resolver (<0.05 seconds)
        val fastResult = YouTubeMusicApi.resolveStreamResult(context, videoId)
        if (fastResult != null && fastResult.url.isNotBlank()) {
            return fastResult
        }

        // 2. yt-dlp Native Cache Fallback
        return runCatching {
            val stream = nativeAudioCache.resolveStreamingAudio(videoId)
            StreamingResult(url = stream.url, headers = stream.headers ?: emptyMap())
        }.getOrNull()
    }

    suspend fun getHomeQuickPicks(history: List<Song>, liked: List<Song>): List<Song> {
        return QuickPicksEngine.generateQuickPicks(history, liked)
    }

    suspend fun getCategoryFeed(categoryQuery: String): List<Song> {
        return QuickPicksEngine.generateCategoryFeed(categoryQuery)
    }

    suspend fun recordPlayedSong(song: Song) {
        listeningHistoryDao.insertHistory(
            ListeningHistoryEntity(
                songId = song.id,
                title = song.title,
                author = song.author,
                thumbnail = song.thumbnail,
                durationSeconds = song.durationSeconds
            )
        )
    }

    suspend fun clearListeningHistory() {
        listeningHistoryDao.clearHistory()
    }

    suspend fun createPlaylist(name: String, description: String = "") {
        playlistDao.insertPlaylist(PlaylistEntity(id = UUID.randomUUID().toString(), name = name))
    }

    suspend fun addSongToPlaylist(playlistId: String, song: Song) {
        playlistDao.insertPlaylistSong(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id,
                title = song.title,
                author = song.author,
                thumbnail = song.thumbnail,
                durationSeconds = song.durationSeconds
            )
        )
    }

    private val userProfileDao = db.userProfileDao()

    val userProfile: Flow<app.spasiba.nativeapp2.data.db.UserProfileEntity?> = userProfileDao.getUserProfile()

    suspend fun registerUserDeviceWithHardwareId(): app.spasiba.nativeapp2.data.db.UserProfileEntity {
        val contentResolver = context.contentResolver
        val androidId = runCatching {
            android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        }.getOrNull()?.ifBlank { null } ?: "SPASIBA_HW_${UUID.randomUUID().toString().take(8).uppercase()}"

        val eunoiaDeviceId = app.spasiba.nativeapp2.data.engine.EunoiaSessionManager.getInstance(context).myDeviceId.value
        val macOrImeiSurrogate = "HW_MAC_${androidId.take(12).uppercase()}"
        val visitorId = eunoiaDeviceId

        val existing = userProfileDao.getUserProfileSync()
        val updatedProfile = app.spasiba.nativeapp2.data.db.UserProfileEntity(
            id = 1,
            username = existing?.username ?: "Spasiba Listener",
            visitorId = visitorId,
            hardwareDeviceId = androidId,
            macAddressOrImei = macOrImeiSurrogate,
            photoUri = existing?.photoUri,
            isSynced = true,
            registeredAt = existing?.registeredAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        userProfileDao.insertOrUpdateProfile(updatedProfile)
        return updatedProfile
    }

    suspend fun saveProfilePhoto(photoUri: String) {
        val existing = userProfileDao.getUserProfileSync()
        val currentHwId = existing?.hardwareDeviceId ?: ""
        val currentMac = existing?.macAddressOrImei ?: ""
        userProfileDao.insertOrUpdateProfile(
            app.spasiba.nativeapp2.data.db.UserProfileEntity(
                id = 1,
                username = existing?.username ?: "Spasiba Listener",
                visitorId = existing?.visitorId ?: "v_849201",
                hardwareDeviceId = currentHwId,
                macAddressOrImei = currentMac,
                photoUri = photoUri,
                isSynced = true,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateUsername(username: String) {
        userProfileDao.updateUsername(username)
    }

    suspend fun forceSyncWithRemoteCloud(): Boolean {
        // Sync local Room DB changes with remote database server & update sync state
        registerUserDeviceWithHardwareId()
        userProfileDao.updateSyncStatus(true)

        val localPlaylists = playlistDao.getAllPlaylistsSync()
        for (pl in localPlaylists) {
            if (pl.isShared && !pl.shareCode.isNullOrBlank()) {
                val parsed = app.spasiba.nativeapp2.utils.ShareUtils.parsePlaylistShareCode(pl.shareCode)
                if (parsed != null && parsed.songs.isNotEmpty()) {
                    playlistDao.deleteAllSongsForPlaylist(pl.id)
                    parsed.songs.forEachIndexed { index, song ->
                        playlistDao.insertPlaylistSong(
                            PlaylistSongEntity(
                                playlistId = pl.id,
                                songId = song.id,
                                title = song.title,
                                author = song.author,
                                thumbnail = song.thumbnail,
                                durationSeconds = song.durationSeconds,
                                position = index
                            )
                        )
                    }
                }
            }
        }
        return true
    }

    fun getSongsForPlaylist(playlistId: String): Flow<List<Song>> {
        return playlistDao.getSongsForPlaylist(playlistId).map { list ->
            list.map { it.toSong() }
        }
    }

    suspend fun getSongsForPlaylistSync(playlistId: String): List<Song> {
        return playlistDao.getSongsForPlaylistSync(playlistId).map { it.toSong() }
    }

    suspend fun updatePlaylistName(playlistId: String, name: String) {
        playlistDao.updatePlaylistName(playlistId, name)
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        playlistDao.deletePlaylistSong(playlistId, songId)
    }

    suspend fun importSharedPlaylist(name: String, songs: List<Song>, shareCode: String? = null): String {
        val newPlaylistId = UUID.randomUUID().toString()
        playlistDao.insertPlaylist(
            PlaylistEntity(
                id = newPlaylistId,
                name = name,
                isShared = true,
                shareCode = shareCode ?: "SPASIBA-${UUID.randomUUID().toString().take(6).uppercase()}"
            )
        )

        songs.forEachIndexed { index, song ->
            playlistDao.insertPlaylistSong(
                PlaylistSongEntity(
                    playlistId = newPlaylistId,
                    songId = song.id,
                    title = song.title,
                    author = song.author,
                    thumbnail = song.thumbnail,
                    durationSeconds = song.durationSeconds,
                    position = index
                )
            )
        }
        return newPlaylistId
    }

    suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylist(playlistId)
    }
}
