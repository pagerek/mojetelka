package cz.webshare.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
            .height(58.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF50A0A0A),
                        Color(0xCC0A0A0A)
                    )
                )
            )
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Iconic Netflix-style Red Ribbon Logo
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TvColors.NetflixRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "W",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
            Text(
                text = "WEBSHARE",
                color = TvColors.NetflixRed,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(end = 14.dp)
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

        // Minimalist status indicators on the right
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TechBadgePill(
                text = "${preferences.preferredLanguage.badge} • ${preferences.preferredQuality.badge}",
                backgroundColor = Color(0xFF1A1A1A),
                textColor = Color(0xFFE5E5E5),
                borderColor = Color(0x33FFFFFF)
            )
            if (userProfile != null) {
                TechBadgePill(
                    text = if (userProfile.isVip) "VIP • ${userProfile.vipDaysLeft}d" else userProfile.username,
                    backgroundColor = if (userProfile.isVip) Color(0x2646D369) else Color(0x26F5C518),
                    textColor = if (userProfile.isVip) TvColors.VipGreen else TvColors.AccentGold,
                    borderColor = if (userProfile.isVip) Color(0x5546D369) else Color(0x55F5C518)
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
    myList: List<CatalogItem> = emptyList(),
    filterType: MediaType?,
    isLoading: Boolean,
    onFocusItem: (CatalogItem) -> Unit,
    onOpenDetail: (CatalogItem) -> Unit,
    onQuickPlayHero: (CatalogItem) -> Unit = { onOpenDetail(it) },
    onToggleMyList: (CatalogItem) -> Unit = {},
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

    val isHeroInMyList = remember(displayedHero?.id, myList) {
        displayedHero != null && myList.any { it.id == displayedHero.id }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
    ) {
        // Full-Bleed Netflix Cinematic Billboard Backdrop (anchored top-right, 78% width, 370dp height)
        if (displayedHero != null) {
            val bgUrl = displayedHero.backdropUrl ?: displayedHero.posterUrl
            if (!bgUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = bgUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth(0.78f)
                        .height(370.dp)
                )
            }
            // Deep Left-to-Right Cinematic Vignette so text on the left is 100% legible
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(375.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                TvColors.Background,
                                TvColors.Background,
                                Color(0xE60A0A0A),
                                Color(0x880A0A0A),
                                Color(0x220A0A0A)
                            )
                        )
                    )
            )
            // Smooth Bottom Vignette blending into the first poster row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(375.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x330A0A0A),
                                Color(0x660A0A0A),
                                Color(0xDD0A0A0A),
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
                contentPadding = PaddingValues(bottom = 42.dp)
            ) {
                // Redesigned Netflix Hero Billboard Spotlight for the featured title
                if (displayedHero != null) {
                    item(key = "hero_banner") {
                        HeroSpotlightSection(
                            item = displayedHero,
                            isInMyList = isHeroInMyList,
                            onPrimaryPlay = { onQuickPlayHero(displayedHero) },
                            onOpenDetail = { onOpenDetail(displayedHero) },
                            onToggleMyList = { onToggleMyList(displayedHero) }
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
                        Column(modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pokračovat ve sledování",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Podrž OK pro možnosti",
                                    color = TvColors.TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 6.dp),
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
                itemsIndexed(filteredSections, key = { _, sec -> sec.id }) { secIdx, section ->
                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
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
                            itemsIndexed(section.items, key = { _, item -> item.id }) { itemIdx, item ->
                                val topRank = if (secIdx == 0 && itemIdx < 10) itemIdx + 1 else null
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

/**
 * Authentic Netflix TV Hero Billboard for the featured first/focused movie or series on Home.
 */
@Composable
private fun HeroSpotlightSection(
    item: CatalogItem,
    isInMyList: Boolean,
    onPrimaryPlay: () -> Unit,
    onOpenDetail: () -> Unit,
    onToggleMyList: () -> Unit
) {
    val matchScore = remember(item.id, item.rating) {
        val r = item.rating ?: 8.2
        (r * 10).toInt().coerceIn(78, 99)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 14.dp, bottom = 16.dp)
    ) {
        // 1. Netflix "W" Original-style Header Brand Tag
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(TvColors.NetflixRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "W",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                text = if (item.type == MediaType.SERIES) "SERIÁL WEBSHARE" else "FILM WEBSHARE",
                color = Color(0xFFD4D4D4),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.2.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Large Cinematic Billboard Title
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(640.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Netflix Metadata Line: Green Match % + Year + Maturity/Runtime + 4K/HD + CZ/EN Badges + Genre Dots
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "$matchScore% shoda",
                color = TvColors.VipGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )

            if (item.year != null) {
                Text(
                    text = item.year.toString(),
                    color = Color(0xFFE5E5E5),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (item.runtimeMinutes != null && item.runtimeMinutes > 0) {
                val hrs = item.runtimeMinutes / 60
                val mins = item.runtimeMinutes % 60
                val formattedRuntime = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                Text(
                    text = formattedRuntime,
                    color = Color(0xFFB3B3B3),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Crisp Outlined HD / 4K Quality Box (Netflix style)
            Box(
                modifier = Modifier
                    .border(1.dp, Color(0x88FFFFFF), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "4K • HD",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Box(
                modifier = Modifier
                    .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "CZ • EN",
                    color = Color(0xFFE5E5E5),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (item.genres.isNotEmpty()) {
                Text(
                    text = item.genres.take(3).joinToString(" • "),
                    color = Color(0xFFD4D4D4),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Synopsis
        Text(
            text = item.overview,
            color = Color(0xFFD4D4D4),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(560.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Netflix Hero Action Bar: Play + More Info / Streams + My List
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvButton(
                text = if (item.type == MediaType.SERIES) "Přehrát seriál" else "Přehrát",
                icon = Icons.Default.PlayArrow,
                isPrimary = true,
                paddingValues = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                onClick = onPrimaryPlay
            )
            TvButton(
                text = "Další informace a zdroje",
                icon = Icons.Default.Info,
                isPrimary = false,
                paddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                onClick = onOpenDetail
            )
            TvButton(
                text = if (isInMyList) "V mém seznamu" else "Můj seznam",
                icon = if (isInMyList) Icons.Default.Check else Icons.Default.Add,
                isSelected = isInMyList,
                paddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                onClick = onToggleMyList
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
