import Foundation

public enum RepeatMode: String, CaseIterable {
    case off = "OFF"
    case all = "ALL"
    case one = "ONE"
}

public final class SmartQueueEngine: ObservableObject {
    public static let shared = SmartQueueEngine()

    @Published public private(set) var queue: [Song] = []
    @Published public private(set) var currentIndex: Int = -1
    @Published public var isShuffleEnabled: Bool = false
    @Published public var repeatMode: RepeatMode = .off

    private var originalQueue: [Song] = []

    private init() {}

    public var currentSong: Song? {
        guard currentIndex >= 0 && currentIndex < queue.count else { return nil }
        return queue[currentIndex]
    }

    public var hasNext: Bool {
        if repeatMode == .all || repeatMode == .one { return true }
        return currentIndex + 1 < queue.count
    }

    public var hasPrevious: Bool {
        return currentIndex > 0
    }

    public func setQueue(_ newQueue: [Song], startIndex: Int = 0) {
        self.originalQueue = newQueue
        if isShuffleEnabled {
            var shuffled = newQueue
            if startIndex < newQueue.count {
                let startSong = newQueue[startIndex]
                shuffled.remove(at: startIndex)
                shuffled.shuffle()
                shuffled.insert(startSong, at: 0)
                self.queue = shuffled
                self.currentIndex = 0
            } else {
                shuffled.shuffle()
                self.queue = shuffled
                self.currentIndex = 0
            }
        } else {
            self.queue = newQueue
            self.currentIndex = max(0, min(startIndex, newQueue.count - 1))
        }
    }

    public func setCurrentIndex(_ index: Int) {
        guard index >= 0 && index < queue.count else { return }
        self.currentIndex = index
    }

    public func appendTracks(_ tracks: [Song]) {
        var current = queue
        let seenIds = Set(current.map { $0.id })
        let newUnique = tracks.filter { !seenIds.contains($0.id) }
        guard !newUnique.isEmpty else { return }
        current.append(contentsOf: newUnique)
        self.queue = Array(current.prefix(80))
        self.originalQueue = self.queue
    }

    public func addNext(_ song: Song) {
        if queue.isEmpty {
            setQueue([song])
            return
        }
        let insertIndex = min(currentIndex + 1, queue.count)
        queue.insert(song, at: insertIndex)
    }

    public func addToQueue(_ song: Song) {
        queue.append(song)
    }

    public func advanceNext() -> Song? {
        guard !queue.isEmpty else { return nil }

        if repeatMode == .one {
            return currentSong
        }

        if currentIndex + 1 < queue.count {
            currentIndex += 1
            return queue[currentIndex]
        } else if repeatMode == .all {
            currentIndex = 0
            return queue.first
        }
        return nil
    }

    public func retreatPrevious() -> Song? {
        guard !queue.isEmpty else { return nil }

        if currentIndex > 0 {
            currentIndex -= 1
            return queue[currentIndex]
        }
        return queue.first
    }

    public func toggleShuffle() {
        isShuffleEnabled.toggle()
        guard let current = currentSong else { return }

        if isShuffleEnabled {
            var remaining = queue.filter { $0.id != current.id }
            remaining.shuffle()
            queue = [current] + remaining
            currentIndex = 0
        } else {
            queue = originalQueue
            if let idx = queue.firstIndex(where: { $0.id == current.id }) {
                currentIndex = idx
            }
        }
    }

    public func cycleRepeatMode() {
        switch repeatMode {
        case .off: repeatMode = .all
        case .all: repeatMode = .one
        case .one: repeatMode = .off
        }
    }
}

// MARK: - Queue Signal

public enum QueueSignal: String, CaseIterable, Hashable {
    case bollywood = "BOLLYWOOD"
    case englishGlobal = "ENGLISH_GLOBAL"
    case punjabi = "PUNJABI"
    case lofiChill = "LOFI_CHILL"
    case loveRomantic = "LOVE_ROMANTIC"
    case sadEmotional = "SAD_EMOTIONAL"
    case highEnergyWorkout = "HIGH_ENERGY_WORKOUT"
    case trendingViral = "TRENDING_VIRAL"
    case indieNonFilm = "INDIE_NON_FILM"
}

// MARK: - Recommendation & Smart Queue Algorithms Extension

extension SmartQueueEngine {
    public static let UPCOMING_QUEUE_TARGET = 10
    public static let SMART_QUEUE_ARTIST_CAP = 2
    public static let MIN_QUEUE_TRACK_DURATION_SECONDS = 75
    public static let MAX_QUEUE_TRACK_DURATION_SECONDS = 480 // 8 mins
    public static let QUEUE_DURATION_MULTIPLIER = 2.15

    private static let songStopWords: Set<String> = [
        "the", "and", "for", "with", "from", "your", "you", "this", "that",
        "into", "onto", "over", "under", "official", "music", "audio", "video", "song"
    ]

    private static let signalQueryTemplates: [QueueSignal: [String]] = [
        .bollywood: ["bollywood official audio", "hindi film songs official audio", "romantic bollywood official audio"],
        .englishGlobal: ["pop official audio", "global pop official audio", "english pop anthems official audio"],
        .punjabi: ["punjabi official audio", "punjabi pop official audio", "punjabi romantic official audio"],
        .lofiChill: ["lofi official audio", "chill pop official audio", "soft chill official audio"],
        .loveRomantic: ["romantic official audio", "love songs official audio", "soft pop official audio"],
        .sadEmotional: ["sad official audio", "heartbreak official audio", "emotional pop official audio"],
        .highEnergyWorkout: ["workout official audio", "high energy pop official audio", "party anthem official audio"],
        .trendingViral: ["chart hits official audio", "viral hits official audio", "top pop official audio"],
        .indieNonFilm: ["indie official audio", "alt pop official audio", "independent pop official audio"]
    ]

    private static let relatedArtistMap: [String: Set<String>] = [
        "arijitsingh": ["jubinnautiyal", "atifaslam", "pritam", "shreyaghoshal", "darshanraval", "mohitchauhan", "vishalmishra", "sachinjigar", "bpraak"],
        "jubinnautiyal": ["arijitsingh", "atifaslam", "darshanraval", "vishalmishra", "tulsikumar", "nehakakkar"],
        "atifaslam": ["arijitsingh", "rahatfatehalikhan", "mohitchauhan", "mustafazahid", "kk"],
        "diljitdosanjh": ["karanaujla", "shubh", "apdhillon", "sidhumoosewala", "gururandhawa", "jassmanak", "harnoor", "jassiegill"],
        "karanaujla": ["diljitdosanjh", "shubh", "apdhillon", "sidhumoosewala", "badshah", "divine"],
        "shubh": ["karanaujla", "apdhillon", "diljitdosanjh", "sidhumoosewala"],
        "theweeknd": ["drake", "postmalone", "dualipa", "brunomars", "taylorswift", "sza", "khalid", "traviscott", "justinbieber"],
        "drake": ["theweeknd", "21savage", "postmalone", "traviscott", "future", "kanyewest", "kendricklamar"],
        "taylorswift": ["gracieabrams", "oliviarodrigo", "lanadelrey", "sabrinacarpenter", "dualipa", "edsheeran", "shawnmendes"],
        "coldplay": ["onerepublic", "imaginedragons", "thechainsmokers", "maroon5", "lumineers", "bastille"],
        "bts": ["jungkook", "blackpink", "straykids", "twice", "exo", "newjeans", "txt", "seventeen"],
        "pritam": ["arijitsingh", "shreyaghoshal", "vishalshekhar", "amittrivedi", "sanjoy"],
        "badshah": ["honeysingh", "raftaar", "divine", "karanaujla", "mcstan", "ikka"]
    ]

    private static func regexMatches(_ pattern: String, in text: String) -> Bool {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return false }
        let range = NSRange(location: 0, length: text.utf16.count)
        return regex.firstMatch(in: text, options: [], range: range) != nil
    }

    private static func regexReplace(_ pattern: String, in text: String, with replacement: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return text }
        let range = NSRange(location: 0, length: text.utf16.count)
        return regex.stringByReplacingMatches(in: text, options: [], range: range, withTemplate: replacement)
    }

    public static func stripTitleNoise(_ value: String) -> String {
        var str = regexReplace("\\([^)]*\\)", in: value, with: " ")
        str = regexReplace("\\[[^\\]]*\\]", in: str, with: " ")
        str = regexReplace("\\b(official|video|music|lyrics?|lyric|audio|hd|4k|visualizer|feat\\.?|ft\\.?|remix|live|version|slowed|reverb|cover|karaoke|shorts?|sped\\s*up|bass\\s*boosted|instrumental)\\b", in: str, with: " ")
        str = regexReplace("[|:/\\\\-]+", in: str, with: " ")
        str = regexReplace("\\s+", in: str, with: " ")
        return str.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public static func normalizeToken(_ value: String) -> String {
        let lower = value.lowercased()
        return regexReplace("[^a-z0-9]", in: lower, with: "")
    }

    public static func getMeaningfulSongTokens(_ value: String) -> [String] {
        return stripTitleNoise(value)
            .components(separatedBy: " ")
            .map { normalizeToken($0) }
            .filter { $0.count > 1 && !songStopWords.contains($0) }
    }

    public static func getPrimaryArtistName(_ author: String) -> String {
        let stripped = stripTitleNoise(author)
        let splitParts = stripped.components(separatedBy: CharacterSet(charactersIn: ",&/"))
        let primary = splitParts.first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? author.trimmingCharacters(in: .whitespacesAndNewlines)
        return !primary.isEmpty ? primary : author.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public static func getArtistKey(_ author: String) -> String {
        let primary = getPrimaryArtistName(author)
        return normalizeSongTitle(!primary.isEmpty ? primary : author)
    }

    public static func normalizeTitle(_ title: String) -> String {
        return normalizeSongTitle(title)
    }

    public static func normalizeSongTitle(_ value: String) -> String {
        return getMeaningfulSongTokens(value).joined(separator: " ")
    }

    public static func getTrackIdentityKey(_ track: Song) -> String {
        return "\(getArtistKey(track.author))::\(normalizeSongTitle(track.title))"
    }

    public static func getTitleSimilarity(_ leftTitle: String, _ rightTitle: String) -> Double {
        let leftTokens = getMeaningfulSongTokens(leftTitle)
        let rightTokens = getMeaningfulSongTokens(rightTitle)
        if leftTokens.isEmpty || rightTokens.isEmpty { return 0.0 }

        let rightSet = Set(rightTokens)
        let shared = leftTokens.filter { rightSet.contains($0) }.count
        return Double(shared) / Double(min(leftTokens.count, rightTokens.count))
    }

    public static func detectSignals(_ track: Song) -> Set<QueueSignal> {
        let text = "\(track.title) \(track.author)".lowercased()
        var signals = Set<QueueSignal>()

        if regexMatches("[\\u0A00-\\u0A7F]|\\b(punjabi|diljit|aujla|shubh|sidhu|dhillon|dosanjh|randhawa|punjab)\\b", in: text) {
            signals.insert(.punjabi)
        } else if regexMatches("[\\u0900-\\u097F]|\\b(bollywood|hindi|arijit|jubin|shreya|atif|darshan|vishal|neha|tulsi|mohit|pritam)\\b", in: text) {
            signals.insert(.bollywood)
        } else {
            signals.insert(.englishGlobal)
        }

        if regexMatches("\\b(lofi|chill|beats?|ambient|study|focus|sleep|instrumental)\\b", in: text) {
            signals.insert(.lofiChill)
        }
        if regexMatches("\\b(love|romantic|romance|heart|ishq|pyaar|pyar|mohabbat|dil)\\b", in: text) {
            signals.insert(.loveRomantic)
        }
        if regexMatches("\\b(sad|heartbreak|breakup|broken|alone|emotional|pain|cry)\\b", in: text) {
            signals.insert(.sadEmotional)
        }
        if regexMatches("\\b(workout|gym|run|running|energy|party|hype|pump|motivation)\\b", in: text) {
            signals.insert(.highEnergyWorkout)
        }
        if regexMatches("\\b(indie|acoustic|non film|alt|alternative|independent)\\b", in: text) {
            signals.insert(.indieNonFilm)
        }
        if regexMatches("\\b(trending|viral|hits?|top|chart|popular)\\b", in: text) || track.views > 100_000_000 {
            signals.insert(.trendingViral)
        }

        return signals
    }

    public static func inferQueueSignals(reference: Song, queue: [Song]) -> Set<QueueSignal> {
        var counts: [QueueSignal: Int] = [:]
        let candidateTracks = [reference] + queue.filter { $0.id != reference.id }.prefix(6)

        for candidate in candidateTracks {
            for signal in detectSignals(candidate) {
                counts[signal, default: 0] += 1
            }
        }

        var inferredSignals = detectSignals(reference)
        let rankedSignals = counts.sorted { $0.value > $1.value }.map { $0.key }

        for signal in rankedSignals {
            if (counts[signal] ?? 0) >= 2 || inferredSignals.count < 2 {
                inferredSignals.insert(signal)
            }
            if inferredSignals.count >= 3 { break }
        }

        return inferredSignals
    }

    public static func getRecommendedQueueDurationCap(reference: Song? = nil) -> Int {
        guard let ref = reference, ref.durationSeconds > 0 else {
            return MAX_QUEUE_TRACK_DURATION_SECONDS
        }
        return max(210, min(MAX_QUEUE_TRACK_DURATION_SECONDS, Int(Double(ref.durationSeconds) * QUEUE_DURATION_MULTIPLIER)))
    }

    public static func isQueueableTrack(_ candidate: Song, reference: Song? = nil) -> Bool {
        let noisyText = "\(candidate.title) \(candidate.author)"

        let lowQualityPattern = "\\b(reels?|shorts?|status|whatsapp|tiktok|capcut|edit\\s*audio|dialogue|scene|meme|template|ringtone|interview|podcast|episode|reaction|review|trailer|teaser|speech|lecture|news|vlog|roast|prank|full\\s*movie|behind\\s*the\\s*scenes|making\\s*of|mashup|mix|mega\\s*mix|playlist|jukebox|non[\\s-]*stop|best\\s+of|hour|hours|loop)\\b"
        if regexMatches(lowQualityPattern, in: noisyText) { return false }

        let genericCompilationPattern = "\\b(top|best|latest|new|global|hollywood|viral|trending|hits?)\\b.*\\b(song|songs|music|playlist|list)\\b|\\b(song|songs)\\s*list\\b|\\bnew\\s+english\\s+song\\b"
        if regexMatches(genericCompilationPattern, in: candidate.title) || regexMatches(genericCompilationPattern, in: candidate.author) { return false }

        let dur = candidate.durationSeconds
        if dur > 0 && dur < MIN_QUEUE_TRACK_DURATION_SECONDS { return false }
        if dur > getRecommendedQueueDurationCap(reference: reference) { return false }

        let normTitle = normalizeTitle(candidate.title)
        let normAuthor = normalizeTitle(candidate.author)
        let wordCount = normTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0 : normTitle.components(separatedBy: " ").count
        let musicIdentityPattern = "\\b(official|audio|video|song|songs|music|records|topic|vevo|album|single|provided to youtube|ost|soundtrack)\\b"
        let hasMusicIdentity = regexMatches(musicIdentityPattern, in: noisyText)

        if !hasMusicIdentity && wordCount > 8 { return false }
        if !hasMusicIdentity && normAuthor.components(separatedBy: " ").count > 6 { return false }

        return true
    }

    public static func isLikelySameSong(reference: Song, candidate: Song) -> Bool {
        if reference.id == candidate.id { return true }
        let refTitle = normalizeTitle(reference.title)
        let candTitle = normalizeTitle(candidate.title)
        if refTitle.isEmpty || candTitle.isEmpty { return false }
        if refTitle == candTitle { return true }

        let sameArtist = getArtistKey(reference.author) == getArtistKey(candidate.author)
        if sameArtist && (refTitle.contains(candTitle) || candTitle.contains(refTitle)) { return true }

        let similarity = getTitleSimilarity(reference.title, candidate.title)
        return sameArtist ? similarity >= 0.7 : similarity >= 0.9
    }

    private static func getTimeOfDayIntentBonus(candSignals: Set<QueueSignal>) -> Int {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 22...23, 0...4:
            return (candSignals.contains(.lofiChill) || candSignals.contains(.sadEmotional)) ? 12 : 0
        case 6...10:
            return (candSignals.contains(.indieNonFilm) || candSignals.contains(.englishGlobal)) ? 10 : 0
        case 18...21:
            return (candSignals.contains(.highEnergyWorkout) || candSignals.contains(.trendingViral)) ? 10 : 0
        default:
            return 0
        }
    }

    public static func scoreCandidate(reference: Song, candidate: Song) -> Int {
        let sameArtist = getArtistKey(reference.author) == getArtistKey(candidate.author)
        let refSignals = detectSignals(reference)
        let candSignals = detectSignals(candidate)
        let titleSimilarity = getTitleSimilarity(reference.title, candidate.title)
        let sharedSignals = refSignals.intersection(candSignals).count

        // 1. PRIMARY CORE SPASIBA RECOMMENDATION ENGINE SCORING (80% DOMINANCE)
        var coreScore = 14 + (sharedSignals * 22)
        if sameArtist { coreScore -= 6 }

        // Language & Cultural Signal Guard: Penalize cross-language genre jumping
        let isRefBollywood = refSignals.contains(.bollywood)
        let isCandBollywood = candSignals.contains(.bollywood)
        let isRefPunjabi = refSignals.contains(.punjabi)
        let isCandPunjabi = candSignals.contains(.punjabi)

        if isRefBollywood && !isCandBollywood { coreScore -= 40 }
        if isRefPunjabi && !isCandPunjabi { coreScore -= 40 }
        if !isRefBollywood && !isRefPunjabi && (isCandBollywood || isCandPunjabi) { coreScore -= 30 }

        coreScore += Int(min(25.0, log10(max(Double(candidate.views), 1.0)) * 4.5))

        if regexMatches("\\btopic\\b", in: candidate.author) { coreScore += 8 }
        if regexMatches("\\bofficial\\b|\\baudio\\b", in: candidate.title) { coreScore += 8 }
        if candSignals.contains(.trendingViral) { coreScore += 5 }

        let titleVariantPattern = "\\b(lyrics?|lyric|live|remix|slowed|reverb|cover|karaoke|shorts?|sped\\s*up|bass\\s*boosted|instrumental|nightcore|edit|edit\\s*audio|mashup|mix|mega\\s*mix|playlist|jukebox|non[\\s-]*stop|reels?|status|whatsapp|tiktok|capcut|dialogue|scene|meme|template|bgm|ringtone)\\b"
        if regexMatches(titleVariantPattern, in: candidate.title) || regexMatches(titleVariantPattern, in: candidate.author) { coreScore -= 35 }

        let genericCompilationPattern = "\\b(top|best|latest|new|global|hollywood|viral|trending|hits?)\\b.*\\b(song|songs|music|playlist|list)\\b|\\b(song|songs)\\s*list\\b|\\bnew\\s+english\\s+song\\b"
        if regexMatches(genericCompilationPattern, in: candidate.title) || regexMatches(genericCompilationPattern, in: candidate.author) { coreScore -= 55 }

        if titleSimilarity > 0.45 { coreScore -= 25 } else if titleSimilarity > 0.25 { coreScore -= 12 }

        // 2. SECONDARY CONTEXTUAL & SESSION MODIFIERS (20% MAX WEIGHT)
        var contextualScore = 0

        // Related Artist Matrix (+6 max)
        let refArtistKey = getArtistKey(reference.author)
        let candArtistKey = getArtistKey(candidate.author)
        let isRelatedArtist = relatedArtistMap[refArtistKey]?.contains(candArtistKey) == true ||
                             relatedArtistMap[candArtistKey]?.contains(refArtistKey) == true
        if isRelatedArtist { contextualScore += 6 }

        // Time of day (+4 max)
        contextualScore += min(4, getTimeOfDayIntentBonus(candSignals: candSignals))

        // Session Brain Affinity & Penalties
        let artistKey = getArtistKey(candidate.author)
        let rawAffinity = !artistKey.isEmpty ? (SessionBrainManager.shared.artistAffinity[artistKey] ?? 0) : 0
        let clampedAffinity = max(-6, min(8, rawAffinity))
        let isRecentTrack = SessionBrainManager.shared.recentTracks.contains { $0.id == candidate.id }
        let recentPenalty = isRecentTrack ? 45 : 0
        let isArtistRecent = !artistKey.isEmpty && SessionBrainManager.shared.recentTracks.contains { getArtistKey($0.author) == artistKey }
        let repetitionPenalty = isArtistRecent ? 18 : 0
        let isBannedArtist = !artistKey.isEmpty && SessionBrainManager.shared.bannedArtists.contains(artistKey)
        let bannedPenalty = isBannedArtist ? 100 : 0
        let freshnessBonus = !isRecentTrack ? 4 : 0

        contextualScore += clampedAffinity + freshnessBonus - repetitionPenalty - bannedPenalty - recentPenalty

        // Dynamic Core Recommendation Engine Weighting
        let engagementFactor = SessionBrainManager.shared.getEngagementFactor()
        let coreWeight = 0.80 - (engagementFactor * 0.20)
        let contextualWeight = 1.0 - coreWeight

        let finalScore = (Double(coreScore) * coreWeight) + (Double(contextualScore) * contextualWeight)
        return Int(finalScore)
    }

    public static func buildQueueSearchBankQueries(track: Song, signals: Set<QueueSignal>) -> [String] {
        let primaryArtist = getPrimaryArtistName(track.author)
        let effectiveArtist = !primaryArtist.isEmpty ? primaryArtist : track.author
        let cleanedTitle = Array(getMeaningfulSongTokens(track.title).prefix(4)).joined(separator: " ")
        let artistQueryBase = effectiveArtist.trimmingCharacters(in: .whitespacesAndNewlines)
        let titleQueryBase = [artistQueryBase, cleanedTitle].filter { !$0.isEmpty }.joined(separator: " ")
        let signalQueries = signals.flatMap { signalQueryTemplates[$0] ?? [] }

        var candidates: [String] = []
        if !artistQueryBase.isEmpty { candidates.append("\(artistQueryBase) official audio") }
        if !artistQueryBase.isEmpty { candidates.append("\(artistQueryBase) top tracks official audio") }
        if let firstSig = signalQueries.first { candidates.append(firstSig) }
        if signalQueries.count > 1 { candidates.append(signalQueries[1]) }
        if !titleQueryBase.isEmpty { candidates.append("\(titleQueryBase) official audio") }

        var uniqueCandidates: [String] = []
        for c in candidates.map({ $0.trimmingCharacters(in: .whitespacesAndNewlines) }) where !c.isEmpty {
            if !uniqueCandidates.contains(c) {
                uniqueCandidates.append(c)
            }
            if uniqueCandidates.count >= 4 { break }
        }

        return uniqueCandidates
    }

    public static func appendSmartTracks(
        reference: Song,
        baseQueue: [Song],
        candidates: [Song],
        limit: Int = 80
    ) -> [Song] {
        var result = baseQueue
        var seenIds = Set(baseQueue.map { $0.id })
        var seenIdentityKeys = Set(baseQueue.map { getTrackIdentityKey($0) }.filter { !$0.isEmpty })
        var artistCounts: [String: Int] = [:]

        for track in baseQueue {
            let artistKey = getArtistKey(track.author)
            if !artistKey.isEmpty {
                artistCounts[artistKey, default: 0] += 1
            }
        }

        var appearanceCounts: [String: Int] = [:]
        for candidate in candidates {
            appearanceCounts[candidate.id, default: 0] += 1
        }

        let rankedCandidates = candidates
            .filter { isQueueableTrack($0, reference: reference) }
            .filter { !isLikelySameSong(reference: reference, candidate: $0) }
            .reduce(into: [Song]()) { acc, next in
                if !acc.contains(where: { $0.id == next.id }) { acc.append(next) }
            }
            .sorted { cand1, cand2 in
                let score1 = scoreCandidate(reference: reference, candidate: cand1) + ((appearanceCounts[cand1.id] ?? 1) * 6)
                let score2 = scoreCandidate(reference: reference, candidate: cand2) + ((appearanceCounts[cand2.id] ?? 1) * 6)
                return score1 > score2
            }

        for candidate in rankedCandidates {
            if seenIds.contains(candidate.id) { continue }
            if result.contains(where: { isLikelySameSong(reference: $0, candidate: candidate) }) { continue }

            let identityKey = getTrackIdentityKey(candidate)
            if !identityKey.isEmpty && seenIdentityKeys.contains(identityKey) { continue }

            let artistKey = getArtistKey(candidate.author)
            if !artistKey.isEmpty && (artistCounts[artistKey] ?? 0) >= SMART_QUEUE_ARTIST_CAP {
                continue
            }

            result.append(candidate)
            seenIds.insert(candidate.id)
            if !identityKey.isEmpty { seenIdentityKeys.insert(identityKey) }

            if !artistKey.isEmpty {
                artistCounts[artistKey, default: 0] += 1
            }

            if result.count >= limit { break }
        }

        return result
    }
}

