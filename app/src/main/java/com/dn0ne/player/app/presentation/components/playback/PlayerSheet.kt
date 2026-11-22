package com.dn0ne.player.app.presentation.components.playback

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.playback.PlaybackMode
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.domain.track.audioQualityBadgeText
import com.dn0ne.player.app.domain.track.isLossless
import com.dn0ne.player.app.presentation.components.CoverArt
import com.dn0ne.player.app.presentation.components.TrackMenuButton
import com.dn0ne.player.app.presentation.components.isSystemInLandscapeOrientation
import com.dn0ne.player.app.presentation.components.isTablet
import com.dn0ne.player.app.presentation.components.playback.lyrics.EmbeddedLyricsPanel
import com.dn0ne.player.app.presentation.components.settings.Theme
import com.dn0ne.player.core.data.Settings
import com.dn0ne.player.core.util.shareTrack
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerSheet(
    playbackStateFlow: StateFlow<PlaybackState>,
    playbackPositionFlow: StateFlow<Long>? = null,
    onPlayerExpandedChange: (Boolean) -> Unit,
    onLyricsSheetExpandedChange: (Boolean) -> Unit,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSeekToNextClick: () -> Unit,
    onSeekToPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onReset: () -> Unit,
    onPlaybackModeClick: () -> Unit,
    onCoverArtLoaded: (ImageBitmap?) -> Unit,
    onPlayNextClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onViewTrackInfoClick: () -> Unit,
    onGoToAlbumClick: () -> Unit,
    onGoToArtistClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onRemoveFromQueueClick: (Int) -> Unit,
    onReorderingQueue: (Int, Int) -> Unit,
    onTrackClick: (Track, Playlist) -> Unit,
    settings: Settings,
    modifier: Modifier = Modifier
) {
    val playbackState by playbackStateFlow.collectAsState()
    val isExpanded by remember {
        derivedStateOf {
            playbackState.isPlayerExpanded
        }
    }
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val thresholdY = with(density) { 200.dp.toPx() }
    val decay = rememberSplineBasedDecay<Float>()
    val translationY = remember {
        Animatable(0f).apply {
            if (isExpanded) {
                updateBounds(
                    lowerBound = 0f,
                    upperBound = thresholdY
                )
            } else {
                updateBounds(
                    lowerBound = -thresholdY,
                    upperBound = thresholdY
                )
            }
        }
    }
    val draggableState = rememberDraggableState { dragAmount ->
        coroutineScope.launch {
            translationY.snapTo(
                translationY.value + (dragAmount * (1 - (translationY.value / thresholdY).absoluteValue))
            )
        }
    }

    AnimatedContent(
        targetState = isExpanded,
        label = "player-content",
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.translationY = translationY.value

                val cornerRadius =
                    lerp(0.dp, 36.dp, this.translationY.absoluteValue / (thresholdY * 0.5f))
                this.clip = true
                this.shape = if (isExpanded) {
                    RoundedCornerShape(cornerRadius)
                } else {
                    RoundedCornerShape(
                        topStart = 36.dp,
                        topEnd = 36.dp,
                        bottomStart = cornerRadius,
                        bottomEnd = cornerRadius
                    )
                }
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    val decayY = decay.calculateTargetValue(
                        initialValue = translationY.value,
                        initialVelocity = velocity
                    )

                    coroutineScope.launch {
                        if (!isExpanded) {
                            val shouldStopPlayback = decayY > thresholdY * .5f
                            if (shouldStopPlayback) {
                                onReset()
                                return@launch
                            }
                        }

                        val shouldChangeExpandedState = decayY.absoluteValue > (thresholdY * 0.5f)
                        if (shouldChangeExpandedState) {
                            onPlayerExpandedChange(!isExpanded)
                            translationY.apply {
                                animateTo(0f)
                                if (isExpanded) {
                                    updateBounds(
                                        lowerBound = 0f,
                                        upperBound = thresholdY
                                    )
                                } else {
                                    updateBounds(
                                        lowerBound = -thresholdY,
                                        upperBound = thresholdY
                                    )
                                }
                            }
                        } else {
                            translationY.animateTo(0f)
                        }
                    }
                }
            )
            .background(color = MaterialTheme.colorScheme.surfaceContainer)

    ) { state ->
        when (state) {
            false -> {
                BottomPlayer(
                    playbackStateFlow = playbackStateFlow,
                    onClick = {
                        onPlayerExpandedChange(true)
                        translationY.updateBounds(
                            lowerBound = 0f,
                            upperBound = thresholdY
                        )
                    },
                    onPlayClick = onPlayClick,
                    onPauseClick = onPauseClick,
                    onSeekToPreviousClick = onSeekToPreviousClick,
                    onSeekToNextClick = onSeekToNextClick,
                    onCoverArtLoaded = onCoverArtLoaded
                )
            }

            true -> {
                val appearance by settings.appearance.collectAsState()
                val amoledDarkTheme by settings.amoledDarkTheme.collectAsState()
                val isDarkTheme = when (appearance) {
                    Theme.Appearance.System -> isSystemInDarkTheme()
                    Theme.Appearance.Light -> false
                    Theme.Appearance.Dark -> true
                }

                ExpandedPlayer(
                    playbackStateFlow = playbackStateFlow,
                    playbackPositionFlow = playbackPositionFlow,
                    lyricsSyncOffset = settings.lyricsSyncOffset,
                    onSeekTo = onSeekTo,
                    onPauseClick = onPauseClick,
                    onPlayClick = onPlayClick,
                    onSeekToNextClick = onSeekToNextClick,
                    onSeekToPreviousClick = onSeekToPreviousClick,
                    onHideClick = {
                        onPlayerExpandedChange(false)
                        translationY.updateBounds(
                            lowerBound = -thresholdY,
                            upperBound = 0f
                        )
                    },
                    onPlaybackModeClick = onPlaybackModeClick,
                    onCoverArtLoaded = onCoverArtLoaded,
                    onPlayNextClick = onPlayNextClick,
                    onAddToQueueClick = onAddToQueueClick,
                    onAddToPlaylistClick = onAddToPlaylistClick,
                    onViewTrackInfoClick = onViewTrackInfoClick,
                    onGoToAlbumClick = onGoToAlbumClick,
                    onGoToArtistClick = onGoToArtistClick,
                    onLyricsSheetExpandedChange = onLyricsSheetExpandedChange,
                    onLyricsClick = onLyricsClick,
                    lyricsTextStyle = MaterialTheme.typography.headlineMedium
                        .copy(
                            fontSize = settings.lyricsFontSize,
                            lineHeight = settings.lyricsLineHeight,
                            letterSpacing = settings.lyricsLetterSpacing,
                            textAlign = settings.lyricsAlignment,
                            fontWeight = FontWeight(settings.lyricsFontWeight),
                        ),
                    lyricsContainerColor = if (isDarkTheme && amoledDarkTheme) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.inversePrimary
                    },
                    lyricsContentColor = MaterialTheme.colorScheme.inverseSurface,
                    isDarkLyricsTheme = isDarkTheme,
                    onRemoveFromQueueClick = onRemoveFromQueueClick,
                    onReorderingQueue = onReorderingQueue,
                    onTrackClick = onTrackClick,
                    modifier = Modifier.clickable(
                        onClick = {},
                        interactionSource = null,
                        indication = null
                    )
                )
            }
        }
    }
}

@Composable
fun BottomPlayer(
    playbackStateFlow: StateFlow<PlaybackState>,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSeekToPreviousClick: () -> Unit,
    onSeekToNextClick: () -> Unit,
    onCoverArtLoaded: (ImageBitmap?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable {
                onClick()
            }
    ) {
        val playbackState by playbackStateFlow.collectAsState()
        val currentTrack by remember {
            derivedStateOf {
                playbackState.currentTrack!!
            }
        }
        val isPlaying by remember {
            derivedStateOf {
                playbackState.isPlaying
            }
        }
        val position by remember {
            derivedStateOf {
                playbackState.position
            }
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(
                    animateFloatAsState(
                        targetValue = position.toFloat() / currentTrack.duration,
                        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
                        label = "bottom-player-progress"
                    ).value
                )
                .background(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AnimatedContent(
                targetState = currentTrack,
                transitionSpec = {
                    fadeIn() + slideInHorizontally(
                        initialOffsetX = { it / 5 }
                    ) togetherWith fadeOut() + slideOutHorizontally(
                        targetOffsetX = { -it / 5 }
                    )
                },
                label = "bottom-player-track-change-animation",
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) { currentTrack ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CoverArt(
                        uri = currentTrack.coverArtUri,
                        onCoverArtLoaded = onCoverArtLoaded,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(ShapeDefaults.Small)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        val context = LocalContext.current
                        Text(
                            text = currentTrack.title
                                ?: context.resources.getString(R.string.unknown_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = currentTrack.artist
                                ?: context.resources.getString(R.string.unknown_artist),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val layoutDirection = LocalLayoutDirection.current
                val isRtl = layoutDirection == LayoutDirection.Rtl

                IconButton(
                    onClick = onSeekToPreviousClick
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer {
                                if (isRtl) {
                                    rotationY = 180f
                                }
                            }
                    )
                }

                if (isPlaying) {
                    IconButton(
                        onClick = onPauseClick,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onPlayClick,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSeekToNextClick
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer {
                                if (isRtl) {
                                    rotationY = 180f
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandedPlayer(
    playbackStateFlow: StateFlow<PlaybackState>,
    playbackPositionFlow: StateFlow<Long>? = null,
    lyricsSyncOffset: Long = 0L,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSeekToNextClick: () -> Unit,
    onSeekToPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onHideClick: () -> Unit,
    onPlaybackModeClick: () -> Unit,
    onCoverArtLoaded: (ImageBitmap?) -> Unit,
    onPlayNextClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onViewTrackInfoClick: () -> Unit,
    onGoToAlbumClick: () -> Unit,
    onGoToArtistClick: () -> Unit,
    onLyricsSheetExpandedChange: (Boolean) -> Unit,
    onLyricsClick: () -> Unit,
    lyricsTextStyle: TextStyle,
    lyricsContainerColor: Color,
    lyricsContentColor: Color,
    isDarkLyricsTheme: Boolean = isSystemInDarkTheme(),
    onRemoveFromQueueClick: (Int) -> Unit,
    onReorderingQueue: (Int, Int) -> Unit,
    onTrackClick: (Track, Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onHideClick()
    }

    val playbackState by playbackStateFlow.collectAsState()
    val showLyricsSheet by remember {
        derivedStateOf {
            playbackState.isLyricsSheetExpanded
        }
    }
    var showEmbeddedLyrics by rememberSaveable {
        mutableStateOf(false)
    }
    var isLyricsFullscreen by rememberSaveable {
        mutableStateOf(false)
    }
    var showQueue by remember {
        mutableStateOf(false)
    }
    var showSleepTimerSheet by remember {
        mutableStateOf(false)
    }

    val activeTrack = playbackState.currentTrack
    val isTablet = isTablet()
    val onOpenLyrics = {
        if (isTablet) {
            showEmbeddedLyrics = !showEmbeddedLyrics
            if (showEmbeddedLyrics) {
                onLyricsClick()
            }
        } else {
            isLyricsFullscreen = true
            onLyricsSheetExpandedChange(true)
            onLyricsClick()
        }
    }

    LaunchedEffect(activeTrack?.uri, showEmbeddedLyrics, showLyricsSheet) {
        if ((showEmbeddedLyrics || showLyricsSheet) && activeTrack != null) {
            onLyricsClick()
        }
    }

    Box {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.surface)
                .safeDrawingPadding()
                .padding(horizontal = if (isTablet && showEmbeddedLyrics) 16.dp else 28.dp),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            if (isTablet && showEmbeddedLyrics) {
                val currentTrack by remember {
                    derivedStateOf {
                        playbackState.currentTrack!!
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmbeddedLyricsPanel(
                        playbackStateFlow = playbackStateFlow,
                        playbackPositionFlow = playbackPositionFlow,
                        lyricsSyncOffset = lyricsSyncOffset,
                        lyricsTextStyle = lyricsTextStyle,
                        coverArtUri = currentTrack.coverArtUri,
                        onCoverArtLoaded = onCoverArtLoaded,
                        isDarkTheme = isDarkLyricsTheme,
                        onSeekTo = onSeekTo,
                        onFullscreenClick = {
                            isLyricsFullscreen = true
                            onLyricsSheetExpandedChange(true)
                            onLyricsClick()
                        },
                        onCloseClick = {
                            showEmbeddedLyrics = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(ShapeDefaults.ExtraLarge)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PlaybackControl(
                        playbackStateFlow = playbackStateFlow,
                        onPlayClick = onPlayClick,
                        onPauseClick = onPauseClick,
                        onSeekTo = onSeekTo,
                        onSeekToNextClick = onSeekToNextClick,
                        onSeekToPreviousClick = onSeekToPreviousClick,
                        onPlaybackModeClick = onPlaybackModeClick,
                        showEmbeddedLyrics = true,
                        onEmbeddedLyricsToggle = onOpenLyrics,
                        compact = true,
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                    )
                }
            } else if (!isSystemInLandscapeOrientation()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(0.35f))

                    val currentTrack by remember {
                        derivedStateOf {
                            playbackState.currentTrack!!
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(ShapeDefaults.Large)
                    ) {
                        AnimatedContent(
                            targetState = isTablet && showEmbeddedLyrics,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "portrait-cover-or-lyrics"
                        ) { embedded ->
                            if (embedded) {
                                EmbeddedLyricsPanel(
                                    playbackStateFlow = playbackStateFlow,
                                    playbackPositionFlow = playbackPositionFlow,
                                    lyricsSyncOffset = lyricsSyncOffset,
                                    lyricsTextStyle = lyricsTextStyle,
                                    coverArtUri = currentTrack.coverArtUri,
                                    onCoverArtLoaded = onCoverArtLoaded,
                                    isDarkTheme = isDarkLyricsTheme,
                                    onSeekTo = onSeekTo,
                                    onFullscreenClick = {
                                        isLyricsFullscreen = true
                                        onLyricsSheetExpandedChange(true)
                                        onLyricsClick()
                                    },
                                    onCloseClick = {
                                        showEmbeddedLyrics = false
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AnimatedContent(
                                    targetState = currentTrack,
                                    label = "cover-art-animation"
                                ) { track ->
                                    CoverArt(
                                        uri = track.coverArtUri,
                                        onCoverArtLoaded = onCoverArtLoaded,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(ShapeDefaults.Large)
                                            .clickable {
                                                onOpenLyrics()
                                            }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    AnimatedContent(
                        targetState = currentTrack,
                        label = "title-artist-text-animation",
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { track ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val context = LocalContext.current
                            Text(
                                text = track.title
                                    ?: context.resources.getString(R.string.unknown_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.basicMarquee()
                            )

                            Text(
                                text = track.artist
                                    ?: context.resources.getString(R.string.unknown_artist),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.basicMarquee()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            AudioQualityTag(
                                track = track,
                                onClick = onViewTrackInfoClick
                            )
                        }

                    }

                    Spacer(modifier = Modifier.weight(0.75f))

                    PlaybackControl(
                        playbackStateFlow = playbackStateFlow,
                        onPlayClick = onPlayClick,
                        onPauseClick = onPauseClick,
                        onSeekTo = onSeekTo,
                        onSeekToNextClick = onSeekToNextClick,
                        onSeekToPreviousClick = onSeekToPreviousClick,
                        onPlaybackModeClick = onPlaybackModeClick,
                        showEmbeddedLyrics = if (isTablet) showEmbeddedLyrics else showLyricsSheet,
                        onEmbeddedLyricsToggle = onOpenLyrics,
                        modifier = Modifier
                            .fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.weight(0.25f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onHideClick
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = context.resources.getString(R.string.close_player_sheet),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }


                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    showSleepTimerSheet = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = context.resources.getString(R.string.set_sleep_timer),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = playbackState.playbackMode != PlaybackMode.Shuffle,
                                enter = expandHorizontally(),
                                exit = shrinkHorizontally()
                            ) {
                                IconButton(
                                    onClick = {
                                        if (playbackState.playbackMode != PlaybackMode.Shuffle) {
                                            showQueue = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = context.resources.getString(R.string.show_queue),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isTablet) {
                                IconButton(
                                    onClick = {
                                        isLyricsFullscreen = true
                                        onLyricsSheetExpandedChange(true)
                                        onLyricsClick()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fullscreen,
                                        contentDescription = context.resources.getString(R.string.lyrics_fullscreen),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            TrackMenuButton(
                                onPlayNextClick = onPlayNextClick,
                                onAddToQueueClick = onAddToQueueClick,
                                onAddToPlaylistClick = onAddToPlaylistClick,
                                onViewTrackInfoClick = onViewTrackInfoClick,
                                onGoToAlbumClick = onGoToAlbumClick,
                                onGoToArtistClick = onGoToArtistClick,
                                onShareClick = { context.shareTrack(currentTrack) }
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)
                ) {
                    val currentTrack by remember {
                        derivedStateOf {
                            playbackState.currentTrack!!
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxSize()
                                .clip(ShapeDefaults.ExtraLarge)
                        ) {
                            AnimatedContent(
                                targetState = isTablet && showEmbeddedLyrics,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "landscape-cover-or-lyrics"
                            ) { embedded ->
                                if (embedded) {
                                    EmbeddedLyricsPanel(
                                        playbackStateFlow = playbackStateFlow,
                                        playbackPositionFlow = playbackPositionFlow,
                                        lyricsSyncOffset = lyricsSyncOffset,
                                        lyricsTextStyle = lyricsTextStyle,
                                        coverArtUri = currentTrack.coverArtUri,
                                        onCoverArtLoaded = onCoverArtLoaded,
                                        isDarkTheme = isDarkLyricsTheme,
                                        onSeekTo = onSeekTo,
                                        onFullscreenClick = {
                                            isLyricsFullscreen = true
                                            onLyricsSheetExpandedChange(true)
                                            onLyricsClick()
                                        },
                                        onCloseClick = {
                                            showEmbeddedLyrics = false
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    AnimatedContent(
                                        targetState = currentTrack,
                                        label = "cover-art-animation"
                                    ) { track ->
                                        CoverArt(
                                            uri = track.coverArtUri,
                                            onCoverArtLoaded = onCoverArtLoaded,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(ShapeDefaults.ExtraLarge)
                                                .clickable {
                                                    onOpenLyrics()
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 520.dp)
                                .fillMaxHeight()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                AnimatedContent(
                                    targetState = currentTrack,
                                    label = "title-artist-text-animation",
                                    transitionSpec = {
                                        fadeIn() togetherWith fadeOut()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { track ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        val context = LocalContext.current
                                        Text(
                                            text = track.title
                                                ?: context.resources.getString(R.string.unknown_title),
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.basicMarquee()
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = track.artist
                                                ?: context.resources.getString(R.string.unknown_artist),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.basicMarquee()
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        AudioQualityTag(
                                            track = track,
                                            onClick = onViewTrackInfoClick
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                PlaybackControl(
                                    playbackStateFlow = playbackStateFlow,
                                    onPlayClick = onPlayClick,
                                    onPauseClick = onPauseClick,
                                    onSeekTo = onSeekTo,
                                    onSeekToNextClick = onSeekToNextClick,
                                    onSeekToPreviousClick = onSeekToPreviousClick,
                                    onPlaybackModeClick = onPlaybackModeClick,
                                    showEmbeddedLyrics = if (isTablet) showEmbeddedLyrics else showLyricsSheet,
                                    onEmbeddedLyricsToggle = onOpenLyrics,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = onHideClick
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ExpandMore,
                                        contentDescription = context.resources.getString(R.string.close_player_sheet),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            showSleepTimerSheet = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Timer,
                                            contentDescription = context.resources.getString(R.string.set_sleep_timer),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = playbackState.playbackMode != PlaybackMode.Shuffle,
                                        enter = expandHorizontally(),
                                        exit = shrinkHorizontally()
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (playbackState.playbackMode != PlaybackMode.Shuffle) {
                                                    showQueue = true
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                                contentDescription = context.resources.getString(R.string.show_queue),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isTablet) {
                                        IconButton(
                                            onClick = {
                                                isLyricsFullscreen = true
                                                onLyricsSheetExpandedChange(true)
                                                onLyricsClick()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Fullscreen,
                                                contentDescription = context.resources.getString(R.string.lyrics_fullscreen),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    TrackMenuButton(
                                        onPlayNextClick = onPlayNextClick,
                                        onAddToQueueClick = onAddToQueueClick,
                                        onAddToPlaylistClick = onAddToPlaylistClick,
                                        onViewTrackInfoClick = onViewTrackInfoClick,
                                        onGoToAlbumClick = onGoToAlbumClick,
                                        onGoToArtistClick = onGoToArtistClick,
                                        onShareClick = { context.shareTrack(currentTrack) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showLyricsSheet,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(
                initialOffsetY = { it / 10 }),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + slideOutVertically(
                targetOffsetY = { it / 10 }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LyricsSheet(
                playbackStateFlow = playbackStateFlow,
                playbackPositionFlow = playbackPositionFlow,
                lyricsSyncOffset = lyricsSyncOffset,
                lyricsTextStyle = lyricsTextStyle,
                onBackClick = {
                    isLyricsFullscreen = false
                    onLyricsSheetExpandedChange(false)
                },
                isFullscreen = if (isTablet) isLyricsFullscreen else true,
                onFullscreenToggle = if (isTablet) {
                    {
                        isLyricsFullscreen = false
                        onLyricsSheetExpandedChange(false)
                        showEmbeddedLyrics = true
                    }
                } else null,
                containerColor = lyricsContainerColor,
                contentColor = lyricsContentColor,
                onSeekTo = onSeekTo,
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = showQueue,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(
                initialOffsetY = { it / 10 }),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + slideOutVertically(
                targetOffsetY = { it / 10 }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val playlist by remember {
                derivedStateOf {
                    playbackState.playlist
                }
            }
            val currentTrack by remember {
                derivedStateOf {
                    playbackState.currentTrack
                }
            }

            val listState = rememberLazyListState()
            Queue(
                listState = listState,
                playlist = playlist,
                currentTrack = currentTrack,
                onRemoveFromQueueClick = onRemoveFromQueueClick,
                onReorderingQueue = onReorderingQueue,
                onTrackClick = onTrackClick,
                onBackClick = {
                    showQueue = false
                }
            )
        }

        if (showSleepTimerSheet) {
            SleepTimerBottomSheet(
                onDismissRequest = {
                    showSleepTimerSheet = false
                }
            )
        }
    }
}

@Composable
fun PlaybackControl(
    playbackStateFlow: StateFlow<PlaybackState>,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekToNextClick: () -> Unit,
    onSeekToPreviousClick: () -> Unit,
    onPlaybackModeClick: () -> Unit,
    showEmbeddedLyrics: Boolean,
    onEmbeddedLyricsToggle: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val context = LocalContext.current
        val playbackState by playbackStateFlow.collectAsState()
        val position by remember {
            derivedStateOf {
                playbackState.position
            }
        }
        val currentTrack by remember {
            derivedStateOf {
                playbackState.currentTrack!!
            }
        }
        val isPlaying by remember {
            derivedStateOf {
                playbackState.isPlaying
            }
        }
        val playbackMode by remember {
            derivedStateOf {
                playbackState.playbackMode
            }
        }

        WavingSeekBar(
            position = position,
            duration = currentTrack.duration.toLong(),
            onPositionChange = {
                onSeekTo(it)
            },
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(if (compact) 16.dp else 28.dp))

        val sideButtonSize = if (compact) 40.dp else 48.dp
        val sideIconSize = if (compact) 22.dp else 28.dp
        val skipButtonSize = if (compact) 48.dp else 64.dp
        val skipIconSize = if (compact) 36.dp else 48.dp
        val playButtonSize = if (compact) 64.dp else 80.dp
        val playIconSize = if (compact) 48.dp else 64.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val layoutDirection = LocalLayoutDirection.current
            val isRtl = layoutDirection == LayoutDirection.Rtl

            IconButton(
                onClick = onPlaybackModeClick,
                modifier = Modifier.size(sideButtonSize)
            ) {
                Icon(
                    imageVector = when (playbackMode) {
                        PlaybackMode.Repeat -> Icons.Rounded.Repeat
                        PlaybackMode.RepeatOne -> Icons.Rounded.RepeatOne
                        PlaybackMode.Shuffle -> Icons.Rounded.Shuffle
                    },
                    contentDescription = context.resources.getString(R.string.playback_mode_toggle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(sideIconSize)
                )
            }

            IconButton(
                onClick = onSeekToPreviousClick,
                modifier = Modifier.size(skipButtonSize)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(skipIconSize)
                        .graphicsLayer {
                            if (isRtl) {
                                rotationY = 180f
                            }
                        }
                )
            }

            if (isPlaying) {
                IconButton(
                    onClick = onPauseClick,
                    modifier = Modifier.size(playButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(playIconSize)
                    )
                }
            } else {
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(playButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(playIconSize)
                    )
                }
            }

            IconButton(
                onClick = onSeekToNextClick,
                modifier = Modifier.size(skipButtonSize)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(skipIconSize)
                        .graphicsLayer {
                            if (isRtl) {
                                rotationY = 180f
                            }
                        }
                )
            }

            IconButton(
                onClick = onEmbeddedLyricsToggle,
                modifier = Modifier.size(sideButtonSize),
                colors = if (showEmbeddedLyrics) {
                    IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    IconButtonDefaults.iconButtonColors()
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    contentDescription = context.resources.getString(R.string.show_lyrics),
                    tint = if (showEmbeddedLyrics) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(sideIconSize)
                )
            }
        }
    }
}

@Composable
fun AudioQualityTag(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeText = remember(track.data, track.bitrate) {
        track.audioQualityBadgeText
    }
    if (badgeText.isBlank()) return

    val isLossless = remember(track.data, track.bitrate) {
        track.isLossless
    }
    val containerColor = if (isLossless) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isLossless) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(ShapeDefaults.Small)
            .background(color = containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (isLossless) Icons.Rounded.HighQuality else Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
fun PersistentTabletPlayer(
    playbackStateFlow: StateFlow<PlaybackState>,
    playbackPositionFlow: StateFlow<Long>? = null,
    lyricsSyncOffset: Long = 0L,
    showEmbeddedLyrics: Boolean,
    onShowEmbeddedLyricsChange: (Boolean) -> Unit,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSeekToNextClick: () -> Unit,
    onSeekToPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlaybackModeClick: () -> Unit,
    onCoverArtLoaded: (ImageBitmap?) -> Unit,
    onPlayNextClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onViewTrackInfoClick: () -> Unit,
    onGoToAlbumClick: () -> Unit,
    onGoToArtistClick: () -> Unit,
    onExpandToFullscreenClick: () -> Unit,
    onLyricsFullscreenClick: () -> Unit,
    onLyricsClick: () -> Unit,
    lyricsTextStyle: TextStyle,
    isDarkLyricsTheme: Boolean = isSystemInDarkTheme(),
    onRemoveFromQueueClick: (Int) -> Unit,
    onReorderingQueue: (Int, Int) -> Unit,
    onTrackClick: (Track, Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by playbackStateFlow.collectAsState()
    val currentTrack by remember {
        derivedStateOf { playbackState.currentTrack }
    }
    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    LaunchedEffect(currentTrack?.uri, showEmbeddedLyrics) {
        if (showEmbeddedLyrics && currentTrack != null) {
            onLyricsClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(color = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        val track = currentTrack
        if (track == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = context.resources.getString(R.string.nothing_yet),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            val isCompactControls = screenWidthDp < 900

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Compact Title & Artist at the top when lyrics are expanded
                AnimatedVisibility(
                    visible = showEmbeddedLyrics,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = track,
                        label = "tablet-compact-top-title-artist",
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) { activeTrack ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = activeTrack.title
                                    ?: context.resources.getString(R.string.unknown_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )

                            Text(
                                text = activeTrack.artist
                                    ?: context.resources.getString(R.string.unknown_artist),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                }

                // Cover art / Embedded lyrics
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = (if (showEmbeddedLyrics) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .aspectRatio(1f)
                                .fillMaxSize()
                        })
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            .clip(ShapeDefaults.Large)
                    ) {
                        AnimatedContent(
                            targetState = showEmbeddedLyrics,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                        scaleIn(
                                            initialScale = 0.96f,
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        )) togetherWith
                                        (fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                scaleOut(
                                                    targetScale = 0.96f,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                ))
                            },
                            label = "tablet-persistent-cover-or-lyrics"
                        ) { embedded ->
                            if (embedded) {
                                EmbeddedLyricsPanel(
                                    playbackStateFlow = playbackStateFlow,
                                    playbackPositionFlow = playbackPositionFlow,
                                    lyricsSyncOffset = lyricsSyncOffset,
                                    lyricsTextStyle = lyricsTextStyle,
                                    coverArtUri = track.coverArtUri,
                                    onCoverArtLoaded = onCoverArtLoaded,
                                    isDarkTheme = isDarkLyricsTheme,
                                    onSeekTo = onSeekTo,
                                    onFullscreenClick = onLyricsFullscreenClick,
                                    onCloseClick = {
                                        onShowEmbeddedLyricsChange(false)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AnimatedContent(
                                    targetState = track,
                                    label = "tablet-cover-art-animation"
                                ) { activeTrack ->
                                    CoverArt(
                                        uri = activeTrack.coverArtUri,
                                        onCoverArtLoaded = onCoverArtLoaded,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(ShapeDefaults.Large)
                                            .clickable {
                                                onShowEmbeddedLyricsChange(true)
                                                onLyricsClick()
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // Full Title, Artist & Audio quality (hidden when lyrics are expanded)
                AnimatedVisibility(
                    visible = !showEmbeddedLyrics,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedContent(
                            targetState = track,
                            label = "tablet-title-artist-animation",
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            modifier = Modifier.fillMaxWidth()
                        ) { activeTrack ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = activeTrack.title
                                        ?: context.resources.getString(R.string.unknown_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.basicMarquee()
                                )

                                Text(
                                    text = activeTrack.artist
                                        ?: context.resources.getString(R.string.unknown_artist),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.basicMarquee()
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                AudioQualityTag(
                                    track = activeTrack,
                                    onClick = onViewTrackInfoClick
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (showEmbeddedLyrics) 10.dp else 16.dp))

                // Playback controls (compact when lyrics are expanded)
                PlaybackControl(
                    playbackStateFlow = playbackStateFlow,
                    onPlayClick = onPlayClick,
                    onPauseClick = onPauseClick,
                    onSeekTo = onSeekTo,
                    onSeekToNextClick = onSeekToNextClick,
                    onSeekToPreviousClick = onSeekToPreviousClick,
                    onPlaybackModeClick = onPlaybackModeClick,
                    showEmbeddedLyrics = showEmbeddedLyrics,
                    onEmbeddedLyricsToggle = {
                        val next = !showEmbeddedLyrics
                        onShowEmbeddedLyricsChange(next)
                        if (next) {
                            onLyricsClick()
                        }
                    },
                    compact = isCompactControls || showEmbeddedLyrics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                )

                // Bottom action row: hidden when lyrics are expanded
                AnimatedVisibility(
                    visible = !showEmbeddedLyrics,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onExpandToFullscreenClick) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInFull,
                                contentDescription = context.resources.getString(R.string.lyrics_fullscreen),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showSleepTimerSheet = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = context.resources.getString(R.string.set_sleep_timer),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = playbackState.playbackMode != PlaybackMode.Shuffle,
                                enter = expandHorizontally(),
                                exit = shrinkHorizontally()
                            ) {
                                IconButton(
                                    onClick = {
                                        if (playbackState.playbackMode != PlaybackMode.Shuffle) {
                                            showQueue = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = context.resources.getString(R.string.show_queue),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            TrackMenuButton(
                                onPlayNextClick = onPlayNextClick,
                                onAddToQueueClick = onAddToQueueClick,
                                onAddToPlaylistClick = onAddToPlaylistClick,
                                onViewTrackInfoClick = onViewTrackInfoClick,
                                onGoToAlbumClick = onGoToAlbumClick,
                                onGoToArtistClick = onGoToArtistClick,
                                onShareClick = { context.shareTrack(track) }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showQueue,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                        slideInVertically(initialOffsetY = { it / 10 }),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                        slideOutVertically(targetOffsetY = { it / 10 }),
                modifier = Modifier.matchParentSize()
            ) {
                val listState = rememberLazyListState()
                Queue(
                    listState = listState,
                    playlist = playbackState.playlist,
                    currentTrack = currentTrack,
                    onRemoveFromQueueClick = onRemoveFromQueueClick,
                    onReorderingQueue = onReorderingQueue,
                    onTrackClick = onTrackClick,
                    onBackClick = {
                        showQueue = false
                    }
                )
            }

            if (showSleepTimerSheet) {
                SleepTimerBottomSheet(
                    onDismissRequest = {
                        showSleepTimerSheet = false
                    }
                )
            }
        }
    }
}