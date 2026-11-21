package cz.webshare.tv.stream

import cz.webshare.tv.api.WebshareApi
import cz.webshare.tv.model.AudioLanguage
import cz.webshare.tv.model.CatalogItem
import cz.webshare.tv.model.EpisodeInfo
import cz.webshare.tv.model.MediaType
import cz.webshare.tv.model.ParsedStream
import cz.webshare.tv.model.StreamGroup
import cz.webshare.tv.model.StreamResolutionResult
import cz.webshare.tv.model.UserPreferences
import cz.webshare.tv.model.VideoQuality
import cz.webshare.tv.model.WebshareFile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

class StreamResolver(
    private val webshareApi: WebshareApi
) {
    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mkv", "mp4", "avi", "mov", "m4v", "ts", "m2ts", "webm", "mpg", "mpeg"
        )
        private val JUNK_KEYWORDS = Regex("(?i)\\b(sample|trailer|ukazka|teaser|bonus\\.featurette)\\b")
        private val CZECH_DIACRITICS = setOf(
            'á', 'č', 'ď', 'é', 'ě', 'í', 'ň', 'ó', 'ř', 'š', 'ť', 'ú', 'ů', 'ý', 'ž', 'ľ', 'ĺ', 'ô', 'ä'
        )
        private val GENERIC_TITLE_STOPWORDS = setOf(
            "the", "and", "for", "from", "with", "into", "over", "under",
            "man", "men", "woman", "women", "boy", "girl", "part", "cast",
            "movie", "film", "series", "serial", "season", "episode",
            "new", "old", "big", "little", "good", "bad", "dark", "light",
            "last", "first", "next", "day", "night", "world", "war", "love",
            "life", "dead", "death", "house", "home", "city", "king", "queen",
            "dil", "serie", "epizoda"
        )
        private val ROMAN_TO_ARABIC = mapOf(
            "ii" to "2",
            "iii" to "3",
            "iv" to "4",
            "vi" to "6",
            "vii" to "7",
            "viii" to "8",
            "ix" to "9"
        )
    }

    /**
     * Searches Webshare.cz for all matching video files for a Movie or Series Episode,
     * filters out junk/duplicates, classifies quality & language, groups results,
     * and picks the optimal stream according to UserPreferences with automatic fallback.
     */
    suspend fun resolveStreams(
        item: CatalogItem,
        episode: EpisodeInfo? = null,
        token: String,
        preferences: UserPreferences
    ): StreamResolutionResult = coroutineScope {
        val queries = buildSearchQueries(item, episode)

        // Execute parallel searches (relevance, largest, and top-rated) for accurate and complete coverage
        val deferredSearches = queries.flatMapIndexed { index, q ->
            buildList {
                // Default Webshare relevance search finds the exact movie/series title first
                add(async { webshareApi.searchVideos(q, token, sort = "", limit = 70).getOrNull().orEmpty() })
                add(async { webshareApi.searchVideos(q, token, sort = "largest", limit = 70).getOrNull().orEmpty() })
                if (index < 3) {
                    add(async { webshareApi.searchVideos(q, token, sort = "rating", limit = 45).getOrNull().orEmpty() })
                }
            }
        }

        val rawFiles = deferredSearches.awaitAll()
            .flatten()
            .distinctBy { it.ident }

        val isEpisode = item.type == MediaType.SERIES && episode != null
        val minSizeBytes = if (isEpisode) 45L * 1024 * 1024 else 110L * 1024 * 1024

        // 1. Strict match: exact title words + release year (or episode code)
        var parsedList = rawFiles.mapNotNull { file ->
            parseAndValidateFile(
                file = file,
                item = item,
                episode = episode,
                minSizeBytes = minSizeBytes,
                strictYearAndFullTitle = true
            )
        }

        // 2. If strict year/subtitle match found nothing, relax year requirement slightly
        //    while NEVER relaxing the primary distinctive title tokens (e.g. "spider" in "Spider-Man")!
        if (parsedList.isEmpty() && rawFiles.isNotEmpty()) {
            parsedList = rawFiles.mapNotNull { file ->
                parseAndValidateFile(
                    file = file,
                    item = item,
                    episode = episode,
                    minSizeBytes = minSizeBytes,
                    strictYearAndFullTitle = false
                )
            }
        }

        val sortedStreams = parsedList.sortedByDescending { it.score }

        // Group by (AudioLanguage, VideoQuality)
        val groupMap = linkedMapOf<Pair<AudioLanguage, VideoQuality>, MutableList<ParsedStream>>()
        for (lang in AudioLanguage.entries.sortedByDescending { it.rank }) {
            for (qual in VideoQuality.entries.sortedByDescending { it.rank }) {
                groupMap[lang to qual] = mutableListOf()
            }
        }
        for (stream in sortedStreams) {
            groupMap.getOrPut(stream.language to stream.quality) { mutableListOf() }.add(stream)
        }

        val nonEmptyGroups = groupMap.entries
            .filter { it.value.isNotEmpty() }
            .map { (key, list) ->
                StreamGroup(
                    language = key.first,
                    quality = key.second,
                    streams = list.sortedByDescending { it.score }
                )
            }

        val (recommended, fallbackApplied, notice) = selectStreamWithFallback(
            groups = nonEmptyGroups,
            preferences = preferences
        )

        val displayQuery = if (episode != null) {
            "${item.title} (${episode.code})"
        } else {
            "${item.title} ${item.year?.let { "($it)" } ?: ""}".trim()
        }

        StreamResolutionResult(
            queryTitle = displayQuery,
            allStreams = sortedStreams,
            groups = nonEmptyGroups,
            recommendedStream = recommended,
            fallbackApplied = fallbackApplied,
            selectionNotice = notice
        )
    }

    /**
     * Builds search queries including both space-separated ("Spider Man") and
     * joined compound variants ("Spiderman") so Webshare's search engine returns all uploads.
     */
    private fun buildSearchQueries(item: CatalogItem, episode: EpisodeInfo?): List<String> {
        val queries = linkedSetOf<String>()
        val titleVariants = linkedSetOf<String>()

        fun addTitleVariants(rawTitle: String) {
            val cleaned = rawTitle.trim()
            if (cleaned.isEmpty()) return
            val deaccented = normalizeTextPreservingSpaces(cleaned)
            titleVariants.add(cleaned)
            if (!deaccented.equals(cleaned, ignoreCase = true) && deaccented.isNotEmpty()) {
                titleVariants.add(deaccented)
            }
            // If title contains hyphenated words like "Spider-Man", "X-Men", "Ant-Man",
            // also add the joined compound form ("Spiderman", "Xmen", "Antman")
            if (cleaned.contains('-')) {
                val compound = cleaned.replace(Regex("(?<=\\p{L})-(?=\\p{L})"), "")
                val compoundDeaccented = normalizeTextPreservingSpaces(compound)
                titleVariants.add(compound)
                if (compoundDeaccented.isNotEmpty()) {
                    titleVariants.add(compoundDeaccented)
                }
            }
            // If title has a colon subtitle (e.g. "Spider-Man: Bez domova"), also prepare main prefix + year
            if (cleaned.contains(':')) {
                val subtitleOnly = cleaned.substringAfter(':').trim()
                val prefixOnly = cleaned.substringBefore(':').trim()
                if (prefixOnly.contains('-')) {
                    val compoundPrefix = prefixOnly.replace(Regex("(?<=\\p{L})-(?=\\p{L})"), "")
                    titleVariants.add("$compoundPrefix $subtitleOnly".trim())
                }
            }
        }

        addTitleVariants(item.title)
        if (!item.originalTitle.equals(item.title, ignoreCase = true)) {
            addTitleVariants(item.originalTitle)
        }

        if (episode != null) {
            val sCode = episode.code // e.g. S01E02
            val xCode = "${episode.seasonNumber}x%02d".format(episode.episodeNumber)
            for (variant in titleVariants) {
                queries.add("$variant $sCode")
            }
            queries.add("${item.title.trim()} $xCode")
            if (!item.originalTitle.equals(item.title, ignoreCase = true) && item.originalTitle.isNotBlank()) {
                queries.add("${item.originalTitle.trim()} $xCode")
            }
        } else {
            val yearSuffix = item.year?.toString() ?: ""
            if (yearSuffix.isNotEmpty()) {
                for (variant in titleVariants) {
                    queries.add("$variant $yearSuffix")
                }
            }
            for (variant in titleVariants) {
                queries.add(variant)
            }
        }
        return queries.take(6)
    }

    private data class TitleMatchScore(
        val isMatch: Boolean,
        val isExactAllTokens: Boolean,
        val matchedRatio: Double
    )

    private fun parseAndValidateFile(
        file: WebshareFile,
        item: CatalogItem,
        episode: EpisodeInfo?,
        minSizeBytes: Long,
        strictYearAndFullTitle: Boolean
    ): ParsedStream? {
        if (file.passwordProtected) return null
        if (file.sizeBytes < minSizeBytes) return null

        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext !in VIDEO_EXTENSIONS) return null

        val nameWithoutExt = file.name.substringBeforeLast('.')
        if (JUNK_KEYWORDS.containsMatchIn(nameWithoutExt)) return null

        // Extract the movie/series title portion of the filename (before year/resolution/codec tags)
        val fileTitleSegment = extractTitlePortionFromFilename(nameWithoutExt)
        val fileFullTokens = tokenizeNormalized(nameWithoutExt)
        val fileTitleTokens = tokenizeNormalized(fileTitleSegment).ifEmpty { fileFullTokens }
        val fileFullCollapsed = fileFullTokens.joinToString("")
        val fileTitleCollapsed = fileTitleTokens.joinToString("")

        // 1. Episode vs Movie Structural Validation
        if (episode != null) {
            val s = episode.seasonNumber
            val e = episode.episodeNumber
            val epRegex = Regex(
                "(?i)(?:\\bS0?$s[._ -]?E0?$e\\b|\\b0?${s}x0?$e\\b|\\b$s\\.\\s*serie.*\\b$e\\.\\s*dil\\b|\\bE0?$e\\b)"
            )
            val strictEpRegex = Regex(
                "(?i)(?:\\bS0?$s[._ -]?E0?$e\\b|\\b0?${s}x0?$e\\b|\\b$s\\.\\s*serie.*\\b$e\\.\\s*dil\\b)"
            )
            val anyEpRegex = Regex("(?i)\\b(S\\d{1,2}[._ -]?E\\d{1,2}|\\d{1,2}x\\d{2})\\b")
            if (strictYearAndFullTitle) {
                if (!strictEpRegex.containsMatchIn(nameWithoutExt)) return null
            } else {
                // Reject if it belongs to a different episode, or has no episode marker at all
                if (anyEpRegex.containsMatchIn(nameWithoutExt) && !strictEpRegex.containsMatchIn(nameWithoutExt)) {
                    return null
                }
                if (!epRegex.containsMatchIn(nameWithoutExt)) return null
            }
        } else {
            // Movie validation: ALWAYS reject TV series episodes
            if (Regex("(?i)\\b(S\\d{1,2}[._ -]?E\\d{1,2}|\\d{1,2}x\\d{2})\\b").containsMatchIn(nameWithoutExt)) {
                return null
            }

            // Release year validation:
            // If the filename explicitly contains a 4-digit release year (e.g. 2000 vs 2002),
            // verify it matches the catalog item's release year!
            if (item.year != null) {
                val fileYears = Regex("\\b(19[5-9]\\d|20[0-2]\\d)\\b")
                    .findAll(nameWithoutExt)
                    .mapNotNull { it.value.toIntOrNull() }
                    .filter { it != 1080 && it != 2160 }
                    .toList()

                if (fileYears.isNotEmpty()) {
                    val tolerance = if (strictYearAndFullTitle) 1 else 2
                    if (fileYears.none { abs(it - item.year) <= tolerance }) {
                        // File explicitly states a different movie year (e.g. Spider-Man 2002 vs 2021, or X-Men 2000) -> reject!
                        return null
                    }
                }
            }
        }

        // 2. Strict Title Token & Compound Word Validation
        val czMatch = evaluateTitleMatch(
            targetTitle = item.title,
            fileTitleTokens = fileTitleTokens,
            fileFullTokens = fileFullTokens,
            fileTitleCollapsed = fileTitleCollapsed,
            fileFullCollapsed = fileFullCollapsed,
            strictAllTokens = strictYearAndFullTitle
        )
        val origMatch = evaluateTitleMatch(
            targetTitle = item.originalTitle,
            fileTitleTokens = fileTitleTokens,
            fileFullTokens = fileFullTokens,
            fileTitleCollapsed = fileTitleCollapsed,
            fileFullCollapsed = fileFullCollapsed,
            strictAllTokens = strictYearAndFullTitle
        )

        if (!czMatch.isMatch && !origMatch.isMatch) {
            return null
        }

        val isEpisode = episode != null
        val quality = detectQuality(nameWithoutExt, file.sizeBytes, isEpisode)
        val normCz = normalizeTextPreservingSpaces(item.title)
        val normOrig = normalizeTextPreservingSpaces(item.originalTitle)
        val language = detectLanguage(
            rawFilename = file.name,
            nameWithoutExt = nameWithoutExt,
            hasLocalizedCzechTitleInFile = czMatch.isMatch && !normCz.equals(normOrig, ignoreCase = true)
        )
        val isDolbyVision = Regex("(?i)\\b(dovi|dolby[._ -]?vision|dv)\\b").containsMatchIn(nameWithoutExt)
        val isHdr = isDolbyVision || Regex("(?i)\\b(hdr|hdr10|hdr10plus)\\b").containsMatchIn(nameWithoutExt)
        val isHevc = Regex("(?i)\\b(hevc|x265|h265|h\\.265)\\b").containsMatchIn(nameWithoutExt)
        val isSurround = Regex("(?i)\\b(5\\.1|7\\.1|dts|atmos|truehd|eac3|dd5|ac3)\\b").containsMatchIn(nameWithoutExt)

        val hasExactYearInFile = item.year != null &&
            Regex("\\b${item.year}\\b").containsMatchIn(nameWithoutExt)

        val bestMatchRatio = maxOf(czMatch.matchedRatio, origMatch.matchedRatio)
        val isExactAllTokens = czMatch.isExactAllTokens || origMatch.isExactAllTokens

        val score = computeStreamScore(
            file = file,
            quality = quality,
            language = language,
            ext = ext,
            isHevc = isHevc,
            isSurround = isSurround,
            exactTitleMatch = isExactAllTokens,
            matchRatio = bestMatchRatio,
            hasExactYearInFile = hasExactYearInFile,
            isEpisode = isEpisode
        )

        return ParsedStream(
            file = file,
            quality = quality,
            language = language,
            isHdr = isHdr,
            isDolbyVision = isDolbyVision,
            isHevc = isHevc,
            isSurround51 = isSurround,
            extension = ext.uppercase(),
            score = score
        )
    }

    /**
     * Evaluates whether a Webshare filename actually matches the requested movie/series title.
     *
     * Fixes:
     * 1. Never uses raw `.contains()` on substring tokens (so "man" never matches "xmen" or "roman").
     * 2. Supports compound words ("spider" + "man" <-> "spiderman").
     * 3. Requires ALL primary/distinctive title words (before colon, and non-stopword tokens) to match,
     *    so "Spider-Man" can NEVER match "X-Men", "Iron Man", or "Batman".
     * 4. Prevents sequel number mismatch when no release year is in the filename (e.g. "Spider-Man" vs "Spider-Man 2" / "3").
     */
    private fun evaluateTitleMatch(
        targetTitle: String,
        fileTitleTokens: List<String>,
        fileFullTokens: List<String>,
        fileTitleCollapsed: String,
        fileFullCollapsed: String,
        strictAllTokens: Boolean
    ): TitleMatchScore {
        val allTargetTokens = tokenizeNormalized(targetTitle)
        if (allTargetTokens.isEmpty()) return TitleMatchScore(false, false, 0.0)

        // Tokens before ':' (the main franchise/movie name, e.g. "Spider-Man" in "Spider-Man: Bez domova")
        val prefixPart = targetTitle.substringBefore(':')
        val prefixTokens = tokenizeNormalized(prefixPart).ifEmpty { allTargetTokens }

        val fileTokenSet = fileFullTokens.toSet()
        val fileTitleTokenSet = fileTitleTokens.toSet()

        fun isTokenPresent(token: String, indexInTarget: Int, targetList: List<String>): Boolean {
            // 1. Exact whole-word token match (or Roman/Arabic numeral equivalent)
            if (token in fileTokenSet) return true
            val arabic = ROMAN_TO_ARABIC[token]
            if (arabic != null && arabic in fileTokenSet) return true

            // 2. Compound word match with adjacent token in target title
            //    e.g., target ["spider", "man"] -> compound "spiderman" in fileTokenSet
            if (indexInTarget + 1 < targetList.size) {
                val combinedNext = token + targetList[indexInTarget + 1]
                if (combinedNext in fileTokenSet || fileTitleTokenSet.any { it == combinedNext }) {
                    return true
                }
            }
            if (indexInTarget > 0) {
                val combinedPrev = targetList[indexInTarget - 1] + token
                if (combinedPrev in fileTokenSet || fileTitleTokenSet.any { it == combinedPrev }) {
                    return true
                }
            }

            // 3. Target itself is a single compound token (e.g. "spiderman") and file has ["spider", "man"]
            if (token.length >= 6 && (fileTitleCollapsed.contains(token) || fileFullCollapsed.startsWith(token))) {
                return true
            }
            return false
        }

        // Identify distinctive tokens (non-generic words like "spider", "avengers", "oppenheimer", "pelisky")
        val distinctivePrefixTokens = prefixTokens.filter { tok ->
            tok.length >= 2 && tok !in GENERIC_TITLE_STOPWORDS
        }.ifEmpty { prefixTokens }

        // RULE A: Every single distinctive token from the primary title prefix MUST be present!
        // For "Spider-Man", distinctivePrefixTokens = ["spider"] -> MUST be present (rejects X-Men, Iron Man, Batman).
        // For "X-Men", distinctivePrefixTokens = ["x"] -> "x" or "xmen" MUST be present.
        for (dTok in distinctivePrefixTokens) {
            val idx = allTargetTokens.indexOf(dTok).coerceAtLeast(0)
            if (!isTokenPresent(dTok, idx, allTargetTokens)) {
                return TitleMatchScore(false, false, 0.0)
            }
        }

        // RULE B: All prefix tokens (e.g. both "spider" and "man", or compound "spiderman") must match
        val allPrefixMatched = prefixTokens.indices.all { idx ->
            isTokenPresent(prefixTokens[idx], idx, prefixTokens)
        }
        if (!allPrefixMatched) {
            return TitleMatchScore(false, false, 0.0)
        }

        // RULE C: Sequel number protection
        // If target title has an explicit sequel number (e.g. "Spider-Man 2", "Duna 2", "Anděl Páně 2"),
        // that number MUST be present in the file title tokens.
        // Conversely, if target title is a standalone unnumbered title (no colon subtitle and no number, e.g. "Spider-Man"),
        // reject files whose extracted title segment is clearly a numbered sequel ("spiderman 2", "spiderman 3").
        val targetNumbers = allTargetTokens.mapNotNull { tok ->
            ROMAN_TO_ARABIC[tok] ?: tok.takeIf { it.all(Char::isDigit) && it.length <= 2 }
        }.toSet()

        val fileTitleNumbers = fileTitleTokens.mapNotNull { tok ->
            ROMAN_TO_ARABIC[tok] ?: tok.takeIf { it.all(Char::isDigit) && it.length <= 2 }
        }.toSet()

        if (targetNumbers.isNotEmpty()) {
            if (!targetNumbers.all { it in fileTitleNumbers || it in fileTokenSet }) {
                return TitleMatchScore(false, false, 0.0)
            }
        } else if (!targetTitle.contains(':')) {
            // Unnumbered base movie (e.g. "Spider-Man", "Gladiátor", "Matrix", "Duna"):
            // If file's title portion ends with a sequel number like "2", "3", "4", reject it!
            val unexpectedSequelNumbers = fileTitleNumbers.filter { it in setOf("2", "3", "4", "5") }
            if (unexpectedSequelNumbers.isNotEmpty()) {
                return TitleMatchScore(false, false, 0.0)
            }
        }

        // RULE D: Check full title tokens (including any subtitle after ':')
        var matchedCount = 0
        for (idx in allTargetTokens.indices) {
            if (isTokenPresent(allTargetTokens[idx], idx, allTargetTokens)) {
                matchedCount++
            }
        }

        val ratio = matchedCount.toDouble() / allTargetTokens.size.toDouble()
        val isExactAll = matchedCount == allTargetTokens.size

        if (strictAllTokens) {
            // In strict mode, require either 100% of tokens or at least 75% when title has a long subtitle
            val passesStrict = isExactAll || (allTargetTokens.size >= 4 && ratio >= 0.75)
            return TitleMatchScore(passesStrict, isExactAll, ratio)
        } else {
            // In relaxed mode, prefix tokens + distinctive tokens already matched above;
            // require at least 60% overall token match
            val passesRelaxed = ratio >= 0.60
            return TitleMatchScore(passesRelaxed, isExactAll, ratio)
        }
    }

    /**
     * Extracts the movie/series title segment from a raw filename by cutting off at the first
     * technical tag (year, S01E01, 1080p, 4K, BluRay, WEB-DL, CZ, etc.).
     */
    private fun extractTitlePortionFromFilename(nameWithoutExt: String): String {
        val spaced = nameWithoutExt.replace(Regex("[._]+"), " ")
        val cutRegex = Regex(
            "(?i)\\b((19[5-9]\\d|20[0-2]\\d)|S\\d{1,2}E\\d{1,2}|\\d{1,2}x\\d{2}|2160p|1080p|1080i|720p|480p|576p|4K|UHD|BluRay|Blu-Ray|BDRip|BRRip|REMUX|WEB-?DL|WEBRip|DVDRip|HDRip|TVRip|HEVC|x264|x265|H264|H265|AVC|CZ|CZE|SK|EN|ENG|DABING|CZDAB|TITULKY|CZSUB)\\b"
        )
        val match = cutRegex.find(spaced)
        return if (match != null && match.range.first > 1) {
            spaced.substring(0, match.range.first).trim()
        } else {
            spaced.trim()
        }
    }

    /**
     * Tokenizes text into normalized lowercase alphanumeric words (preserving even 1-char tokens like "x" or "2").
     */
    private fun tokenizeNormalized(text: String): List<String> {
        val norm = normalizeTextPreservingSpaces(text)
        if (norm.isEmpty()) return emptyList()
        return norm.split(" ").filter { it.isNotEmpty() }
    }

    private fun detectQuality(name: String, sizeBytes: Long, isEpisode: Boolean): VideoQuality {
        val gb = sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return when {
            Regex("(?i)\\b(2160p|4k|uhd|3840x2160)\\b").containsMatchIn(name) -> VideoQuality.UHD_4K
            Regex("(?i)\\b(1080p|1080i|fhd|fullhd|1920x1080|bluray|bdrip|remux)\\b").containsMatchIn(name) -> {
                if (gb >= 14.0 && !Regex("(?i)\\b1080p\\b").containsMatchIn(name)) VideoQuality.UHD_4K
                else VideoQuality.FHD_1080P
            }
            Regex("(?i)\\b(720p|1280x720|hdrip)\\b").containsMatchIn(name) -> VideoQuality.HD_720P
            Regex("(?i)\\b(480p|576p|dvdrip|tvrip|xvid|divx)\\b").containsMatchIn(name) -> VideoQuality.SD
            else -> {
                // Heuristic fallback based on file size
                if (isEpisode) {
                    when {
                        gb >= 3.2 -> VideoQuality.UHD_4K
                        gb >= 1.0 -> VideoQuality.FHD_1080P
                        gb >= 0.45 -> VideoQuality.HD_720P
                        else -> VideoQuality.SD
                    }
                } else {
                    when {
                        gb >= 12.0 -> VideoQuality.UHD_4K
                        gb >= 3.0 -> VideoQuality.FHD_1080P
                        gb >= 1.35 -> VideoQuality.HD_720P
                        else -> VideoQuality.SD
                    }
                }
            }
        }
    }

    private fun detectLanguage(rawFilename: String, nameWithoutExt: String, hasLocalizedCzechTitleInFile: Boolean): AudioLanguage {
        val hasCzDubTag = Regex(
            "(?i)(?:\\b(cz|cze|ces|cesky|česky|sk|svk|slovensky|dabing|dab|czdab|skdab|dual)\\b|cz[._+-]en|en[._+-]cz)"
        ).containsMatchIn(nameWithoutExt)

        val hasSubOnlyTag = Regex(
            "(?i)\\b(czsub|cztit|titulky|cz[._ -]?sub|sub[._ -]?cz)\\b"
        ).containsMatchIn(nameWithoutExt)

        val hasCzechDiacritics = rawFilename.any { it.lowercaseChar() in CZECH_DIACRITICS }

        return when {
            hasSubOnlyTag && !Regex("(?i)\\b(dabing|czdab)\\b").containsMatchIn(nameWithoutExt) -> AudioLanguage.CZ_SUB
            hasCzDubTag || hasCzechDiacritics || hasLocalizedCzechTitleInFile -> AudioLanguage.CZ_SK
            else -> AudioLanguage.EN_ORIG
        }
    }

    private fun computeStreamScore(
        file: WebshareFile,
        quality: VideoQuality,
        language: AudioLanguage,
        ext: String,
        isHevc: Boolean,
        isSurround: Boolean,
        exactTitleMatch: Boolean,
        matchRatio: Double,
        hasExactYearInFile: Boolean,
        isEpisode: Boolean
    ): Int {
        var score = 0
        if (exactTitleMatch) score += 280
        score += (matchRatio * 120.0).toInt()
        if (hasExactYearInFile) score += 160

        // Community votes weight
        score += (file.posVotes * 18).coerceAtMost(250)
        score -= (file.negVotes * 50)

        // Container preference for ExoPlayer (MKV & MP4 stream and seek fastest)
        score += when (ext) {
            "mkv", "mp4" -> 120
            "m4v", "webm", "ts", "m2ts" -> 70
            else -> 10 // old AVI containers
        }

        if (isHevc) score += 45
        if (isSurround) score += 35

        // Sweet-spot bitrate scoring (fast start + crisp image without 80GB raw BD remux buffering)
        val gb = file.sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        score += when (quality) {
            VideoQuality.UHD_4K -> when {
                gb in 8.0..28.0 -> 140
                gb in 28.0..48.0 -> 90
                else -> 40
            }
            VideoQuality.FHD_1080P -> if (isEpisode) {
                if (gb in 0.9..3.5) 140 else 80
            } else {
                if (gb in 2.8..11.0) 140 else 85
            }
            VideoQuality.HD_720P -> if (gb in 1.1..3.2) 120 else 70
            VideoQuality.SD -> 50
        }

        score += language.rank * 30
        return score
    }

    /**
     * Selects the best stream according to user's preferred quality & language,
     * with automatic multi-stage fallback if the exact combination isn't available.
     */
    private fun selectStreamWithFallback(
        groups: List<StreamGroup>,
        preferences: UserPreferences
    ): Triple<ParsedStream?, Boolean, String?> {
        if (groups.isEmpty()) {
            return Triple(null, false, null)
        }

        val prefLang = preferences.preferredLanguage
        val prefQual = preferences.preferredQuality

        // 1. Exact match: Preferred Language + Preferred Quality
        val exactGroup = groups.firstOrNull { it.language == prefLang && it.quality == prefQual }
        if (exactGroup != null) {
            val best = exactGroup.bestStream
            return Triple(
                best,
                false,
                "Výchozí volba: ${prefLang.badge} • ${prefQual.badge} (${best.file.formattedSize})"
            )
        }

        // 2. Quality Fallback within Preferred Language
        val qualityFallbacks = VideoQuality.fallbackOrderFor(prefQual)
        for (candidateQual in qualityFallbacks) {
            val group = groups.firstOrNull { it.language == prefLang && it.quality == candidateQual }
            if (group != null) {
                val best = group.bestStream
                return Triple(
                    best,
                    true,
                    "Kvalita ${prefQual.badge} není v ${prefLang.badge} dostupná → Fallback na ${prefLang.badge} • ${candidateQual.badge} (${best.file.formattedSize})"
                )
            }
        }

        // 3. Language + Quality Fallback
        val langFallbacks = AudioLanguage.fallbackOrderFor(prefLang)
        for (candidateLang in langFallbacks) {
            for (candidateQual in qualityFallbacks) {
                val group = groups.firstOrNull { it.language == candidateLang && it.quality == candidateQual }
                if (group != null) {
                    val best = group.bestStream
                    return Triple(
                        best,
                        true,
                        "Preferovaný ${prefLang.badge} (${prefQual.badge}) nenalezen → Fallback na ${candidateLang.badge} • ${candidateQual.badge} (${best.file.formattedSize})"
                    )
                }
            }
        }

        // 4. Ultimate fallback: top available stream overall
        val fallbackBest = groups.first().bestStream
        return Triple(
            fallbackBest,
            true,
            "Fallback na nejlepší dostupný stream: ${fallbackBest.language.badge} • ${fallbackBest.quality.badge}"
        )
    }

    private fun normalizeTextPreservingSpaces(text: String): String {
        val lower = text.lowercase()
        val map = mapOf(
            'á' to 'a', 'č' to 'c', 'ď' to 'd', 'é' to 'e', 'ě' to 'e',
            'í' to 'i', 'ň' to 'n', 'ó' to 'o', 'ř' to 'r', 'š' to 's',
            'ť' to 't', 'ú' to 'u', 'ů' to 'u', 'ý' to 'y', 'ž' to 'z',
            'ľ' to 'l', 'ĺ' to 'l', 'ô' to 'o', 'ä' to 'a', 'ŕ' to 'r'
        )
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            val mapped = map[ch] ?: ch
            if (mapped in 'a'..'z' || mapped in '0'..'9') {
                sb.append(mapped)
            } else {
                sb.append(' ')
            }
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }
}
