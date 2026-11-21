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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
 * and a compact anchored Popover menu triggered by holding OK / Enter (or pressing MENU).
 *
 * Importantly, uses `focusable()` + explicit key state tracking instead of `combinedClickable`
 * so releasing the OK button after a long-press NEVER accidentally triggers the first popover item!
 */
@Composable
fun TvContinueWatchingCard(
    item: WatchProgressItem,
    onClick: () -> Unit,
    onPlayFromBeginning: (() -> Unit)? = null,
    onMarkAsWatched: (() -> Unit)? = null,
    onRemoveFromRow: (() -> Unit)? = null,
    onOpenDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    var isHoldingSelect by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    var showPopoverMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val hasPopoverActions = onPlayFromBeginning != null ||
        onMarkAsWatched != null ||
        onRemoveFromRow != null ||
        onOpenDetail != null

    // Timer for D-Pad OK/Enter long press (450ms hold opens compact popover right on the card)
    LaunchedEffect(isHoldingSelect) {
        if (isHoldingSelect && hasPopoverActions) {
            delay(450L)
            if (isHoldingSelect && !longPressFired) {
                longPressFired = true
                showPopoverMenu = true
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused || showPopoverMenu) 1.06f else 1.0f,
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

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(210.dp)
                .height(118.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(10.dp))
                .background(TvColors.SurfaceDark)
                .border(
                    width = if (isFocused || showPopoverMenu) 3.dp else 1.dp,
                    color = when {
                        showPopoverMenu -> TvColors.NetflixRed
                        isFocused -> Color.White
                        else -> Color(0xFF222636)
                    },
                    shape = RoundedCornerShape(10.dp)
                )
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (!it.isFocused) {
                        isHoldingSelect = false
                    }
                }
                .onPreviewKeyEvent { event ->
                    val keyCode = event.nativeKeyEvent.keyCode
                    val action = event.nativeKeyEvent.action

                    // Dedicated Menu / Info / Guide remote buttons open popover immediately
                    if (hasPopoverActions && action == KeyEvent.ACTION_DOWN &&
                        (keyCode == KeyEvent.KEYCODE_MENU ||
                            keyCode == KeyEvent.KEYCODE_INFO ||
                            keyCode == KeyEvent.KEYCODE_BOOKMARK)
                    ) {
                        showPopoverMenu = true
                        return@onPreviewKeyEvent true
                    }

                    // D-Pad Center / Enter short-press vs long-press handling
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    ) {
                        when (action) {
                            KeyEvent.ACTION_DOWN -> {
                                if (event.nativeKeyEvent.repeatCount == 0) {
                                    isHoldingSelect = true
                                    longPressFired = false
                                } else if (hasPopoverActions && event.nativeKeyEvent.repeatCount >= 1 && !longPressFired) {
                                    longPressFired = true
                                    isHoldingSelect = false
                                    showPopoverMenu = true
                                }
                                true
                            }
                            KeyEvent.ACTION_UP -> {
                                val wasLongPress = longPressFired
                                isHoldingSelect = false
                                longPressFired = false
                                // Only fire short click if long press did NOT open the popover!
                                if (!wasLongPress && !showPopoverMenu) {
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
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (!showPopoverMenu && !longPressFired) {
                            onClick()
                        }
                    }
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

            // Subtle top-right hint when focused that holding OK opens popover
            if ((isFocused || showPopoverMenu) && hasPopoverActions) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (showPopoverMenu) TvColors.NetflixRed else Color(0xCC06070A))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (showPopoverMenu) "Možnosti ▾" else "Podrž OK: Možnosti",
                        color = Color.White,
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

        // Compact Anchored Popover Menu right under the card (no fullscreen overlay!)
        if (showPopoverMenu) {
            ContinueWatchingPopoverMenu(
                item = item,
                onResume = {
                    showPopoverMenu = false
                    onClick()
                },
                onPlayFromBeginning = {
                    showPopoverMenu = false
                    onPlayFromBeginning?.invoke()
                },
                onMarkAsWatched = {
                    showPopoverMenu = false
                    onMarkAsWatched?.invoke()
                },
                onRemoveFromRow = {
                    showPopoverMenu = false
                    onRemoveFromRow?.invoke()
                },
                onOpenDetail = {
                    showPopoverMenu = false
                    onOpenDetail?.invoke()
                },
                onDismiss = { showPopoverMenu = false }
            )
        }
    }
}

/**
 * Compact anchored popover menu attached directly below the Continue Watching card.
 * Ignores the trailing `ACTION_UP` from the initial long-press hold so releasing the OK button
 * never auto-triggers the first option or closes the menu.
 */
@Composable
private fun ContinueWatchingPopoverMenu(
    item: WatchProgressItem,
    onResume: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onMarkAsWatched: () -> Unit,
    onRemoveFromRow: () -> Unit,
    onOpenDetail: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstOptionFocusRequester = remember { FocusRequester() }
    // Arm selection only after we receive a fresh ACTION_DOWN inside the popover
    // (or after the user has released the initial long-press key).
    var armedForClick by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(30L)
        runCatching { firstOptionFocusRequester.requestFocus() }
        // Safety fallback if the long-press key was already released before focus arrived
        delay(700L)
        armedForClick = true
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, 130),
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .width(236.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xF7121521))
                .border(1.5.dp, Color(0xFF38405C), RoundedCornerShape(12.dp))
                .onPreviewKeyEvent { event ->
                    val keyCode = event.nativeKeyEvent.keyCode
                    val action = event.nativeKeyEvent.action

                    if (action == KeyEvent.ACTION_DOWN &&
                        (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)
                    ) {
                        onDismiss()
                        return@onPreviewKeyEvent true
                    }

                    // Swallow the lingering ACTION_UP or repeated ACTION_DOWN from the initial long-press
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    ) {
                        if (action == KeyEvent.ACTION_DOWN) {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                armedForClick = true
                            } else if (!armedForClick) {
                                // Still holding from the long press -> swallow repeat
                                return@onPreviewKeyEvent true
                            }
                        } else if (action == KeyEvent.ACTION_UP && !armedForClick) {
                            // User just released the long-press OK button!
                            // Swallow this ACTION_UP and arm the popover for the next normal press.
                            armedForClick = true
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                }
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                color = TvColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )

            PopoverMenuItem(
                text = "▶ Pokračovat (${item.formattedPosition})",
                isPrimary = true,
                isArmed = { armedForClick },
                modifier = Modifier.focusRequester(firstOptionFocusRequester),
                onClick = onResume
            )

            PopoverMenuItem(
                text = "⏮ Přehrát od začátku",
                isArmed = { armedForClick },
                onClick = onPlayFromBeginning
            )

            PopoverMenuItem(
                text = "✓ Označit jako zhlédnuté",
                isArmed = { armedForClick },
                onClick = onMarkAsWatched
            )

            PopoverMenuItem(
                text = "✕ Odebrat z pásu",
                isDestructive = true,
                isArmed = { armedForClick },
                onClick = onRemoveFromRow
            )

            PopoverMenuItem(
                text = "ℹ Detail a epizody",
                isArmed = { armedForClick },
                onClick = onOpenDetail
            )
        }
    }
}

@Composable
private fun PopoverMenuItem(
    text: String,
    isArmed: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val bgColor = when {
        isFocused && isPrimary -> TvColors.NetflixRed
        isFocused && isDestructive -> Color(0xFFB91C1C)
        isFocused -> Color.White
        else -> Color.Transparent
    }

    val textColor = when {
        isFocused && (isPrimary || isDestructive) -> Color.White
        isFocused -> Color(0xFF06070A)
        isDestructive -> Color(0xFFFCA5A5)
        else -> Color(0xFFE5E7EB)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                ) {
                    // Block any key events (including the trailing ACTION_UP from the long-press)
                    // until the popover has been armed by a fresh key release / press cycle!
                    if (!isArmed()) {
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (isArmed()) {
                        onClick()
                    }
                }
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
