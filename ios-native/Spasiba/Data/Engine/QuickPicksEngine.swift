import Foundation

public final class QuickPicksEngine {
    public static let shared = QuickPicksEngine()

    private init() {}

    public static func generateQuickPicks(history: [Song], likedSongs: [Song]) async -> [Song] {
        return await shared.generateQuickPicks(history: history, likedSongs: likedSongs)
    }

    public func generateQuickPicks(history: [Song], likedSongs: [Song]) async -> [Song] {
        var seed: [Song] = []
        var seenIds = Set<String>()
        for song in (likedSongs + history) {
            if !seenIds.contains(song.id) {
                seenIds.insert(song.id)
                seed.append(song)
                if seed.count >= 5 { break }
            }
        }

        if seed.isEmpty {
            return await YouTubeMusicApi.getHomeRecommendations()
        }

        let firstAuthor = seed.first?.author ?? "pop music"
        let topArtist = SmartQueueEngine.getPrimaryArtistName(firstAuthor)
        let query = "\(topArtist) radio mix official audio"
        let results = await YouTubeMusicApi.searchSongs(query: query)

        return !results.isEmpty ? results : YouTubeMusicApi.getFallbackTracks()
    }

    public static func generateCategoryFeed(categoryQuery: String) async -> [Song] {
        return await shared.generateCategoryFeed(categoryQuery: categoryQuery)
    }

    public func generateCategoryFeed(categoryQuery: String) async -> [Song] {
        let results = await YouTubeMusicApi.searchSongs(query: "\(categoryQuery) top hits official audio")
        return !results.isEmpty ? results : YouTubeMusicApi.getFallbackTracks()
    }

    public static func generateRadioQueue(seedSong: Song) async -> [Song] {
        return await shared.generateRadioQueue(seedSong: seedSong)
    }

    public func generateRadioQueue(seedSong: Song) async -> [Song] {
        // 0. TOP PRIORITY: Live Web App Engine Server Endpoint (spasiba.vercel.app/api/search?relatedTo=)
        let webRelatedSongs = await YouTubeMusicApi.fetchWebRelatedSongs(videoId: seedSong.id)
        if webRelatedSongs.count >= 4 {
            return SmartQueueEngine.appendSmartTracks(
                reference: seedSong,
                baseQueue: [seedSong],
                candidates: webRelatedSongs,
                limit: SmartQueueEngine.UPCOMING_QUEUE_TARGET
            )
        }

        // 1. YouTube Music Official Radio Queue (/youtubei/v1/next)
        let officialRadioTracks = await YouTubeMusicApi.fetchYouTubeMusicNextRadioQueue(videoId: seedSong.id)
        if officialRadioTracks.count >= 6 {
            return SmartQueueEngine.appendSmartTracks(
                reference: seedSong,
                baseQueue: [seedSong],
                candidates: officialRadioTracks,
                limit: SmartQueueEngine.UPCOMING_QUEUE_TARGET
            )
        }

        // 2. Exact audio-controller.ts Web Engine 4-Query Search Bank with Multi-Signal Inferencing
        let signals = SmartQueueEngine.inferQueueSignals(reference: seedSong, queue: [seedSong])
        let queries = SmartQueueEngine.buildQueueSearchBankQueries(track: seedSong, signals: signals)

        var banks: [[Song]] = []
        await withTaskGroup(of: [Song].self) { group in
            for q in queries {
                group.addTask {
                    return await YouTubeMusicApi.searchSongs(query: q)
                }
            }
            for await songs in group {
                banks.append(songs)
            }
        }

        // Interleave candidate sets from search banks
        var combinedCandidates: [Song] = []
        let maxLen = banks.map { $0.count }.max() ?? 0
        for i in 0..<maxLen {
            for bank in banks {
                if i < bank.count {
                    combinedCandidates.append(bank[i])
                }
            }
        }

        var uniqueCandidates: [Song] = []
        var candidateIds = Set<String>()
        for song in (officialRadioTracks + combinedCandidates) {
            if !candidateIds.contains(song.id) {
                candidateIds.insert(song.id)
                uniqueCandidates.append(song)
            }
        }

        let finalQueue = SmartQueueEngine.appendSmartTracks(
            reference: seedSong,
            baseQueue: [seedSong],
            candidates: uniqueCandidates,
            limit: SmartQueueEngine.UPCOMING_QUEUE_TARGET
        )

        return finalQueue.count > 1 ? finalQueue : ([seedSong] + YouTubeMusicApi.getFallbackTracks())
    }
}
