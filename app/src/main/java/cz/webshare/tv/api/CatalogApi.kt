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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Result of a catalog search: deduplicated title cards + the raw Webshare files that were found,
 * so the caller can immediately confirm which titles are really streamable.
 */
data class CatalogSearchOutcome(
    val items: List<CatalogItem>,
    val webshareFiles: List<WebshareFile>
)

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
     * Loads kid-friendly and family sections for the "Děti" tab across a broad age range.
     */
    suspend fun loadKidsSections(tmdbApiKey: String): List<CatalogSection> =
        withContext(Dispatchers.IO) {
            val cleanKey = tmdbApiKey.trim()
            if (cleanKey.isNotEmpty()) {
                val tmdbKids = runCatching { loadTmdbKidsSections(cleanKey) }.getOrNull()
                if (!tmdbKids.isNullOrEmpty()) {
                    return@withContext tmdbKids
                }
            }
            loadCinemetaKidsSections()
        }

    /**
     * Searches movies and TV series across TMDB / Cinemeta AND clusters raw Webshare video files
     * into deduplicated title cards so even obscure Czech movies appear as single Netflix cards.
     *
     * Webshare full-text search matches file names, which are usually written without diacritics,
     * with dots instead of spaces and with compound words ("Spiderman"). We therefore query several
     * normalized variants (relevance + largest sort) and also return the raw files, so the caller can
     * instantly verify which catalog titles really exist on Webshare.
     */
    suspend fun searchCatalog(
        query: String,
        tmdbApiKey: String,
        webshareApi: WebshareApi,
        webshareToken: String?
    ): CatalogSearchOutcome = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext CatalogSearchOutcome(emptyList(), emptyList())

        val deaccented = stripDiacritics(trimmed)
        val compound = trimmed.replace(Regex("(?<=\\p{L})[-\\s](?=\\p{L})"), "")
        val webshareQueries = linkedSetOf(trimmed, deaccented)
        if (compound.length >= 4 && !compound.equals(trimmed, ignoreCase = true)) {
            webshareQueries.add(stripDiacritics(compound))
        }

        coroutineScope {
            val cleanKey = tmdbApiKey.trim()
            val dbDeferred = async {
                val primary = if (cleanKey.isNotEmpty()) {
                    runCatching { searchTmdb(trimmed, cleanKey) }.getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?: searchCinemetaAndCurated(trimmed)
                } else {
                    searchCinemetaAndCurated(trimmed)
                }
                if (primary.isEmpty() && !deaccented.equals(trimmed, ignoreCase = true)) {
                    searchCinemetaAndCurated(deaccented)
                } else {
                    primary
                }
            }

            val wsDeferred = webshareQueries.flatMap { q ->
                listOf(
                    async {
                        if (webshareToken.isNullOrEmpty()) emptyList()
                        else webshareApi.searchVideos(q, webshareToken, sort = "", limit = 80).getOrNull().orEmpty()
                    },
                    async {
                        if (webshareToken.isNullOrEmpty()) emptyList()
                        else webshareApi.searchVideos(q, webshareToken, sort = "largest", limit = 40).getOrNull().orEmpty()
                    }
                )
            }

            val dbItems = dbDeferred.await()
            val wsFiles = wsDeferred.awaitAll().flatten().distinctBy { it.ident }

            val clusteredWsItems = clusterWebshareFilesIntoCatalogItems(trimmed, wsFiles, dbItems)
            val items = (dbItems + clusteredWsItems).distinctBy {
                "${it.type}_${normalizeForComparison(it.title).replace(" ", "")}_${it.year ?: 0}"
            }
            CatalogSearchOutcome(items = items, webshareFiles = wsFiles)
        }
    }

    private fun stripDiacritics(text: String): String {
        val map = mapOf(
            'á' to 'a', 'č' to 'c', 'ď' to 'd', 'é' to 'e', 'ě' to 'e',
            'í' to 'i', 'ň' to 'n', 'ó' to 'o', 'ř' to 'r', 'š' to 's',
            'ť' to 't', 'ú' to 'u', 'ů' to 'u', 'ý' to 'y', 'ž' to 'z',
            'ľ' to 'l', 'ĺ' to 'l', 'ô' to 'o', 'ä' to 'a', 'ŕ' to 'r'
        )
        return buildString(text.length) {
            for (ch in text) {
                val lower = ch.lowercaseChar()
                val mapped = map[lower]
                if (mapped != null) append(if (ch.isUpperCase()) mapped.uppercaseChar() else mapped) else append(ch)
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
        val christmasDef = async {
            fetchTmdbList(
                "$TMDB_BASE/discover/movie?with_keywords=207317&sort_by=popularity.desc&language=cs-CZ&api_key=$apiKey",
                MediaType.MOVIE
            )
        }
        val trendingMoviesDef = async {
            fetchTmdbList("$TMDB_BASE/trending/movie/week?language=cs-CZ&api_key=$apiKey", MediaType.MOVIE)
        }
        val popularSeriesDef = async {
            fetchTmdbList("$TMDB_BASE/trending/tv/week?language=cs-CZ&api_key=$apiKey", MediaType.SERIES)
        }
        val dramaDef = async {
            fetchTmdbList(
                "$TMDB_BASE/discover/movie?with_genres=18&sort_by=popularity.desc&language=cs-CZ&api_key=$apiKey",
                MediaType.MOVIE
            )
        }
        val thrillerDef = async {
            fetchTmdbList(
                "$TMDB_BASE/discover/movie?with_genres=53&sort_by=popularity.desc&language=cs-CZ&api_key=$apiKey",
                MediaType.MOVIE
            )
        }
        val documentaryDef = async {
            fetchTmdbList(
                "$TMDB_BASE/discover/movie?with_genres=99&sort_by=popularity.desc&language=cs-CZ&api_key=$apiKey",
                MediaType.MOVIE
            )
        }
        val sciFiActionDef = async {
            fetchTmdbList(
                "$TMDB_BASE/discover/movie?with_genres=28,878&sort_by=popularity.desc&language=cs-CZ&api_key=$apiKey",
                MediaType.MOVIE
            )
        }
        val comedyDef = async {
            fetchTmdbList(
                "$TMDB_BASE/discover/movie?with_genres=35&sort_by=popularity.desc&language=cs-CZ&api_key=$apiKey",
                MediaType.MOVIE
            )
        }
        val topMoviesDef = async {
            fetchTmdbList("$TMDB_BASE/movie/top_rated?language=cs-CZ&api_key=$apiKey", MediaType.MOVIE)
        }

        val curatedChristmas = christmasMoviesSection().items
        val tmdbChristmas = christmasDef.await()
        val combinedChristmas = mergeUniqueCatalogItems(curatedChristmas, tmdbChristmas)

        buildList {
            if (combinedChristmas.isNotEmpty()) {
                add(CatalogSection("christmas_movies", "Vánoční filmy a pohádky", combinedChristmas))
            }
            val trending = trendingMoviesDef.await()
            if (trending.isNotEmpty()) add(CatalogSection("trending_movies", "Trendy filmy týdne", trending))

            val series = popularSeriesDef.await()
            if (series.isNotEmpty()) add(CatalogSection("trending_series", "Populární seriály", series))

            val drama = dramaDef.await()
            if (drama.isNotEmpty()) add(CatalogSection("drama_movies", "Drama", drama))

            val thriller = thrillerDef.await()
            if (thriller.isNotEmpty()) add(CatalogSection("thriller_movies", "Thriller a napětí", thriller))

            val docs = documentaryDef.await()
            if (docs.isNotEmpty()) add(CatalogSection("documentary_movies", "Dokumenty", docs))

            val sciFi = sciFiActionDef.await()
            if (sciFi.isNotEmpty()) add(CatalogSection("scifi_action", "Akční a Sci-Fi trháky", sciFi))

            val comedy = comedyDef.await()
            if (comedy.isNotEmpty()) add(CatalogSection("comedy_movies", "Komedie", comedy))

            val top = topMoviesDef.await()
            if (top.isNotEmpty()) add(CatalogSection("top_movies", "Nejlépe hodnocené filmy", top))
        }
    }

    private suspend fun loadTmdbKidsSections(apiKey: String): List<CatalogSection> = coroutineScope {
        val animatedMoviesDef = async {
            fetchTmdbList(
                "$TMDB_BASE/discover/movie?with_genres=16,10751&include_adult=false&sort_by=popularity.desc&language=cs-CZ&api_key=$apiKey",
                MediaType.MOVIE
            )
        }
        val familyAdventureDef = async {
            fetchTmdbList(
                "$TMDB_BASE/discover/movie?with_genres=10751,12,14&include_adult=false&certification_country=US&certification.lte=PG-13&sort_by=popularity.desc&language=cs-CZ&api_key=$apiKey",
                MediaType.MOVIE
            )
        }
        val kidsSeriesDef = async {
            fetchTmdbList(
                "$TMDB_BASE/discover/tv?with_genres=16,10751&include_adult=false&sort_by=popularity.desc&language=cs-CZ&api_key=$apiKey",
                MediaType.SERIES
            )
        }

        val animated = mergeUniqueCatalogItems(kidsCuratedAnimatedSection().items, animatedMoviesDef.await())
        val series = mergeUniqueCatalogItems(kidsCuratedSeriesSection().items, kidsSeriesDef.await())
        val adventure = familyAdventureDef.await()

        buildList {
            add(kidsCuratedCzechSection())
            if (animated.isNotEmpty()) {
                add(CatalogSection("kids_animated_movies", "Animované a rodinné filmy", animated))
            }
            if (series.isNotEmpty()) {
                add(CatalogSection("kids_series", "Dětské a rodinné seriály", series))
            }
            if (adventure.isNotEmpty()) {
                add(CatalogSection("kids_adventure", "Dobrodružství a fantasy", adventure))
            }
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
        val dramaMoviesDef = async {
            fetchCinemetaCatalog("$CINEMETA_BASE/catalog/movie/top/genre=Drama.json", MediaType.MOVIE, limit = 20)
        }
        val thrillerMoviesDef = async {
            fetchCinemetaCatalog("$CINEMETA_BASE/catalog/movie/top/genre=Thriller.json", MediaType.MOVIE, limit = 20)
        }
        val docMoviesDef = async {
            fetchCinemetaCatalog("$CINEMETA_BASE/catalog/movie/top/genre=Documentary.json", MediaType.MOVIE, limit = 20)
        }
        val comedyMoviesDef = async {
            fetchCinemetaCatalog("$CINEMETA_BASE/catalog/movie/top/genre=Comedy.json", MediaType.MOVIE, limit = 20)
        }
        val popularMoviesDef = async {
            fetchCinemetaCatalog("$CINEMETA_BASE/catalog/movie/year/year=2024.json", MediaType.MOVIE, limit = 20)
        }

        val topMovies = topMoviesDef.await()
        val topSeries = topSeriesDef.await()
        val dramaMovies = dramaMoviesDef.await()
        val thrillerMovies = thrillerMoviesDef.await()
        val docMovies = docMoviesDef.await()
        val comedyMovies = comedyMoviesDef.await()
        val recentMovies = popularMoviesDef.await()

        buildList {
            add(christmasMoviesSection())
            add(czechAndSlovakHitsSection())
            if (topMovies.isNotEmpty()) {
                add(CatalogSection("top_movies", "Trendy a nejoblíbenější filmy", topMovies))
            }
            if (dramaMovies.isNotEmpty()) {
                add(CatalogSection("drama_movies", "Drama", dramaMovies))
            }
            if (thrillerMovies.isNotEmpty()) {
                add(CatalogSection("thriller_movies", "Thriller a napětí", thrillerMovies))
            }
            if (docMovies.isNotEmpty()) {
                add(CatalogSection("documentary_movies", "Dokumenty", docMovies))
            }
            if (comedyMovies.isNotEmpty()) {
                add(CatalogSection("comedy_movies", "Komedie", comedyMovies))
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

    private suspend fun loadCinemetaKidsSections(): List<CatalogSection> = coroutineScope {
        val animatedMoviesDef = async {
            fetchCinemetaCatalog("$CINEMETA_BASE/catalog/movie/top/genre=Animation.json", MediaType.MOVIE, limit = 20)
        }
        val familyMoviesDef = async {
            fetchCinemetaCatalog("$CINEMETA_BASE/catalog/movie/top/genre=Family.json", MediaType.MOVIE, limit = 20)
        }
        val animatedSeriesDef = async {
            fetchCinemetaCatalog("$CINEMETA_BASE/catalog/series/top/genre=Animation.json", MediaType.SERIES, limit = 20)
        }

        val animatedMovies = mergeUniqueCatalogItems(kidsCuratedAnimatedSection().items, animatedMoviesDef.await())
        val familyMovies = familyMoviesDef.await()
        val animatedSeries = mergeUniqueCatalogItems(kidsCuratedSeriesSection().items, animatedSeriesDef.await())

        buildList {
            add(kidsCuratedCzechSection())
            if (animatedMovies.isNotEmpty()) {
                add(CatalogSection("kids_animated_movies", "Animované a rodinné filmy", animatedMovies))
            }
            if (familyMovies.isNotEmpty()) {
                add(CatalogSection("kids_family_movies", "Dobrodružství a fantasy", familyMovies))
            }
            if (animatedSeries.isNotEmpty()) {
                add(CatalogSection("kids_series", "Dětské a rodinné seriály", animatedSeries))
            }
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
        // Compare without spaces too, so "Spiderman" cluster is not duplicated next to the "Spider-Man" DB card
        val existingKeys = existingDbItems.flatMap {
            listOf(
                normalizeForComparison(it.title).replace(" ", ""),
                normalizeForComparison(it.originalTitle).replace(" ", "")
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
            if (norm.length < 2 || norm.replace(" ", "") in existingKeys) continue

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

    private fun mergeUniqueCatalogItems(
        primary: List<CatalogItem>,
        secondary: List<CatalogItem>
    ): List<CatalogItem> {
        val seenKeys = LinkedHashSet<String>()
        val merged = ArrayList<CatalogItem>(primary.size + secondary.size)
        for (item in primary + secondary) {
            val key = item.imdbId?.takeIf { it.isNotBlank() }
                ?: normalizeForComparison(item.title)
            if (seenKeys.add(key)) {
                merged.add(item)
            }
        }
        return merged
    }

    private fun christmasMoviesSection(): CatalogSection = CatalogSection(
        id = "christmas_movies",
        title = "Vánoční filmy a pohádky",
        items = listOf(
            CatalogItem(
                id = "xmas_tri_orisky_1973",
                imdbId = "tt0070832",
                type = MediaType.MOVIE,
                title = "Tři oříšky pro Popelku",
                originalTitle = "Tri orisky pro Popelku",
                year = 1973,
                overview = "Nejslavnější česká vánoční pohádka o chytré Popelce, třech kouzelných oříšcích a princi, kterého si musí sama získat.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0070832/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0070832/img",
                rating = 8.4,
                genres = listOf("Pohádka", "Rodinný", "Vánoční"),
                runtimeMinutes = 82
            ),
            CatalogItem(
                id = "xmas_sam_doma_1990",
                imdbId = "tt0099785",
                type = MediaType.MOVIE,
                title = "Sám doma",
                originalTitle = "Home Alone",
                year = 1990,
                overview = "Osmiletý Kevin zůstane omylem sám doma na Vánoce a musí ubránit dům před dvojicí nešikovných zlodějů.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0099785/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0099785/img",
                rating = 7.7,
                genres = listOf("Komedie", "Rodinný", "Vánoční"),
                runtimeMinutes = 103
            ),
            CatalogItem(
                id = "xmas_pelisky_1999",
                imdbId = "tt0167331",
                type = MediaType.MOVIE,
                title = "Pelíšky",
                originalTitle = "Pelisky",
                year = 1999,
                overview = "Kultovní česká vánoční komedie Jana Hřebejka o dvou sousedských rodinách v pražské vile na přelomu let 1967 a 1968.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0167331/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0167331/img",
                rating = 8.6,
                genres = listOf("Komedie", "Drama", "Vánoční"),
                runtimeMinutes = 116
            ),
            CatalogItem(
                id = "xmas_andel_pane_2005",
                imdbId = "tt0430880",
                type = MediaType.MOVIE,
                title = "Anděl Páně",
                originalTitle = "Andel Pane",
                year = 2005,
                overview = "Popletený anděl Petronel a škodolibý čert Uriáš musí na Štědrý den napravit alespoň jednoho hříšníka.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0430880/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0430880/img",
                rating = 7.8,
                genres = listOf("Pohádka", "Komedie", "Vánoční"),
                runtimeMinutes = 91
            ),
            CatalogItem(
                id = "xmas_andel_pane_2_2016",
                imdbId = "tt5981728",
                type = MediaType.MOVIE,
                title = "Anděl Páně 2",
                originalTitle = "Andel Pane 2",
                year = 2016,
                overview = "Anděl Petronel a čert Uriáš se na předvečer svatého Mikuláše vydávají na Zem najít ztracené jablko Poznání.",
                posterUrl = "https://images.metahub.space/poster/medium/tt5981728/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt5981728/img",
                rating = 7.7,
                genres = listOf("Pohádka", "Komedie", "Vánoční"),
                runtimeMinutes = 99
            ),
            CatalogItem(
                id = "xmas_s_certy_nejsou_zerty_1984",
                imdbId = "tt0089950",
                type = MediaType.MOVIE,
                title = "S čerty nejsou žerty",
                originalTitle = "S certy nejsou zerty",
                year = 1984,
                overview = "Legendární česká pohádka o poctivém Petrovi Máchalovi, chamtivé maceše a spravedlivém pekle.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0089950/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0089950/img",
                rating = 8.4,
                genres = listOf("Pohádka", "Komedie", "Rodinný"),
                runtimeMinutes = 91
            ),
            CatalogItem(
                id = "xmas_sam_doma_2_1992",
                imdbId = "tt0104431",
                type = MediaType.MOVIE,
                title = "Sám doma 2: Ztracen v New Yorku",
                originalTitle = "Home Alone 2: Lost in New York",
                year = 1992,
                overview = "Kevin na letišti nastoupí do špatného letadla a místo na Floridě prožije Vánoce sám v New Yorku.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0104431/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0104431/img",
                rating = 6.9,
                genres = listOf("Komedie", "Rodinný", "Vánoční"),
                runtimeMinutes = 120
            ),
            CatalogItem(
                id = "xmas_laska_nebeska_2003",
                imdbId = "tt0314331",
                type = MediaType.MOVIE,
                title = "Láska nebeská",
                originalTitle = "Love Actually",
                year = 2003,
                overview = "Osm propletených milostných příběhů v předvánočním Londýně plném humoru, dojetí a sváteční atmosféry.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0314331/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0314331/img",
                rating = 7.6,
                genres = listOf("Romantický", "Komedie", "Vánoční"),
                runtimeMinutes = 135
            ),
            CatalogItem(
                id = "xmas_klaus_2019",
                imdbId = "tt4729430",
                type = MediaType.MOVIE,
                title = "Klaus",
                originalTitle = "Klaus",
                year = 2019,
                overview = "Rozmazlený pošťák je poslán za polární kruh, kde se spřátelí se samotářským výrobcem hraček Klausem.",
                posterUrl = "https://images.metahub.space/poster/medium/tt4729430/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt4729430/img",
                rating = 8.2,
                genres = listOf("Animovaný", "Rodinný", "Vánoční"),
                runtimeMinutes = 96
            ),
            CatalogItem(
                id = "xmas_grinch_2018",
                imdbId = "tt2709692",
                type = MediaType.MOVIE,
                title = "Grinch",
                originalTitle = "The Grinch",
                year = 2018,
                overview = "Bručounský zelený Grinch se rozhodne zkazit obyvatelům městečka Kdosice Vánoce tím, že jim je ukradne.",
                posterUrl = "https://images.metahub.space/poster/medium/tt2709692/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt2709692/img",
                rating = 6.4,
                genres = listOf("Animovaný", "Komedie", "Vánoční"),
                runtimeMinutes = 85
            ),
            CatalogItem(
                id = "xmas_polarni_expres_2004",
                imdbId = "tt0338348",
                type = MediaType.MOVIE,
                title = "Polární expres",
                originalTitle = "The Polar Express",
                year = 2004,
                overview = "Chlapec, který pochybuje o existenci Santy, nastoupí na Štědrý večer do kouzelného vlaku mířícího na severní pól.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0338348/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0338348/img",
                rating = 6.6,
                genres = listOf("Animovaný", "Dobrodružný", "Vánoční"),
                runtimeMinutes = 100
            ),
            CatalogItem(
                id = "xmas_vanocni_koleda_2009",
                imdbId = "tt1067106",
                type = MediaType.MOVIE,
                title = "Vánoční koleda",
                originalTitle = "A Christmas Carol",
                year = 2009,
                overview = "Lakotného Ebenezera Scrooge navštíví o Vánocích tři duchové, aby mu ukázali jeho minulost, přítomnost i budoucnost.",
                posterUrl = "https://images.metahub.space/poster/medium/tt1067106/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt1067106/img",
                rating = 6.8,
                genres = listOf("Animovaný", "Rodinný", "Vánoční"),
                runtimeMinutes = 96
            )
        )
    )

    private fun kidsCuratedCzechSection(): CatalogSection = CatalogSection(
        id = "kids_curated_cz",
        title = "České pohádky a rodinné hity",
        items = listOf(
            CatalogItem(
                id = "kids_tri_orisky_1973",
                imdbId = "tt0070832",
                type = MediaType.MOVIE,
                title = "Tři oříšky pro Popelku",
                originalTitle = "Tri orisky pro Popelku",
                year = 1973,
                overview = "Klasická česká pohádka o chytré Popelce a třech kouzelných oříšcích.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0070832/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0070832/img",
                rating = 8.4,
                genres = listOf("Pohádka", "Rodinný"),
                runtimeMinutes = 82
            ),
            CatalogItem(
                id = "kids_s_certy_1984",
                imdbId = "tt0089950",
                type = MediaType.MOVIE,
                title = "S čerty nejsou žerty",
                originalTitle = "S certy nejsou zerty",
                year = 1984,
                overview = "Petr Máchal, zlá macecha a spravedlivé peklo v jedné z nejoblíbenějších českých pohádek.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0089950/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0089950/img",
                rating = 8.4,
                genres = listOf("Pohádka", "Komedie"),
                runtimeMinutes = 91
            ),
            CatalogItem(
                id = "kids_andel_pane_2005",
                imdbId = "tt0430880",
                type = MediaType.MOVIE,
                title = "Anděl Páně",
                originalTitle = "Andel Pane",
                year = 2005,
                overview = "Anděl Petronel a čert Uriáš se vydávají na Zem napravit hříšníka.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0430880/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0430880/img",
                rating = 7.8,
                genres = listOf("Pohádka", "Komedie"),
                runtimeMinutes = 91
            ),
            CatalogItem(
                id = "kids_andel_pane_2_2016",
                imdbId = "tt5981728",
                type = MediaType.MOVIE,
                title = "Anděl Páně 2",
                originalTitle = "Andel Pane 2",
                year = 2016,
                overview = "Pokračování dobrodružství anděla Petronela a čerta Uriáše při hledání ztraceného jablka.",
                posterUrl = "https://images.metahub.space/poster/medium/tt5981728/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt5981728/img",
                rating = 7.7,
                genres = listOf("Pohádka", "Rodinný"),
                runtimeMinutes = 99
            ),
            CatalogItem(
                id = "kids_lotrando_1997",
                imdbId = "tt0120735",
                type = MediaType.MOVIE,
                title = "Lotrando a Zubejda",
                originalTitle = "Lotrando a Zubejda",
                year = 1997,
                overview = "Loupežnický synek Lotrando se vydává do světa a poznává nemocnou princeznu Zubejdu.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0120735/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0120735/img",
                rating = 7.8,
                genres = listOf("Pohádka", "Komedie"),
                runtimeMinutes = 99
            ),
            CatalogItem(
                id = "kids_at_ziji_duchove_1977",
                imdbId = "tt0075705",
                type = MediaType.MOVIE,
                title = "Ať žijí duchové!",
                originalTitle = "At ziji duchove!",
                year = 1977,
                overview = "Parta dětí se snaží zachránit starý hrad Brtník a pomáhají jim při tom kouzelní duchové i trpaslíci.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0075705/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0075705/img",
                rating = 7.9,
                genres = listOf("Rodinný", "Pohádka", "Komedie"),
                runtimeMinutes = 84
            ),
            CatalogItem(
                id = "kids_tajemstvi_stare_bambitky_2011",
                imdbId = "tt2114461",
                type = MediaType.MOVIE,
                title = "Tajemství staré bambitky",
                originalTitle = "Tajemstvi stare bambitky",
                year = 2011,
                overview = "Hrnčířův učeň Jakub a vesnická dívka Anička se postaví chamtivým rádcům mladého krále.",
                posterUrl = "https://images.metahub.space/poster/medium/tt2114461/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt2114461/img",
                rating = 7.0,
                genres = listOf("Pohádka", "Rodinný"),
                runtimeMinutes = 86
            )
        )
    )

    private fun kidsCuratedAnimatedSection(): CatalogSection = CatalogSection(
        id = "kids_curated_animated",
        title = "Animované a rodinné filmy",
        items = listOf(
            CatalogItem(
                id = "kids_jak_vycvicit_draka_2010",
                imdbId = "tt0892769",
                type = MediaType.MOVIE,
                title = "Jak vycvičit draka",
                originalTitle = "How to Train Your Dragon",
                year = 2010,
                overview = "Mladý Viking Škyťák se nečekaně spřátelí se zraněným drakem Bezzubkou.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0892769/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0892769/img",
                rating = 8.1,
                genres = listOf("Animovaný", "Dobrodružný", "Rodinný"),
                runtimeMinutes = 98
            ),
            CatalogItem(
                id = "kids_zootropolis_2016",
                imdbId = "tt2948356",
                type = MediaType.MOVIE,
                title = "Zootropolis: Město zvířat",
                originalTitle = "Zootopia",
                year = 2016,
                overview = "Zaječí policistka Judy a mazaný lišák Nick vyšetřují záhadné zmizení v metropoli zvířat.",
                posterUrl = "https://images.metahub.space/poster/medium/tt2948356/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt2948356/img",
                rating = 8.0,
                genres = listOf("Animovaný", "Dobrodružný", "Komedie"),
                runtimeMinutes = 108
            ),
            CatalogItem(
                id = "kids_v_hlave_2015",
                imdbId = "tt2096673",
                type = MediaType.MOVIE,
                title = "V hlavě",
                originalTitle = "Inside Out",
                year = 2015,
                overview = "Nahlédněte do hlavy jedenáctileté Riley, kde o slovo bojují Radost, Smutek, Vztek, Strach a Nechuť.",
                posterUrl = "https://images.metahub.space/poster/medium/tt2096673/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt2096673/img",
                rating = 8.1,
                genres = listOf("Animovaný", "Rodinný", "Komedie"),
                runtimeMinutes = 95
            ),
            CatalogItem(
                id = "kids_toy_story_1995",
                imdbId = "tt0114709",
                type = MediaType.MOVIE,
                title = "Toy Story: Příběh hraček",
                originalTitle = "Toy Story",
                year = 1995,
                overview = "Kovboj Woody a vesmírný strážce Buzz Rakeťák prožívají velké dobrodružství, když si lidé nedívají.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0114709/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0114709/img",
                rating = 8.3,
                genres = listOf("Animovaný", "Dobrodružný", "Rodinný"),
                runtimeMinutes = 81
            ),
            CatalogItem(
                id = "kids_hledase_nemo_2003",
                imdbId = "tt0266543",
                type = MediaType.MOVIE,
                title = "Hledá se Nemo",
                originalTitle = "Finding Nemo",
                year = 2003,
                overview = "Bázlivý klaun očkatý Marlin se vydává přes celý oceán zachránit svého syna Nema.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0266543/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0266543/img",
                rating = 8.2,
                genres = listOf("Animovaný", "Dobrodružný", "Rodinný"),
                runtimeMinutes = 100
            ),
            CatalogItem(
                id = "kids_shrek_2001",
                imdbId = "tt0126029",
                type = MediaType.MOVIE,
                title = "Shrek",
                originalTitle = "Shrek",
                year = 2001,
                overview = "Zelený zlobr Shrek a upovídaný Oslík vyrážejí vysvobodit princeznu Fionu z dračí věže.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0126029/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0126029/img",
                rating = 7.9,
                genres = listOf("Animovaný", "Komedie", "Fantasy"),
                runtimeMinutes = 90
            ),
            CatalogItem(
                id = "kids_coco_2017",
                imdbId = "tt2380307",
                type = MediaType.MOVIE,
                title = "Coco",
                originalTitle = "Coco",
                year = 2017,
                overview = "Malý Miguel sní o hudbě a ocitne se v barevné Říši mrtvých, kde hledá rodinné tajemství.",
                posterUrl = "https://images.metahub.space/poster/medium/tt2380307/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt2380307/img",
                rating = 8.4,
                genres = listOf("Animovaný", "Rodinný", "Fantasy"),
                runtimeMinutes = 105
            )
        )
    )

    private fun kidsCuratedSeriesSection(): CatalogSection = CatalogSection(
        id = "kids_curated_series",
        title = "Dětské a rodinné seriály",
        items = listOf(
            CatalogItem(
                id = "kids_pat_a_mat_1976",
                imdbId = "tt0825995",
                type = MediaType.SERIES,
                title = "Pat a Mat",
                originalTitle = "Pat a Mat",
                year = 1976,
                overview = "Kultovní český večerníček o dvou nešikovných, ale nezdolných kutilech.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0825995/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0825995/img",
                rating = 8.4,
                genres = listOf("Animovaný", "Komedie", "Rodinný")
            ),
            CatalogItem(
                id = "kids_krkonosske_pohadky_1974",
                imdbId = "tt0324869",
                type = MediaType.SERIES,
                title = "Krkonošské pohádky",
                originalTitle = "Krkonosske pohadky",
                year = 1974,
                overview = "Krakonoš, Trautenberk, Anče, Kuba a hajný v legendárním českém večerníčku.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0324869/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0324869/img",
                rating = 8.4,
                genres = listOf("Pohádka", "Rodinný")
            ),
            CatalogItem(
                id = "kids_byl_jednou_jeden_zivot_1987",
                imdbId = "tt0284735",
                type = MediaType.SERIES,
                title = "Byl jednou jeden… život",
                originalTitle = "Il était une fois... la vie",
                year = 1987,
                overview = "Kultovní animovaný seriál, který dětem zábavně vysvětluje, jak funguje lidské tělo.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0284735/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0284735/img",
                rating = 8.4,
                genres = listOf("Animovaný", "Rodinný")
            ),
            CatalogItem(
                id = "kids_avatar_aang_2005",
                imdbId = "tt0417299",
                type = MediaType.SERIES,
                title = "Avatar: Legenda o Aangovi",
                originalTitle = "Avatar: The Last Airbender",
                year = 2005,
                overview = "Mladý vládce větru Aang se musí naučit ovládat všechny čtyři živly a přinést světu mír.",
                posterUrl = "https://images.metahub.space/poster/medium/tt0417299/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt0417299/img",
                rating = 9.3,
                genres = listOf("Animovaný", "Dobrodružný", "Fantasy")
            ),
            CatalogItem(
                id = "kids_gravity_falls_2012",
                imdbId = "tt1865718",
                type = MediaType.SERIES,
                title = "Městečko záhad",
                originalTitle = "Gravity Falls",
                year = 2012,
                overview = "Dvojčata Dipper a Mabel tráví prázdniny u prastrýčka Stana v městečku plném nadpřirozených záhad.",
                posterUrl = "https://images.metahub.space/poster/medium/tt1865718/img",
                backdropUrl = "https://images.metahub.space/background/medium/tt1865718/img",
                rating = 8.9,
                genres = listOf("Animovaný", "Dobrodružný", "Mystery")
            )
        )
    )

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
            "tt0071562" to "Kmotr II",
            "tt0468569" to "Temný rytíř",
            "tt1345836" to "Temný rytíř povstal",
            "tt0372784" to "Batman začíná",
            "tt1877830" to "Batman",
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
            "tt0109830" to "Forrest Gump",
            "tt0110912" to "Pulp Fiction: Historky z podsvětí",
            "tt0137523" to "Klub rváčů",
            "tt0120815" to "Zachraňte vojína Ryana",
            "tt0120689" to "Zelená míle",
            "tt0114369" to "Sedm",
            "tt0102926" to "Mlčení jehňátek",
            "tt0076759" to "Star Wars: Nová naděje",
            "tt0080684" to "Star Wars: Impérium vrací úder",
            "tt0086190" to "Star Wars: Návrat Jediů",
            "tt0103064" to "Terminátor 2: Den zúčtování",
            "tt0088763" to "Návrat do budoucnosti",
            "tt0110357" to "Lví král",
            "tt0245429" to "Cesta do fantazie",
            "tt6751668" to "Parazit",
            "tt7286456" to "Joker",
            "tt4154796" to "Avengers: Endgame",
            "tt4154756" to "Avengers: Infinity War",
            "tt0848228" to "Avengers",
            "tt10872600" to "Spider-Man: Bez domova",
            "tt6320628" to "Spider-Man: Daleko od domova",
            "tt2250912" to "Spider-Man: Homecoming",
            "tt0145487" to "Spider-Man",
            "tt0316654" to "Spider-Man 2",
            "tt0413300" to "Spider-Man 3",
            "tt4633694" to "Spider-Man: Paralelní světy",
            "tt9362722" to "Spider-Man: Napříč paralelními světy",
            "tt1630029" to "Avatar: The Way of Water",
            "tt0499549" to "Avatar",
            "tt1745960" to "Top Gun: Maverick",
            "tt0993846" to "Vlk z Wall Street",
            "tt0361748" to "Hanebný pancharti",
            "tt1853728" to "Nespoutaný Django",
            "tt1392190" to "Šílený Max: Zběsilá cesta",
            "tt2911666" to "John Wick",
            "tt0241527" to "Harry Potter a Kámen mudrců",
            "tt0295297" to "Harry Potter a Tajemná komnata",
            "tt0304141" to "Harry Potter a vězeň z Azkabanu",
            "tt0330373" to "Harry Potter a Ohnivý pohár",
            "tt0325980" to "Piráti z Karibiku: Prokletí Černé perly",
            "tt1190634" to "Banda",
            "tt2861424" to "Rick a Morty",
            "tt0386676" to "Kancl",
            "tt0108778" to "Přátelé",
            "tt1475582" to "Sherlock",
            "tt2442560" to "Gangy z Birminghamu",
            "tt0455275" to "Útěk z vězení",
            "tt0412142" to "Dr. House",
            "tt0944947" to "Hra o trůny",
            "tt2356777" to "Temný případ",
            "tt2802850" to "Fargo",
            "tt8111088" to "Mandalorian",
            "tt13443470" to "Wednesday",
            "tt10919420" to "Hra na oliheň"
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
