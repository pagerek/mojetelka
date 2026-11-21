package cz.webshare.tv.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.webshare.tv.model.CatalogItem
import cz.webshare.tv.ui.components.TechBadgePill
import cz.webshare.tv.ui.components.TvButton
import cz.webshare.tv.ui.components.TvColors
import cz.webshare.tv.ui.components.TvPosterCard
import kotlinx.coroutines.delay

private val QUICK_SEARCH_TIPS = listOf(
    "Spider-Man",
    "Vlny",
    "Pelíšky",
    "Duna",
    "Gladiátor",
    "Oppenheimer",
    "Zaklínač",
    "MOST!",
    "Bod obnovy",
    "Pán prstenů",
    "Interstellar"
)

private val MAIN_KEY_ROWS = listOf(
    listOf("A", "B", "C", "D", "E", "F"),
    listOf("G", "H", "I", "J", "K", "L"),
    listOf("M", "N", "O", "P", "Q", "R"),
    listOf("S", "T", "U", "V", "W", "X"),
    listOf("Y", "Z", "0", "1", "2", "3"),
    listOf("4", "5", "6", "7", "8", "9")
)

private val CZECH_KEY_ROWS = listOf(
    listOf("Á", "Č", "Ď", "É", "Ě", "Í"),
    listOf("Ň", "Ó", "Ř", "Š", "Ť", "Ú"),
    listOf("Ů", "Ý", "Ž", "-", ":", ".")
)

@Composable
fun SearchScreen(
    query: String,
    isSearching: Boolean,
    results: List<CatalogItem>,
    onQueryChange: (String) -> Unit,
    onTriggerSearch: () -> Unit,
    onFocusItem: (CatalogItem) -> Unit,
    onOpenDetail: (CatalogItem) -> Unit
) {
    var showCzechChars by remember { mutableStateOf(false) }
    val firstKeyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(50L)
        runCatching { firstKeyFocusRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // =====================================================================
        // LEFT PANEL: Built-in Netflix-style D-Pad On-Screen Keyboard
        // (Zero Android system IME popup -> never covers the screen!)
        // =====================================================================
        Column(
            modifier = Modifier
                .width(304.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(TvColors.SurfaceDark)
                .border(1.dp, Color(0xFF23283B), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Action Row: Spacebar, Backspace, Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TvKeyboardKey(
                    label = "Mezera",
                    icon = Icons.Default.SpaceBar,
                    modifier = Modifier.weight(1.35f),
                    height = 38.dp,
                    onClick = {
                        if (query.isNotEmpty() && !query.endsWith(" ")) {
                            onQueryChange("$query ")
                        }
                    }
                )
                TvKeyboardKey(
                    label = "Smazat",
                    icon = Icons.Default.Backspace,
                    modifier = Modifier.weight(1.1f),
                    height = 38.dp,
                    onClick = {
                        if (query.isNotEmpty()) {
                            onQueryChange(query.dropLast(1))
                        }
                    }
                )
                TvKeyboardKey(
                    label = "Vše",
                    icon = Icons.Default.Clear,
                    modifier = Modifier.weight(0.85f),
                    height = 38.dp,
                    onClick = {
                        if (query.isNotEmpty()) {
                            onQueryChange("")
                        }
                    }
                )
            }

            // 6x6 Alphanumeric Grid (A-Z, 0-9)
            MAIN_KEY_ROWS.forEachIndexed { rowIdx, rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    rowKeys.forEachIndexed { colIdx, keyChar ->
                        val keyMod = if (rowIdx == 0 && colIdx == 0) {
                            Modifier
                                .weight(1f)
                                .focusRequester(firstKeyFocusRequester)
                        } else {
                            Modifier.weight(1f)
                        }
                        TvKeyboardKey(
                            label = keyChar,
                            modifier = keyMod,
                            height = 36.dp,
                            onClick = {
                                val nextChar = if (query.isEmpty() || query.endsWith(" ") || query.endsWith("-")) {
                                    keyChar
                                } else {
                                    keyChar.lowercase()
                                }
                                onQueryChange(query + nextChar)
                            }
                        )
                    }
                }
            }

            // Optional Czech Diacritics & Punctuation Rows
            if (showCzechChars) {
                CZECH_KEY_ROWS.forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        rowKeys.forEach { keyChar ->
                            TvKeyboardKey(
                                label = keyChar,
                                modifier = Modifier.weight(1f),
                                height = 34.dp,
                                isAccent = true,
                                onClick = {
                                    val nextChar = if (query.isEmpty() || query.endsWith(" ") || query.endsWith("-")) {
                                        keyChar
                                    } else {
                                        keyChar.lowercase()
                                    }
                                    onQueryChange(query + nextChar)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Row: Toggle Czech Diacritics + Immediate Search Trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TvKeyboardKey(
                    label = if (showCzechChars) "Skrýt háčky" else "Háčky / Čárky",
                    isAccent = showCzechChars,
                    modifier = Modifier.weight(1f),
                    height = 38.dp,
                    onClick = { showCzechChars = !showCzechChars }
                )

                TvKeyboardKey(
                    label = "Hledat",
                    icon = Icons.Default.Search,
                    isPrimary = true,
                    modifier = Modifier.weight(1f),
                    height = 38.dp,
                    onClick = onTriggerSearch
                )
            }
        }

        // =====================================================================
        // RIGHT PANEL: Search Query Display, Quick Suggestions & Live Poster Grid
        // =====================================================================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Live Non-IME Search Query Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TvColors.SurfaceDark)
                    .border(
                        width = 1.5.dp,
                        color = if (query.isNotEmpty()) TvColors.NetflixRed else Color(0xFF2B3044),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TvColors.NetflixRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    if (query.isEmpty()) {
                        Text(
                            text = "Vyber písmena na klávesnici vlevo nebo rychlý tip níže...",
                            color = TvColors.TextSecondary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "$query|",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isSearching) {
                    CircularProgressIndicator(
                        color = TvColors.NetflixRed,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                } else if (results.isNotEmpty()) {
                    TechBadgePill(
                        text = "${results.size} výsledků",
                        backgroundColor = Color(0xFF1E2436),
                        textColor = TvColors.AccentGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Search Suggestion Pills
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rychlé tipy:",
                    color = TvColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(QUICK_SEARCH_TIPS) { tip ->
                        TvButton(
                            text = tip,
                            isSelected = query.equals(tip, ignoreCase = true),
                            paddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            onClick = { onQueryChange(tip) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results Grid
            when {
                isSearching && results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = TvColors.NetflixRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Vyhledávám filmy a seriály pro '$query'...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                query.trim().length >= 2 && !isSearching && results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pro dotaz '$query' nebyl nalezen žádný film ani seriál.",
                            color = TvColors.TextSecondary,
                            fontSize = 15.sp
                        )
                    }
                }

                results.isNotEmpty() -> {
                    Text(
                        text = "Nalezené tituly (${results.size}) – po rozkliknutí si zvolíš kvalitu (4K / 1080p / 720p) a jazyk (CZ / EN):",
                        color = TvColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 136.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(results, key = { it.id }) { item ->
                            TvPosterCard(
                                item = item,
                                onFocused = onFocusItem,
                                onClick = onOpenDetail
                            )
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Začni psát na klávesnici vlevo",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Výsledky se automaticky zobrazují vpravo bez překrývání obrazovky systémovou klávesnicí.",
                                color = TvColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvKeyboardKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 36.dp,
    isPrimary: Boolean = false,
    isAccent: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(100),
        label = "key_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused && isPrimary -> Color(0xFFFF1E27)
            isFocused -> Color.White
            isPrimary -> TvColors.NetflixRed
            isAccent -> Color(0xFF1E2438)
            else -> Color(0xFF161925)
        },
        animationSpec = tween(100),
        label = "key_bg"
    )

    val contentColor = when {
        isFocused && isPrimary -> Color.White
        isFocused -> Color(0xFF06070A)
        isPrimary -> Color.White
        isAccent -> TvColors.AccentGold
        else -> Color(0xFFE5E7EB)
    }

    val borderColor = when {
        isFocused -> Color.White
        isPrimary -> TvColors.NetflixRed
        isAccent -> Color(0xFF343D5B)
        else -> Color(0xFF252A3D)
    }

    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(7.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                if (label.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(3.dp))
                }
            }
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = if (label.length == 1) 14.sp else 11.sp,
                    fontWeight = if (isFocused || label.length == 1) FontWeight.ExtraBold else FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
