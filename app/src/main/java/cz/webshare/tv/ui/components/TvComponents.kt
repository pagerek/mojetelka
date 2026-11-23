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
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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

class FocusRestoreRegistry {
    var lastFocusedRequester: FocusRequester? = null
}

val LocalFocusRestoreRegistry = staticCompositionLocalOf<FocusRestoreRegistry?> { null }

object TvColors {
    val Background = Color(0xFF0A0A0A)
    val SurfaceDark = Color(0xFF141414)
    val SurfaceCard = Color(0xFF14161D)
    val SurfaceElevated = Color(0xFF1F1F1F)
    val BorderSubtle = Color(0x1EFFFFFF)
    val NetflixRed = Color(0xFFE50914)
    val NetflixRedDark = Color(0xFFB81D24)
    val AccentGold = Color(0xFFF5C518)
    val VipGreen = Color(0xFF46D369)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextMuted = Color(0xFF737373)
    val FocusBorder = Color(0xFFFFFFFF)
}

/**
 * Remote D-Pad + Click friendly TV button in sleek modern Netflix TV style.
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
    paddingValues: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(110),
        label = "btn_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isPrimary -> TvColors.NetflixRed
            isSelected -> Color(0x55FFFFFF)
            else -> Color(0x2EFFFFFF)
        },
        animationSpec = tween(110),
        label = "btn_bg"
    )

    val contentColor = when {
        isFocused -> Color(0xFF0A0A0A)
        isPrimary -> Color.White
        isSelected -> Color.White
        else -> Color(0xFFE5E5E5)
    }

    val borderColor = when {
        isFocused -> Color.White
        isSelected -> TvColors.NetflixRed
        else -> Color.Transparent
    }

    val borderWidth = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 0.dp

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
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
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
            }
            Column {
                Text(
                    text = text,
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = if (isFocused || isPrimary || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        color = contentColor.copy(alpha = 0.75f),
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
 * Authentic Netflix TV top navigation tab item with animated bottom active indicator bar.
 */
@Composable
fun TvNavTabItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(110),
        label = "nav_scale"
    )

    val bgColor = when {
        isFocused -> Color.White
        else -> Color.Transparent
    }

    val textColor = when {
        isFocused -> Color(0xFF0A0A0A)
        isSelected -> Color.White
        else -> Color(0xFFB3B3B3)
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .padding(horizontal = if (icon != null) 12.dp else 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(if (isSelected) 24.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(if (isSelected) TvColors.NetflixRed else Color.Transparent)
        )
    }
}

/**
 * Clean Netflix-style vertical poster card for Android TV D-Pad navigation.
 * Uncluttered poster artwork when unfocused; reveals crisp white focus frame and title when focused.
 */
@Composable
fun TvPosterCard(
    item: CatalogItem,
    onFocused: (CatalogItem) -> Unit,
    onClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 136.dp,
    height: Dp = 200.dp,
    rankNumber: Int? = null
) {
    val context = LocalContext.current
    val focusRegistry = LocalFocusRestoreRegistry.current
    val cardFocusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var imageFailed by remember(item.posterUrl) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(120),
        label = "card_scale"
    )

    val imageRequest = remember(item.posterUrl) {
        item.posterUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(272, 400)
                .crossfade(false)
                .build()
        }
    }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .tvBringIntoViewPadding(horizontalPadding = 32.dp, topPadding = 0.dp, bottomPadding = 0.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(6.dp))
            .background(TvColors.SurfaceDark)
            .border(
                width = if (isFocused) 2.5.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .focusRequester(cardFocusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    focusRegistry?.lastFocusedRequester = cardFocusRequester
                    onFocused(item)
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(item) }
            )
    ) {
        // Subtle dark fallback canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF24161A), Color(0xFF141414))
                    )
                )
        )

        if (imageRequest != null && !imageFailed) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Minimal Netflix "W" series badge or Top 10 ribbon
        if (rankNumber != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(bottomStart = 6.dp))
                    .background(TvColors.NetflixRed)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "TOP $rankNumber",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        } else if (item.type == MediaType.SERIES) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xCC0A0A0A))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "SERIÁL",
                    color = Color(0xFFE5E5E5),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1
                )
            }
        }

        // Show title overlay if poster URL is missing/failed OR when card is focused
        if (imageRequest == null || imageFailed || isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xCC0A0A0A),
                                Color(0xF50A0A0A)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Column {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.year != null) {
                            Text(
                                text = item.year.toString(),
                                color = TvColors.TextSecondary,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                        item.displayRating?.let { rating ->
                            Text(
                                text = "${(rating.toDoubleOrNull()?.times(10)?.toInt() ?: 85)}% shoda",
                                color = TvColors.VipGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
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
    val focusRegistry = LocalFocusRestoreRegistry.current
    val cardFocusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var isHoldingSelect by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    var showPopoverMenu by remember { mutableStateOf(false) }
    var usePosterFallback by remember(item.backdropUrl) { mutableStateOf(false) }
    var imageFailed by remember(item.backdropUrl, item.posterUrl) { mutableStateOf(false) }
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

    val imageUrl = if (usePosterFallback) item.posterUrl else (item.backdropUrl ?: item.posterUrl)
    val imageRequest = remember(imageUrl) {
        imageUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(396, 208)
                .crossfade(false)
                .build()
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(198.dp)
                .height(104.dp)
                .tvBringIntoViewPadding(horizontalPadding = 32.dp, topPadding = 0.dp, bottomPadding = 0.dp)
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
                .focusRequester(cardFocusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) {
                        focusRegistry?.lastFocusedRequester = cardFocusRequester
                    } else {
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
            if (imageRequest != null && !imageFailed) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    onError = {
                        if (!usePosterFallback && !item.posterUrl.isNullOrEmpty() && item.posterUrl != item.backdropUrl) {
                            usePosterFallback = true
                        } else {
                            imageFailed = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x11000000),
                                Color(0x770A0A0A),
                                Color(0xF20A0A0A)
                            )
                        )
                    )
            )

            // Show active indicator only while the options popover is open
            if (showPopoverMenu && hasPopoverActions) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TvColors.NetflixRed)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Možnosti ▾",
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
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "▶",
                        color = TvColors.NetflixRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Pokračovat • ${item.formattedPosition}",
                        color = Color(0xFFE5E5E5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
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
    backgroundColor: Color = Color(0xCC1F1F1F),
    textColor: Color = Color(0xFFE5E5E5),
    borderColor: Color = Color(0x33FFFFFF)
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(0.75.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
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
    size: Dp = 42.dp,
    borderlessInactive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = tween(110),
        label = "icon_btn_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            borderlessInactive -> Color.Transparent
            else -> Color(0xFF1A1A1A)
        },
        animationSpec = tween(110),
        label = "icon_btn_bg"
    )

    val iconTint = if (isFocused) Color(0xFF0A0A0A) else Color(0xFFE5E5E5)
    val borderColor = when {
        isFocused -> Color.White
        borderlessInactive -> Color.Transparent
        else -> Color(0x33FFFFFF)
    }
    val borderWidth = if (isFocused) 2.dp else if (borderlessInactive) 0.dp else 1.dp

    val fadeOutBrush = if (borderlessInactive && !isFocused) {
        Brush.radialGradient(
            colors = listOf(Color(0x36FFFFFF), Color(0x10FFFFFF), Color.Transparent)
        )
    } else null

    val shape = if (borderlessInactive) RoundedCornerShape(50) else RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(
                if (fadeOutBrush != null) Modifier.background(fadeOutBrush)
                else Modifier.background(bgColor)
            )
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, shape)
                } else {
                    Modifier
                }
            )
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

/**
 * Ensures that when any focusable child inside a section/card gains focus (e.g. via D-Pad),
 * the parent LazyColumn scrolls to reveal the entire container (including section titles above
 * and breathing room below), preventing headers from getting clipped when scrolling back up.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvBringIntoViewContainer(
    topPadding: Dp = 14.dp,
    bottomPadding: Dp = 28.dp
): Modifier = composed {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val topPx = with(density) { topPadding.toPx() }
    val bottomPx = with(density) { bottomPadding.toPx() }

    val responder = remember(topPx, bottomPx) {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect {
                if (containerSize.height <= 0 || containerSize.width <= 0) {
                    return localRect
                }
                return Rect(
                    left = localRect.left,
                    top = -topPx,
                    right = localRect.right,
                    bottom = containerSize.height.toFloat() + bottomPx
                )
            }

            override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
        }
    }

    this
        .onSizeChanged { containerSize = it }
        .bringIntoViewResponder(responder)
}

/**
 * Adds vertical/horizontal breathing room around a focusable item's bring-into-view rect
 * so scaled focus borders and titles are never clipped at scrollable viewport edges.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvBringIntoViewPadding(
    topPadding: Dp = 16.dp,
    bottomPadding: Dp = 28.dp,
    horizontalPadding: Dp = 0.dp
): Modifier = composed {
    val density = LocalDensity.current
    val topPx = with(density) { topPadding.toPx() }
    val bottomPx = with(density) { bottomPadding.toPx() }
    val horizontalPx = with(density) { horizontalPadding.toPx() }

    val responder = remember(topPx, bottomPx, horizontalPx) {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect {
                return Rect(
                    left = localRect.left - horizontalPx,
                    top = localRect.top - topPx,
                    right = localRect.right + horizontalPx,
                    bottom = localRect.bottom + bottomPx
                )
            }

            override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
        }
    }

    this.bringIntoViewResponder(responder)
}

/**
 * Keeps D-Pad focus inside a horizontal row: pressing LEFT/RIGHT on the first/last item (or
 * while the neighbour is not yet composed) is consumed instead of letting the focus search
 * escape to an unrelated target such as the top navigation bar.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.tvTrapHorizontalFocus(): Modifier = this
    .focusProperties {
        exit = { direction ->
            if (direction == FocusDirection.Left || direction == FocusDirection.Right) {
                FocusRequester.Cancel
            } else {
                FocusRequester.Default
            }
        }
    }
    .focusGroup()
