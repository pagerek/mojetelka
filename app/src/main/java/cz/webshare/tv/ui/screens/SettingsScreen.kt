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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import cz.webshare.tv.ui.components.TvIconButton

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
    onLogout: () -> Unit
) {
    var tmdbInput by remember(preferences.tmdbApiKey) { mutableStateOf(preferences.tmdbApiKey) }

    val qualityFallbackChain = VideoQuality.fallbackOrderFor(preferences.preferredQuality)
        .joinToString(" → ") { it.badge }
    val languageFallbackChain = AudioLanguage.fallbackOrderFor(preferences.preferredLanguage)
        .joinToString(" → ") { it.shortLabel }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Default Video Quality + Fallback
        item {
            SettingsCard(
                title = "1. Výchozí kvalita videa (primární spouštění + fallback)",
                subtitle = "Aplikace primárně vybere tuto kvalitu. Pokud pro daný film na Webshare neexistuje, automaticky použije fallback pořadí: $qualityFallbackChain"
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VideoQuality.entries.forEach { quality ->
                        val isSelected = preferences.preferredQuality == quality
                        TvButton(
                            text = quality.labelCz,
                            icon = if (isSelected) Icons.Default.Check else null,
                            isSelected = isSelected,
                            isPrimary = isSelected,
                            onClick = { onSelectQuality(quality) }
                        )
                    }
                }
            }
        }

        // 2. Default Audio Language + Fallback
        item {
            SettingsCard(
                title = "2. Výchozí jazyk zvuku a titulků (primární spouštění + fallback)",
                subtitle = "Primárně se spouští zvolený jazyk. Pokud není na Webshare k dispozici, automaticky přepne podle fallback pořadí: $languageFallbackChain"
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AudioLanguage.entries.forEach { lang ->
                        val isSelected = preferences.preferredLanguage == lang
                        TvButton(
                            text = lang.labelCz,
                            icon = if (isSelected) Icons.Default.Check else null,
                            isSelected = isSelected,
                            isPrimary = isSelected,
                            onClick = { onSelectLanguage(lang) }
                        )
                    }
                }
            }
        }

        // 3. Primary Play Button Launch Mode
        item {
            SettingsCard(
                title = "3. Chování při kliknutí na „Přehrát“",
                subtitle = "Zvol, zda má tlačítko Přehrát rovnou spustit film podle výchozí kvality a jazyka výše (s fallbackem), nebo vždy nejdřív otevřít tabulku zdrojů."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LaunchMode.entries.forEach { mode ->
                        val isSelected = preferences.launchMode == mode
                        TvButton(
                            text = mode.labelCz,
                            subtitle = mode.descriptionCz,
                            icon = if (isSelected) Icons.Default.Check else null,
                            isSelected = isSelected,
                            isPrimary = isSelected,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelectLaunchMode(mode) }
                        )
                    }
                }
            }
        }

        // 4. Webshare.cz VIP Account
        item {
            SettingsCard(
                title = "4. Účet Webshare.cz VIP",
                subtitle = "Stav přihlášeného účtu pro přímé VIP streamování bez omezení rychlosti."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Uživatel: ${userProfile?.username ?: savedUsername}",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val isVip = userProfile?.isVip == true
                            TechBadgePill(
                                text = if (isVip) "VIP AKTIVNÍ (${userProfile?.vipDaysLeft} dní)" else "BEZ VIP / NEOVĚŘENO",
                                backgroundColor = if (isVip) Color(0x3310B981) else Color(0x33F59E0B),
                                textColor = if (isVip) TvColors.VipGreen else TvColors.AccentGold
                            )
                        }
                        if (!userProfile?.vipUntil.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Platnost VIP do: ${userProfile?.vipUntil}",
                                color = TvColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    TvButton(
                        text = "Odhlásit účet",
                        icon = Icons.Default.ExitToApp,
                        onClick = onLogout
                    )
                }
            }
        }

        // 5. Optional Custom TMDB v3 API Key
        item {
            SettingsCard(
                title = "5. Volitelný TMDB API klíč (v3)",
                subtitle = "Aplikace funguje ihned po instalaci bez klíče. Pokud chceš rozšířené české popisky přímo z TheMovieDB.org, můžeš sem vložit svůj TMDB v3 API klíč."
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = tmdbInput,
                        onValueChange = { tmdbInput = it },
                        placeholder = { Text("Volitelný TMDB v3 API klíč...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = TvColors.NetflixRed,
                            unfocusedBorderColor = Color(0xFF34394F)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    TvButton(
                        text = "Uložit a obnovit katalog",
                        isPrimary = true,
                        onClick = { onSaveTmdbKey(tmdbInput) }
                    )
                }
            }
        }

        // 6. Subtle Git Update Row inside Settings (Icon-only update button)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B0D13))
                    .border(1.dp, Color(0xFF1E2230), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Webshare TV v$appVersionName (build $appVersionCode)",
                            color = TvColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TechBadgePill(
                            text = "Git: pagerek/mojetelka",
                            backgroundColor = Color(0xFF151824),
                            textColor = TvColors.TextMuted
                        )
                    }

                    if (!updateStatusMessage.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = updateStatusMessage,
                            color = if (updateStatusMessage.startsWith("✓")) TvColors.VipGreen else TvColors.AccentGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (isUpdatingApp && updateProgressPercent > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0x33FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((updateProgressPercent / 100f).coerceIn(0.02f, 1f))
                                    .height(4.dp)
                                    .background(TvColors.NetflixRed)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                if (isUpdatingApp) {
                    CircularProgressIndicator(
                        color = TvColors.NetflixRed,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    // Subtle icon-only update button
                    TvIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Zkontrolovat aktualizaci v Gitu",
                        onClick = onCheckGitUpdate
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TvColors.SurfaceDark)
            .border(1.dp, Color(0xFF222636), RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = subtitle,
            color = TvColors.TextSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}
