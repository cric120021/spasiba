package app.spasiba.nativeapp2.data.analytics

import app.spasiba.nativeapp2.data.db.ListeningHistoryEntity
import app.spasiba.nativeapp2.data.model.Song
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class ListenerStats(
    val totalPlayedSongs: Int,
    val totalListeningMinutes: Long,
    val topArtists: List<Pair<String, Int>>,
    val listeningStreakDays: Int,
    val engagementBadge: String,
    val recentSongs: List<Song>
)

object ListenerProfileEngine {

    fun computeProfileStats(
        history: List<ListeningHistoryEntity>,
        likedSongsCount: Int
    ): ListenerStats {
        if (history.isEmpty()) {
            return ListenerStats(
                totalPlayedSongs = 0,
                totalListeningMinutes = 0L,
                topArtists = emptyList(),
                listeningStreakDays = 0,
                engagementBadge = "Music Explorer",
                recentSongs = emptyList()
            )
        }

        val totalPlayedSongs = history.size

        // Total listening minutes
        val totalSeconds = history.sumOf { it.durationSeconds.toLong() }
        val totalMinutes = TimeUnit.SECONDS.toMinutes(totalSeconds)

        // Top artists count
        val artistCounts = history
            .groupBy { it.author }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        // Streak calculation (consecutive days with plays)
        val uniqueDays = history.map { entity ->
            val cal = Calendar.getInstance().apply { timeInMillis = entity.playedAt }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        }.distinct().size

        // Engagement badge algorithm
        val badge = when {
            totalPlayedSongs >= 100 || likedSongsCount >= 50 -> "Master Connoisseur 🎧"
            totalPlayedSongs >= 40 || likedSongsCount >= 20 -> "Sonic Enthusiast ⚡"
            totalPlayedSongs >= 10 || likedSongsCount >= 5 -> "Rhythm Explorer 🌟"
            else -> "Music Adventurer 🚀"
        }

        return ListenerStats(
            totalPlayedSongs = totalPlayedSongs,
            totalListeningMinutes = totalMinutes,
            topArtists = artistCounts,
            listeningStreakDays = uniqueDays,
            engagementBadge = badge,
            recentSongs = history.take(10).map { it.toSong() }
        )
    }
}
