package cz.webshare.tv.ui.screens

import android.graphics.Color as AndroidColor
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import cz.webshare.tv.model.ParsedStream
import cz.webshare.tv.ui.ActivePlaybackSession
import cz.webshare.tv.ui.components.TechBadgePill
import cz.webshare.tv.ui.components.TvButton
import cz.webshare.tv.ui.components.TvColors
import cz.webshare.tv.ui.components.TvIconButton
import kotlinx.coroutines.delay

data class EmbeddedTrackOption(
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    session: ActivePlaybackSession,
    onBack: () -> Unit,
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit,
    onSwitchStream: (ParsedStream, currentPositionMs: Long) -> Unit,
    onStreamErrorFallback: (currentPositionMs: Long) -> Unit,
    onNextEpisode: () -> Unit
) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasRenderedFirstFrame by remember(session.directVideoUrl) { mutableStateOf(false) }
    var showBufferingSpinner by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(session.startPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var showAudioTrackModal by remember { mutableStateOf(false) }
    var showSubtitleTrackModal by remember { mutableStateOf(false) }
    var showQualityModal by remember { mutableStateOf(false) }

    var audioTracks by remember { mutableStateOf<List<EmbeddedTrackOption>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<EmbeddedTrackOption>>(emptyList()) }
    var subtitlesDisabled by remember { mutableStateOf(false) }

    var subtitleSizeMode by remember { mutableIntStateOf(1) } // 0 = Malé, 1 = Střední, 2 = Velké
    var subtitleStyleMode by remember { mutableIntStateOf(0) } // 0 = Bílé s obrysem, 1 = Žluté, 2 = Bílé s pozadím

    var isTimelineFocused by remember { mutableStateOf(false) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }

    var upNextDismissed by remember(session.episode?.code) { mutableStateOf(false) }
    var upNextTriggered by remember(session.episode?.code) { mutableStateOf(false) }
    var playbackEnded by remember(session.episode?.code) { mutableStateOf(false) }

    val rootFocusRequester = remember { FocusRequester() }
    val timelineFocusRequester = remember { FocusRequester() }

    val anyModalOpen = showAudioTrackModal || showSubtitleTrackModal || showQualityModal

    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs
                50_000, // maxBufferMs
                2_000,  // bufferForPlaybackMs (fast start!)
                4_000   // bufferForPlaybackAfterRebufferMs
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // Intercept Back button inside player: close open modal first, otherwise stop playback & exit
    BackHandler(enabled = true) {
        when {
            showQualityModal -> showQualityModal = false
            showAudioTrackModal -> showAudioTrackModal = false
            showSubtitleTrackModal -> showSubtitleTrackModal = false
            else -> {
                exoPlayer.pause()
                exoPlayer.stop()
                onBack()
            }
        }
    }

    // Load or switch stream URL when session.directVideoUrl changes
    LaunchedEffect(session.directVideoUrl) {
        isBuffering = true
        val mediaItem = MediaItem.fromUri(session.directVideoUrl)
        exoPlayer.setMediaItem(mediaItem, session.startPositionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
    }

    // Ensure D-Pad focus is always active on the timeline (when OSD is visible) or root player container (when hidden)
    LaunchedEffect(showControls, anyModalOpen) {
        if (!anyModalOpen) {
            delay(40L)
            if (showControls) {
                runCatching { timelineFocusRequester.requestFocus() }
            } else {
                runCatching { rootFocusRequester.requestFocus() }
            }
        }
    }

    // Clear seek feedback badge automatically
    LaunchedEffect(seekFeedbackText) {
        if (seekFeedbackText != null) {
            delay(1200L)
            seekFeedbackText = null
        }
    }

    // Debounce buffering spinner during seeks so quick scrubs don't flash an indicator
    LaunchedEffect(isBuffering, hasRenderedFirstFrame) {
        if (!isBuffering) {
            showBufferingSpinner = false
        } else if (!hasRenderedFirstFrame) {
            showBufferingSpinner = true
        } else {
            delay(350L)
            showBufferingSpinner = true
        }
    }

    // Listen to player events & extract embedded audio/subtitle tracks
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    hasRenderedFirstFrame = true
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    playbackEnded = false
                } else if (playbackState == Player.STATE_ENDED) {
                    playbackEnded = true
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val newAudio = mutableListOf<EmbeddedTrackOption>()
                val newSubs = mutableListOf<EmbeddedTrackOption>()

                tracks.groups.forEachIndexed { gIdx, group ->
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (tIdx in 0 until group.length) {
                            val format = group.getTrackFormat(tIdx)
                            val lang = format.language?.uppercase() ?: "AUDIO"
                            val label = format.label ?: ""
                            val channels = if (format.channelCount >= 6) "5.1" else "${format.channelCount}ch"
                            val codec = format.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: ""
                            val fullLabel = listOf(lang, label, channels, codec)
                                .filter { it.isNotEmpty() }
                                .joinToString(" • ")
                            newAudio.add(
                                EmbeddedTrackOption(
                                    label = fullLabel.ifEmpty { "Zvuková stopa ${newAudio.size + 1}" },
                                    groupIndex = gIdx,
                                    trackIndex = tIdx,
                                    isSelected = group.isTrackSelected(tIdx)
                                )
                            )
                        }
                    } else if (group.type == C.TRACK_TYPE_TEXT) {
                        for (tIdx in 0 until group.length) {
                            val format = group.getTrackFormat(tIdx)
                            val lang = format.language?.uppercase() ?: "TITULKY"
                            val label = format.label ?: ""
                            val fullLabel = listOf(lang, label)
                                .filter { it.isNotEmpty() }
                                .joinToString(" • ")
                            newSubs.add(
                                EmbeddedTrackOption(
                                    label = fullLabel.ifEmpty { "Titulky ${newSubs.size + 1}" },
                                    groupIndex = gIdx,
                                    trackIndex = tIdx,
                                    isSelected = group.isTrackSelected(tIdx)
                                )
                            )
                        }
                    }
                }
                audioTracks = newAudio
                subtitleTracks = newSubs
            }

            override fun onPlayerError(error: PlaybackException) {
                onStreamErrorFallback(exoPlayer.currentPosition.coerceAtLeast(0L))
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (dur > 0L) {
                onProgressUpdate(pos, dur)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Periodic progress tracker & OSD auto-hide timer
    LaunchedEffect(exoPlayer) {
        var tick = 0
        while (true) {
            delay(1000L)
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration
            if (dur > 0L) {
                durationMs = dur
            }
            tick++
            if (tick % 15 == 0 && durationMs > 0L) {
                onProgressUpdate(currentPosMs, durationMs)
            }
            val modalOpen = showAudioTrackModal || showSubtitleTrackModal || showQualityModal
            if (showControls && isPlaying && !modalOpen &&
                (System.currentTimeMillis() - lastInteractionTime) > 6_000L
            ) {
                showControls = false
            }

            if (session.nextEpisode != null && !upNextDismissed && !upNextTriggered && !modalOpen) {
                val remMs = if (durationMs > 0L) (durationMs - currentPosMs).coerceAtLeast(0L) else Long.MAX_VALUE
                if ((playbackEnded || (durationMs > 30_000L && remMs <= 1_000L))) {
                    upNextTriggered = true
                    onNextEpisode()
                }
            }
        }
    }

    fun wakeControls(focusTimeline: Boolean = false) {
        val wasHidden = !showControls
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
        if (wasHidden && focusTimeline) {
            runCatching { timelineFocusRequester.requestFocus() }
        }
    }

    fun seekRelative(deltaMs: Long) {
        wakeControls(focusTimeline = true)
        val maxDur = if (durationMs > 0L) durationMs else exoPlayer.duration.coerceAtLeast(3_600_000L)
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, maxDur)
        exoPlayer.seekTo(target)
        currentPosMs = target
        seekFeedbackText = if (deltaMs > 0) "+${deltaMs / 1000} s" else "${deltaMs / 1000} s"
    }

    fun togglePlayPause() {
        wakeControls()
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val keyCode = event.nativeKeyEvent.keyCode
                    lastInteractionTime = System.currentTimeMillis()

                    if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP || keyCode == KeyEvent.KEYCODE_MEDIA_CLOSE) {
                        exoPlayer.pause()
                        onBack()
                        return@onPreviewKeyEvent true
                    }

                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        when {
                            showQualityModal -> {
                                showQualityModal = false
                                true
                            }
                            showAudioTrackModal -> {
                                showAudioTrackModal = false
                                true
                            }
                            showSubtitleTrackModal -> {
                                showSubtitleTrackModal = false
                                true
                            }
                            else -> {
                                exoPlayer.pause()
                                exoPlayer.stop()
                                onBack()
                                true
                            }
                        }
                    } else if (anyModalOpen) {
                        false
                    } else {
                        // Dedicated media keys always work immediately
                        when (keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                togglePlayPause()
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                wakeControls()
                                exoPlayer.play()
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                wakeControls()
                                exoPlayer.pause()
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                seekRelative(-15_000L)
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                seekRelative(15_000L)
                                return@onPreviewKeyEvent true
                            }
                        }

                        // When OSD is hidden OR default timeline bar is focused:
                        // Left / Right D-Pad arrows immediately scrub playback time!
                        if (!showControls || isTimelineFocused) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    seekRelative(-15_000L)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    seekRelative(15_000L)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    if (event.nativeKeyEvent.repeatCount == 0) {
                                        togglePlayPause()
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (!showControls) {
                                        wakeControls(focusTimeline = true)
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (!showControls) {
                                        wakeControls(focusTimeline = true)
                                        true
                                    } else {
                                        // Let focus move up to the "Ukončit přehrávání" top button
                                        false
                                    }
                                }
                                else -> false
                            }
                        } else {
                            // OSD is visible and one of the buttons is focused -> let standard D-Pad move between buttons
                            false
                        }
                    }
                } else if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    val keyCode = event.nativeKeyEvent.keyCode
                    if (!anyModalOpen && (!showControls || isTimelineFocused) &&
                        (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER ||
                            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                    ) {
                        // Consume ACTION_UP so the timeline's clickable() never fires a second togglePlayPause()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .focusable()
    ) {
        // Hardware-accelerated Media3 ExoPlayer Surface (disabled focus so it never steals D-Pad keys from Compose)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
                view.subtitleView?.let { sv ->
                    val fraction = when (subtitleSizeMode) {
                        0 -> SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 0.8f
                        2 -> SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.3f
                        else -> SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                    }
                    sv.setFractionalTextSize(fraction)

                    val style = when (subtitleStyleMode) {
                        1 -> CaptionStyleCompat(
                            AndroidColor.YELLOW,
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            AndroidColor.BLACK,
                            null
                        )
                        2 -> CaptionStyleCompat(
                            AndroidColor.WHITE,
                            AndroidColor.argb(190, 0, 0, 0),
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE,
                            AndroidColor.TRANSPARENT,
                            null
                        )
                        else -> CaptionStyleCompat(
                            AndroidColor.WHITE,
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            AndroidColor.BLACK,
                            null
                        )
                    }
                    sv.setStyle(style)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Clean, frameless Buffering Indicator (no boxy dialog during seeks)
        AnimatedVisibility(
            visible = showBufferingSpinner,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.9f),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    if (!hasRenderedFirstFrame) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Načítám...",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Sleek, borderless Seek Delta Pill positioned above the timeline (keeps center picture clear)
        AnimatedVisibility(
            visible = seekFeedbackText != null,
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(240)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 132.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x99090A0F))
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${seekFeedbackText.orEmpty()}  ·  ${formatDurationMs(currentPosMs)}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Netflix-style Custom TV OSD Overlay with organic inhale / exhale animation
        val inhaleEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
        val exhaleEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(durationMillis = 480, easing = inhaleEasing)) +
                scaleIn(
                    initialScale = 0.965f,
                    animationSpec = tween(durationMillis = 520, easing = inhaleEasing)
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 420, easing = exhaleEasing)) +
                scaleOut(
                    targetScale = 0.97f,
                    animationSpec = tween(durationMillis = 420, easing = exhaleEasing)
                ),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC090A0F),
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xEE090A0F)
                            )
                        )
                    )
            ) {
                // Top Bar: Back + Title + Stream Tech Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 36.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TvIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Ukončit přehrávání",
                            size = 38.dp,
                            onClick = onBack
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = buildString {
                                    append(session.item.title)
                                    session.episode?.let { ep ->
                                        append(" • ${ep.code} – ${ep.title}")
                                    }
                                },
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val streamMeta = listOfNotNull(
                                session.activeStream.language.badge,
                                session.activeStream.quality.badge,
                                session.activeStream.file.formattedSize.takeIf { it.isNotEmpty() }
                            ).joinToString(" • ")
                            Text(
                                text = streamMeta,
                                color = TvColors.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val extraBadges = buildList {
                        if (session.activeStream.isDolbyVision) add("Dolby Vision")
                        else if (session.activeStream.isHdr) add("HDR")
                        if (session.activeStream.isHevc) add("HEVC")
                        if (session.activeStream.isSurround51) add("5.1")
                    }
                    if (extraBadges.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            extraBadges.forEach { badge ->
                                TechBadgePill(
                                    text = badge,
                                    backgroundColor = Color(0xDD1E2233)
                                )
                            }
                        }
                    }
                }

                // Bottom Bar: Track/Audio/Subtitle Settings above & Merged Play/Pause + Timeline at the very bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 36.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvIconButton(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Zvuk",
                                size = 38.dp,
                                onClick = { showAudioTrackModal = true }
                            )

                            TvIconButton(
                                icon = Icons.Default.Subtitles,
                                contentDescription = "Titulky",
                                size = 38.dp,
                                onClick = { showSubtitleTrackModal = true }
                            )

                            if (session.resolutionResult != null && session.resolutionResult.groups.isNotEmpty()) {
                                TvIconButton(
                                    icon = Icons.Default.HighQuality,
                                    contentDescription = "Kvalita / Zdroj (${session.activeStream.quality.badge} • ${session.activeStream.language.badge})",
                                    size = 38.dp,
                                    onClick = { showQualityModal = true }
                                )
                            }

                            if (session.nextEpisode != null) {
                                TvButton(
                                    text = "Další díl (${session.nextEpisode.code})",
                                    icon = Icons.Default.SkipNext,
                                    isPrimary = true,
                                    onClick = onNextEpisode
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Single Unified Focusable Element: Play/Pause Icon + Interactive Seek Timeline
                    val progressFraction = if (durationMs > 0L) {
                        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val timelineInteraction = remember { MutableInteractionSource() }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isTimelineFocused) Color(0x16FFFFFF) else Color.Transparent)
                            .border(
                                width = if (isTimelineFocused) 1.dp else 0.dp,
                                color = if (isTimelineFocused) Color(0x4DFFFFFF) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .focusRequester(timelineFocusRequester)
                            .onFocusChanged { isTimelineFocused = it.isFocused }
                            .clickable(
                                interactionSource = timelineInteraction,
                                indication = null,
                                onClick = { togglePlayPause() }
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pozastavit" else "Přehrát",
                            tint = if (isTimelineFocused) Color.White else Color(0xFFE5E5E5),
                            modifier = Modifier.size(22.dp)
                        )

                        Text(
                            text = formatDurationMs(currentPosMs),
                            color = if (isTimelineFocused) Color.White else Color(0xFFE5E5E5),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (isTimelineFocused) 7.dp else 5.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x44FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .height(if (isTimelineFocused) 7.dp else 5.dp)
                                    .background(TvColors.NetflixRed)
                            )
                        }

                        val remainingMs = (durationMs - currentPosMs).coerceAtLeast(0L)
                        Text(
                            text = if (durationMs > 0L) "-${formatDurationMs(remainingMs)}" else formatDurationMs(0L),
                            color = if (isTimelineFocused) Color(0xFFE5E5E5) else TvColors.TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Up Next ("Další díl za X s") auto-play card near end of episode
        val nextEp = session.nextEpisode
        val upNextRemainingSec = if (nextEp != null && durationMs > 30_000L) {
            ((durationMs - currentPosMs).coerceAtLeast(0L) / 1000L).toInt()
        } else Int.MAX_VALUE
        if (nextEp != null && !upNextDismissed && !upNextTriggered && !anyModalOpen &&
            (playbackEnded || upNextRemainingSec <= 15)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 36.dp, bottom = 92.dp)
                    .width(340.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TvColors.SurfaceCard.copy(alpha = 0.94f))
                    .border(1.dp, TvColors.BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (playbackEnded || upNextRemainingSec <= 0) "Spouštím další díl..." else "Další díl za ${upNextRemainingSec} s",
                    color = TvColors.AccentGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${nextEp.code} – ${nextEp.title}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(
                        text = "Přehrát hned",
                        icon = Icons.Default.SkipNext,
                        isPrimary = true,
                        paddingValues = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                        onClick = {
                            upNextTriggered = true
                            onNextEpisode()
                        }
                    )
                    TvButton(
                        text = "Zrušit",
                        paddingValues = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                        onClick = { upNextDismissed = true }
                    )
                }
            }
        }

        // Audio Settings Modal (embedded audio track selector)
        if (showAudioTrackModal) {
            AudioSettingsOverlay(
                tracks = audioTracks,
                onSelectTrack = { opt ->
                    val group = exoPlayer.currentTracks.groups.getOrNull(opt.groupIndex)
                    if (group != null) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, opt.trackIndex)
                            )
                            .build()
                    }
                    showAudioTrackModal = false
                },
                onDismiss = { showAudioTrackModal = false }
            )
        }

        // Subtitle Settings Modal (track, on/off, text size, style)
        if (showSubtitleTrackModal) {
            SubtitleSettingsOverlay(
                tracks = subtitleTracks,
                isOffSelected = subtitlesDisabled,
                currentSizeMode = subtitleSizeMode,
                currentStyleMode = subtitleStyleMode,
                onSelectTrack = { opt ->
                    val group = exoPlayer.currentTracks.groups.getOrNull(opt.groupIndex)
                    if (group != null) {
                        subtitlesDisabled = false
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, opt.trackIndex)
                            )
                            .build()
                    }
                    showSubtitleTrackModal = false
                },
                onSelectOff = {
                    subtitlesDisabled = true
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    showSubtitleTrackModal = false
                },
                onSelectSizeMode = { subtitleSizeMode = it },
                onSelectStyleMode = { subtitleStyleMode = it },
                onDismiss = { showSubtitleTrackModal = false }
            )
        }

        // Live Webshare Stream Quality & Language Switcher inside Player
        if (showQualityModal && session.resolutionResult != null) {
            StreamPickerModal(
                title = session.resolutionResult.queryTitle,
                resolution = session.resolutionResult,
                onSelectStream = { newStream ->
                    showQualityModal = false
                    onSwitchStream(newStream, currentPosMs)
                },
                onDismiss = { showQualityModal = false }
            )
        }
    }
}

@Composable
private fun AudioSettingsOverlay(
    tracks: List<EmbeddedTrackOption>,
    onSelectTrack: (EmbeddedTrackOption) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(40L)
        runCatching { firstFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TvColors.SurfaceDark)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Nastavení zvuku",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Zvuková stopa",
                color = TvColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (tracks.isEmpty()) {
                Text(
                    text = "Výchozí zvuková stopa videa",
                    color = Color(0xFFB3B3B3),
                    fontSize = 13.sp
                )
            } else {
                tracks.forEachIndexed { index, opt ->
                    TvButton(
                        text = opt.label,
                        isSelected = opt.isSelected,
                        modifier = if (index == 0) {
                            Modifier.fillMaxWidth().focusRequester(firstFocusRequester)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        onClick = { onSelectTrack(opt) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            TvButton(
                text = "Zavřít",
                isPrimary = true,
                modifier = if (tracks.isEmpty()) {
                    Modifier.fillMaxWidth().focusRequester(firstFocusRequester)
                } else {
                    Modifier.fillMaxWidth()
                },
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun SubtitleSettingsOverlay(
    tracks: List<EmbeddedTrackOption>,
    isOffSelected: Boolean,
    currentSizeMode: Int,
    currentStyleMode: Int,
    onSelectTrack: (EmbeddedTrackOption) -> Unit,
    onSelectOff: () -> Unit,
    onSelectSizeMode: (Int) -> Unit,
    onSelectStyleMode: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(40L)
        runCatching { firstFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TvColors.SurfaceDark)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Nastavení titulků",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Stopa titulků",
                color = TvColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            TvButton(
                text = "Vypnout titulky",
                isSelected = isOffSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstFocusRequester),
                onClick = onSelectOff
            )

            if (tracks.isEmpty()) {
                Text(
                    text = "Video neobsahuje žádné vestavěné titulky.",
                    color = TvColors.TextMuted,
                    fontSize = 12.sp
                )
            } else {
                tracks.forEach { opt ->
                    TvButton(
                        text = opt.label,
                        isSelected = opt.isSelected && !isOffSelected,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelectTrack(opt) }
                    )
                }
            }

            Text(
                text = "Velikost písma",
                color = TvColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "Malé", 1 to "Střední", 2 to "Velké").forEach { (mode, label) ->
                    TvButton(
                        text = label,
                        isSelected = currentSizeMode == mode,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectSizeMode(mode) }
                    )
                }
            }

            Text(
                text = "Vzhled titulků",
                color = TvColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "Bílé (obrys)", 1 to "Žluté", 2 to "Tmavé pozadí").forEach { (mode, label) ->
                    TvButton(
                        text = label,
                        isSelected = currentStyleMode == mode,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectStyleMode(mode) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            TvButton(
                text = "Zavřít",
                isPrimary = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss
            )
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
