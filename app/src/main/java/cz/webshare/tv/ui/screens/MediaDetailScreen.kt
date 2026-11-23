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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import cz.webshare.tv.ui.components.TechBadgePill
import cz.webshare.tv.ui.components.TvButton
import cz.webshare.tv.ui.components.TvColors
import cz.webshare.tv.ui.components.TvIconButton
import cz.webshare.tv.ui.components.tvBringIntoViewContainer
import cz.webshare.tv.ui.components.tvBringIntoViewPadding
import cz.webshare.tv.ui.components.tvTrapHorizontalFocus

@OptIn(ExperimentalComposeUiApi::class)
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
                    .height(520.dp)
            )
        }

        // Apple TV+ / Netflix-style directional vignette: keeps upper-right scene clear
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xF0090A0F),
                        0.42f to Color(0x9E090A0F),
                        0.82f to Color.Transparent
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0x22090A0F),
                        0.45f to Color(0x55090A0F),
                        0.78f to TvColors.Background,
                        1.0f to TvColors.Background
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 46.dp)
                .focusGroup()
                .then(
                    if (showStreamPickerModal) Modifier.focusProperties { canFocus = false }
                    else Modifier
                ),
            contentPadding = PaddingValues(top = 24.dp, bottom = 56.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.tvBringIntoViewPadding(
                        topPadding = 24.dp,
                        bottomPadding = 20.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zpět",
                        size = 38.dp,
                        onClick = onBack
                    )
                }

                Spacer(modifier = Modifier.height(if (item.type == MediaType.SERIES) 64.dp else 128.dp))

                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 40.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(640.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Clean, authentic metadata row
                val metaParts = buildList {
                    item.year?.let { add(it.toString()) }
                    item.runtimeMinutes?.let { mins ->
                        val hrs = mins / 60
                        val rem = mins % 60
                        add(if (hrs > 0) "${hrs}h ${rem}m" else "${rem}m")
                    }
                    if (item.genres.isNotEmpty()) {
                        add(item.genres.take(3).joinToString(", "))
                    }
                    item.rating?.takeIf { it > 0.0 }?.let { r ->
                        add("★ %.1f".format(r))
                    }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString("  ·  "),
                        color = Color(0xFFD4D8E0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!item.originalTitle.isNullOrBlank() &&
                    !item.originalTitle.equals(item.title, ignoreCase = true)
                ) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.originalTitle,
                        color = TvColors.TextMuted,
                        fontSize = 12.sp
                    )
                }

                if (item.type == MediaType.SERIES && selectedEpisode != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${selectedEpisode.code} – ${selectedEpisode.title}",
                        color = TvColors.AccentGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val cleanOverview = item.overview
                    .takeIf { it.isNotBlank() && !it.contains("dostupné ke streamování přes Webshare", ignoreCase = true) }
                if (!cleanOverview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = cleanOverview,
                        color = Color(0xFFE5E7EB),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(600.dp)
                    )
                }

                if (details.cast.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Hrají: ${details.cast.take(4).joinToString(", ")}",
                        color = TvColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(600.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Clean Action Buttons (no oversized boxes or duplicated subtitles)
                val recommended = streamResolution?.recommendedStream
                val primaryPlayLabel = when {
                    item.type == MediaType.SERIES && selectedEpisode != null -> "Přehrát ${selectedEpisode.code}"
                    else -> "Přehrát"
                }

                Row(
                    modifier = Modifier.tvBringIntoViewPadding(
                        topPadding = 24.dp,
                        bottomPadding = 20.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TvButton(
                        text = primaryPlayLabel,
                        icon = Icons.Default.PlayArrow,
                        isPrimary = true,
                        paddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = Modifier.focusRequester(playFocusRequester),
                        onClick = onPrimaryPlay
                    )

                    val totalStreams = streamResolution?.allStreams?.size ?: 0
                    TvButton(
                        text = if (totalStreams > 0) "Verze ($totalStreams)" else "Verze",
                        icon = Icons.Default.Settings,
                        paddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        onClick = onOpenStreamPicker
                    )

                    TvButton(
                        text = if (isInMyList) "V seznamu" else "Můj seznam",
                        icon = if (isInMyList) Icons.Default.Check else Icons.Default.Star,
                        isSelected = isInMyList,
                        paddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        onClick = { onToggleMyList(item) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isResolvingStreams) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = TvColors.TextSecondary,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hledám dostupné verze na Webshare...",
                            color = TvColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                } else if (recommended != null) {
                    val notice = streamResolution.selectionNotice?.let { " · $it" } ?: ""
                    Text(
                        text = "${recommended.language.badge} · ${recommended.quality.badge} · ${recommended.file.formattedSize}$notice",
                        color = TvColors.TextMuted,
                        fontSize = 12.sp
                    )
                }

                if (!streamError.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33E50914))
                            .border(1.dp, TvColors.NetflixRed, RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = streamError,
                            color = Color(0xFFFFB4B8),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // TV Series Seasons & Episodes Browser
            if (item.type == MediaType.SERIES && details.seasons.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.tvBringIntoViewContainer(
                            topPadding = 16.dp,
                            bottomPadding = 20.dp
                        )
                    ) {
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = "Série a epizody",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Season Tabs
                        LazyRow(
                            modifier = Modifier.tvTrapHorizontalFocus(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
