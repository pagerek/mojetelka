package cz.webshare.tv.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
import cz.webshare.tv.ui.components.TvPosterCard

@Composable
fun TopNavigationBar(
    activeTab: NavTab,
    userProfile: WebshareUserProfile?,
    preferences: UserPreferences,
    onSelectTab: (NavTab) -> Unit
) {
    val homeTabFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { homeTabFocusRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color(0xF206070A))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Netflix-Red Brand Mark
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(TvColors.NetflixRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "W",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
            Text(
                text = "WEBSHARE",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(end = 8.dp)
            )

            NavTab.entries.forEach { tab ->
                val tabModifier = if (tab == NavTab.HOME) {
                    Modifier.focusRequester(homeTabFocusRequester)
                } else {
                    Modifier
                }
                TvNavTabItem(
                    text = tab.labelCz,
                    isSelected = activeTab == tab,
                    onClick = { onSelectTab(tab) },
                    modifier = tabModifier
                )
            }
        }

        // Single-line compact status indicator on the right
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TechBadgePill(
                text = "${preferences.preferredLanguage.badge} • ${preferences.preferredQuality.badge}",
                backgroundColor = Color(0xFF181C29),
                textColor = Color(0xFFE5E7EB)
            )
            if (userProfile != null) {
                TechBadgePill(
                    text = if (userProfile.isVip) "VIP (${userProfile.vipDaysLeft}d)" else userProfile.username,
                    backgroundColor = if (userProfile.isVip) Color(0x3310B981) else Color(0x33F59E0B),
                    textColor = if (userProfile.isVip) TvColors.VipGreen else TvColors.AccentGold
                )
            }
        }
    }
}

@Composable
fun HomeCatalogScreen(
    sections: List<CatalogSection>,
    heroItem: CatalogItem?,
    watchHistory: List<WatchProgressItem>,
    filterType: MediaType?,
    isLoading: Boolean,
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

    val displayedHero = heroItem?.takeIf { filterType == null || it.type == filterType }
        ?: filteredSections.firstOrNull()?.items?.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
    ) {
        // Cinematic Background Hero Image on Top Right
        if (displayedHero != null) {
            val bgUrl = displayedHero.backdropUrl ?: displayedHero.posterUrl
            if (!bgUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = bgUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth(0.72f)
                        .height(290.dp)
                )
            }
            // Left-to-Right & Bottom Vignette Gradients
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(295.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                TvColors.Background,
                                TvColors.Background,
                                Color(0xCC06070A),
                                Color(0x3306070A)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(295.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x4406070A),
                                Color(0xAA06070A),
                                TvColors.Background
                            )
                        )
                    )
            )
        }

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
                contentPadding = PaddingValues(bottom = 36.dp)
            ) {
                // Compact Netflix Hero Spotlight at top
                if (displayedHero != null) {
                    item(key = "hero_banner") {
                        HeroSpotlightSection(
                            item = displayedHero,
                            onPlayOrDetail = { onOpenDetail(displayedHero) }
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
                        Column(modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pokračovat ve sledování",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tip: Dlouze podrž OK na ovladači pro možnosti (označit jako zhlédnuté / odebrat)",
                                    color = TvColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 4.dp),
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
                items(filteredSections, key = { it.id }) { section ->
                    Column(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                        Text(
                            text = section.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(section.items, key = { it.id }) { item ->
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
private fun HeroSpotlightSection(
    item: CatalogItem,
    onPlayOrDetail: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TechBadgePill(
                text = if (item.type == MediaType.SERIES) "SERIÁL" else "FILM",
                backgroundColor = TvColors.NetflixRed
            )
            if (item.year != null) {
                TechBadgePill(text = item.year.toString())
            }
            item.displayRating?.let { rating ->
                TechBadgePill(
                    text = "★ $rating",
                    backgroundColor = Color(0x33F59E0B),
                    textColor = TvColors.AccentGold
                )
            }
            item.genres.take(3).forEach { genre ->
                TechBadgePill(text = genre)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.overview,
            color = Color(0xFFD1D5DB),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(520.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(
                text = if (item.type == MediaType.SERIES) "Vybrat epizodu a přehrát" else "Přehrát film",
                icon = Icons.Default.PlayArrow,
                isPrimary = true,
                onClick = onPlayOrDetail
            )
            TvButton(
                text = "Kvalita a jazyk (Webshare)",
                icon = Icons.Default.Info,
                isPrimary = false,
                onClick = onPlayOrDetail
            )
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

            item {
                Text(
                    text = "Můj seznam (${myList.size})",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (myList.isEmpty()) {
                item {
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
                }
            } else {
                item {
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
