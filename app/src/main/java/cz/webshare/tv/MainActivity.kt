package cz.webshare.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cz.webshare.tv.model.MediaDetails
import cz.webshare.tv.model.MediaType
import cz.webshare.tv.ui.MainViewModel
import cz.webshare.tv.ui.NavTab
import cz.webshare.tv.ui.components.TvColors
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
    ) {
        when {
            // 1. Not logged in -> Webshare VIP Login Screen
            !state.isLoggedIn -> {
                LoginScreen(
                    initialUsername = state.savedUsername,
                    isLoading = state.isAuthLoading,
                    errorMessage = state.authError,
                    onLoginSubmit = { user, pass -> viewModel.login(user, pass) }
                )
            }

            // 2. Fullscreen Media3 Video Player active
            state.activePlayback != null -> {
                val session = state.activePlayback!!
                VideoPlayerScreen(
                    session = session,
                    onBack = { viewModel.closePlayer() },
                    onProgressUpdate = { pos, dur -> viewModel.updatePlaybackProgress(pos, dur) },
                    onSwitchStream = { newStream, pos -> viewModel.switchStreamInPlayer(newStream, pos) },
                    onStreamErrorFallback = { pos -> viewModel.onPlaybackErrorFallback(pos) },
                    onNextEpisode = { viewModel.playNextEpisodeFromPlayer() }
                )
            }

            // 3. Movie / Series Detail & Grouped Stream Selection Screen
            state.activeDetailItem != null -> {
                val item = state.activeDetailItem!!
                val details = state.activeMediaDetails ?: MediaDetails(item)
                val inMyList = state.myList.any { it.id == item.id }

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

            // 4. Main Netflix-style Shell with Top Navigation
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopNavigationBar(
                        activeTab = state.activeTab,
                        userProfile = state.userProfile,
                        preferences = state.preferences,
                        onSelectTab = { viewModel.selectTab(it) }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        when (state.activeTab) {
                            NavTab.HOME -> {
                                HomeCatalogScreen(
                                    sections = state.sections,
                                    heroItem = state.focusedHeroItem,
                                    watchHistory = state.watchHistory,
                                    myList = state.myList,
                                    filterType = null,
                                    isLoading = state.isCatalogLoading,
                                    onFocusItem = { viewModel.updateFocusedHero(it) },
                                    onQuickPlayHero = { viewModel.quickPlayFromHero(it) },
                                    onOpenDetail = { viewModel.openMediaDetail(it) },
                                    onToggleMyList = { viewModel.toggleFavorite(it) },
                                    onResumeHistory = { viewModel.resumeFromWatchHistory(it, startFromBeginning = false) },
                                    onRestartHistoryFromBeginning = { viewModel.resumeFromWatchHistory(it, startFromBeginning = true) },
                                    onMarkHistoryWatched = { viewModel.markWatchHistoryAsWatched(it) },
                                    onRemoveHistoryItem = { viewModel.removeFromWatchHistory(it) },
                                    onOpenHistoryDetail = { viewModel.openDetailFromWatchHistory(it) }
                                )
                            }

                            NavTab.MOVIES -> {
                                HomeCatalogScreen(
                                    sections = state.sections,
                                    heroItem = state.focusedHeroItem,
                                    watchHistory = state.watchHistory,
                                    myList = state.myList,
                                    filterType = MediaType.MOVIE,
                                    isLoading = state.isCatalogLoading,
                                    onFocusItem = { viewModel.updateFocusedHero(it) },
                                    onQuickPlayHero = { viewModel.quickPlayFromHero(it) },
                                    onOpenDetail = { viewModel.openMediaDetail(it) },
                                    onToggleMyList = { viewModel.toggleFavorite(it) },
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
                                    heroItem = state.focusedHeroItem,
                                    watchHistory = state.watchHistory,
                                    myList = state.myList,
                                    filterType = MediaType.SERIES,
                                    isLoading = state.isCatalogLoading,
                                    onFocusItem = { viewModel.updateFocusedHero(it) },
                                    onQuickPlayHero = { viewModel.quickPlayFromHero(it) },
                                    onOpenDetail = { viewModel.openMediaDetail(it) },
                                    onToggleMyList = { viewModel.toggleFavorite(it) },
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
                                    onLogout = { viewModel.logout() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
