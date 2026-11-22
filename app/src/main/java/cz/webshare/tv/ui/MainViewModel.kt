package cz.webshare.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.webshare.tv.api.CatalogApi
import cz.webshare.tv.api.WebshareApi
import cz.webshare.tv.data.PreferencesStore
import cz.webshare.tv.model.AudioLanguage
import cz.webshare.tv.model.CatalogItem
import cz.webshare.tv.model.CatalogSection
import cz.webshare.tv.model.EpisodeInfo
import cz.webshare.tv.model.LaunchMode
import cz.webshare.tv.model.MediaDetails
import cz.webshare.tv.model.MediaType
import cz.webshare.tv.model.ParsedStream
import cz.webshare.tv.model.StreamResolutionResult
import cz.webshare.tv.model.UserPreferences
import cz.webshare.tv.model.VideoQuality
import cz.webshare.tv.model.WatchProgressItem
import cz.webshare.tv.model.WebshareUserProfile
import cz.webshare.tv.stream.StreamResolver
import cz.webshare.tv.update.AppUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NavTab(val labelCz: String) {
    HOME("Domů"),
    SEARCH("Hledat"),
    MOVIES("Filmy"),
    SERIES("Seriály"),
    MY_LIST("Můj seznam"),
    SETTINGS("Nastavení")
}

data class ActivePlaybackSession(
    val item: CatalogItem,
    val episode: EpisodeInfo?,
    val nextEpisode: EpisodeInfo?,
    val activeStream: ParsedStream,
    val directVideoUrl: String,
    val resolutionResult: StreamResolutionResult?,
    val startPositionMs: Long = 0L,
    val bannerMessage: String? = null
)

data class UiState(
    val isLoggedIn: Boolean = false,
    val isAuthLoading: Boolean = false,
    val authError: String? = null,
    val savedUsername: String = "",
    val userProfile: WebshareUserProfile? = null,

    val activeTab: NavTab = NavTab.HOME,
    val isCatalogLoading: Boolean = false,
    val sections: List<CatalogSection> = emptyList(),
    val focusedHeroItem: CatalogItem? = null,

    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<CatalogItem> = emptyList(),

    val watchHistory: List<WatchProgressItem> = emptyList(),
    val myList: List<CatalogItem> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),

    // Detail & Stream Resolution State
    val activeDetailItem: CatalogItem? = null,
    val activeMediaDetails: MediaDetails? = null,
    val isDetailLoading: Boolean = false,
    val selectedSeasonNumber: Int = 1,
    val selectedEpisode: EpisodeInfo? = null,
    val isResolvingStreams: Boolean = false,
    val streamResolution: StreamResolutionResult? = null,
    val streamError: String? = null,
    val showStreamPickerModal: Boolean = false,

    // Active Video Player State
    val activePlayback: ActivePlaybackSession? = null,

    // Git Auto-Updater State (in Settings)
    val appVersionName: String = "1.2.0",
    val appVersionCode: Int = 3,
    val isUpdatingApp: Boolean = false,
    val updateProgressPercent: Int = 0,
    val updateStatusMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsStore = PreferencesStore(application.applicationContext)
    private val webshareApi = WebshareApi()
    private val catalogApi = CatalogApi()
    private val streamResolver = StreamResolver(webshareApi)
    private val appUpdater = AppUpdater()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var resolveJob: Job? = null
    private var heroFocusJob: Job? = null
    private var catalogVerificationJob: Job? = null
    private val availabilityCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    init {
        val ctx = application.applicationContext
        val currentVerCode = AppUpdater.getCurrentVersionCode(ctx)
        val currentVerName = AppUpdater.getCurrentVersionName(ctx)
        val token = prefsStore.getWebshareToken()
        val username = prefsStore.getSavedUsername()
        val userPrefs = prefsStore.getUserPreferences()
        val history = prefsStore.getWatchHistory()
        val myList = prefsStore.getMyList()

        _uiState.update {
            it.copy(
                isLoggedIn = !token.isNullOrEmpty(),
                savedUsername = username,
                preferences = userPrefs,
                watchHistory = history,
                myList = myList,
                appVersionCode = currentVerCode,
                appVersionName = currentVerName
            )
        }

        if (!token.isNullOrEmpty()) {
            refreshUserProfile(token)
            loadHomeCatalog()
        }
    }

    fun login(usernameOrEmail: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            val result = webshareApi.login(usernameOrEmail, password)
            result.onSuccess { token ->
                prefsStore.saveWebshareSession(token, usernameOrEmail.trim())
                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        isAuthLoading = false,
                        savedUsername = usernameOrEmail.trim(),
                        authError = null
                    )
                }
                refreshUserProfile(token)
                loadHomeCatalog()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authError = err.message ?: "Přihlášení k Webshare.cz selhalo."
                    )
                }
            }
        }
    }

    fun logout() {
        prefsStore.clearWebshareSession()
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                userProfile = null,
                activeDetailItem = null,
                activePlayback = null
            )
        }
    }

    private fun refreshUserProfile(token: String) {
        viewModelScope.launch {
            webshareApi.getUserProfile(token).onSuccess { profile ->
                _uiState.update { it.copy(userProfile = profile) }
            }
        }
    }

    fun loadHomeCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCatalogLoading = true) }
            val tmdbKey = _uiState.value.preferences.tmdbApiKey
            val loadedSections = catalogApi.loadHomeSections(tmdbKey)
            val hero = loadedSections.firstOrNull()?.items?.firstOrNull()
            _uiState.update {
                it.copy(
                    isCatalogLoading = false,
                    sections = loadedSections,
                    focusedHeroItem = it.focusedHeroItem ?: hero
                )
            }
            verifyAndFilterHomeSectionsOnWebshare(loadedSections)
        }
    }

    /**
     * Progressively checks catalog cards against Webshare.cz in the background and removes
     * any movie/series that has zero matching video files on Webshare, so the user never
     * clicks a card only to see "nebyly nalezeny žádné video soubory".
     */
    private fun verifyAndFilterHomeSectionsOnWebshare(initialSections: List<CatalogSection>) {
        val token = prefsStore.getWebshareToken()?.takeIf { it.isNotEmpty() } ?: return
        catalogVerificationJob?.cancel()
        catalogVerificationJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (section in initialSections) {
                val verifiedItems = mutableListOf<CatalogItem>()
                for (chunk in section.items.chunked(4)) {
                    val checks = chunk.map { item ->
                        val cached = availabilityCache[item.id]
                        if (cached != null) {
                            item to cached
                        } else {
                            val ok = runCatching {
                                streamResolver.checkAvailability(item, token)
                            }.getOrDefault(true)
                            availabilityCache[item.id] = ok
                            item to ok
                        }
                    }
                    checks.filter { it.second }.forEach { verifiedItems.add(it.first) }
                }

                if (verifiedItems.size != section.items.size && verifiedItems.isNotEmpty()) {
                    _uiState.update { state ->
                        val updatedSections = state.sections.map { sec ->
                            if (sec.id == section.id) sec.copy(items = verifiedItems) else sec
                        }
                        val currentHero = state.focusedHeroItem
                        val heroStillAvailable = currentHero == null ||
                            availabilityCache[currentHero.id] != false
                        val nextHero = if (heroStillAvailable) {
                            currentHero
                        } else {
                            updatedSections.firstOrNull()?.items?.firstOrNull()
                        }
                        state.copy(
                            sections = updatedSections,
                            focusedHeroItem = nextHero
                        )
                    }
                }
            }
        }
    }

    fun selectTab(tab: NavTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                activeDetailItem = null,
                showStreamPickerModal = false
            )
        }
    }

    fun updateFocusedHero(item: CatalogItem) {
        if (_uiState.value.focusedHeroItem?.id == item.id) return
        heroFocusJob?.cancel()
        heroFocusJob = viewModelScope.launch {
            delay(140L)
            _uiState.update { it.copy(focusedHeroItem = item) }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
        searchJob?.cancel()
        val trimmed = newQuery.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(isSearching = false, searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            performSearch(trimmed)
        }
    }

    fun triggerImmediateSearch() {
        val q = _uiState.value.searchQuery.trim()
        if (q.isNotEmpty()) {
            searchJob?.cancel()
            viewModelScope.launch { performSearch(q) }
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isSearching = true) }
        val token = prefsStore.getWebshareToken()
        val outcome = catalogApi.searchCatalog(
            query = query,
            tmdbApiKey = _uiState.value.preferences.tmdbApiKey,
            webshareApi = webshareApi,
            webshareToken = token
        )

        // Filter and prioritize search results so only titles actually available on Webshare are shown
        val verifiedResults = if (!token.isNullOrEmpty() && outcome.webshareFiles.isNotEmpty()) {
            val fastMatched = outcome.items.filter { item ->
                if (item.id.startsWith("ws_")) {
                    true
                } else {
                    val count = streamResolver.countMatchingFiles(item, outcome.webshareFiles)
                    if (count > 0) {
                        availabilityCache[item.id] = true
                        true
                    } else {
                        false
                    }
                }
            }

            // For top DB items whose localized/original title differed from the user's raw query,
            // run a quick direct availability check so valid hits are never dropped.
            val uncheckedCandidates = outcome.items
                .filter { it !in fastMatched && !it.id.startsWith("ws_") }
                .take(4)
            val extraVerified = uncheckedCandidates.filter { candidate ->
                val cached = availabilityCache[candidate.id]
                val ok = cached ?: runCatching {
                    streamResolver.checkAvailability(candidate, token)
                }.getOrDefault(false)
                availabilityCache[candidate.id] = ok
                ok
            }
            (fastMatched + extraVerified).distinctBy { it.id }
        } else {
            outcome.items
        }

        _uiState.update {
            it.copy(
                isSearching = false,
                searchResults = verifiedResults
            )
        }
    }

    /**
     * Launches playback of a [CatalogItem] directly from the Home Hero Banner without forcing the user
     * to open the detail screen first (for movies, or when launchMode is AUTO_PREFERRED_WITH_FALLBACK).
     */
    fun quickPlayFromHero(item: CatalogItem) {
        if (item.type == MediaType.SERIES || _uiState.value.preferences.launchMode == LaunchMode.ALWAYS_ASK) {
            openMediaDetail(item)
            return
        }
        resolveJob?.cancel()
        _uiState.update {
            it.copy(
                activeDetailItem = item,
                activeMediaDetails = MediaDetails(item),
                isDetailLoading = false,
                selectedSeasonNumber = 1,
                selectedEpisode = null,
                streamResolution = null,
                streamError = null,
                showStreamPickerModal = false
            )
        }
        resolveStreamsForCurrentSelection(autoLaunchWhenDone = true, openModalWhenDone = false)
    }

    fun openMediaDetail(item: CatalogItem) {
        resolveJob?.cancel()
        _uiState.update {
            it.copy(
                activeDetailItem = item,
                activeMediaDetails = MediaDetails(item),
                isDetailLoading = true,
                selectedSeasonNumber = 1,
                selectedEpisode = null,
                streamResolution = null,
                streamError = null,
                showStreamPickerModal = false
            )
        }

        viewModelScope.launch {
            val details = catalogApi.getMediaDetails(item, _uiState.value.preferences.tmdbApiKey)
            val firstSeason = details.seasons.firstOrNull()?.seasonNumber ?: 1
            val firstEpisode = details.episodesBySeason[firstSeason]?.firstOrNull()

            _uiState.update {
                it.copy(
                    activeMediaDetails = details,
                    isDetailLoading = false,
                    selectedSeasonNumber = firstSeason,
                    selectedEpisode = firstEpisode
                )
            }

            // Pre-resolve streams in the background so playback starts immediately
            resolveStreamsForCurrentSelection(autoLaunchWhenDone = false, openModalWhenDone = false)
        }
    }

    fun closeMediaDetail() {
        resolveJob?.cancel()
        _uiState.update {
            it.copy(
                activeDetailItem = null,
                activeMediaDetails = null,
                streamResolution = null,
                streamError = null,
                showStreamPickerModal = false,
                isResolvingStreams = false
            )
        }
    }

    fun selectSeason(seasonNumber: Int) {
        val details = _uiState.value.activeMediaDetails ?: return
        val firstEp = details.episodesBySeason[seasonNumber]?.firstOrNull()
        _uiState.update {
            it.copy(
                selectedSeasonNumber = seasonNumber,
                selectedEpisode = firstEp,
                streamResolution = null,
                streamError = null
            )
        }
        resolveStreamsForCurrentSelection(autoLaunchWhenDone = false, openModalWhenDone = false)
    }

    fun selectEpisode(episode: EpisodeInfo, autoPlay: Boolean) {
        _uiState.update {
            it.copy(
                selectedSeasonNumber = episode.seasonNumber,
                selectedEpisode = episode,
                streamResolution = null,
                streamError = null
            )
        }
        if (autoPlay) {
            onPrimaryPlayClicked(episodeOverride = episode)
        } else {
            resolveStreamsForCurrentSelection(autoLaunchWhenDone = false, openModalWhenDone = false)
        }
    }

    /**
     * Triggered when user clicks the primary "▶ Přehrát" button.
     * Respects UserPreferences:
     * - AUTO_PREFERRED_WITH_FALLBACK: automatically starts recommended stream (with fallback notice).
     * - ALWAYS_ASK: opens the grouped Quality x Language modal.
     */
    fun onPrimaryPlayClicked(episodeOverride: EpisodeInfo? = null) {
        val state = _uiState.value
        val item = state.activeDetailItem ?: return
        val episode = episodeOverride ?: state.selectedEpisode
        val existingResolution = state.streamResolution

        // Check if we already have resolved streams for this selection
        if (existingResolution != null && episodeOverride == null && !state.isResolvingStreams) {
            if (existingResolution.allStreams.isEmpty()) {
                _uiState.update {
                    it.copy(streamError = "Na Webshare.cz nebyly pro '${item.title}' nalezeny žádné video streamy.")
                }
                return
            }
            if (state.preferences.launchMode == LaunchMode.ALWAYS_ASK) {
                _uiState.update { it.copy(showStreamPickerModal = true) }
            } else {
                val targetStream = existingResolution.recommendedStream ?: existingResolution.allStreams.first()
                startStreamPlayback(
                    item = item,
                    episode = episode,
                    stream = targetStream,
                    resolution = existingResolution,
                    bannerMessage = existingResolution.selectionNotice
                )
            }
            return
        }

        val shouldOpenModal = state.preferences.launchMode == LaunchMode.ALWAYS_ASK
        resolveStreamsForCurrentSelection(
            autoLaunchWhenDone = !shouldOpenModal,
            openModalWhenDone = shouldOpenModal
        )
    }

    fun openStreamPickerModal() {
        val state = _uiState.value
        if (state.streamResolution != null && !state.isResolvingStreams) {
            _uiState.update { it.copy(showStreamPickerModal = true) }
        } else {
            resolveStreamsForCurrentSelection(autoLaunchWhenDone = false, openModalWhenDone = true)
        }
    }

    fun closeStreamPickerModal() {
        _uiState.update { it.copy(showStreamPickerModal = false) }
    }

    private fun resolveStreamsForCurrentSelection(
        autoLaunchWhenDone: Boolean,
        openModalWhenDone: Boolean
    ) {
        val state = _uiState.value
        val item = state.activeDetailItem ?: return
        val episode = if (item.type == MediaType.SERIES) state.selectedEpisode else null
        val token = prefsStore.getWebshareToken() ?: return

        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResolvingStreams = true,
                    streamError = null
                )
            }

            val resolution = streamResolver.resolveStreams(
                item = item,
                episode = episode,
                token = token,
                preferences = _uiState.value.preferences
            )

            _uiState.update {
                it.copy(
                    isResolvingStreams = false,
                    streamResolution = resolution,
                    showStreamPickerModal = openModalWhenDone && resolution.allStreams.isNotEmpty(),
                    streamError = if (resolution.allStreams.isEmpty()) {
                        "Pro '${resolution.queryTitle}' nebyly na Webshare nalezeny žádné video soubory."
                    } else null
                )
            }

            if (autoLaunchWhenDone && resolution.recommendedStream != null) {
                startStreamPlayback(
                    item = item,
                    episode = episode,
                    stream = resolution.recommendedStream,
                    resolution = resolution,
                    bannerMessage = resolution.selectionNotice
                )
            }
        }
    }

    /**
     * Resolves the Webshare `file_link` VIP URL and launches the Media3 ExoPlayer screen.
     */
    fun startStreamPlayback(
        item: CatalogItem,
        episode: EpisodeInfo?,
        stream: ParsedStream,
        resolution: StreamResolutionResult?,
        bannerMessage: String? = null
    ) {
        val token = prefsStore.getWebshareToken() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingStreams = true, streamError = null, showStreamPickerModal = false) }
            val urlResult = webshareApi.getDirectStreamUrl(stream.file.ident, token)
            urlResult.onSuccess { directUrl ->
                // Check saved resume position
                val savedProgress = _uiState.value.watchHistory.firstOrNull {
                    it.mediaId == item.id &&
                        it.seasonNumber == episode?.seasonNumber &&
                        it.episodeNumber == episode?.episodeNumber
                }
                val nextEp = computeNextEpisode(episode)

                _uiState.update {
                    it.copy(
                        isResolvingStreams = false,
                        activePlayback = ActivePlaybackSession(
                            item = item,
                            episode = episode,
                            nextEpisode = nextEp,
                            activeStream = stream,
                            directVideoUrl = directUrl,
                            resolutionResult = resolution,
                            startPositionMs = savedProgress?.positionMs ?: 0L,
                            bannerMessage = bannerMessage
                                ?: "${stream.language.badge} • ${stream.quality.badge} (${stream.file.formattedSize})"
                        )
                    )
                }
            }.onFailure { err ->
                // Automatic stream link fallback if another stream exists
                val alternatives = resolution?.allStreams?.filter { it.file.ident != stream.file.ident }.orEmpty()
                if (alternatives.isNotEmpty()) {
                    val fallbackStream = alternatives.first()
                    val secondTry = webshareApi.getDirectStreamUrl(fallbackStream.file.ident, token)
                    secondTry.onSuccess { fallbackUrl ->
                        _uiState.update {
                            it.copy(
                                isResolvingStreams = false,
                                activePlayback = ActivePlaybackSession(
                                    item = item,
                                    episode = episode,
                                    nextEpisode = computeNextEpisode(episode),
                                    activeStream = fallbackStream,
                                    directVideoUrl = fallbackUrl,
                                    resolutionResult = resolution,
                                    startPositionMs = 0L,
                                    bannerMessage = "Primární soubor nebyl dostupný → Automatický fallback na ${fallbackStream.language.badge} • ${fallbackStream.quality.badge}"
                                )
                            )
                        }
                        return@launch
                    }
                }
                _uiState.update {
                    it.copy(
                        isResolvingStreams = false,
                        streamError = err.message ?: "Nepodařilo se získat VIP streamovací odkaz z Webshare.cz."
                    )
                }
            }
        }
    }

    /**
     * Switches to a different quality/language stream while already inside the video player,
     * preserving the current playback position!
     */
    fun switchStreamInPlayer(newStream: ParsedStream, currentPositionMs: Long) {
        val currentSession = _uiState.value.activePlayback ?: return
        val token = prefsStore.getWebshareToken() ?: return
        viewModelScope.launch {
            webshareApi.getDirectStreamUrl(newStream.file.ident, token).onSuccess { newUrl ->
                _uiState.update {
                    it.copy(
                        activePlayback = currentSession.copy(
                            activeStream = newStream,
                            directVideoUrl = newUrl,
                            startPositionMs = currentPositionMs,
                            bannerMessage = "Přepnuto na: ${newStream.language.badge} • ${newStream.quality.badge} (${newStream.file.formattedSize})"
                        )
                    )
                }
            }
        }
    }

    /**
     * Called automatically if ExoPlayer encounters a corrupt/unsupported container on the 1st stream.
     */
    fun onPlaybackErrorFallback(currentPositionMs: Long) {
        val session = _uiState.value.activePlayback ?: return
        val allStreams = session.resolutionResult?.allStreams.orEmpty()
        val currentIdx = allStreams.indexOfFirst { it.file.ident == session.activeStream.file.ident }
        val nextCandidate = allStreams.getOrNull(currentIdx + 1)
        if (nextCandidate != null) {
            switchStreamInPlayer(nextCandidate, currentPositionMs)
        }
    }

    fun playNextEpisodeFromPlayer() {
        val session = _uiState.value.activePlayback ?: return
        val nextEp = session.nextEpisode ?: return
        _uiState.update {
            it.copy(
                activePlayback = null,
                selectedSeasonNumber = nextEp.seasonNumber,
                selectedEpisode = nextEp,
                streamResolution = null
            )
        }
        onPrimaryPlayClicked(episodeOverride = nextEp)
    }

    fun resumeFromWatchHistory(historyItem: WatchProgressItem, startFromBeginning: Boolean = false) {
        val token = prefsStore.getWebshareToken() ?: return
        val catalogItem = CatalogItem(
            id = historyItem.mediaId,
            type = historyItem.mediaType,
            title = historyItem.title,
            originalTitle = historyItem.originalTitle,
            year = historyItem.year,
            overview = historyItem.subtitle ?: "",
            posterUrl = historyItem.posterUrl,
            backdropUrl = historyItem.backdropUrl
        )
        val episode = if (historyItem.seasonNumber != null && historyItem.episodeNumber != null) {
            EpisodeInfo(
                seasonNumber = historyItem.seasonNumber,
                episodeNumber = historyItem.episodeNumber,
                title = historyItem.subtitle ?: "Epizoda ${historyItem.episodeNumber}",
                overview = ""
            )
        } else null

        val targetStartMs = if (startFromBeginning) 0L else historyItem.positionMs

        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingStreams = true) }
            webshareApi.getDirectStreamUrl(historyItem.ident, token).onSuccess { directUrl ->
                val dummyStream = ParsedStream(
                    file = cz.webshare.tv.model.WebshareFile(
                        ident = historyItem.ident,
                        name = historyItem.streamFileName,
                        sizeBytes = historyItem.sizeBytes,
                        posVotes = 0,
                        negVotes = 0,
                        passwordProtected = false
                    ),
                    quality = _uiState.value.preferences.preferredQuality,
                    language = _uiState.value.preferences.preferredLanguage,
                    isHdr = false,
                    isDolbyVision = false,
                    isHevc = false,
                    isSurround51 = false,
                    extension = "MKV",
                    score = 100
                )
                _uiState.update {
                    it.copy(
                        isResolvingStreams = false,
                        activePlayback = ActivePlaybackSession(
                            item = catalogItem,
                            episode = episode,
                            nextEpisode = null,
                            activeStream = dummyStream,
                            directVideoUrl = directUrl,
                            resolutionResult = null,
                            startPositionMs = targetStartMs,
                            bannerMessage = if (startFromBeginning) {
                                "Spuštěno od začátku"
                            } else {
                                "Pokračování od ${historyItem.formattedPosition}"
                            }
                        )
                    )
                }
            }.onFailure {
                // If old file ident was deleted on Webshare, open detail and resolve fresh streams
                _uiState.update { it.copy(isResolvingStreams = false) }
                openMediaDetail(catalogItem)
            }
        }
    }

    fun removeFromWatchHistory(historyItem: WatchProgressItem) {
        val updated = prefsStore.removeWatchHistoryItem(historyItem)
        _uiState.update { it.copy(watchHistory = updated) }
    }

    fun markWatchHistoryAsWatched(historyItem: WatchProgressItem) {
        val updated = prefsStore.removeWatchHistoryItem(historyItem)
        _uiState.update { it.copy(watchHistory = updated) }
    }

    fun openDetailFromWatchHistory(historyItem: WatchProgressItem) {
        val imdbId = historyItem.mediaId.removePrefix("imdb_").takeIf { historyItem.mediaId.startsWith("imdb_") }
        val tmdbId = historyItem.mediaId
            .substringAfterLast('_')
            .toIntOrNull()
            ?.takeIf { historyItem.mediaId.startsWith("tmdb_") }
        val catalogItem = CatalogItem(
            id = historyItem.mediaId,
            imdbId = imdbId,
            tmdbId = tmdbId,
            type = historyItem.mediaType,
            title = historyItem.title,
            originalTitle = historyItem.originalTitle,
            year = historyItem.year,
            overview = historyItem.subtitle ?: "",
            posterUrl = historyItem.posterUrl,
            backdropUrl = historyItem.backdropUrl
        )
        openMediaDetail(catalogItem)
    }

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long) {
        val session = _uiState.value.activePlayback ?: return
        if (durationMs <= 0L) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val progressItem = WatchProgressItem(
                mediaId = session.item.id,
                mediaType = session.item.type,
                title = session.item.title,
                originalTitle = session.item.originalTitle,
                year = session.item.year,
                subtitle = session.episode?.let { "${it.code} – ${it.title}" },
                posterUrl = session.item.posterUrl,
                backdropUrl = session.item.backdropUrl,
                ident = session.activeStream.file.ident,
                streamFileName = session.activeStream.file.name,
                sizeBytes = session.activeStream.file.sizeBytes,
                positionMs = positionMs,
                durationMs = durationMs,
                seasonNumber = session.episode?.seasonNumber,
                episodeNumber = session.episode?.episodeNumber
            )
            prefsStore.saveWatchProgress(progressItem)
            val updatedHistory = prefsStore.getWatchHistory()
            _uiState.update { it.copy(watchHistory = updatedHistory) }
        }
    }

    fun closePlayer() {
        _uiState.update { it.copy(activePlayback = null) }
    }

    fun toggleFavorite(item: CatalogItem) {
        val updated = prefsStore.toggleMyList(item)
        _uiState.update { it.copy(myList = updated) }
    }

    fun updatePreferredQuality(quality: VideoQuality) {
        val updated = _uiState.value.preferences.copy(preferredQuality = quality)
        prefsStore.saveUserPreferences(updated)
        _uiState.update { it.copy(preferences = updated) }
    }

    fun updatePreferredLanguage(language: AudioLanguage) {
        val updated = _uiState.value.preferences.copy(preferredLanguage = language)
        prefsStore.saveUserPreferences(updated)
        _uiState.update { it.copy(preferences = updated) }
    }

    fun updateLaunchMode(mode: LaunchMode) {
        val updated = _uiState.value.preferences.copy(launchMode = mode)
        prefsStore.saveUserPreferences(updated)
        _uiState.update { it.copy(preferences = updated) }
    }

    fun updateTmdbApiKey(apiKey: String) {
        val updated = _uiState.value.preferences.copy(tmdbApiKey = apiKey.trim())
        prefsStore.saveUserPreferences(updated)
        _uiState.update { it.copy(preferences = updated) }
        loadHomeCatalog()
    }

    /**
     * Checks `https://raw.githubusercontent.com/pagerek/mojetelka/main/version.json`
     * and if a newer versionCode is available, downloads `WebshareTV.apk` and triggers Android TV installer.
     */
    fun checkAndInstallGitUpdate() {
        if (_uiState.value.isUpdatingApp) return
        val ctx = getApplication<Application>().applicationContext
        val currentCode = AppUpdater.getCurrentVersionCode(ctx)
        val currentName = AppUpdater.getCurrentVersionName(ctx)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUpdatingApp = true,
                    updateProgressPercent = 0,
                    updateStatusMessage = "Kontroluji novou verzi v Git repozitáři (${AppUpdater.GITHUB_REPO_DISPLAY})..."
                )
            }

            val remoteResult = appUpdater.fetchRemoteVersion()
            remoteResult.onSuccess { remote ->
                if (remote.versionCode <= currentCode) {
                    _uiState.update {
                        it.copy(
                            isUpdatingApp = false,
                            updateStatusMessage = "✓ Aplikace je aktuální (verze $currentName, build $currentCode)."
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        updateProgressPercent = 1,
                        updateStatusMessage = "Nalezena nová verze ${remote.versionName} (build ${remote.versionCode})! Stahuji APK z Gitu..."
                    )
                }

                val downloadResult = appUpdater.downloadUpdateApk(
                    context = ctx,
                    apkUrl = remote.apkUrl,
                    onProgressPercent = { pct ->
                        _uiState.update { state ->
                            state.copy(
                                updateProgressPercent = pct,
                                updateStatusMessage = "Stahuji aktualizaci ${remote.versionName} z Gitu... $pct %"
                            )
                        }
                    }
                )

                downloadResult.onSuccess { apkFile ->
                    val launched = runCatching {
                        appUpdater.triggerInstallApk(ctx, apkFile)
                    }.getOrElse { err ->
                        _uiState.update {
                            it.copy(
                                isUpdatingApp = false,
                                updateStatusMessage = "Chyba při spuštění instalátoru: ${err.message}"
                            )
                        }
                        return@launch
                    }

                    if (launched) {
                        _uiState.update {
                            it.copy(
                                isUpdatingApp = false,
                                updateProgressPercent = 100,
                                updateStatusMessage = "✓ Aktualizace ${remote.versionName} stažena – potvrď instalaci na obrazovce TV."
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isUpdatingApp = false,
                                updateStatusMessage = "Povol instalaci z neznámých zdrojů pro Webshare TV a klikni na ikonu aktualizace znovu."
                            )
                        }
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isUpdatingApp = false,
                            updateStatusMessage = "Chyba při stahování aktualizace: ${err.message}"
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isUpdatingApp = false,
                        updateStatusMessage = "Nepodařilo se ověřit verzi na Gitu: ${err.message}"
                    )
                }
            }
        }
    }

    private fun computeNextEpisode(current: EpisodeInfo?): EpisodeInfo? {
        if (current == null) return null
        val details = _uiState.value.activeMediaDetails ?: return null
        val currentSeasonEps = details.episodesBySeason[current.seasonNumber].orEmpty()
        val nextInSeason = currentSeasonEps.firstOrNull { it.episodeNumber == current.episodeNumber + 1 }
        if (nextInSeason != null) return nextInSeason
        val nextSeasonEps = details.episodesBySeason[current.seasonNumber + 1].orEmpty()
        return nextSeasonEps.firstOrNull()
    }
}
