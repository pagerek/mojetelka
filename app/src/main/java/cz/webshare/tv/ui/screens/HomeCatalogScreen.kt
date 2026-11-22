package cz.webshare.tv.ui.screens

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.webshare.tv.model.CatalogItem
import cz.webshare.tv.model.CatalogSection
import cz.webshare.tv.model.MediaType
import cz.webshare.tv.model.UserPreferences
import cz.webshare.tv.model.WatchProgressItem
import cz.webshare.tv.model.WebshareUserProfile
import cz.webshare.tv.ui.NavTab
import cz.webshare.tv.ui.components.TechBadgePill
import cz.webshare.tv.ui.components.TvButton
import cz.webshare.tv.ui.components.TvColors
import cz.webshare.tv.ui.components.TvContinueWatchingCard
import cz.webshare.tv.ui.components.TvNavTabItem
import cz.webshare.tv.ui.components.tvBringIntoViewContainer
import cz.webshare.tv.ui.components.TvPosterCard
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TopNavigationBar(
    activeTab: NavTab,
    userProfile: WebshareUserProfile?,
    preferences: UserPreferences,
    onSelectTab: (NavTab) -> Unit,
    settingsLeftFocusRequester: FocusRequester? = null
) {
    val tabFocusRequesters = remember {
        NavTab.entries.associateWith { FocusRequester() }
    }
    var isNavBarFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { tabFocusRequesters[NavTab.HOME]?.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 32.dp)
    ) {
        // Minimalist Brand Icon on the left: Black fill, Red border, Red W
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0A0A0A))
                .border(1.8.dp, TvColors.NetflixRed, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "W",
                color = TvColors.NetflixRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }

        // Centered Navigation Menu
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .onFocusChanged { isNavBarFocused = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                        activeTab == NavTab.SETTINGS &&
                        settingsLeftFocusRequester != null
                    ) {
                        runCatching { settingsLeftFocusRequester.requestFocus() }
                        true
                    } else {
                        false
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavTab.entries.forEach { tab ->
                val requester = tabFocusRequesters[tab]
                val tabModifier = (if (requester != null) {
                    Modifier.focusRequester(requester)
                } else {
                    Modifier
                }).focusProperties {
                    canFocus = isNavBarFocused || tab == activeTab
                }
                val tabIcon = when (tab) {
                    NavTab.HOME -> Icons.Rounded.Home
                    NavTab.SEARCH -> Icons.Rounded.Search
                    NavTab.SETTINGS -> Icons.Rounded.Settings
                    else -> null
                }
                TvNavTabItem(
                    text = tab.labelCz,
                    icon = tabIcon,
                    isSelected = activeTab == tab,
                    onClick = { onSelectTab(tab) },
                    modifier = tabModifier
                )
            }
        }

        // Ultra-minimalist status text on the right (no heavy pill boxes or borders)
        val clockFormat = remember { SimpleDateFormat("EEE d. M. · HH:mm", Locale("cs", "CZ")) }
        var currentDateTime by remember { mutableStateOf(clockFormat.format(Date())) }
        LaunchedEffect(Unit) {
            while (true) {
                currentDateTime = clockFormat.format(Date())
                delay(15_000L)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${preferences.preferredQuality.badge} · ${preferences.preferredLanguage.badge}",
                    color = TvColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (userProfile != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (userProfile.isVip) TvColors.VipGreen else TvColors.AccentGold)
                        )
                        Text(
                            text = if (userProfile.isVip) "${userProfile.vipDaysLeft}d" else userProfile.username,
                            color = TvColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                text = currentDateTime,
                color = TvColors.TextMuted.copy(alpha = 0.78f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
fun HomeCatalogScreen(
    sections: List<CatalogSection>,
    watchHistory: List<WatchProgressItem>,
    filterType: MediaType?,
    isLoading: Boolean,
    showCategoryTiles: Boolean = false,
    onOpenCategory: (CatalogSection) -> Unit = {},
    onFocusItem: (CatalogItem) -> Unit,
    onOpenDetail: (CatalogItem) -> Unit,
    onResumeHistory: (WatchProgressItem) -> Unit,
    onRestartHistoryFromBeginning: (WatchProgressItem) -> Unit = {},
    onMarkHistoryWatched: (WatchProgressItem) -> Unit = {},
    onRemoveHistoryItem: (WatchProgressItem) -> Unit = {},
    onOpenHistoryDetail: (WatchProgressItem) -> Unit = {}
) {
    val filteredSections = remember(sections, filterType) {
        if (filterType == null) {
            sections
        } else {
            sections.mapNotNull { sec ->
                val matching = sec.items.filter { it.type == filterType }
                if (matching.isNotEmpty()) sec.copy(items = matching) else null
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading && filteredSections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TvColors.NetflixRed)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Načítám filmový a seriálový katalog...",
                        color = TvColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 42.dp)
            ) {
                // Netflix-style Glassmorphic Category Tiles at the top of Domů
                if (showCategoryTiles && filteredSections.isNotEmpty()) {
                    item(key = "category_tiles_row") {
                        CategoryTilesRow(
                            sections = filteredSections,
                            onSelectCategory = onOpenCategory
                        )
                    }
                }

                // Continue Watching ("Pokračovat ve sledování")
                val relevantHistory = if (filterType == null) {
                    watchHistory
                } else {
                    watchHistory.filter { it.mediaType == filterType }
                }

                if (relevantHistory.isNotEmpty()) {
                    item(key = "continue_watching") {
                        Column(
                            modifier = Modifier
                                .tvBringIntoViewContainer(topPadding = 12.dp, bottomPadding = 24.dp)
                                .padding(top = 2.dp, bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Pokračovat ve sledování",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "•  Podrž OK pro možnosti",
                                    color = TvColors.TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(
                                    relevantHistory,
                                    key = { "${it.mediaId}_${it.seasonNumber}_${it.episodeNumber}" }
                                ) { hItem ->
                                    TvContinueWatchingCard(
                                        item = hItem,
                                        onClick = { onResumeHistory(hItem) },
                                        onPlayFromBeginning = { onRestartHistoryFromBeginning(hItem) },
                                        onMarkAsWatched = { onMarkHistoryWatched(hItem) },
                                        onRemoveFromRow = { onRemoveHistoryItem(hItem) },
                                        onOpenDetail = { onOpenHistoryDetail(hItem) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Horizontal Movie & Series Poster Rows
                itemsIndexed(filteredSections, key = { _, sec -> sec.id }) { _, section ->
                    Column(
                        modifier = Modifier
                            .tvBringIntoViewContainer(topPadding = 12.dp, bottomPadding = 24.dp)
                            .padding(top = 6.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = section.title,
                            color = Color(0xFFE5E5E5),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val isRankedChart = section.id == "trending_movies" || section.id == "top_movies"
                            itemsIndexed(section.items, key = { _, item -> item.id }) { itemIdx, item ->
                                val topRank = if (isRankedChart && itemIdx < 10) itemIdx + 1 else null
                                TvPosterCard(
                                    item = item,
                                    rankNumber = topRank,
                                    onFocused = onFocusItem,
                                    onClick = onOpenDetail
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyListScreen(
    myList: List<CatalogItem>,
    watchHistory: List<WatchProgressItem>,
    onFocusItem: (CatalogItem) -> Unit,
    onOpenDetail: (CatalogItem) -> Unit,
    onResumeHistory: (WatchProgressItem) -> Unit,
    onRestartHistoryFromBeginning: (WatchProgressItem) -> Unit = {},
    onMarkHistoryWatched: (WatchProgressItem) -> Unit = {},
    onRemoveHistoryItem: (WatchProgressItem) -> Unit = {},
    onOpenHistoryDetail: (WatchProgressItem) -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp)
        ) {
            if (watchHistory.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.tvBringIntoViewContainer(
                            topPadding = 12.dp,
                            bottomPadding = 24.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Rozkoukané filmy a epizody",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Podrž OK na kartě pro další možnosti",
                                color = TvColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(
                                watchHistory,
                                key = { "${it.mediaId}_${it.seasonNumber}_${it.episodeNumber}" }
                            ) { hItem ->
                                TvContinueWatchingCard(
                                    item = hItem,
                                    onClick = { onResumeHistory(hItem) },
                                    onPlayFromBeginning = { onRestartHistoryFromBeginning(hItem) },
                                    onMarkAsWatched = { onMarkHistoryWatched(hItem) },
                                    onRemoveFromRow = { onRemoveHistoryItem(hItem) },
                                    onOpenDetail = { onOpenHistoryDetail(hItem) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.tvBringIntoViewContainer(
                        topPadding = 12.dp,
                        bottomPadding = 28.dp
                    )
                ) {
                    Text(
                        text = "Můj seznam (${myList.size})",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (myList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tvůj seznam je zatím prázdný. U libovolného filmu nebo seriálu klikni na '+ Můj seznam'.",
                                color = TvColors.TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            items(myList, key = { it.id }) { item ->
                                TvPosterCard(
                                    item = item,
                                    onFocused = onFocusItem,
                                    onClick = onOpenDetail
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTilesRow(
    sections: List<CatalogSection>,
    onSelectCategory: (CatalogSection) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .tvBringIntoViewContainer(topPadding = 12.dp, bottomPadding = 20.dp)
            .padding(top = 4.dp, bottom = 10.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sections, key = { "tile_${it.id}" }) { section ->
            CategoryTileCard(
                label = categoryTileShortLabel(section),
                onClick = { onSelectCategory(section) }
            )
        }
    }
}

@Composable
private fun CategoryTileCard(
    label: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 140),
        label = "cat_tile_scale"
    )

    val shape = RoundedCornerShape(12.dp)
    val bgBrush = if (isFocused) {
        Brush.verticalGradient(
            colors = listOf(Color(0x52FFFFFF), Color(0x28FFFFFF))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0x34FFFFFF), Color(0x16FFFFFF))
        )
    }

    Box(
        modifier = Modifier
            .width(154.dp)
            .height(70.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bgBrush)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x2EFFFFFF),
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 16.sp,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun categoryTileShortLabel(section: CatalogSection): String = when (section.id) {
    "christmas_movies" -> "VÁNOČNÍ"
    "cz_sk_hits" -> "ČESKÉ HITY"
    "trending_movies", "top_movies" -> "TRENDY"
    "trending_series", "top_series" -> "SERIÁLY"
    "drama_movies" -> "DRAMA"
    "thriller_movies" -> "THRILLERY"
    "documentary_movies" -> "DOKUMENTY"
    "scifi_action" -> "AKČNÍ & SCI-FI"
    "comedy_movies" -> "KOMEDIE"
    "recent_movies" -> "NOVINKY"
    "cz_sk_series" -> "ČESKÉ SERIÁLY"
    else -> section.title.uppercase()
}

@Composable
fun CategoryGridScreen(
    section: CatalogSection,
    isExpanding: Boolean,
    onBack: () -> Unit,
    onFocusItem: (CatalogItem) -> Unit,
    onOpenDetail: (CatalogItem) -> Unit
) {
    val firstGridFocusRequester = remember { FocusRequester() }
    LaunchedEffect(section.id) {
        runCatching { firstGridFocusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TvButton(
                text = "Zpět",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                paddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            )
            Text(
                text = section.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "(${section.items.size})",
                color = TvColors.TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (isExpanding) {
                CircularProgressIndicator(
                    color = TvColors.NetflixRed,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(section.items.size, key = { idx -> section.items[idx].id }) { idx ->
                val item = section.items[idx]
                TvPosterCard(
                    item = item,
                    onFocused = onFocusItem,
                    onClick = onOpenDetail,
                    modifier = if (idx == 0) Modifier.focusRequester(firstGridFocusRequester) else Modifier
                )
            }
        }
    }
}
