package app.spasiba.nativeapp2.data.analytics

import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.player.SmartQueueEngine

object SessionBrainManager {
    private const val RECENT_TRACK_LIMIT = 10

    var intent: String? = null
    var energy: String = "medium"
    val recentTracks = mutableListOf<Song>()
    val artistAffinity = mutableMapOf<String, Int>()
    val bannedArtists = mutableSetOf<String>()
    var playCount: Int = 0
    var skipCount: Int = 0

    @Synchronized
    fun recordPlayStart(song: Song) {
        playCount++
        val artistKey = SmartQueueEngine.getArtistKey(song.author)
        if (artistKey.isNotBlank()) {
            artistAffinity[artistKey] = (artistAffinity[artistKey] ?: 0) + 1
        }
        recentTracks.removeAll { it.id == song.id }
        recentTracks.add(0, song)
        if (recentTracks.size > RECENT_TRACK_LIMIT) {
            recentTracks.removeAt(recentTracks.size - 1)
        }
    }

    @Synchronized
    fun recordSkip(song: Song) {
        skipCount++
        val artistKey = SmartQueueEngine.getArtistKey(song.author)
        if (artistKey.isNotBlank()) {
            val currentScore = (artistAffinity[artistKey] ?: 0) - 2
            artistAffinity[artistKey] = currentScore

            val skipsForArtist = recentTracks.count { SmartQueueEngine.getArtistKey(it.author) == artistKey }
            if (skipsForArtist >= 2) {
                bannedArtists.add(artistKey)
            }
        }
    }

    @Synchronized
    fun recordLike(song: Song) {
        val artistKey = SmartQueueEngine.getArtistKey(song.author)
        if (artistKey.isNotBlank()) {
            artistAffinity[artistKey] = (artistAffinity[artistKey] ?: 0) + 3
            bannedArtists.remove(artistKey)
        }
    }

    @Synchronized
    fun getEngagementFactor(): Double {
        val engagement = (playCount * 0.12) + (skipCount * 0.18) + (artistAffinity.size * 0.05)
        return minOf(1.0, maxOf(0.0, engagement))
    }
}
