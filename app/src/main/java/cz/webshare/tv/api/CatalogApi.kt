package cz.webshare.tv.api

import cz.webshare.tv.model.CatalogItem
import cz.webshare.tv.model.CatalogSection
import cz.webshare.tv.model.EpisodeInfo
import cz.webshare.tv.model.MediaDetails
import cz.webshare.tv.model.MediaType
import cz.webshare.tv.model.SeasonInfo
import cz.webshare.tv.model.WebshareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class CatalogApi(
    private val client: OkHttpClient = WebshareApi.defaultHttpClient()
) {
    companion object {
        private const val TMDB_BASE = "https://api.themoviedb.org/3"
        private const val TMDB_IMG_POSTER = "https://image.tmdb.org/t/p/w500"
        private const val TMDB_IMG_BACKDROP = "https://image.tmdb.org/t/p/w1280"
        private const val CINEMETA_BASE = "https://v3-cinemeta.strem.io"
    }

    /**
     * Loads the Netflix-style home sections.
     * Uses TMDB (cs-CZ) if an API key is provided, otherwise seamlessly combines
     * Cinemeta live catalogs + curated Czech & World cinema localization.
     */
    suspend fun loadHomeSections(tmdbApiKey: String): List<CatalogSection> =
        withContext(Dispatchers.IO) {
            val cleanKey = tmdbApiKey.trim()
            if (cleanKey.isNotEmpty()) {
                val tmdbSections = runCatching { loadTmdbHomeSections(cleanKey) }.getOrNull()
                if (!tmdbSections.isNullOrEmpty()) {
                    return@withContext tmdbSections + listOf(czechAndSlovakHitsSection())
                }
            }
            loadCinemetaHomeSections()
        }

    /**
     * Searches movies and TV series across TMDB / Cinemeta AND clusters raw Webshare video files
     * into deduplicated title cards so even obscure Czech movies appear as single Netflix cards.
     */
    suspend fun searchCatalog(
        query: String,
        tmdbApiKey: String,
        webshareApi: WebshareApi,
        webshareToken: String?
    ): List<CatalogItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        coroutineScope {
            val cleanKey = tmdbApiKey.trim()
            val dbDeferred = async {
                if (cleanKey.isNotEmpty()) {
                    runCatching { searchTmdb(trimmed, cleanKey) }.getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?: searchCinemetaAndCurated(trimmed)
                } else {
                    searchCinemetaAndCurated(trimmed)
                }
            }

            val wsDeferred = async {
                if (!webshareToken.isNullOrEmpty()) {
                    webshareApi.searchVideos(trimmed, webshareToken, sort = "largest", limit = 60)
                        .getOrNull()
                        .orEmpty()
                } else {
                    emptyList()
                }
            }

            val dbItems = dbDeferred.await()
            val wsFiles = wsDeferred.await()

            val clusteredWsItems = clusterWebshareFilesIntoCatalogItems(trimmed, wsFiles, dbItems)
            (dbItems + clusteredWsItems).distinctBy {
                "${it.type}_${normalizeForComparison(it.title)}_${it.year ?: 0}"
            }
        }
    }

    /**
     * Fetches full details (including seasons and episodes for SERIES).
     */
    suspend fun getMediaDetails(item: CatalogItem, tmdbApiKey: String): MediaDetails =
        withContext(Dispatchers.IO) {
            val cleanKey = tmdbApiKey.trim()
            if (item.tmdbId != null && cleanKey.isNotEmpty()) {
                runCatching { getTmdbDetails(item, cleanKey) }.getOrNull()?.let { return@withContext it }
            }
            if (item.imdbId != null && item.imdbId.startsWith("tt")) {
                runCatching { getCinemetaDetails(item) }.getOrNull()?.let { return@withContext it }
            }
            // Fallback for curated or clustered items
            if (item.type == MediaType.SERIES) {
                val defaultSeasons = (1..3).map { s ->
                    SeasonInfo(seasonNumber = s, name = "$s. série", episodeCount = 10)
                }
                val episodesMap = defaultSeasons.associate { s ->
                    s.seasonNumber to (1..s.episodeCount).map { ep ->
                        EpisodeInfo(
                            seasonNumber = s.seasonNumber,
                            episodeNumber = ep,
                            title = "Epizoda $ep",
                            overview = "${item.title} – ${s.seasonNumber}. série, $ep. díl"
                        )
                    }
                }
                MediaDetails(item = item, seasons = defaultSeasons, episodesBySeason = episodesMap)
            } else {
                MediaDetails(item = item)
            }
        }

    // -------------------------------------------------------------------------
    // TMDB (cs-CZ) Implementation
    // -------------------------------------------------------------------------

    private suspend fun loadTmdbHomeSections(apiKey: String): List<CatalogSection> = coroutineScope {
        val trendingMoviesDef = async {
            fetchTmdbList("$TMDB_BASE/trending/movie/week?language=cs-CZ&api_key=$apiKey", MediaType.MOVIE)
        }
        val popularSeriesDef = async {
            fetchTmdbList("$TMDB_BASE/trending/tv/week?language=cs-CZ&api_key=$apiKey", MediaType.SERIES)
        }
        val topMoviesDef = async {
            fetchTmdbList("$TMDB_BASE/movie/top_rated?language=cs-CZ&api_key=$apiKey", MediaType.MOVIE)
        }
        val sciFiActionDef = async {
            fetchTmdbList(
                "$TMDB_BASE/discover/movie?with_genres=28,878&sort_by=popularity.desc&language=cs-CZ&api_key=$apiKey",
                MediaType.MOVIE
            )
        }

        buildList {
            val trending = trendingMoviesDef.await()
            if (trending.isNotEmpty()) add(CatalogSection("trending_movies", "Trendy filmy týdne", trending))

            val series = popularSeriesDef.await()
            if (series.isNotEmpty()) add(CatalogSection("trending_series", "Populární seriály", series))

            val sciFi = sciFiActionDef.await()
            if (sciFi.isNotEmpty()) add(CatalogSection("scifi_action", "Akční a Sci-Fi trháky", sciFi))

            val top = topMoviesDef.await()
            if (top.isNotEmpty()) add(CatalogSection("top_movies", "Nejlépe hodnocené filmy", top))
        }
    }

    private fun fetchTmdbList(url: String, defaultType: MediaType): List<CatalogItem> {
        val json = getJson(url) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()
        return parseTmdbResultsArray(results, defaultType)
    }

    private fun searchTmdb(query: String, apiKey: String): List<CatalogItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$TMDB_BASE/search/multi?query=$encoded&language=cs-CZ&include_adult=false&api_key=$apiKey"
        val json = getJson(url) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()
        return parseTmdbResultsArray(results, MediaType.MOVIE)
    }

    private fun parseTmdbResultsArray(results: JSONArray, fallbackType: MediaType): List<CatalogItem> {
        val items = mutableListOf<CatalogItem>()
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val mediaTypeStr = obj.optString("media_type", "")
            if (mediaTypeStr == "person") continue
            val type = when {
                mediaTypeStr == "tv" || obj.has("first_air_date") -> MediaType.SERIES
                else -> fallbackType
            }
            val tmdbId = obj.optInt("id", 0).takeIf { it > 0 } ?: continue
            val title = obj.optString("title").ifEmpty { obj.optString("name") }.trim()
            if (title.isEmpty()) continue
            val originalTitle = obj.optString("original_title")
                .ifEmpty { obj.optString("original_name") }
                .ifEmpty { title }
                .trim()
            val dateStr = obj.optString("release_date").ifEmpty { obj.optString("first_air_date") }
            val year = dateStr.take(4).toIntOrNull()
            val overview = obj.optString("overview", "").trim()
            val posterPath = obj.optString("poster_path", "").takeIf { it.startsWith("/") }
            val backdropPath = obj.optString("backdrop_path", "").takeIf { it.startsWith("/") }
            val rating = obj.optDouble("vote_average", 0.0).takeIf { !it.isNaN() && it > 0.0 }

            items.add(
                CatalogItem(
                    id = "tmdb_${type.name.lowercase()}_$tmdbId",
                    tmdbId = tmdbId,
                    type = type,
                    title = title,
                    originalTitle = originalTitle,
                    year = year,
                    overview = overview.ifEmpty { "$title (${year ?: ""}) – dostupné ke streamování přes Webshare VIP." },
                    posterUrl = posterPath?.let { "$TMDB_IMG_POSTER$it" },
                    backdropUrl = backdropPath?.let { "$TMDB_IMG_BACKDROP$it" } ?: posterPath?.let { "$TMDB_IMG_POSTER$it" },
                    rating = rating
                )
            )
        }
        return items
    }

    private fun getTmdbDetails(item: CatalogItem, apiKey: String): MediaDetails {
        val tmdbId = item.tmdbId ?: return MediaDetails(item)
        val endpoint = if (item.type == MediaType.SERIES) "tv" else "movie"
        val json = getJson("$TMDB_BASE/$endpoint/$tmdbId?language=cs-CZ&append_to_response=credits&api_key=$apiKey")
            ?: return MediaDetails(item)

        val genresArr = json.optJSONArray("genres")
        val genres = mutableListOf<String>()
        if (genresArr != null) {
            for (i in 0 until genresArr.length()) {
                genresArr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotEmpty() }?.let { genres.add(it) }
            }
        }
        val runtime = json.optInt("runtime", 0).takeIf { it > 0 }
        val cast = mutableListOf<String>()
        val castArr = json.optJSONObject("credits")?.optJSONArray("cast")
        if (castArr != null) {
            for (i in 0 until minOf(6, castArr.length())) {
                castArr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotEmpty() }?.let { cast.add(it) }
            }
        }

        val updatedItem = item.copy(
            genres = if (genres.isNotEmpty()) genres else item.genres,
            runtimeMinutes = runtime ?: item.runtimeMinutes,
            overview = json.optString("overview").takeIf { it.isNotEmpty() } ?: item.overview
        )

        if (item.type != MediaType.SERIES) {
            return MediaDetails(item = updatedItem, cast = cast)
        }

        val seasonsArr = json.optJSONArray("seasons")
        val seasons = mutableListOf<SeasonInfo>()
        val episodesBySeason = mutableMapOf<Int, List<EpisodeInfo>>()

        if (seasonsArr != null) {
            for (i in 0 until seasonsArr.length()) {
                val sObj = seasonsArr.optJSONObject(i) ?: continue
                val sNum = sObj.optInt("season_number", 0)
                val epCount = sObj.optInt("episode_count", 0)
                if (sNum <= 0 || epCount <= 0) continue
                val sName = sObj.optString("name").ifEmpty { "$sNum. série" }
                seasons.add(SeasonInfo(seasonNumber = sNum, name = sName, episodeCount = epCount))
            }
        }

        for (season in seasons.take(8)) {
            val sJson = getJson("$TMDB_BASE/tv/$tmdbId/season/${season.seasonNumber}?language=cs-CZ&api_key=$apiKey")
            val epArr = sJson?.optJSONArray("episodes")
            val eps = mutableListOf<EpisodeInfo>()
            if (epArr != null) {
                for (j in 0 until epArr.length()) {
                    val eObj = epArr.optJSONObject(j) ?: continue
                    val epNum = eObj.optInt("episode_number", j + 1)
                    val epName = eObj.optString("name").ifEmpty { "Epizoda $epNum" }
                    val epOverview = eObj.optString("overview", "")
                    val stillPath = eObj.optString("still_path", "").takeIf { it.startsWith("/") }
                    eps.add(
                        EpisodeInfo(
                            seasonNumber = season.seasonNumber,
                            episodeNumber = epNum,
                            title = epName,
                            overview = epOverview,
                            stillUrl = stillPath?.let { "$TMDB_IMG_POSTER$it" }
                        )
                    )
                }
            }
            if (eps.isNotEmpty()) {
                episodesBySeason[season.seasonNumber] = eps
            }
        }

        return MediaDetails(
            item = updatedItem,
            seasons = seasons,
            episodesBySeason = episodesBySeason,
            cast = cast
        )
    }

    // -------------------------------------------------------------------------
    // Zero-Key Out-of-the-Box Cinemeta + Curated Czech Localization
    // -------------------------------------------------------------------------

    private suspend fun loadCinemetaHomeSections(): List<CatalogSection> = coroutineScope {
        val topMoviesDef = async {
            fetchCinemetaCatalog("$CINEMETA_BASE/catalog/movie/top.json", MediaType.MOVIE, limit = 25)
        }
        val topSeriesDef = async {
            fetchCinemetaCatalog("$CINEMETA_BASE/catalog/series/top.json", MediaType.SERIES, limit = 25)
        }
        val popularMoviesDef = async {
            fetchCinemetaCatalog("$CINEMETA_BASE/catalog/movie/year/year=2024.json", MediaType.MOVIE, limit = 20)
        }

        val topMovies = topMoviesDef.await()
        val topSeries = topSeriesDef.await()
        val recentMovies = popularMoviesDef.await()

        buildList {
            add(czechAndSlovakHitsSection())
            if (topMovies.isNotEmpty()) {
                add(CatalogSection("top_movies", "Trendy a nejoblíbenější filmy", topMovies))
            }
            if (topSeries.isNotEmpty()) {
                add(CatalogSection("top_series", "Populární seriály", topSeries))
            }
            if (recentMovies.isNotEmpty()) {
                add(CatalogSection("recent_movies", "Filmové novinky", recentMovies))
            }
            add(czechAndSlovakSeriesSection())
        }
    }

    private fun fetchCinemetaCatalog(url: String, type: MediaType, limit: Int = 25): List<CatalogItem> {
        val json = getJson(url) ?: return emptyList()
        val metas = json.optJSONArray("metas") ?: return emptyList()
        return parseCinemetaMetas(metas, type, limit)
    }

    private fun searchCinemetaAndCurated(query: String): List<CatalogItem> {
        val normQuery = normalizeForComparison(query)
        val curatedMatches = (czechAndSlovakHitsSection().items + czechAndSlovakSeriesSection().items)
            .filter {
                normalizeForComparison(it.title).contains(normQuery) ||
                    normalizeForComparison(it.originalTitle).contains(normQuery)
            }

        val encoded = URLEncoder.encode(query, "UTF-8")
        val movies = fetchCinemetaCatalog(
            "$CINEMETA_BASE/catalog/movie/top/search=$encoded.json",
            MediaType.MOVIE,
            limit = 15
        )
        val series = fetchCinemetaCatalog(
            "$CINEMETA_BASE/catalog/series/top/search=$encoded.json",
            MediaType.SERIES,
            limit = 15
        )
        return (curatedMatches + movies + series).distinctBy { it.id }
    }

    private fun parseCinemetaMetas(metas: JSONArray, type: MediaType, limit: Int): List<CatalogItem> {
        val list = mutableListOf<CatalogItem>()
        for (i in 0 until minOf(limit, metas.length())) {
            val obj = metas.optJSONObject(i) ?: continue
            val imdbId = obj.optString("id").ifEmpty { obj.optString("imdb_id") }
            if (imdbId.isEmpty()) continue
            val rawName = obj.optString("name").trim()
            if (rawName.isEmpty()) continue

            val yearStr = obj.optString("releaseInfo").take(4)
                .ifEmpty { obj.optString("year").take(4) }
            val year = yearStr.toIntOrNull()
            val poster = obj.optString("poster").takeIf { it.startsWith("http") }
            val background = obj.optString("background").takeIf { it.startsWith("http") }
                ?: "https://images.metahub.space/background/medium/$imdbId/img"
            val rating = obj.optString("imdbRating").toDoubleOrNull()
            val description = obj.optString("description", "").trim()

            val genres = mutableListOf<String>()
            val genresArr = obj.optJSONArray("genres")
            if (genresArr != null) {
                for (g in 0 until genresArr.length()) {
                    genresArr.optString(g)?.takeIf { it.isNotEmpty() }?.let { genres.add(translateGenreToCz(it)) }
                }
            }

            val localizedTitle = localizeKnownTitle(imdbId, rawName)
            list.add(
                CatalogItem(
                    id = "imdb_$imdbId",
                    imdbId = imdbId,
                    type = type,
                    title = localizedTitle,
                    originalTitle = rawName,
                    year = year,
                    overview = description.ifEmpty {
                        "$localizedTitle (${year ?: ""}) – vyhledej a přehraj nejlepší dostupný stream z Webshare.cz."
                    },
                    posterUrl = poster ?: "https://images.metahub.space/poster/medium/$imdbId/img",
                    backdropUrl = background,
                    rating = rating,
                    genres = genres
                )
            )
        }
        return list
    }

    private fun getCinemetaDetails(item: CatalogItem): MediaDetails {
        val imdbId = item.imdbId ?: return MediaDetails(item)
        val typeSegment = if (item.type == MediaType.SERIES) "series" else "movie"
        val json = getJson("$CINEMETA_BASE/meta/$typeSegment/$imdbId.json")
            ?: return MediaDetails(item)
        val meta = json.optJSONObject("meta") ?: return MediaDetails(item)

        val cast = mutableListOf<String>()
        val castArr = meta.optJSONArray("cast")
        if (castArr != null) {
            for (i in 0 until minOf(6, castArr.length())) {
                castArr.optString(i)?.takeIf { it.isNotEmpty() }?.let { cast.add(it) }
            }
        }
        val directorArr = meta.optJSONArray("director")
        val director = directorArr?.optString(0)?.takeIf { it.isNotEmpty() }
        val description = meta.optString("description").takeIf { it.isNotEmpty() } ?: item.overview

        val updatedItem = item.copy(overview = description)

        if (item.type != MediaType.SERIES) {
            return MediaDetails(item = updatedItem, cast = cast, director = director)
        }

        val videosArr = meta.optJSONArray("videos")
        val episodesBySeason = mutableMapOf<Int, MutableList<EpisodeInfo>>()
        if (videosArr != null) {
            for (i in 0 until videosArr.length()) {
                val vObj = videosArr.optJSONObject(i) ?: continue
                val sNum = vObj.optInt("season", 0)
                val epNum = vObj.optInt("episode", vObj.optInt("number", 0))
                if (sNum <= 0 || epNum <= 0) continue
                val epTitle = vObj.optString("name").ifEmpty { vObj.optString("title") }.ifEmpty { "Epizoda $epNum" }
                val epOverview = vObj.optString("overview").ifEmpty { vObj.optString("description", "") }
                val thumb = vObj.optString("thumbnail").takeIf { it.startsWith("http") }

                episodesBySeason.getOrPut(sNum) { mutableListOf() }.add(
                    EpisodeInfo(
                        seasonNumber = sNum,
                        episodeNumber = epNum,
                        title = epTitle,
                        overview = epOverview,
                        stillUrl = thumb
                    )
                )
            }
        }

        val sortedSeasons = episodesBySeason.keys.sorted().map { sNum ->
            val eps = episodesBySeason[sNum].orEmpty().sortedBy { it.episodeNumber }
            episodesBySeason[sNum] = eps.toMutableList()
            SeasonInfo(
                seasonNumber = sNum,
                name = "$sNum. série",
                episodeCount = eps.size
            )
        }

        return MediaDetails(
            item = updatedItem,
            seasons = sortedSeasons,
            episodesBySeason = episodesBySeason,
            cast = cast,
            director = director
        )
    }

    // -------------------------------------------------------------------------
    // Smart Webshare File -> Catalog Card Clusterer
    // -------------------------------------------------------------------------

    /**
     * Groups raw Webshare files by clean movie/series title + year so that even titles
     * not present in Hollywood databases appear as clean deduplicated cards.
     */
    private fun clusterWebshareFilesIntoCatalogItems(
        query: String,
        files: List<WebshareFile>,
        existingDbItems: List<CatalogItem>
    ): List<CatalogItem> {
        if (files.isEmpty()) return emptyList()
        val existingKeys = existingDbItems.flatMap {
            listOf(
                normalizeForComparison(it.title),
                normalizeForComparison(it.originalTitle)
            )
        }.toSet()

        data class ClusterKey(val normTitle: String, val year: Int?, val isSeries: Boolean)
        data class ClusterAccumulator(
            val displayTitle: String,
            val year: Int?,
            val isSeries: Boolean,
            var count: Int,
            var previewUrl: String?
        )

        val clusters = linkedMapOf<ClusterKey, ClusterAccumulator>()
        val videoExtensions = setOf("mkv", "mp4", "avi", "mov", "ts", "m2ts", "webm", "m4v")
        val queryTokens = normalizeForComparison(query).split(" ").filter { it.isNotEmpty() }
        val distinctiveQueryTokens = queryTokens.filter {
            it.length >= 2 && it !in setOf("man", "men", "the", "and", "film", "movie", "serial", "series")
        }.ifEmpty { queryTokens }

        for (file in files) {
            if (file.passwordProtected || file.sizeBytes < 80L * 1024 * 1024) continue
            val ext = file.name.substringAfterLast('.', "").lowercase()
            if (ext !in videoExtensions) continue

            val extracted = extractCleanTitleAndYearFromFilename(file.name) ?: continue
            val norm = normalizeForComparison(extracted.first)
            if (norm.length < 2 || norm in existingKeys) continue

            val extractedTokens = norm.split(" ").filter { it.isNotEmpty() }
            val extractedCollapsed = extractedTokens.joinToString("")
            val matchesUserQuery = distinctiveQueryTokens.all { qTok ->
                qTok in extractedTokens || extractedCollapsed.contains(qTok)
            }
            if (!matchesUserQuery) continue

            val key = ClusterKey(norm, extracted.second, extracted.third)
            val existing = clusters[key]
            if (existing == null) {
                clusters[key] = ClusterAccumulator(
                    displayTitle = extracted.first,
                    year = extracted.second,
                    isSeries = extracted.third,
                    count = 1,
                    previewUrl = file.stripeUrl
                )
            } else {
                existing.count += 1
                if (existing.previewUrl == null && file.stripeUrl != null) {
                    existing.previewUrl = file.stripeUrl
                }
            }
        }

        return clusters.entries
            .sortedByDescending { it.value.count }
            .take(10)
            .map { (key, acc) ->
                CatalogItem(
                    id = "ws_${key.normTitle}_${key.year ?: 0}",
                    type = if (acc.isSeries) MediaType.SERIES else MediaType.MOVIE,
                    title = acc.displayTitle,
                    originalTitle = acc.displayTitle,
                    year = acc.year,
                    overview = "Seskupeno z Webshare.cz (${acc.count} dostupných video streamů pro '${acc.displayTitle}'). Klikni pro výběr kvality a jazyka.",
                    posterUrl = acc.previewUrl,
                    backdropUrl = acc.previewUrl,
                    genres = listOf("Webshare Katalog")
                )
            }
    }

    private fun extractCleanTitleAndYearFromFilename(filename: String): Triple<String, Int?, Boolean>? {
        val withoutExt = filename.substringBeforeLast('.')
        val isSeries = Regex("(?i)\\b(S\\d{1,2}E\\d{1,2}|\\d{1,2}x\\d{1,2})\\b").containsMatchIn(withoutExt)

        // Replace dots/underscores with spaces
        val spaced = withoutExt.replace(Regex("[._]+"), " ")

        // Find year (1950..2027) or S01E01 cut-off point
        val cutRegex = Regex("(?i)\\b((19[5-9]\\d|20[0-2]\\d)|S\\d{1,2}E\\d{1,2}|2160p|1080p|720p|480p|4K|UHD|BluRay|WEB-?DL|WEBRip|BDRip|DVDRip|HDRip|HEVC|x264|x265|H264|H265|CZ|SK|EN|CZE|DABING)\\b")
        val match = cutRegex.find(spaced)
        val yearMatch = Regex("\\b(19[5-9]\\d|20[0-2]\\d)\\b").find(spaced)
        val year = yearMatch?.value?.toIntOrNull()

        val rawTitle = if (match != null && match.range.first > 1) {
            spaced.substring(0, match.range.first)
        } else {
            spaced.take(45)
        }.replace(Regex("[\\[\\]()]"), " ").trim().replace(Regex("\\s+"), " ")

        if (rawTitle.length < 2) return null
        return Triple(rawTitle, year, isSeries)
    }

    // -------------------------------------------------------------------------
    // Curated Czech & Slovak Hits (Works Immediately Out-of-the-Box)
    // -------------------------------------------------------------------------

    private fun czechAndSlovakHitsSection(): CatalogSection = CatalogSection(
        id = "cz_sk_hits",
        title = "České a slovenské filmové hity",
        items = listOf(
            CatalogItem(
                id = "cz_vlny_2024",
                imdbId = "tt27729779",
                type = MediaType.MOVIE,
                title = "Vlny",
                originalTitle = "Vlny",
                year = 2024,
                overview = "Dobový thriller Jiřího Mádla inspirovaný skutečnými příběhy novinářů Československého rozhlasu na konci 60. let.",
                posterUrl = "https://images.metahub.space/poster/medium/tt27729779/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt27729779/img",
                rating = 8.4,
                genres = listOf("Historický", "Drama", "Thriller"),
                runtimeMinutes = 131
            ),
            CatalogItem(
                id = "cz_pelisky_1999",
                imdbId = "tt0167331",
                type = MediaType.MOVIE,
                title = "Pelíšky",
                originalTitle = "Pelisky",
                year = 1999,
                overview = "Kultovní česká komedie Jana Hřebejka o dvou sousedských rodinách v pražské vile na přelomu let 1967 a 1968.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0167331/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0167331/img",
                rating = 8.6,
                genres = listOf("Komedie", "Drama"),
                runtimeMinutes = 116
            ),
            CatalogItem(
                id = "cz_bod_obnovy_2023",
                imdbId = "tt15189056",
                type = MediaType.MOVIE,
                title = "Bod obnovy",
                originalTitle = "Restore Point",
                year = 2023,
                overview = "Strhující české sci-fi z roku 2041, kde má každý občan ústavní právo být po násilné smrti oživen ze zálohy.",
                posterUrl = "https://images.metahub.space/poster/medium/tt15189056/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt15189056/img",
                rating = 7.2,
                genres = listOf("Sci-Fi", "Krimi", "Thriller"),
                runtimeMinutes = 111
            ),
            CatalogItem(
                id = "cz_samotari_2000",
                imdbId = "tt0240879",
                type = MediaType.MOVIE,
                title = "Samotáři",
                originalTitle = "Samotari",
                year = 2000,
                overview = "Příběhy sedmi mladých lidí v Praze na konci 90. let, jejichž osudy se nečekaně proplétají.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0240879/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0240879/img",
                rating = 8.1,
                genres = listOf("Komedie", "Drama"),
                runtimeMinutes = 103
            ),
            CatalogItem(
                id = "cz_andel_pane_2_2016",
                imdbId = "tt5981728",
                type = MediaType.MOVIE,
                title = "Anděl Páně 2",
                originalTitle = "Andel Pane 2",
                year = 2016,
                overview = "Anděl Petronel a čert Uriáš se vydávají na Zem najít ztracené jablko ze stromu Poznání.",
                posterUrl = "https://images.metahub.space/poster/medium/tt5981728/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt5981728/img",
                rating = 7.7,
                genres = listOf("Pohádka", "Komedie", "Rodinný"),
                runtimeMinutes = 99
            ),
            CatalogItem(
                id = "cz_vratne_lahve_2007",
                imdbId = "tt0809951",
                type = MediaType.MOVIE,
                title = "Vratné lahve",
                originalTitle = "Vratne lahve",
                year = 2007,
                overview = "Laskavá komedie Zdeňka a Jana Svěrákových o stárnoucím učiteli, který nastoupí do výkupu lahví.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0809951/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0809951/img",
                rating = 7.6,
                genres = listOf("Komedie", "Drama"),
                runtimeMinutes = 104
            ),
            CatalogItem(
                id = "cz_zatopek_2021",
                imdbId = "tt7938424",
                type = MediaType.MOVIE,
                title = "Zátopek",
                originalTitle = "Zatopek",
                year = 2021,
                overview = "Životopisné drama o legendárním československém běžci Emilu Zátopkovi a jeho ženě Daně.",
                posterUrl = "https://images.metahub.space/poster/medium/tt7938424/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt7938424/img",
                rating = 7.8,
                genres = listOf("Životopisný", "Drama", "Sportovní"),
                runtimeMinutes = 131
            )
        )
    )

    private fun czechAndSlovakSeriesSection(): CatalogSection = CatalogSection(
        id = "cz_sk_series",
        title = "Oblíbené české seriály",
        items = listOf(
            CatalogItem(
                id = "cz_most_2019",
                imdbId = "tt9348692",
                type = MediaType.SERIES,
                title = "MOST!",
                originalTitle = "MOST!",
                year = 2019,
                overview = "Nekorektní komediální seriál Jana Prušinovského a Petra Kolečka ze severočeského Mostu. Dycky Most!",
                posterUrl = "https://images.metahub.space/poster/medium/tt9348692/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt9348692/img",
                rating = 8.4,
                genres = listOf("Komedie", "Drama")
            ),
            CatalogItem(
                id = "cz_pustina_2016",
                imdbId = "tt5459168",
                type = MediaType.SERIES,
                title = "Pustina",
                originalTitle = "Wasteland",
                year = 2016,
                overview = "Temná dramatická minisérie HBO odehrávající se v severočeské vesnici na hranici těžebních limitů.",
                posterUrl = "https://images.metahub.space/poster/medium/tt5459168/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt5459168/img",
                rating = 8.2,
                genres = listOf("Krimi", "Drama", "Mystery")
            ),
            CatalogItem(
                id = "cz_okresni_prebor_2010",
                imdbId = "tt1728445",
                type = MediaType.SERIES,
                title = "Okresní přebor",
                originalTitle = "Okresni prebor",
                year = 2010,
                overview = "Kultovní komediální seriál z prostředí venkovského fotbalového klubu Slavoj Houslice.",
                posterUrl = "https://images.metahub.space/poster/medium/tt1728445/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt1728445/img",
                rating = 8.5,
                genres = listOf("Komedie", "Sportovní")
            ),
            CatalogItem(
                id = "cz_devadesatky_2022",
                imdbId = "tt16419036",
                type = MediaType.SERIES,
                title = "Devadesátky",
                originalTitle = "Devadesatky",
                year = 2022,
                overview = "Kriminální minisérie o vyšetřování nejznámějších případů pražské mordparty v divokých 90. letech.",
                posterUrl = "https://images.metahub.space/poster/medium/tt16419036/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt16419036/img",
                rating = 8.4,
                genres = listOf("Krimi", "Historický")
            )
        )
    )

    private fun localizeKnownTitle(imdbId: String, originalName: String): String {
        val map = mapOf(
            "tt15239678" to "Duna: Část druhá",
            "tt1160419" to "Duna",
            "tt15398776" to "Oppenheimer",
            "tt9218128" to "Gladiátor II",
            "tt0172495" to "Gladiátor",
            "tt6263850" to "Deadpool & Wolverine",
            "tt12037194" to "Furiosa: Sága Šíleného Maxe",
            "tt11389872" to "Království Planety opic",
            "tt22022452" to "V hlavě 2",
            "tt0111161" to "Vykoupení z věznice Shawshank",
            "tt0068646" to "Kmotr",
            "tt0468569" to "Temný rytíř",
            "tt0108052" to "Schindlerův seznam",
            "tt0167260" to "Pán prstenů: Návrat krále",
            "tt0120737" to "Pán prstenů: Společenstvo Prstenu",
            "tt0167261" to "Pán prstenů: Dvě věže",
            "tt1375666" to "Počátek",
            "tt0133093" to "Matrix",
            "tt0816692" to "Interstellar",
            "tt0903747" to "Perníkový táta",
            "tt0944947" to "Hra o trůny",
            "tt11198330" to "Rod draka",
            "tt4574334" to "Stranger Things",
            "tt5180504" to "Zaklínač",
            "tt3581920" to "The Last of Us",
            "tt12637874" to "Fallout",
            "tt2788316" to "Šógun",
            "tt7366338" to "Černobyl",
            "tt0109830" to "Forrest Gump"
        )
        return map[imdbId] ?: originalName
    }

    private fun translateGenreToCz(en: String): String = when (en.lowercase()) {
        "action" -> "Akční"
        "adventure" -> "Dobrodružný"
        "animation" -> "Animovaný"
        "biography" -> "Životopisný"
        "comedy" -> "Komedie"
        "crime" -> "Krimi"
        "documentary" -> "Dokument"
        "drama" -> "Drama"
        "family" -> "Rodinný"
        "fantasy" -> "Fantasy"
        "history" -> "Historický"
        "horror" -> "Horor"
        "mystery" -> "Mystery"
        "romance" -> "Romantický"
        "sci-fi", "science fiction" -> "Sci-Fi"
        "thriller" -> "Thriller"
        "war" -> "Válečný"
        "western" -> "Western"
        else -> en
    }

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "WebshareTV/1.0")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                JSONObject(body)
            }
        }.getOrNull()
    }

    private fun normalizeForComparison(text: String): String {
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
