package app.spasiba.nativeapp2.data.engine

import app.spasiba.nativeapp2.data.api.YouTubeMusicApi
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.player.SmartQueueEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object QuickPicksEngine {

    suspend fun generateQuickPicks(history: List<Song>, likedSongs: List<Song>): List<Song> {
        val seed = (likedSongs + history).distinctBy { it.id }.take(5)
        if (seed.isEmpty()) {
            return YouTubeMusicApi.getHomeRecommendations()
        }

        val topArtist = SmartQueueEngine.getPrimaryArtistName(seed.firstOrNull()?.author ?: "pop music")
        val query = "$topArtist radio mix official audio"
        val results = YouTubeMusicApi.searchSongs(query)

        return if (results.isNotEmpty()) results else YouTubeMusicApi.getFallbackTracks()
    }

    suspend fun generateCategoryFeed(categoryQuery: String): List<Song> {
        val results = YouTubeMusicApi.searchSongs("$categoryQuery top hits official audio")
        return if (results.isNotEmpty()) results else YouTubeMusicApi.getFallbackTracks()
    }

    suspend fun generateRadioQueue(seedSong: Song): List<Song> = coroutineScope {
        // 0. TOP PRIORITY: Live Web App Engine Server Endpoint (spasiba.vercel.app/api/search?relatedTo=)
        val webRelatedSongs = YouTubeMusicApi.fetchWebRelatedSongs(seedSong.id)
        if (webRelatedSongs.size >= 4) {
            return@coroutineScope SmartQueueEngine.appendSmartTracks(
                reference = seedSong,
                baseQueue = listOf(seedSong),
                candidates = webRelatedSongs,
                limit = SmartQueueEngine.UPCOMING_QUEUE_TARGET
            )
        }

        // 1. YouTube Music Official Radio Queue (/youtubei/v1/next)
        val officialRadioTracks = YouTubeMusicApi.fetchYouTubeMusicNextRadioQueue(seedSong.id)
        if (officialRadioTracks.size >= 6) {
            return@coroutineScope SmartQueueEngine.appendSmartTracks(
                reference = seedSong,
                baseQueue = listOf(seedSong),
                candidates = officialRadioTracks,
                limit = SmartQueueEngine.UPCOMING_QUEUE_TARGET
            )
        }

        // 2. Exact audio-controller.ts Web Engine 4-Query Search Bank with Multi-Signal Inferencing
        val signals = SmartQueueEngine.inferQueueSignals(seedSong, listOf(seedSong))
        val queries = SmartQueueEngine.buildQueueSearchBankQueries(seedSong, signals)

        val deferredList = queries.map { q ->
            async { YouTubeMusicApi.searchSongs(q) }
        }
        val banks = deferredList.map { it.await() }

        // Interleave candidate sets from search banks
        val combinedCandidates = mutableListOf<Song>()
        val maxLen = banks.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) {
            for (bank in banks) {
                if (i < bank.size) combinedCandidates.add(bank[i])
            }
        }

        val allCandidates = (officialRadioTracks + combinedCandidates).distinctBy { it.id }

        val finalQueue = SmartQueueEngine.appendSmartTracks(
            reference = seedSong,
            baseQueue = listOf(seedSong),
            candidates = allCandidates,
            limit = SmartQueueEngine.UPCOMING_QUEUE_TARGET
        )

        if (finalQueue.size > 1) finalQueue else listOf(seedSong) + YouTubeMusicApi.getFallbackTracks()
    }
}

