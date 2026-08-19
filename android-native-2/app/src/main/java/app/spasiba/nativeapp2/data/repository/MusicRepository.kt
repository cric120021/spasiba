package app.spasiba.nativeapp2.data.repository

import android.content.Context
import android.util.Log
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
            searchHistoryDao.insertQuery(SearchHistoryEntity(query.trim()))
        }
        return YouTubeMusicApi.searchSongs(query)
    }

    suspend fun resolveStreamResult(videoId: String, forceRefresh: Boolean = false): StreamingResult? = withContext(Dispatchers.IO) {
        if (forceRefresh) {
            nativeAudioCache.invalidateStreamCache(videoId)
        }

        // 7-Racer Verified Parallel Race Engine with Live 2-Byte CDN Probing
        YouTubeMusicApi.resolveStreamResult(context, videoId)
    }

    suspend fun resolveDownloadedAudioResult(videoId: String): StreamingResult? = withContext(Dispatchers.IO) {
        runCatching {
            val cached = nativeAudioCache.getOrDownload(videoId)
            StreamingResult(url = cached.file.absolutePath)
        }.onFailure { error ->
            Log.w("MusicRepository", "Native audio download fallback failed for videoId=$videoId", error)
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

    suspend fun createPlaylist(name: String, description: String = ""): PlaylistEntity {
        val newPlaylist = PlaylistEntity(id = UUID.randomUUID().toString(), name = name)
        playlistDao.insertPlaylist(newPlaylist)
        return newPlaylist
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

    private fun uriToBase64(photoUriStr: String): String? {
        return try {
            val uri = android.net.Uri.parse(photoUriStr)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()
            "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBase64ToFileUri(base64Str: String): String? {
        return try {
            val pureBase64 = base64Str.substringAfter("base64,")
            val bytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)
            val destFile = java.io.File(context.filesDir, "profile_photo.jpg")
            java.io.FileOutputStream(destFile).use { it.write(bytes) }
            android.net.Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun registerUserDeviceWithHardwareId(): app.spasiba.nativeapp2.data.db.UserProfileEntity {
        val contentResolver = context.contentResolver
        val androidId = runCatching {
            android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        }.getOrNull()?.ifBlank { null } ?: "SPASIBA_HW_${UUID.randomUUID().toString().take(8).uppercase()}"

        val eunoiaDeviceId = app.spasiba.nativeapp2.data.engine.EunoiaSessionManager.getInstance(context).myDeviceId.value
        val macOrImeiSurrogate = "HW_MAC_${androidId.take(12).uppercase()}"
        val visitorId = eunoiaDeviceId

        val existing = userProfileDao.getUserProfileSync()

        // Sync with Cloud Server upon app start / fresh reinstallation
        var cloudUsername: String? = null
        var cloudPhotoUri: String? = null

        try {
            val client = okhttp3.OkHttpClient.Builder().connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).build()
            val req = okhttp3.Request.Builder()
                .url("https://spasiba.vercel.app/api/user/sync?hardwareId=$androidId")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val json = org.json.JSONObject(bodyStr)
                    cloudUsername = json.optString("username").takeIf { it.isNotBlank() }
                    val b64Photo = json.optString("photoBase64").takeIf { it.isNotBlank() }
                    if (b64Photo != null) {
                        cloudPhotoUri = saveBase64ToFileUri(b64Photo)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("MusicRepository", "Cloud sync fetch note: ${e.message}")
        }

        val deviceModel = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "Spasiba Listener"
        val finalUsername = existing?.username?.takeIf { it.isNotBlank() && it != "Spasiba Listener" }
            ?: cloudUsername?.takeIf { it != "Spasiba Listener" }
            ?: context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE).getString("username", null)?.takeIf { it.isNotBlank() && it != "Spasiba Listener" }
            ?: deviceModel

        val finalPhotoUri = existing?.photoUri?.takeIf { it.isNotBlank() }
            ?: cloudPhotoUri
            ?: context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE).getString("profile_photo_uri", null)

        val updatedProfile = app.spasiba.nativeapp2.data.db.UserProfileEntity(
            id = 1,
            username = finalUsername,
            visitorId = visitorId,
            hardwareDeviceId = androidId,
            macAddressOrImei = macOrImeiSurrogate,
            photoUri = finalPhotoUri,
            isSynced = true,
            registeredAt = existing?.registeredAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        userProfileDao.insertOrUpdateProfile(updatedProfile)

        // Save into prefs for quick access
        context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE).edit()
            .putString("username", finalUsername)
            .apply()

        if (!finalPhotoUri.isNullOrBlank()) {
            context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE).edit()
                .putString("profile_photo_uri", finalPhotoUri)
                .apply()
        }

        // Push profile to cloud DB keyed by both hardware ID & Eunoia ID
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pushClient = okhttp3.OkHttpClient()
                val jsonPayload = org.json.JSONObject().apply {
                    put("hardwareId", androidId)
                    put("visitorId", eunoiaDeviceId)
                    put("username", finalUsername)
                    val b64 = finalPhotoUri?.let { uriToBase64(it) }
                    if (b64 != null) put("photoBase64", b64)
                }
                val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("https://spasiba.vercel.app/api/user/sync")
                    .post(body)
                    .build()
                pushClient.newCall(req).execute().close()
            } catch (e: Exception) {
                android.util.Log.d("MusicRepository", "Cloud registration push note: ${e.message}")
            }
        }

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

        context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE).edit()
            .putString("profile_photo_uri", photoUri)
            .apply()

        // Push photo to Cloud Server
        val b64 = uriToBase64(photoUri)
        if (!b64.isNullOrBlank() && currentHwId.isNotBlank()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val client = OkHttpClient()
                    val jsonPayload = org.json.JSONObject().apply {
                        put("hardwareId", currentHwId)
                        put("username", existing?.username ?: "Spasiba Listener")
                        put("photoBase64", b64)
                    }
                    val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                    val req = Request.Builder()
                        .url("https://spasiba.vercel.app/api/user/sync")
                        .post(body)
                        .build()
                    client.newCall(req).execute()
                } catch (e: Exception) {
                    android.util.Log.e("MusicRepository", "Cloud photo push error: ${e.message}")
                }
            }
        }
    }

    suspend fun updateUsername(username: String) {
        userProfileDao.updateUsername(username)
        context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE).edit()
            .putString("username", username)
            .apply()

        val existing = userProfileDao.getUserProfileSync()
        val hwId = existing?.hardwareDeviceId ?: ""
        if (hwId.isNotBlank()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val client = OkHttpClient()
                    val jsonPayload = org.json.JSONObject().apply {
                        put("hardwareId", hwId)
                        put("username", username)
                    }
                    val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                    val req = Request.Builder()
                        .url("https://spasiba.vercel.app/api/user/sync")
                        .post(body)
                        .build()
                    client.newCall(req).execute()
                } catch (e: Exception) {
                    android.util.Log.e("MusicRepository", "Cloud username push error: ${e.message}")
                }
            }
        }
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
