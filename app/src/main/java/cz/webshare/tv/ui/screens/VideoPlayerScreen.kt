package cz.webshare.tv.ui.screens

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.media3.ui.PlayerView
import cz.webshare.tv.model.ParsedStream
import cz.webshare.tv.ui.ActivePlaybackSession
import cz.webshare.tv.ui.components.TechBadgePill
import cz.webshare.tv.ui.components.TvButton
import cz.webshare.tv.ui.components.TvColors
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

    var isTimelineFocused by remember { mutableStateOf(false) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }

    val rootFocusRequester = remember { FocusRequester() }
    val timelineFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    val anyModalOpen = showAudioTrackModal || showSubtitleTrackModal || showQualityModal

    // Intercept Back button inside player so modals / OSD close first before exiting playback
    BackHandler(enabled = true) {
        when {
            showQualityModal -> showQualityModal = false
            showAudioTrackModal -> showAudioTrackModal = false
            showSubtitleTrackModal -> showSubtitleTrackModal = false
            showControls -> showControls = false
            else -> onBack()
        }
    }

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

    // Listen to player events & extract embedded audio/subtitle tracks
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
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
                            showControls -> {
                                showControls = false
                                true
                            }
                            else -> {
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
                                KeyEvent.KEYCODE_ENTER -> {
                                    togglePlayPause()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (!showControls) {
                                        wakeControls(focusTimeline = true)
                                    } else {
                                        runCatching { playPauseFocusRequester.requestFocus() }
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
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Indicator
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xBB090A0F))
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    CircularProgressIndicator(
                        color = TvColors.NetflixRed,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Načítám VIP stream (${session.activeStream.quality.badge} • ${session.activeStream.language.badge})...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Instant Seek Delta Indicator (e.g. "-15 s" / "+15 s")
        if (seekFeedbackText != null && !isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC090A0F))
                        .border(1.5.dp, TvColors.NetflixRed, RoundedCornerShape(12.dp))
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "$seekFeedbackText  •  ${formatDurationMs(currentPosMs)}",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Netflix-style Custom TV OSD Overlay
        if (showControls) {
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
                        TvButton(
                            text = "Ukončit přehrávání",
                            icon = Icons.Default.ArrowBack,
                            paddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
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
                            Text(
                                text = session.activeStream.file.name,
                                color = TvColors.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        session.activeStream.techBadges.forEach { badge ->
                            TechBadgePill(
                                text = badge,
                                backgroundColor = Color(0xDD1E2233)
                            )
                        }
                    }
                }

                // Bottom Bar: Fallback/Stream Notice, Focusable Timeline & Track Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 36.dp, vertical = 26.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!session.bannerMessage.isNullOrEmpty()) {
                            TechBadgePill(
                                text = session.bannerMessage,
                                backgroundColor = Color(0xCC1F2437),
                                textColor = TvColors.AccentGold
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        TechBadgePill(
                            text = if (isTimelineFocused) {
                                "◀ ▶ Šipky posouvají čas (-15 s / +15 s) • OK Pozastavit • ▼ Další volby"
                            } else {
                                "▲ Časová osa • ◀ ▶ Pohyb mezi tlačítky"
                            },
                            backgroundColor = if (isTimelineFocused) Color(0xCC1E263B) else Color(0x88151822),
                            textColor = if (isTimelineFocused) Color.White else TvColors.TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Default-Focused Interactive Seek Timeline Bar
                    val progressFraction = if (durationMs > 0L) {
                        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isTimelineFocused) Color(0x44FFFFFF) else Color.Transparent)
                            .border(
                                width = if (isTimelineFocused) 2.dp else 0.dp,
                                color = if (isTimelineFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .focusRequester(timelineFocusRequester)
                            .onFocusChanged { isTimelineFocused = it.isFocused }
                            .focusable(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = formatDurationMs(currentPosMs),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (isTimelineFocused) 12.dp else 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x55FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .height(if (isTimelineFocused) 12.dp else 8.dp)
                                    .background(TvColors.NetflixRed)
                            )
                        }

                        Text(
                            text = formatDurationMs(durationMs),
                            color = if (isTimelineFocused) Color.White else TvColors.TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (isTimelineFocused) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // D-Pad Control Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvButton(
                                text = "-15 s",
                                icon = Icons.Default.FastRewind,
                                onClick = { seekRelative(-15_000L) }
                            )

                            TvButton(
                                text = if (isPlaying) "Pozastavit" else "Přehrát",
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                isPrimary = true,
                                modifier = Modifier.focusRequester(playPauseFocusRequester),
                                onClick = { togglePlayPause() }
                            )

                            TvButton(
                                text = "+15 s",
                                icon = Icons.Default.FastForward,
                                onClick = { seekRelative(15_000L) }
                            )

                            TvButton(
                                text = "+60 s",
                                icon = Icons.Default.FastForward,
                                onClick = { seekRelative(60_000L) }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (audioTracks.size > 1) {
                                TvButton(
                                    text = "Zvuková stopa (${audioTracks.size})",
                                    onClick = { showAudioTrackModal = true }
                                )
                            }

                            if (subtitleTracks.isNotEmpty()) {
                                TvButton(
                                    text = "Titulky (${subtitleTracks.size})",
                                    onClick = { showSubtitleTrackModal = true }
                                )
                            }

                            if (session.resolutionResult != null && session.resolutionResult.groups.isNotEmpty()) {
                                TvButton(
                                    text = "Kvalita / Zdroj (${session.activeStream.quality.badge} • ${session.activeStream.language.badge})",
                                    icon = Icons.Default.Settings,
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
                }
            }
        }

        // Embedded Audio Track Switcher Modal
        if (showAudioTrackModal) {
            TrackSelectorOverlay(
                title = "Výběr zvukové stopy ve videu",
                options = audioTracks,
                includeOffOption = false,
                isOffSelected = false,
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
                onSelectOff = {},
                onDismiss = { showAudioTrackModal = false }
            )
        }

        // Embedded Subtitle Track Switcher Modal
        if (showSubtitleTrackModal) {
            TrackSelectorOverlay(
                title = "Výběr titulků ve videu",
                options = subtitleTracks,
                includeOffOption = true,
                isOffSelected = subtitlesDisabled,
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
private fun TrackSelectorOverlay(
    title: String,
    options: List<EmbeddedTrackOption>,
    includeOffOption: Boolean,
    isOffSelected: Boolean,
    onSelectTrack: (EmbeddedTrackOption) -> Unit,
    onSelectOff: () -> Unit,
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
                .clip(RoundedCornerShape(16.dp))
                .background(TvColors.SurfaceDark)
                .border(1.dp, Color(0xFF2F354D), RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            if (includeOffOption) {
                TvButton(
                    text = "Vypnout titulky",
                    isSelected = isOffSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(firstFocusRequester),
                    onClick = onSelectOff
                )
            }

            options.forEachIndexed { index, opt ->
                val btnModifier = if (!includeOffOption && index == 0) {
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(firstFocusRequester)
                } else {
                    Modifier.fillMaxWidth()
                }
                TvButton(
                    text = opt.label,
                    isSelected = opt.isSelected && !isOffSelected,
                    modifier = btnModifier,
                    onClick = { onSelectTrack(opt) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            TvButton(
                text = "Zavřít",
                modifier = if (!includeOffOption && options.isEmpty()) {
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(firstFocusRequester)
                } else {
                    Modifier.fillMaxWidth()
                },
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
