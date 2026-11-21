package cz.webshare.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import cz.webshare.tv.model.EpisodeInfo
import cz.webshare.tv.model.MediaDetails
import cz.webshare.tv.model.MediaType
import cz.webshare.tv.model.ParsedStream
import cz.webshare.tv.model.StreamResolutionResult
import cz.webshare.tv.model.UserPreferences
import cz.webshare.tv.ui.components.TechBadgePill
import cz.webshare.tv.ui.components.TvButton
import cz.webshare.tv.ui.components.TvColors

@Composable
fun MediaDetailScreen(
    details: MediaDetails,
    selectedSeasonNumber: Int,
    selectedEpisode: EpisodeInfo?,
    isResolvingStreams: Boolean,
    streamResolution: StreamResolutionResult?,
    streamError: String?,
    showStreamPickerModal: Boolean,
    isInMyList: Boolean,
    preferences: UserPreferences,
    onBack: () -> Unit,
    onPrimaryPlay: () -> Unit,
    onOpenStreamPicker: () -> Unit,
    onCloseStreamPicker: () -> Unit,
    onPlaySpecificStream: (ParsedStream) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (EpisodeInfo, Boolean) -> Unit,
    onToggleMyList: (CatalogItem) -> Unit
) {
    val item = details.item
    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(item.id) {
        runCatching { playFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
    ) {
        // Full Backdrop Header
        val bgUrl = item.backdropUrl ?: item.posterUrl
        if (!bgUrl.isNullOrEmpty()) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
            )
        }

        // Gradient Overlays
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            TvColors.Background,
                            Color(0xDD090A0F),
                            Color(0x66090A0F)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x66090A0F),
                            Color(0xDD090A0F),
                            TvColors.Background
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 56.dp)
        ) {
            item {
                val matchScore = remember(item.id, item.rating) {
                    val r = item.rating ?: 8.2
                    (r * 10).toInt().coerceIn(78, 99)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TvButton(
                        text = "Zpět",
                        icon = Icons.Default.ArrowBack,
                        paddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        onClick = onBack
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            letterSpacing = 2.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Netflix Metadata Line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "$matchScore% shoda",
                        color = TvColors.VipGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    item.year?.let {
                        Text(
                            text = it.toString(),
                            color = Color(0xFFE5E5E5),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    item.runtimeMinutes?.let { mins ->
                        val hrs = mins / 60
                        val rem = mins % 60
                        Text(
                            text = if (hrs > 0) "${hrs}h ${rem}m" else "${rem}m",
                            color = TvColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .border(1.dp, Color(0x88FFFFFF), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "4K • HD",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (item.genres.isNotEmpty()) {
                        Text(
                            text = item.genres.joinToString(" • "),
                            color = Color(0xFFD4D4D4),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (!item.originalTitle.equals(item.title, ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Originální název: ${item.originalTitle}",
                        color = TvColors.TextMuted,
                        fontSize = 13.sp
                    )
                }

                if (item.type == MediaType.SERIES && selectedEpisode != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Vybraná epizoda: ${selectedEpisode.code} – ${selectedEpisode.title}",
                        color = TvColors.AccentGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = item.overview,
                    color = Color(0xFFE5E7EB),
                    fontSize = 15.sp,
                    modifier = Modifier.width(740.dp)
                )

                if (details.cast.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Hrají: ${details.cast.joinToString(", ")}",
                        color = TvColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Primary Action Buttons
                val recommended = streamResolution?.recommendedStream
                val primaryPlayLabel = when {
                    isResolvingStreams -> "Hledám a seskupuji streamy na Webshare..."
                    recommended != null -> "Přehrát (${recommended.language.badge} • ${recommended.quality.badge} • ${recommended.file.formattedSize})"
                    else -> "Přehrát (${preferences.preferredLanguage.badge} • ${preferences.preferredQuality.badge})"
                }

                val primaryPlaySubtitle = when {
                    isResolvingStreams -> "Filtruji duplicity, kvality a jazyky"
                    streamResolution?.selectionNotice != null -> streamResolution.selectionNotice
                    else -> "Výchozí volba podle tvého nastavení"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TvButton(
                        text = primaryPlayLabel,
                        subtitle = primaryPlaySubtitle,
                        icon = Icons.Default.PlayArrow,
                        isPrimary = true,
                        modifier = Modifier.focusRequester(playFocusRequester),
                        onClick = onPrimaryPlay
                    )

                    val groupCount = streamResolution?.groups?.size ?: 0
                    val totalStreams = streamResolution?.allStreams?.size ?: 0
                    TvButton(
                        text = if (totalStreams > 0) {
                            "Kvalita a jazyk ($groupCount skupin / $totalStreams zdrojů)"
                        } else {
                            "Vybrat kvalitu a jazyk"
                        },
                        subtitle = "Ruční výběr 4K / 1080p / 720p a CZ / EN",
                        icon = Icons.Default.Settings,
                        onClick = onOpenStreamPicker
                    )

                    TvButton(
                        text = if (isInMyList) "V mém seznamu" else "+ Můj seznam",
                        icon = if (isInMyList) Icons.Default.Check else Icons.Default.Star,
                        isSelected = isInMyList,
                        onClick = { onToggleMyList(item) }
                    )
                }

                if (isResolvingStreams) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = TvColors.NetflixRed,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Prohledávám Webshare.cz a seskupuji výsledky podle kvality a jazyka...",
                            color = TvColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                if (!streamError.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33E50914))
                            .border(1.dp, TvColors.NetflixRed, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = streamError,
                            color = Color(0xFFFFB4B8),
                            fontSize = 13.sp
                        )
                    }
                }

                // Quick Grouped Quality x Language Bar right on the Detail Screen!
                if (streamResolution != null && streamResolution.groups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "Dostupné skupiny kvality a jazyka na Webshare.cz (kliknutím rovnou spustíš):",
                        color = TvColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(streamResolution.groups, key = { "${it.language}_${it.quality}" }) { group ->
                            val isRecommendedGroup = recommended != null &&
                                recommended.language == group.language &&
                                recommended.quality == group.quality

                            TvButton(
                                text = "${group.language.badge} • ${group.quality.badge}",
                                subtitle = "${group.bestStream.file.formattedSize} (${group.streams.size} souborů)",
                                isSelected = isRecommendedGroup,
                                paddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                onClick = { onPlaySpecificStream(group.bestStream) }
                            )
                        }
                    }
                }
            }

            // TV Series Seasons & Episodes Browser
            if (item.type == MediaType.SERIES && details.seasons.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "Série a epizody",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Season Tabs
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(details.seasons, key = { it.seasonNumber }) { season ->
                            TvButton(
                                text = "${season.name} (${season.episodeCount})",
                                isSelected = season.seasonNumber == selectedSeasonNumber,
                                paddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                onClick = { onSelectSeason(season.seasonNumber) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                val seasonEpisodes = details.episodesBySeason[selectedSeasonNumber].orEmpty()
                items(seasonEpisodes, key = { "${it.seasonNumber}_${it.episodeNumber}" }) { ep ->
                    val isCurrentEp = selectedEpisode?.seasonNumber == ep.seasonNumber &&
                        selectedEpisode.episodeNumber == ep.episodeNumber

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrentEp) Color(0xFF1F2538) else TvColors.SurfaceDark)
                            .border(
                                width = 1.dp,
                                color = if (isCurrentEp) TvColors.NetflixRed else Color(0xFF252A3D),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TechBadgePill(
                                    text = ep.code,
                                    backgroundColor = if (isCurrentEp) TvColors.NetflixRed else Color(0xFF2A3045)
                                )
                                Text(
                                    text = ep.title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (ep.overview.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ep.overview,
                                    color = TvColors.TextSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvButton(
                                text = "▶ Přehrát díl",
                                isPrimary = isCurrentEp,
                                paddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                onClick = { onSelectEpisode(ep, true) }
                            )
                            TvButton(
                                text = "Zdroje",
                                paddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                onClick = {
                                    onSelectEpisode(ep, false)
                                    onOpenStreamPicker()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Grouped Quality & Language Selection Modal
        if (showStreamPickerModal && streamResolution != null) {
            StreamPickerModal(
                title = streamResolution.queryTitle,
                resolution = streamResolution,
                onSelectStream = onPlaySpecificStream,
                onDismiss = onCloseStreamPicker
            )
        }
    }
}

@Composable
fun StreamPickerModal(
    title: String,
    resolution: StreamResolutionResult,
    onSelectStream: (ParsedStream) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGroupIndex by remember { mutableIntStateOf(0) }
    val groups = resolution.groups
    val activeGroup = groups.getOrNull(selectedGroupIndex) ?: groups.firstOrNull()
    val firstStreamFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(40L)
        runCatching { firstStreamFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD050609))
            .padding(horizontal = 48.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(TvColors.SurfaceDark)
                .border(1.5.dp, Color(0xFF2F354D), RoundedCornerShape(18.dp))
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Výběr kvality a jazyka – $title",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Nalezeno ${resolution.allStreams.size} video streamů na Webshare.cz seskupených do ${groups.size} kategorií",
                        color = TvColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }

                TvButton(
                    text = "Zavřít",
                    onClick = onDismiss
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left Column: Clean Quality x Language Groups
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "1. Zvol jazyk a kvalitu:",
                        color = TvColors.AccentGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(groups.size) { idx ->
                            val grp = groups[idx]
                            val isSelected = idx == selectedGroupIndex
                            TvButton(
                                text = "${grp.language.badge} • ${grp.quality.badge}",
                                subtitle = "Nejlepší: ${grp.bestStream.file.formattedSize} (celkem ${grp.streams.size}×)",
                                isSelected = isSelected,
                                isPrimary = false,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedGroupIndex = idx }
                            )
                        }
                    }
                }

                // Right Column: Streams inside the selected Group
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (activeGroup != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. Streamy ve skupině ${activeGroup.label} (${activeGroup.streams.size}):",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            TvButton(
                                text = "▶ Přehrát nejlepší z této skupiny (${activeGroup.bestStream.file.formattedSize})",
                                isPrimary = true,
                                modifier = Modifier.focusRequester(firstStreamFocusRequester),
                                paddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                onClick = { onSelectStream(activeGroup.bestStream) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(activeGroup.streams, key = { it.file.ident }) { stream ->
                                val badgesText = stream.techBadges.joinToString(" • ")
                                val votesText = if (stream.file.posVotes > 0) " • 👍 +${stream.file.posVotes}" else ""
                                TvButton(
                                    text = "$badgesText • ${stream.extension}$votesText",
                                    subtitle = stream.file.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onSelectStream(stream) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
