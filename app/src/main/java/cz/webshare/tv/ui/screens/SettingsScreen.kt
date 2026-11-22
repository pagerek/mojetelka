package cz.webshare.tv.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.webshare.tv.model.AudioLanguage
import cz.webshare.tv.model.LaunchMode
import cz.webshare.tv.model.UserPreferences
import cz.webshare.tv.model.VideoQuality
import cz.webshare.tv.model.WebshareUserProfile
import cz.webshare.tv.ui.components.TechBadgePill
import cz.webshare.tv.ui.components.TvButton
import cz.webshare.tv.ui.components.TvColors
import cz.webshare.tv.ui.components.tvBringIntoViewContainer

private enum class SettingsCategory(
    val title: String,
    val icon: ImageVector
) {
    PLAYBACK("Přehrávání", Icons.Default.Tune),
    ACCOUNT("Účet Webshare", Icons.Default.Person),
    CATALOG("Katalog a TMDB", Icons.Default.MovieFilter),
    UPDATES("Aktualizace", Icons.Default.SystemUpdate)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    userProfile: WebshareUserProfile?,
    savedUsername: String,
    appVersionName: String,
    appVersionCode: Int,
    isUpdatingApp: Boolean,
    updateProgressPercent: Int,
    updateStatusMessage: String?,
    onSelectQuality: (VideoQuality) -> Unit,
    onSelectLanguage: (AudioLanguage) -> Unit,
    onSelectLaunchMode: (LaunchMode) -> Unit,
    onSaveTmdbKey: (String) -> Unit,
    onCheckGitUpdate: () -> Unit,
    onLogout: () -> Unit,
    leftMenuFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    var selectedCategory by remember { mutableStateOf(SettingsCategory.PLAYBACK) }
    var isLeftRailFocused by remember { mutableStateOf(false) }
    var tmdbInput by remember(preferences.tmdbApiKey) { mutableStateOf(preferences.tmdbApiKey) }

    val qualityFallbackChain = VideoQuality.fallbackOrderFor(preferences.preferredQuality)
        .joinToString(" → ") { it.badge }
    val languageFallbackChain = AudioLanguage.fallbackOrderFor(preferences.preferredLanguage)
        .joinToString(" → ") { it.shortLabel }

    val isVip = userProfile?.isVip == true

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp, vertical = 16.dp)
    ) {
        // Left TV Navigation Rail
        Column(
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight()
                .padding(end = 20.dp)
                .onFocusChanged { isLeftRailFocused = it.hasFocus },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Nastavení",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 10.dp)
            )

            SettingsCategory.entries.forEach { category ->
                val summary = when (category) {
                    SettingsCategory.PLAYBACK -> "${preferences.preferredQuality.badge} • ${preferences.preferredLanguage.badge}"
                    SettingsCategory.ACCOUNT -> if (isVip) "VIP • ${userProfile?.vipDaysLeft} dní" else (userProfile?.username ?: savedUsername)
                    SettingsCategory.CATALOG -> if (preferences.tmdbApiKey.isBlank()) "Výchozí katalog" else "Vlastní TMDB klíč"
                    SettingsCategory.UPDATES -> "v$appVersionName (build $appVersionCode)"
                }

                SettingsNavItem(
                    title = category.title,
                    subtitle = summary,
                    icon = category.icon,
                    isSelected = selectedCategory == category,
                    modifier = (if (selectedCategory == category) {
                        Modifier.focusRequester(leftMenuFocusRequester)
                    } else {
                        Modifier
                    }).focusProperties {
                        canFocus = isLeftRailFocused || selectedCategory == category
                    },
                    onSelect = { selectedCategory = category }
                )
            }
        }

        // Subtle vertical divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .background(Color(0x18FFFFFF))
        )

        // Right Detail Content Pane
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(start = 28.dp, top = 4.dp, end = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            when (selectedCategory) {
                SettingsCategory.PLAYBACK -> {
                    item(key = "quality") {
                        SettingsSectionBlock(
                            title = "Výchozí kvalita videa",
                            helperText = "Fallback pořadí: $qualityFallbackChain"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                VideoQuality.entries.forEach { quality ->
                                    SettingsOptionTile(
                                        title = quality.badge,
                                        subtitle = quality.labelCz.substringBefore(" ("),
                                        isSelected = preferences.preferredQuality == quality,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSelectQuality(quality) }
                                    )
                                }
                            }
                        }
                    }

                    item(key = "language") {
                        SettingsSectionBlock(
                            title = "Jazyk zvuku a titulků",
                            helperText = "Fallback pořadí: $languageFallbackChain"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AudioLanguage.entries.forEach { lang ->
                                    SettingsOptionTile(
                                        title = lang.shortLabel,
                                        subtitle = lang.badge,
                                        isSelected = preferences.preferredLanguage == lang,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSelectLanguage(lang) }
                                    )
                                }
                            }
                        }
                    }

                    item(key = "launch_mode") {
                        SettingsSectionBlock(
                            title = "Akce tlačítka Přehrát",
                            helperText = null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                LaunchMode.entries.forEach { mode ->
                                    val shortTitle = when (mode) {
                                        LaunchMode.AUTO_PREFERRED_WITH_FALLBACK -> "Spustit automaticky"
                                        LaunchMode.ALWAYS_ASK -> "Vždy vybrat zdroj"
                                    }
                                    val shortDesc = when (mode) {
                                        LaunchMode.AUTO_PREFERRED_WITH_FALLBACK -> "Vybere nejlepší stream podle kvality a jazyka výše."
                                        LaunchMode.ALWAYS_ASK -> "Před přehráním otevře přehled dostupných souborů."
                                    }
                                    SettingsOptionTile(
                                        title = shortTitle,
                                        subtitle = shortDesc,
                                        isSelected = preferences.launchMode == mode,
                                        modifier = Modifier.weight(1f),
                                        maxSubtitleLines = 2,
                                        onClick = { onSelectLaunchMode(mode) }
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsCategory.ACCOUNT -> {
                    item(key = "account") {
                        SettingsSectionBlock(
                            title = "Účet Webshare.cz",
                            helperText = "Přímé VIP streamování bez omezení rychlosti."
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TvColors.SurfaceCard)
                                    .border(1.dp, TvColors.BorderSubtle, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 22.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = userProfile?.username ?: savedUsername,
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        TechBadgePill(
                                            text = if (isVip) "VIP AKTIVNÍ • ${userProfile?.vipDaysLeft} dní" else "BEZ VIP / NEOVĚŘENO",
                                            backgroundColor = if (isVip) Color(0x2B10B981) else Color(0x2BF59E0B),
                                            textColor = if (isVip) TvColors.VipGreen else TvColors.AccentGold
                                        )
                                    }
                                    if (!userProfile?.vipUntil.isNullOrEmpty()) {
                                        Text(
                                            text = "Platnost VIP do ${userProfile?.vipUntil}",
                                            color = TvColors.TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                TvButton(
                                    text = "Odhlásit účet",
                                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                                    onClick = onLogout
                                )
                            }
                        }
                    }
                }

                SettingsCategory.CATALOG -> {
                    item(key = "tmdb") {
                        SettingsSectionBlock(
                            title = "TMDB API klíč (volitelné)",
                            helperText = "Aplikace funguje ihned bez klíče. Vlastní TMDB v3 klíč zpřístupní rozšířené české popisky z TheMovieDB.org."
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TvColors.SurfaceCard)
                                    .border(1.dp, TvColors.BorderSubtle, RoundedCornerShape(10.dp))
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                OutlinedTextField(
                                    value = tmdbInput,
                                    onValueChange = { tmdbInput = it },
                                    placeholder = { Text("Vlož TMDB v3 API klíč...") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = TvColors.NetflixRed,
                                        unfocusedBorderColor = Color(0xFF34394F)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TvButton(
                                        text = "Uložit a obnovit katalog",
                                        isPrimary = true,
                                        onClick = { onSaveTmdbKey(tmdbInput) }
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsCategory.UPDATES -> {
                    item(key = "updates") {
                        SettingsSectionBlock(
                            title = "Aktualizace aplikace",
                            helperText = "Kontrola nových verzí přímo z repozitáře GitHub."
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TvColors.SurfaceCard)
                                    .border(1.dp, TvColors.BorderSubtle, RoundedCornerShape(10.dp))
                                    .padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "Webshare TV v$appVersionName",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            TechBadgePill(
                                                text = "build $appVersionCode",
                                                backgroundColor = Color(0xFF1E2230),
                                                textColor = TvColors.TextSecondary
                                            )
                                        }
                                        Text(
                                            text = "Zdroj: pagerek/mojetelka",
                                            color = TvColors.TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }

                                    if (isUpdatingApp) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                color = TvColors.NetflixRed,
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                            Text(
                                                text = if (updateProgressPercent > 0) "$updateProgressPercent %" else "Kontroluji...",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    } else {
                                        TvButton(
                                            text = "Zkontrolovat aktualizace",
                                            icon = Icons.Default.Refresh,
                                            isPrimary = true,
                                            onClick = onCheckGitUpdate
                                        )
                                    }
                                }

                                if (isUpdatingApp && updateProgressPercent > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(2.5.dp))
                                            .background(Color(0x26FFFFFF))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth((updateProgressPercent / 100f).coerceIn(0.02f, 1f))
                                                .height(5.dp)
                                                .background(TvColors.NetflixRed)
                                        )
                                    }
                                }

                                if (!updateStatusMessage.isNullOrEmpty()) {
                                    Text(
                                        text = updateStatusMessage,
                                        color = if (updateStatusMessage.startsWith("✓")) TvColors.VipGreen else TvColors.AccentGold,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        animationSpec = tween(110),
        label = "settings_nav_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> Color(0xFF1A1D26)
            else -> Color.Transparent
        },
        animationSpec = tween(110),
        label = "settings_nav_bg"
    )

    val primaryColor = if (isFocused) Color(0xFF0A0A0A) else Color.White
    val secondaryColor = if (isFocused) Color(0xFF4A4A4A) else TvColors.TextSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left active indicator pill
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    when {
                        isFocused -> TvColors.NetflixRed
                        isSelected -> TvColors.NetflixRed
                        else -> Color.Transparent
                    }
                )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) Color(0xFF0A0A0A) else if (isSelected) Color.White else TvColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = primaryColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = secondaryColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsSectionBlock(
    title: String,
    helperText: String?,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tvBringIntoViewContainer(topPadding = 16.dp, bottomPadding = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        content()
        if (!helperText.isNullOrEmpty()) {
            Text(
                text = helperText,
                color = TvColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SettingsOptionTile(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxSubtitleLines: Int = 1
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = tween(110),
        label = "tile_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> Color(0x24E50914)
            else -> TvColors.SurfaceCard
        },
        animationSpec = tween(110),
        label = "tile_bg"
    )

    val borderColor = when {
        isFocused -> Color.White
        isSelected -> TvColors.NetflixRed
        else -> TvColors.BorderSubtle
    }

    val titleColor = when {
        isFocused -> Color(0xFF0A0A0A)
        else -> Color.White
    }

    val subtitleColor = when {
        isFocused -> Color(0xFF444444)
        isSelected -> Color(0xFFD6D6D6)
        else -> TvColors.TextSecondary
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isFocused) TvColors.NetflixRed else TvColors.NetflixRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Text(
            text = subtitle,
            color = subtitleColor,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = maxSubtitleLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
