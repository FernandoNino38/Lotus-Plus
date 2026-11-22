package com.dn0ne.player.app.presentation.components.playback

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.lyrics.LyricLine
import com.dn0ne.player.app.presentation.components.ProviderText
import com.dn0ne.player.app.presentation.components.playback.lyrics.HideSystemBarsEffect
import com.dn0ne.player.app.presentation.components.playback.lyrics.LyricLineRow
import com.dn0ne.player.app.presentation.components.playback.lyrics.LyricsAutoScrollEffect
import com.dn0ne.player.app.presentation.components.playback.lyrics.rememberCurrentLyricLineIndex
import com.dn0ne.player.app.presentation.components.playback.motion.MotionScheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

@Composable
fun LyricsSheet(
    playbackStateFlow: StateFlow<PlaybackState>,
    playbackPositionFlow: StateFlow<Long>? = null,
    lyricsSyncOffset: Long = 0L,
    lyricsTextStyle: TextStyle,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color = contentColor,
    onBackClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    isFullscreen: Boolean = false,
    onFullscreenToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBackClick()
    }

    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(durationMillis = 300),
        label = "lyrics-container-color"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = tween(durationMillis = 300),
        label = "lyrics-content-color"
    )
    val animatedAccentColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 300),
        label = "lyrics-accent-color"
    )

    val playbackState by playbackStateFlow.collectAsState()
    val isLoadingLyrics by remember {
        derivedStateOf { playbackState.isLoadingLyrics }
    }
    val lyrics by remember {
        derivedStateOf { playbackState.lyrics }
    }
    val context = LocalContext.current

    var showSyncedLyrics by remember(lyrics) {
        mutableStateOf<Boolean?>(
            when {
                lyrics?.synced != null || lyrics?.lines != null -> true
                lyrics?.plain != null -> false
                else -> null
            }
        )
    }

    // 1. Mecanismo de Sincronização Temporal e Resolução de Estado
    // Fluxo contínuo de posição (StateFlow)
    val fallbackPositionFlow = remember(playbackStateFlow) {
        playbackStateFlow.map { it.position }
    }
    val positionState = playbackPositionFlow?.collectAsState()
        ?: fallbackPositionFlow.collectAsState(initial = 0L)

    // Resolução de linhas a partir do modelo
    val lyricLines: List<LyricLine> by remember(lyrics) {
        derivedStateOf {
            lyrics?.resolvedLines ?: emptyList()
        }
    }

    // 1. Resolução de Alta Eficiência (derivedStateOf):
    // A determinação da linha ativa (currentLineIndex) é isolada em derivedStateOf.
    val currentLineIndex by rememberCurrentLyricLineIndex(lyricLines, positionState, lyricsSyncOffset)

    // 2. Material 3 Expressive Motion
    val motionScheme = remember { MotionScheme.expressive() }
    val m3eSpatialSpec = remember { motionScheme.defaultSpatialSpec<Float>() }
    val m3eFastSpatialSpec = remember { motionScheme.fastSpatialSpec<Float>() }

    // Tela cheia: oculta as barras do sistema enquanto ativo
    HideSystemBarsEffect(hide = isFullscreen)

    CompositionLocalProvider(
        LocalContentColor provides animatedContentColor
    ) {
        val listState = rememberLazyListState()
        val focalFraction = 0.34f

        // 2. Autoscroll e Snapping Focal com Tratamento Inteligente de Seek
        LyricsAutoScrollEffect(
            listState = listState,
            currentLineIndex = currentLineIndex,
            lineCount = lyricLines.size,
            enabled = showSyncedLyrics == true,
            focalFraction = focalFraction
        )

        Column(
            modifier = modifier
                .background(color = animatedContainerColor)
                .clickable(enabled = false, onClick = {})
                .safeDrawingPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = context.resources.getString(R.string.close_lyrics_sheet)
                    )
                }

                if ((lyrics?.synced != null || lyrics?.lines != null) && lyrics?.plain != null && showSyncedLyrics != null) {
                    LyricsTypeSwitch(
                        isSynced = showSyncedLyrics!!,
                        onIsSyncedSwitch = {
                            showSyncedLyrics = it
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Text(
                        text = context.resources.getString(R.string.lyrics),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (onFullscreenToggle != null) {
                    IconButton(
                        onClick = onFullscreenToggle,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) {
                                Icons.Rounded.FullscreenExit
                            } else {
                                Icons.Rounded.Fullscreen
                            },
                            contentDescription = context.resources.getString(
                                if (isFullscreen) R.string.exit_fullscreen else R.string.lyrics_fullscreen
                            )
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.08f to Color.Black,
                                0.90f to Color.Black,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = if (showSyncedLyrics == true) {
                        PaddingValues(
                            start = 24.dp,
                            end = 24.dp,
                            top = maxHeight * focalFraction,
                            bottom = maxHeight * 0.6f
                        )
                    } else {
                        PaddingValues(horizontal = 24.dp, vertical = 32.dp)
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (showSyncedLyrics) {
                        null -> {
                            item(key = isLoadingLyrics) {
                                Column(
                                    modifier = Modifier.fillParentMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (isLoadingLyrics) {
                                        Text(
                                            text = context.resources.getString(R.string.loading_lyrics),
                                            style = MaterialTheme.typography.titleMedium
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        LinearProgressIndicator(
                                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .5f),
                                            modifier = Modifier.width(100.dp)
                                        )
                                    } else {
                                        Text(text = context.resources.getString(R.string.cant_find_lyrics))
                                    }
                                }
                            }
                        }

                        true -> {
                            if (lyricLines.isNotEmpty()) {
                                itemsIndexed(
                                    items = lyricLines,
                                    key = { index, line -> "$index-${line.startTimeMs}" }
                                ) { index, line ->
                                    LyricLineRow(
                                        index = index,
                                        currentLineIndex = currentLineIndex,
                                        line = line,
                                        listState = listState,
                                        positionState = positionState,
                                        lyricsSyncOffset = lyricsSyncOffset,
                                        style = lyricsTextStyle,
                                        accentColor = animatedAccentColor,
                                        contentColor = animatedContentColor,
                                        isImmersive = false,
                                        m3eSpatialSpec = m3eSpatialSpec,
                                        m3eFastSpatialSpec = m3eFastSpatialSpec,
                                        onClick = {
                                            onSeekTo(line.startTimeMs)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (lyrics?.areFromRemote == true) {
                                    item {
                                        ProviderText(
                                            providerText = context.resources.getString(R.string.lyrics_provided_by),
                                            uri = context.resources.getString(R.string.lrclib_uri),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        false -> {
                            lyrics?.plain?.let { plain ->
                                itemsIndexed(
                                    items = plain,
                                    key = { index, line -> "$index-$line" }
                                ) { index, line ->
                                    Column {
                                        PlainLyricsLine(
                                            line = line,
                                            style = lyricsTextStyle,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                if (lyrics?.areFromRemote == true) {
                                    item {
                                        ProviderText(
                                            providerText = context.resources.getString(R.string.lyrics_provided_by),
                                            uri = context.resources.getString(R.string.lrclib_uri),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 24.dp)
                                        )
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(120.dp))
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
fun LyricsTypeSwitch(
    isSynced: Boolean,
    onIsSyncedSwitch: (Boolean) -> Unit,
    contentColor: Color = LocalContentColor.current,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .height(IntrinsicSize.Min)
            .clip(ShapeDefaults.ExtraLarge)
            .background(color = contentColor.copy(alpha = .1f))
    ) {
        var midPoint by remember {
            mutableStateOf(0.dp)
        }
        val density = LocalDensity.current
        val capsuleOffset by animateDpAsState(
            targetValue = if (isSynced) 0.dp else midPoint,
            label = "capsule-offset-animation"
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(.5f)
                .offset(x = capsuleOffset)
                .clip(ShapeDefaults.ExtraLarge)
                .background(color = contentColor.copy(alpha = .1f))
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .onGloballyPositioned {
                    midPoint = with(density) { (it.size.width / 2).toDp() }
                }
        ) {
            Text(
                text = context.resources.getString(R.string.synced),
                color = contentColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(ShapeDefaults.ExtraLarge)
                    .clickable(
                        enabled = !isSynced && enabled,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onIsSyncedSwitch(true)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Text(
                text = context.resources.getString(R.string.plain),
                color = contentColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(ShapeDefaults.ExtraLarge)
                    .clickable(
                        enabled = isSynced && enabled,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onIsSyncedSwitch(false)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun PlainLyricsLine(
    line: String,
    style: TextStyle,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier
) {
    Text(
        text = line,
        style = style,
        color = color,
        modifier = modifier
    )
}