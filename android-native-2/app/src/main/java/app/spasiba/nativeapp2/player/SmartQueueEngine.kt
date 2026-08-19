package app.spasiba.nativeapp2.player

import app.spasiba.nativeapp2.data.model.Song
import java.util.Calendar
import java.util.Locale
import kotlin.math.log10

enum class QueueSignal {
    BOLLYWOOD,
    ENGLISH_GLOBAL,
    PUNJABI,
    LOFI_CHILL,
    LOVE_ROMANTIC,
    SAD_EMOTIONAL,
    HIGH_ENERGY_WORKOUT,
    TRENDING_VIRAL,
    INDIE_NON_FILM
}

object SmartQueueEngine {
    const val UPCOMING_QUEUE_TARGET = 10
    const val SMART_QUEUE_ARTIST_CAP = 2
    const val MIN_QUEUE_TRACK_DURATION_SECONDS = 75
    const val MAX_QUEUE_TRACK_DURATION_SECONDS = 480 // 8 mins
    const val QUEUE_DURATION_MULTIPLIER = 2.15

    private val SONG_STOP_WORDS = setOf(
        "the", "and", "for", "with", "from", "your", "you", "this", "that",
        "into", "onto", "over", "under", "official", "music", "audio", "video", "song"
    )

    private val ARTIST_SPLIT_PATTERN = Regex(
        ",|&|\\bx\\b|/|\\bfeaturing\\b|\\bfeat\\.?\\b|\\bft\\.?\\b",
        RegexOption.IGNORE_CASE
    )

    private val LOW_QUALITY_PATTERN = Regex(
        "\\b(reels?|shorts?|status|whatsapp|tiktok|capcut|edit\\s*audio|dialogue|scene|meme|template|ringtone|interview|podcast|episode|reaction|review|trailer|teaser|speech|lecture|news|vlog|roast|prank|full\\s*movie|behind\\s*the\\s*scenes|making\\s*of|mashup|mix|mega\\s*mix|playlist|jukebox|non[\\s-]*stop|best\\s+of|hour|hours|loop)\\b",
        RegexOption.IGNORE_CASE
    )

    private val TITLE_VARIANT_PATTERN = Regex(
        "\\b(lyrics?|lyric|live|remix|slowed|reverb|cover|karaoke|shorts?|sped\\s*up|bass\\s*boosted|instrumental|nightcore|edit|edit\\s*audio|mashup|mix|mega\\s*mix|playlist|jukebox|non[\\s-]*stop|reels?|status|whatsapp|tiktok|capcut|dialogue|scene|meme|template|bgm|ringtone)\\b",
        RegexOption.IGNORE_CASE
    )

    private val GENERIC_COMPILATION_PATTERN = Regex(
        "\\b(top|best|latest|new|global|hollywood|viral|trending|hits?)\\b.*\\b(song|songs|music|playlist|list)\\b|\\b(song|songs)\\s*list\\b|\\bnew\\s+english\\s+song\\b",
        RegexOption.IGNORE_CASE
    )

    private val MUSIC_IDENTITY_PATTERN = Regex(
        "\\b(official|audio|video|song|songs|music|records|topic|vevo|album|single|provided to youtube|ost|soundtrack)\\b",
        RegexOption.IGNORE_CASE
    )

    private val PUNJABI_PATTERN = Regex("[\\u0A00-\\u0A7F]|\\b(punjabi|diljit|aujla|shubh|sidhu|dhillon|dosanjh|randhawa|punjab)\\b", RegexOption.IGNORE_CASE)
    private val BOLLYWOOD_PATTERN = Regex("[\\u0900-\\u097F]|\\b(bollywood|hindi|arijit|jubin|shreya|atif|darshan|vishal|neha|tulsi|mohit|pritam)\\b", RegexOption.IGNORE_CASE)
    private val LOFI_PATTERN = Regex("\\b(lofi|chill|beats?|ambient|study|focus|sleep|instrumental)\\b", RegexOption.IGNORE_CASE)
    private val LOVE_PATTERN = Regex("\\b(love|romantic|romance|heart|ishq|pyaar|pyar|mohabbat|dil)\\b", RegexOption.IGNORE_CASE)
    private val SAD_PATTERN = Regex("\\b(sad|heartbreak|breakup|broken|alone|emotional|pain|cry)\\b", RegexOption.IGNORE_CASE)
    private val ENERGY_PATTERN = Regex("\\b(workout|gym|run|running|energy|party|hype|pump|motivation)\\b", RegexOption.IGNORE_CASE)
    private val TRENDING_PATTERN = Regex("\\b(trending|viral|hits?|top|chart|popular)\\b", RegexOption.IGNORE_CASE)
    private val INDIE_PATTERN = Regex("\\b(indie|acoustic|non film|alt|alternative|independent)\\b", RegexOption.IGNORE_CASE)

    private val SIGNAL_QUERY_TEMPLATES = mapOf(
        QueueSignal.BOLLYWOOD to listOf("bollywood official audio", "hindi film songs official audio", "romantic bollywood official audio"),
        QueueSignal.ENGLISH_GLOBAL to listOf("pop official audio", "global pop official audio", "english pop anthems official audio"),
        QueueSignal.PUNJABI to listOf("punjabi official audio", "punjabi pop official audio", "punjabi romantic official audio"),
        QueueSignal.LOFI_CHILL to listOf("lofi official audio", "chill pop official audio", "soft chill official audio"),
        QueueSignal.LOVE_ROMANTIC to listOf("romantic official audio", "love songs official audio", "soft pop official audio"),
        QueueSignal.SAD_EMOTIONAL to listOf("sad official audio", "heartbreak official audio", "emotional pop official audio"),
        QueueSignal.HIGH_ENERGY_WORKOUT to listOf("workout official audio", "high energy pop official audio", "party anthem official audio"),
        QueueSignal.TRENDING_VIRAL to listOf("chart hits official audio", "viral hits official audio", "top pop official audio"),
        QueueSignal.INDIE_NON_FILM to listOf("indie official audio", "alt pop official audio", "independent pop official audio")
    )

    private val PAREN_NOISE_PATTERN = Regex("\\([^)]*\\)")
    private val BRACKET_NOISE_PATTERN = Regex("\\[[^\\]]*\\]")
    private val KEYWORD_NOISE_PATTERN = Regex(
        "\\b(official|video|music|lyrics?|lyric|audio|hd|4k|visualizer|feat\\.?|ft\\.?|remix|live|version|slowed|reverb|cover|karaoke|shorts?|sped\\s*up|bass\\s*boosted|instrumental)\\b",
        RegexOption.IGNORE_CASE
    )
    private val PUNCTUATION_PATTERN = Regex("[|:/\\\\-]+")
    private val WHITESPACE_PATTERN = Regex("\\s+")
    private val NON_ALPHANUMERIC_PATTERN = Regex("[^a-z0-9]")
    private val TOPIC_PATTERN = Regex("\\btopic\\b", RegexOption.IGNORE_CASE)
    private val OFFICIAL_AUDIO_PATTERN = Regex("\\bofficial\\b|\\baudio\\b", RegexOption.IGNORE_CASE)

    fun stripTitleNoise(value: String): String {
        return value
            .replace(PAREN_NOISE_PATTERN, " ")
            .replace(BRACKET_NOISE_PATTERN, " ")
            .replace(KEYWORD_NOISE_PATTERN, " ")
            .replace(PUNCTUATION_PATTERN, " ")
            .replace(WHITESPACE_PATTERN, " ")
            .trim()
    }

    fun normalizeToken(value: String): String {
        return value.lowercase(Locale.ROOT).replace(NON_ALPHANUMERIC_PATTERN, "")
    }

    fun getMeaningfulSongTokens(value: String): List<String> {
        return stripTitleNoise(value)
            .split(" ")
            .map { normalizeToken(it) }
            .filter { it.length > 1 && !SONG_STOP_WORDS.contains(it) }
    }

    fun getPrimaryArtistName(author: String): String {
        val primary = stripTitleNoise(author).split(ARTIST_SPLIT_PATTERN).firstOrNull()?.trim() ?: author.trim()
        return primary.ifBlank { author.trim() }
    }

    fun getArtistKey(author: String): String {
        return normalizeSongTitle(getPrimaryArtistName(author).ifBlank { author })
    }

    fun normalizeTitle(title: String): String {
        return normalizeSongTitle(title)
    }

    fun normalizeSongTitle(value: String): String {
        return getMeaningfulSongTokens(value).joinToString(" ")
    }

    fun getTrackIdentityKey(track: Song): String {
        return "${getArtistKey(track.author)}::${normalizeSongTitle(track.title)}"
    }

    fun getTitleSimilarity(leftTitle: String, rightTitle: String): Double {
        val leftTokens = getMeaningfulSongTokens(leftTitle)
        val rightTokens = getMeaningfulSongTokens(rightTitle)
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0

        val rightSet = rightTokens.toSet()
        val shared = leftTokens.toSet().count { rightSet.contains(it) }
        return shared.toDouble() / minOf(leftTokens.size, rightTokens.size)
    }

    fun detectSignals(track: Song): Set<QueueSignal> {
        val text = "${track.title} ${track.author}".lowercase(Locale.ROOT)
        val signals = mutableSetOf<QueueSignal>()

        when {
            PUNJABI_PATTERN.containsMatchIn(text) -> signals.add(QueueSignal.PUNJABI)
            BOLLYWOOD_PATTERN.containsMatchIn(text) -> signals.add(QueueSignal.BOLLYWOOD)
            else -> signals.add(QueueSignal.ENGLISH_GLOBAL)
        }

        if (LOFI_PATTERN.containsMatchIn(text)) signals.add(QueueSignal.LOFI_CHILL)
        if (LOVE_PATTERN.containsMatchIn(text)) signals.add(QueueSignal.LOVE_ROMANTIC)
        if (SAD_PATTERN.containsMatchIn(text)) signals.add(QueueSignal.SAD_EMOTIONAL)
        if (ENERGY_PATTERN.containsMatchIn(text)) signals.add(QueueSignal.HIGH_ENERGY_WORKOUT)
        if (INDIE_PATTERN.containsMatchIn(text)) signals.add(QueueSignal.INDIE_NON_FILM)
        if (TRENDING_PATTERN.containsMatchIn(text) || track.views > 100_000_000) signals.add(QueueSignal.TRENDING_VIRAL)

        return signals
    }

    fun inferQueueSignals(reference: Song, queue: List<Song>): Set<QueueSignal> {
        val counts = mutableMapOf<QueueSignal, Int>()
        val candidateTracks = listOf(reference) + queue.filter { it.id != reference.id }.take(6)

        for (candidate in candidateTracks) {
            for (signal in detectSignals(candidate)) {
                counts[signal] = (counts[signal] ?: 0) + 1
            }
        }

        val inferredSignals = detectSignals(reference).toMutableSet()
        val rankedSignals = counts.entries.sortedByDescending { it.value }.map { it.key }

        for (signal in rankedSignals) {
            if ((counts[signal] ?: 0) >= 2 || inferredSignals.size < 2) {
                inferredSignals.add(signal)
            }
            if (inferredSignals.size >= 3) break
        }

        return inferredSignals
    }

    fun getRecommendedQueueDurationCap(reference: Song? = null): Int {
        if (reference == null || reference.durationSeconds <= 0) return MAX_QUEUE_TRACK_DURATION_SECONDS
        return maxOf(210, minOf(MAX_QUEUE_TRACK_DURATION_SECONDS, (reference.durationSeconds * QUEUE_DURATION_MULTIPLIER).toInt()))
    }

    fun isQueueableTrack(candidate: Song, reference: Song? = null): Boolean {
        val noisyText = "${candidate.title} ${candidate.author}"

        if (LOW_QUALITY_PATTERN.containsMatchIn(noisyText)) return false
        if (GENERIC_COMPILATION_PATTERN.containsMatchIn(candidate.title) || GENERIC_COMPILATION_PATTERN.containsMatchIn(candidate.author)) return false

        val dur = candidate.durationSeconds
        if (dur > 0 && dur < MIN_QUEUE_TRACK_DURATION_SECONDS) return false
        if (dur > getRecommendedQueueDurationCap(reference)) return false

        val normTitle = normalizeTitle(candidate.title)
        val normAuthor = normalizeTitle(candidate.author)
        val wordCount = if (normTitle.isBlank()) 0 else normTitle.split(" ").size
        val hasMusicIdentity = MUSIC_IDENTITY_PATTERN.containsMatchIn(noisyText)

        if (!hasMusicIdentity && wordCount > 8) return false
        if (!hasMusicIdentity && normAuthor.split(" ").size > 6) return false

        return true
    }

    fun isLikelySameSong(reference: Song, candidate: Song): Boolean {
        if (reference.id == candidate.id) return true
        val refTitle = normalizeTitle(reference.title)
        val candTitle = normalizeTitle(candidate.title)
        if (refTitle.isBlank() || candTitle.isBlank()) return false
        if (refTitle == candTitle) return true

        val sameArtist = getArtistKey(reference.author) == getArtistKey(candidate.author)
        if (sameArtist && (refTitle.contains(candTitle) || candTitle.contains(refTitle))) return true

        val similarity = getTitleSimilarity(reference.title, candidate.title)
        return if (sameArtist) similarity >= 0.7 else similarity >= 0.9
    }

    private val RELATED_ARTIST_MAP = mapOf(
        "arijitsingh" to setOf("jubinnautiyal", "atifaslam", "pritam", "shreyaghoshal", "darshanraval", "mohitchauhan", "vishalmishra", "sachinjigar", "bpraak"),
        "jubinnautiyal" to setOf("arijitsingh", "atifaslam", "darshanraval", "vishalmishra", "tulsikumar", "nehakakkar"),
        "atifaslam" to setOf("arijitsingh", "rahatfatehalikhan", "mohitchauhan", "mustafazahid", "kk"),
        "diljitdosanjh" to setOf("karanaujla", "shubh", "apdhillon", "sidhumoosewala", "gururandhawa", "jassmanak", "harnoor", "jassiegill"),
        "karanaujla" to setOf("diljitdosanjh", "shubh", "apdhillon", "sidhumoosewala", "badshah", "divine"),
        "shubh" to setOf("karanaujla", "apdhillon", "diljitdosanjh", "sidhumoosewala"),
        "theweeknd" to setOf("drake", "postmalone", "dualipa", "brunomars", "taylorswift", "sza", "khalid", "traviscott", "justinbieber"),
        "drake" to setOf("theweeknd", "21savage", "postmalone", "traviscott", "future", "kanyewest", "kendricklamar"),
        "taylorswift" to setOf("gracieabrams", "oliviarodrigo", "lanadelrey", "sabrinacarpenter", "dualipa", "edsheeran", "shawnmendes"),
        "coldplay" to setOf("onerepublic", "imaginedragons", "thechainsmokers", "maroon5", "lumineers", "bastille"),
        "bts" to setOf("jungkook", "blackpink", "straykids", "twice", "exo", "newjeans", "txt", "seventeen"),
        "pritam" to setOf("arijitsingh", "shreyaghoshal", "vishalshekhar", "amittrivedi", "sanjoy"),
        "badshah" to setOf("honeysingh", "raftaar", "divine", "karanaujla", "mcstan", "ikka")
    )

    private fun getTimeOfDayIntentBonus(candSignals: Set<QueueSignal>): Int {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 22..23, in 0..4 -> {
                if (candSignals.contains(QueueSignal.LOFI_CHILL) || candSignals.contains(QueueSignal.SAD_EMOTIONAL)) 12 else 0
            }
            in 6..10 -> {
                if (candSignals.contains(QueueSignal.INDIE_NON_FILM) || candSignals.contains(QueueSignal.ENGLISH_GLOBAL)) 10 else 0
            }
            in 18..21 -> {
                if (candSignals.contains(QueueSignal.HIGH_ENERGY_WORKOUT) || candSignals.contains(QueueSignal.TRENDING_VIRAL)) 10 else 0
            }
            else -> 0
        }
    }

    fun scoreCandidate(reference: Song, candidate: Song): Int {
        val sameArtist = getArtistKey(reference.author) == getArtistKey(candidate.author)
        val refSignals = detectSignals(reference)
        val candSignals = detectSignals(candidate)
        val titleSimilarity = getTitleSimilarity(reference.title, candidate.title)
        val sharedSignals = refSignals.intersect(candSignals).size

        // 1. PRIMARY CORE SPASIBA RECOMMENDATION ENGINE SCORING (80% DOMINANCE)
        var coreScore = 14 + (sharedSignals * 22)
        if (sameArtist) coreScore -= 6

        // Language & Cultural Signal Guard: Penalize cross-language genre jumping
        val isRefBollywood = refSignals.contains(QueueSignal.BOLLYWOOD)
        val isCandBollywood = candSignals.contains(QueueSignal.BOLLYWOOD)
        val isRefPunjabi = refSignals.contains(QueueSignal.PUNJABI)
        val isCandPunjabi = candSignals.contains(QueueSignal.PUNJABI)

        if (isRefBollywood && !isCandBollywood) coreScore -= 40
        if (isRefPunjabi && !isCandPunjabi) coreScore -= 40
        if (!isRefBollywood && !isRefPunjabi && (isCandBollywood || isCandPunjabi)) coreScore -= 30

        coreScore += minOf(25.0, log10(maxOf(candidate.views.toDouble(), 1.0)) * 4.5).toInt()

        if (Regex("\\btopic\\b", RegexOption.IGNORE_CASE).containsMatchIn(candidate.author)) coreScore += 8
        if (Regex("\\bofficial\\b|\\baudio\\b", RegexOption.IGNORE_CASE).containsMatchIn(candidate.title)) coreScore += 8
        if (candSignals.contains(QueueSignal.TRENDING_VIRAL)) coreScore += 5

        if (TITLE_VARIANT_PATTERN.containsMatchIn(candidate.title) || TITLE_VARIANT_PATTERN.containsMatchIn(candidate.author)) coreScore -= 35
        if (GENERIC_COMPILATION_PATTERN.containsMatchIn(candidate.title) || GENERIC_COMPILATION_PATTERN.containsMatchIn(candidate.author)) coreScore -= 55

        if (titleSimilarity > 0.45) coreScore -= 25 else if (titleSimilarity > 0.25) coreScore -= 12

        // 2. SECONDARY CONTEXTUAL & SESSION MODIFIERS (20% MAX WEIGHT)
        var contextualScore = 0

        // Related Artist Matrix (+6 max)
        val refArtistKey = getArtistKey(reference.author)
        val candArtistKey = getArtistKey(candidate.author)
        val isRelatedArtist = RELATED_ARTIST_MAP[refArtistKey]?.contains(candArtistKey) == true ||
                             RELATED_ARTIST_MAP[candArtistKey]?.contains(refArtistKey) == true
        if (isRelatedArtist) contextualScore += 6

        // Time of day (+4 max)
        contextualScore += minOf(4, getTimeOfDayIntentBonus(candSignals))

        // Session Brain Affinity & Penalties
        val artistKey = getArtistKey(candidate.author)
        val rawAffinity = if (artistKey.isNotBlank()) app.spasiba.nativeapp2.data.analytics.SessionBrainManager.artistAffinity[artistKey] ?: 0 else 0
        val clampedAffinity = rawAffinity.coerceIn(-6, 8)
        val isRecentTrack = app.spasiba.nativeapp2.data.analytics.SessionBrainManager.recentTracks.any { it.id == candidate.id }
        val recentPenalty = if (isRecentTrack) 45 else 0
        val isArtistRecent = artistKey.isNotBlank() && app.spasiba.nativeapp2.data.analytics.SessionBrainManager.recentTracks.any { getArtistKey(it.author) == artistKey }
        val repetitionPenalty = if (isArtistRecent) 18 else 0
        val isBannedArtist = artistKey.isNotBlank() && app.spasiba.nativeapp2.data.analytics.SessionBrainManager.bannedArtists.contains(artistKey)
        val bannedPenalty = if (isBannedArtist) 100 else 0
        val freshnessBonus = if (!isRecentTrack) 4 else 0

        contextualScore += clampedAffinity + freshnessBonus - repetitionPenalty - bannedPenalty - recentPenalty

        // Dynamic Core Recommendation Engine Weighting (80% down to 60% minimum core dominance)
        val engagementFactor = app.spasiba.nativeapp2.data.analytics.SessionBrainManager.getEngagementFactor()
        val coreWeight = 0.80 - (engagementFactor * 0.20) // Dynamically scales from 80% down to 60% min
        val contextualWeight = 1.0 - coreWeight              // Dynamically scales from 20% up to 40% max

        val finalScore = (coreScore * coreWeight) + (contextualScore * contextualWeight)
        return finalScore.toInt()
    }

    fun buildQueueSearchBankQueries(track: Song, signals: Set<QueueSignal>): List<String> {
        val primaryArtist = getPrimaryArtistName(track.author).ifBlank { track.author }
        val cleanedTitle = getMeaningfulSongTokens(track.title).take(4).joinToString(" ")
        val artistQueryBase = primaryArtist.trim()
        val titleQueryBase = listOf(artistQueryBase, cleanedTitle).filter { it.isNotBlank() }.joinToString(" ")
        val signalQueries = signals.flatMap { SIGNAL_QUERY_TEMPLATES[it] ?: emptyList() }

        val candidates = listOfNotNull(
            if (artistQueryBase.isNotBlank()) "$artistQueryBase official audio" else null,
            if (artistQueryBase.isNotBlank()) "$artistQueryBase top tracks official audio" else null,
            signalQueries.getOrNull(0),
            signalQueries.getOrNull(1),
            if (titleQueryBase.isNotBlank()) "$titleQueryBase official audio" else null
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct().take(4)

        return candidates
    }

    fun appendSmartTracks(
        reference: Song,
        baseQueue: List<Song>,
        candidates: List<Song>,
        limit: Int = 80
    ): List<Song> {
        val result = baseQueue.toMutableList()
        val seenIds = baseQueue.map { it.id }.toMutableSet()
        val seenIdentityKeys = baseQueue.map { getTrackIdentityKey(it) }.filter { it.isNotBlank() }.toMutableSet()
        val artistCounts = mutableMapOf<String, Int>()

        for (track in baseQueue) {
            val artistKey = getArtistKey(track.author)
            if (artistKey.isNotBlank()) {
                artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
            }
        }

        // Calculate appearance frequency across search candidates (exact audio-controller.ts match)
        val appearanceCounts = mutableMapOf<String, Int>()
        for (candidate in candidates) {
            appearanceCounts[candidate.id] = (appearanceCounts[candidate.id] ?: 0) + 1
        }

        val rankedCandidates = candidates
            .filter { isQueueableTrack(it, reference) }
            .filter { !isLikelySameSong(reference, it) }
            .distinctBy { it.id }
            .sortedByDescending { candidate ->
                scoreCandidate(reference, candidate) + ((appearanceCounts[candidate.id] ?: 1) * 6)
            }

        for (candidate in rankedCandidates) {
            if (seenIds.contains(candidate.id)) continue
            if (result.any { isLikelySameSong(it, candidate) }) continue

            val identityKey = getTrackIdentityKey(candidate)
            if (identityKey.isNotBlank() && seenIdentityKeys.contains(identityKey)) continue

            val artistKey = getArtistKey(candidate.author)
            if (artistKey.isNotBlank() && (artistCounts[artistKey] ?: 0) >= SMART_QUEUE_ARTIST_CAP) {
                continue
            }

            result.add(candidate)
            seenIds.add(candidate.id)
            if (identityKey.isNotBlank()) seenIdentityKeys.add(identityKey)

            if (artistKey.isNotBlank()) {
                artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
            }

            if (result.size >= limit) break
        }

        return result
    }
}

