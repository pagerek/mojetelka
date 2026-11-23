package cz.webshare.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import cz.webshare.tv.model.MediaDetails
import cz.webshare.tv.model.MediaType
import cz.webshare.tv.ui.MainViewModel
import cz.webshare.tv.ui.NavTab
import cz.webshare.tv.ui.components.FocusRestoreRegistry
import cz.webshare.tv.ui.components.KidsAmbilightContainer
import cz.webshare.tv.ui.components.LocalFocusRestoreRegistry
import cz.webshare.tv.ui.components.TvColors
import cz.webshare.tv.ui.screens.CategoryGridScreen
import cz.webshare.tv.ui.screens.HomeCatalogScreen
import cz.webshare.tv.ui.screens.LoginScreen
import cz.webshare.tv.ui.screens.MediaDetailScreen
import cz.webshare.tv.ui.screens.MyListScreen
import cz.webshare.tv.ui.screens.SearchScreen
import cz.webshare.tv.ui.screens.SettingsScreen
import cz.webshare.tv.ui.screens.TopNavigationBar
import cz.webshare.tv.ui.screens.VideoPlayerScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsState()

            // Keep TV screen awake during video playback
            DisposableEffect(state.activePlayback != null) {
                if (state.activePlayback != null) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {}
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = TvColors.NetflixRed,
                    background = TvColors.Background,
                    surface = TvColors.SurfaceDark
                )
            ) {
                WebshareTvApp(
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun WebshareTvApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()

    // Back button navigation stack for TV remote
    BackHandler(
        enabled = state.activePlayback != null ||
            state.showStreamPickerModal ||
            state.activeDetailItem != null ||
            state.activeTab != NavTab.HOME
    ) {
        when {
            state.activePlayback != null -> viewModel.closePlayer()
            state.showStreamPickerModal -> viewModel.closeStreamPickerModal()
            state.activeDetailItem != null -> viewModel.closeMediaDetail()
            state.activeTab != NavTab.HOME -> viewModel.selectTab(NavTab.HOME)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
    ) {
        if (!state.isLoggedIn) {
            LoginScreen(
                initialUsername = state.savedUsername,
                isLoading = state.isAuthLoading,
                errorMessage = state.authError,
                onLoginSubmit = { user, pass -> viewModel.login(user, pass) }
            )
        } else {
            val focusRegistry = remember { FocusRestoreRegistry() }
            val hasOverlay = state.activePlayback != null || state.activeDetailItem != null
            var wasOverlayOpen by remember { mutableStateOf(false) }

            LaunchedEffect(hasOverlay) {
                if (hasOverlay) {
                    wasOverlayOpen = true
                } else if (wasOverlayOpen) {
                    wasOverlayOpen = false
                    kotlinx.coroutines.delay(50L)
                    runCatching { focusRegistry.lastFocusedRequester?.requestFocus() }
                }
            }

            CompositionLocalProvider(LocalFocusRestoreRegistry provides focusRegistry) {
                val settingsLeftFocusRequester = remember { FocusRequester() }
                val tabContentFocusRequesters = remember {
                    NavTab.entries.associateWith { FocusRequester() }
                }

                // 1. Main Netflix-style Shell (kept mounted so scroll & focus survive Detail/Player overlays)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup()
                        .then(
                            if (hasOverlay) Modifier.focusProperties { canFocus = false }
                            else Modifier
                        )
                ) {
                    val targetTint = when (state.activeTab) {
                        NavTab.HOME -> Color(0xFF280B10)      // Subtle crimson
                        NavTab.MOVIES -> Color(0xFF0A2347)    // Rich sapphire blue
                        NavTab.SERIES -> Color(0xFF09211C)    // Deep emerald teal
                        NavTab.KIDS -> Color(0xFF091B2E)      // Vivid kids teal-blue
                        NavTab.SEARCH -> Color(0xFF1E0F2E)    // Deep violet
                        NavTab.MY_LIST -> Color(0xFF261A09)   // Warm amber
                        NavTab.SETTINGS -> Color(0xFF17181C)  // Neutral cool charcoal
                    }
                    val animatedTint by animateColorAsState(
                        targetValue = targetTint,
                        animationSpec = tween(durationMillis = 350),
                        label = "tab_bg_tint"
                    )
                    val shellContent: @Composable () -> Unit = {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            TopNavigationBar(
                                activeTab = state.activeTab,
                                userProfile = state.userProfile,
                                preferences = state.preferences,
                                onSelectTab = { viewModel.selectTab(it) },
                                settingsLeftFocusRequester = settingsLeftFocusRequester,
                                contentFocusRequester = tabContentFocusRequesters[state.activeTab]
                            )

                            Crossfade(
                                targetState = state.activeTab,
                                animationSpec = tween(durationMillis = 220),
                                label = "tab_content_crossfade",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            ) { activeTab ->
                                when (activeTab) {
                                    NavTab.HOME -> {
                                        val expandedCategory = state.activeCategorySection
                                        var wasCategoryExpanded by remember { mutableStateOf(false) }
                                        LaunchedEffect(expandedCategory) {
                                            if (expandedCategory != null) {
                                                wasCategoryExpanded = true
                                            } else if (wasCategoryExpanded) {
                                                wasCategoryExpanded = false
                                                kotlinx.coroutines.delay(50L)
                                                runCatching { focusRegistry.lastFocusedRequester?.requestFocus() }
                                            }
                                        }
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            Box(
                                                modifier = if (expandedCategory != null) {
                                                    Modifier
                                                        .fillMaxSize()
                                                        .focusGroup()
                                                        .focusProperties { canFocus = false }
                                                } else {
                                                    Modifier.fillMaxSize()
                                                }
                                            ) {
                                                HomeCatalogScreen(
                                                    sections = state.sections,
                                                    watchHistory = state.watchHistory,
                                                    filterType = null,
                                                    isLoading = state.isCatalogLoading,
                                                    showCategoryTiles = true,
                                                    contentFocusRequester = tabContentFocusRequesters[NavTab.HOME],
                                                    onOpenCategory = { viewModel.openCategory(it) },
                                                    onFocusItem = { viewModel.updateFocusedHero(it) },
                                                    onOpenDetail = { viewModel.openMediaDetail(it) },
                                                    onResumeHistory = { viewModel.resumeFromWatchHistory(it, startFromBeginning = false) },
                                                    onRestartHistoryFromBeginning = { viewModel.resumeFromWatchHistory(it, startFromBeginning = true) },
                                                    onMarkHistoryWatched = { viewModel.markWatchHistoryAsWatched(it) },
                                                    onRemoveHistoryItem = { viewModel.removeFromWatchHistory(it) },
                                                    onOpenHistoryDetail = { viewModel.openDetailFromWatchHistory(it) }
                                                )
                                            }
                                            if (expandedCategory != null) {
                                                BackHandler { viewModel.closeCategory() }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(TvColors.Background.copy(alpha = 0.96f))
                                                ) {
                                                    CategoryGridScreen(
                                                        section = expandedCategory,
                                                        isExpanding = state.isCategoryExpanding,
                                                        onBack = { viewModel.closeCategory() },
                                                        onFocusItem = { viewModel.updateFocusedHero(it) },
                                                        onOpenDetail = { viewModel.openMediaDetail(it) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                NavTab.MOVIES -> {
                                    HomeCatalogScreen(
                                        sections = state.sections,
                                        watchHistory = state.watchHistory,
                                        filterType = MediaType.MOVIE,
                                        isLoading = state.isCatalogLoading,
                                        contentFocusRequester = tabContentFocusRequesters[NavTab.MOVIES],
                                        onFocusItem = { viewModel.updateFocusedHero(it) },
                                        onOpenDetail = { viewModel.openMediaDetail(it) },
                                        onResumeHistory = { viewModel.resumeFromWatchHistory(it, startFromBeginning = false) },
                                        onRestartHistoryFromBeginning = { viewModel.resumeFromWatchHistory(it, startFromBeginning = true) },
                                        onMarkHistoryWatched = { viewModel.markWatchHistoryAsWatched(it) },
                                        onRemoveHistoryItem = { viewModel.removeFromWatchHistory(it) },
                                        onOpenHistoryDetail = { viewModel.openDetailFromWatchHistory(it) }
                                    )
                                }

                                NavTab.SERIES -> {
                                    HomeCatalogScreen(
                                        sections = state.sections,
                                        watchHistory = state.watchHistory,
                                        filterType = MediaType.SERIES,
                                        isLoading = state.isCatalogLoading,
                                        contentFocusRequester = tabContentFocusRequesters[NavTab.SERIES],
                                        onFocusItem = { viewModel.updateFocusedHero(it) },
                                        onOpenDetail = { viewModel.openMediaDetail(it) },
                                        onResumeHistory = { viewModel.resumeFromWatchHistory(it, startFromBeginning = false) },
                                        onRestartHistoryFromBeginning = { viewModel.resumeFromWatchHistory(it, startFromBeginning = true) },
                                        onMarkHistoryWatched = { viewModel.markWatchHistoryAsWatched(it) },
                                        onRemoveHistoryItem = { viewModel.removeFromWatchHistory(it) },
                                        onOpenHistoryDetail = { viewModel.openDetailFromWatchHistory(it) }
                                    )
                                }

                                NavTab.KIDS -> {
                                    HomeCatalogScreen(
                                        sections = state.kidsSections,
                                        watchHistory = emptyList(),
                                        filterType = null,
                                        isLoading = state.isKidsLoading,
                                        contentFocusRequester = tabContentFocusRequesters[NavTab.KIDS],
                                        onFocusItem = { viewModel.updateFocusedHero(it) },
                                        onOpenDetail = { viewModel.openMediaDetail(it) },
                                        onResumeHistory = { viewModel.resumeFromWatchHistory(it, startFromBeginning = false) },
                                        onRestartHistoryFromBeginning = { viewModel.resumeFromWatchHistory(it, startFromBeginning = true) },
                                        onMarkHistoryWatched = { viewModel.markWatchHistoryAsWatched(it) },
                                        onRemoveHistoryItem = { viewModel.removeFromWatchHistory(it) },
                                        onOpenHistoryDetail = { viewModel.openDetailFromWatchHistory(it) }
                                    )
                                }

                            NavTab.SEARCH -> {
                                SearchScreen(
                                    query = state.searchQuery,
                                    isSearching = state.isSearching,
                                    results = state.searchResults,
                                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                                    onTriggerSearch = { viewModel.triggerImmediateSearch() },
                                    onFocusItem = { viewModel.updateFocusedHero(it) },
                                    onOpenDetail = { viewModel.openMediaDetail(it) }
                                )
                            }

                            NavTab.MY_LIST -> {
                                MyListScreen(
                                    myList = state.myList,
                                    watchHistory = state.watchHistory,
                                    contentFocusRequester = tabContentFocusRequesters[NavTab.MY_LIST],
                                    onFocusItem = { viewModel.updateFocusedHero(it) },
                                    onOpenDetail = { viewModel.openMediaDetail(it) },
                                    onResumeHistory = { viewModel.resumeFromWatchHistory(it, startFromBeginning = false) },
                                    onRestartHistoryFromBeginning = { viewModel.resumeFromWatchHistory(it, startFromBeginning = true) },
                                    onMarkHistoryWatched = { viewModel.markWatchHistoryAsWatched(it) },
                                    onRemoveHistoryItem = { viewModel.removeFromWatchHistory(it) },
                                    onOpenHistoryDetail = { viewModel.openDetailFromWatchHistory(it) }
                                )
                            }

                            NavTab.SETTINGS -> {
                                SettingsScreen(
                                    preferences = state.preferences,
                                    userProfile = state.userProfile,
                                    savedUsername = state.savedUsername,
                                    appVersionName = state.appVersionName,
                                    appVersionCode = state.appVersionCode,
                                    isUpdatingApp = state.isUpdatingApp,
                                    updateProgressPercent = state.updateProgressPercent,
                                    updateStatusMessage = state.updateStatusMessage,
                                    onSelectQuality = { viewModel.updatePreferredQuality(it) },
                                    onSelectLanguage = { viewModel.updatePreferredLanguage(it) },
                                    onSelectLaunchMode = { viewModel.updateLaunchMode(it) },
                                    onSaveTmdbKey = { viewModel.updateTmdbApiKey(it) },
                                    onCheckGitUpdate = { viewModel.checkAndInstallGitUpdate() },
                                    onLogout = { viewModel.logout() },
                                    leftMenuFocusRequester = settingsLeftFocusRequester
                                )
                            }
                        }
                    }
                }
                }

                if (state.activeTab == NavTab.KIDS) {
                    val ambilightImageUrl = state.focusedHeroItem?.backdropUrl
                        ?: state.focusedHeroItem?.posterUrl
                    KidsAmbilightContainer(imageUrl = ambilightImageUrl) {
                        shellContent()
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        animatedTint,
                                        TvColors.Background,
                                        TvColors.Background
                                    )
                                )
                            )
                    ) {
                        shellContent()
                    }
                }
                } // end Main Shell focus-guarded Box

                // 2. Movie / Series Detail & Grouped Stream Selection Overlay
                if (state.activeDetailItem != null && state.activePlayback == null) {
                    val item = state.activeDetailItem!!
                    val details = state.activeMediaDetails ?: MediaDetails(item)
                    val inMyList = state.myList.any { it.id == item.id }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(TvColors.Background)
                    ) {
                        MediaDetailScreen(
                            details = details,
                            selectedSeasonNumber = state.selectedSeasonNumber,
                            selectedEpisode = state.selectedEpisode,
                            isResolvingStreams = state.isResolvingStreams,
                            streamResolution = state.streamResolution,
                            streamError = state.streamError,
                            showStreamPickerModal = state.showStreamPickerModal,
                            isInMyList = inMyList,
                            preferences = state.preferences,
                            onBack = { viewModel.closeMediaDetail() },
                            onPrimaryPlay = { viewModel.onPrimaryPlayClicked() },
                            onOpenStreamPicker = { viewModel.openStreamPickerModal() },
                            onCloseStreamPicker = { viewModel.closeStreamPickerModal() },
                            onPlaySpecificStream = { stream ->
                                viewModel.startStreamPlayback(
                                    item = item,
                                    episode = if (item.type == MediaType.SERIES) state.selectedEpisode else null,
                                    stream = stream,
                                    resolution = state.streamResolution,
                                    bannerMessage = "Vybraný stream: ${stream.language.badge} • ${stream.quality.badge} (${stream.file.formattedSize})"
                                )
                            },
                            onSelectSeason = { sNum -> viewModel.selectSeason(sNum) },
                            onSelectEpisode = { ep, autoPlay -> viewModel.selectEpisode(ep, autoPlay) },
                            onToggleMyList = { viewModel.toggleFavorite(it) }
                        )
                    }
                }

                // 3. Fullscreen Media3 Video Player Overlay
                if (state.activePlayback != null) {
                    val session = state.activePlayback!!
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        VideoPlayerScreen(
                            session = session,
                            onBack = { viewModel.closePlayer() },
                            onProgressUpdate = { pos, dur -> viewModel.updatePlaybackProgress(pos, dur) },
                            onSwitchStream = { newStream, pos -> viewModel.switchStreamInPlayer(newStream, pos) },
                            onStreamErrorFallback = { pos -> viewModel.onPlaybackErrorFallback(pos) },
                            onNextEpisode = { viewModel.playNextEpisodeFromPlayer() }
                        )
                    }
                }
            }
        }
    }
}
