package cz.webshare.tv.ui.components

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import cz.webshare.tv.model.CatalogItem
import cz.webshare.tv.model.MediaType
import cz.webshare.tv.model.WatchProgressItem
import kotlinx.coroutines.delay

object TvColors {
    val Background = Color(0xFF06070A)
    val SurfaceDark = Color(0xFF0E1017)
    val SurfaceElevated = Color(0xFF151822)
    val NetflixRed = Color(0xFFE50914)
    val NetflixRedDark = Color(0xFFB80710)
    val AccentGold = Color(0xFFF59E0B)
    val VipGreen = Color(0xFF10B981)
    val TextPrimary = Color(0xFFF9FAFB)
    val TextSecondary = Color(0xFF9CA3AF)
    val TextMuted = Color(0xFF6B7280)
    val FocusBorder = Color(0xFFFFFFFF)
}

/**
 * Remote D-Pad + Click friendly TV button.
 * Uses a single `.clickable()` modifier (which includes Compose's native focusable target
 * and D-Pad Center / Enter activation) so focus never gets trapped in nested focus nodes.
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = false,
    isSelected: Boolean = false,
    subtitle: String? = null,
    paddingValues: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(120),
        label = "btn_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused && isPrimary -> Color(0xFFFF1E27)
            isFocused -> Color.White
            isPrimary -> TvColors.NetflixRed
            isSelected -> Color(0xFF1F2330)
            else -> Color(0xFF11131B)
        },
        animationSpec = tween(120),
        label = "btn_bg"
    )

    val contentColor = when {
        isFocused && isPrimary -> Color.White
        isFocused -> Color(0xFF06070A)
        isPrimary -> Color.White
        isSelected -> Color.White
        else -> Color(0xFFD1D5DB)
    }

    val borderColor = when {
        isFocused -> Color.White
        isSelected -> TvColors.NetflixRed
        isPrimary -> TvColors.NetflixRed
        else -> Color(0xFF232736)
    }

    val borderWidth = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 1.dp

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(paddingValues),
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
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Column {
                Text(
                    text = text,
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = if (isFocused || isPrimary || isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        color = contentColor.copy(alpha = 0.78f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Sleek Netflix-style top navigation tab button for Android TV.
 */
@Composable
fun TvNavTabItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1.0f,
        animationSpec = tween(120),
        label = "nav_scale"
    )

    val bgColor = when {
        isFocused -> Color.White
        isSelected -> TvColors.NetflixRed
        else -> Color.Transparent
    }

    val textColor = when {
        isFocused -> Color(0xFF06070A)
        isSelected -> Color.White
        else -> Color(0xFFD1D5DB)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Netflix-style poster card for Android TV D-Pad navigation.
 */
@Composable
fun TvPosterCard(
    item: CatalogItem,
    onFocused: (CatalogItem) -> Unit,
    onClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 132.dp,
    height: Dp = 194.dp
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1.0f,
        animationSpec = tween(120),
        label = "card_scale"
    )

    val imageRequest = remember(item.posterUrl) {
        item.posterUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(264, 388)
                .crossfade(false)
                .build()
        }
    }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(TvColors.SurfaceDark)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF1E2230),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused(item)
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(item) }
            )
    ) {
        // Rich fallback gradient background while poster loads
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF231520), Color(0xFF0E1018))
                    )
                )
        )

        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top badges (Series/Movie + Rating)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (item.type == MediaType.SERIES) Color(0xDD2563EB) else Color(0xDDE50914)
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (item.type == MediaType.SERIES) "SERIÁL" else "FILM",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            item.displayRating?.let { rating ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xDD000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ $rating",
                        color = TvColors.AccentGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Bottom title gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xCC06070A),
                            Color(0xF806070A)
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.year != null) {
                    Text(
                        text = item.year.toString(),
                        color = TvColors.TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Continue Watching ("Rozkoukané") landscape card with Netflix red progress bar
 * and support for D-Pad long-press (or MENU key) action menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvContinueWatchingCard(
    item: WatchProgressItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    var isHoldingSelect by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // Timer for D-Pad OK/Enter long press (500ms hold triggers options modal)
    LaunchedEffect(isHoldingSelect) {
        if (isHoldingSelect && onLongClick != null) {
            delay(480L)
            if (isHoldingSelect && !longPressFired) {
                longPressFired = true
                onLongClick()
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(120),
        label = "cw_scale"
    )

    val imageUrl = item.backdropUrl ?: item.posterUrl
    val imageRequest = remember(imageUrl) {
        imageUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(420, 236)
                .crossfade(false)
                .build()
        }
    }

    Box(
        modifier = modifier
            .width(210.dp)
            .height(118.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(TvColors.SurfaceDark)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF222636),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (!it.isFocused) {
                    isHoldingSelect = false
                    longPressFired = false
                }
            }
            .onPreviewKeyEvent { event ->
                if (onLongClick == null) return@onPreviewKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                val action = event.nativeKeyEvent.action

                // Dedicated Menu / Info / Guide remote buttons open options immediately
                if (action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_BOOKMARK)
                ) {
                    onLongClick()
                    return@onPreviewKeyEvent true
                }

                // D-Pad Center / Enter long-press detection
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                ) {
                    when (action) {
                        KeyEvent.ACTION_DOWN -> {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                isHoldingSelect = true
                                longPressFired = false
                            } else if (event.nativeKeyEvent.repeatCount >= 1 && !longPressFired) {
                                longPressFired = true
                                isHoldingSelect = false
                                onLongClick()
                            }
                            true
                        }
                        KeyEvent.ACTION_UP -> {
                            val wasLongPress = longPressFired
                            isHoldingSelect = false
                            longPressFired = false
                            if (!wasLongPress) {
                                onClick()
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x22000000), Color(0xEE06070A))
                    )
                )
        )

        // Subtle top-right hint when focused that holding OK opens options
        if (isFocused && onLongClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC06070A))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Podrž OK: Možnosti",
                    color = Color(0xFFE5E7EB),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = "▶ Pokračovat (${item.formattedPosition})",
                color = TvColors.NetflixRed,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x55FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.progressFraction)
                        .height(3.5.dp)
                        .background(TvColors.NetflixRed)
                )
            }
        }
    }
}

/**
 * Modal dialog shown on long-press of a Continue Watching card.
 * Allows resuming, restarting from beginning, marking as watched, removing from row, or opening media detail.
 */
@Composable
fun ContinueWatchingOptionsDialog(
    item: WatchProgressItem,
    onResume: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onMarkAsWatched: () -> Unit,
    onRemoveFromRow: () -> Unit,
    onOpenDetail: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstOptionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(item) {
        delay(40L)
        runCatching { firstOptionFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9050609))
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)
                ) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(TvColors.SurfaceDark)
                .border(1.5.dp, Color(0xFF2F354D), RoundedCornerShape(18.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TechBadgePill(
                    text = if (item.mediaType == MediaType.SERIES) "ROZKOUKANÝ SERIÁL" else "ROZKOUKANÝ FILM",
                    backgroundColor = TvColors.NetflixRed
                )
                TechBadgePill(
                    text = "Pozice: ${item.formattedPosition}",
                    backgroundColor = Color(0xFF1F2437),
                    textColor = TvColors.AccentGold
                )
            }

            Text(
                text = item.title,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.subtitle.isNullOrEmpty()) {
                Text(
                    text = item.subtitle,
                    color = TvColors.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            TvButton(
                text = "▶ Pokračovat v přehrávání (od ${item.formattedPosition})",
                isPrimary = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstOptionFocusRequester),
                onClick = onResume
            )

            TvButton(
                text = "⏮ Přehrát znovu od začátku",
                subtitle = "Spustit video od času 00:00",
                modifier = Modifier.fillMaxWidth(),
                onClick = onPlayFromBeginning
            )

            TvButton(
                text = "✓ Označit jako zhlédnuté",
                subtitle = "Dokončit sledování a skrýt z pásu Pokračovat ve sledování",
                modifier = Modifier.fillMaxWidth(),
                onClick = onMarkAsWatched
            )

            TvButton(
                text = "✕ Odebrat z Pokračovat ve sledování",
                subtitle = "Smazat uloženou pozici přehrávání",
                modifier = Modifier.fillMaxWidth(),
                onClick = onRemoveFromRow
            )

            TvButton(
                text = "ℹ Detail titulu a výběr kvality / epizod",
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenDetail
            )

            Spacer(modifier = Modifier.height(4.dp))

            TvButton(
                text = "Zavřít",
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss
            )
        }
    }
}

@Composable
fun TechBadgePill(
    text: String,
    backgroundColor: Color = Color(0xFF1A1E2B),
    textColor: Color = Color.White
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Subtle icon-only button for Android TV D-Pad navigation (e.g. Git update check icon in Settings).
 */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = tween(120),
        label = "icon_btn_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color(0xFF161924),
        animationSpec = tween(120),
        label = "icon_btn_bg"
    )

    val iconTint = if (isFocused) Color(0xFF06070A) else Color(0xFFD1D5DB)
    val borderColor = if (isFocused) Color.White else Color(0xFF282D3E)

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(if (isFocused) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}
