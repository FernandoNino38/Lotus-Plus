package com.dn0ne.player.app.presentation.components.playback.lyrics

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.toBitmap
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.lyrics.LyricLine
import com.dn0ne.player.app.presentation.components.CoverArt
import com.dn0ne.player.app.presentation.components.playback.PlaybackState
import com.dn0ne.player.app.presentation.components.playback.motion.MotionScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign

/**
 * Resolve a linha ativa isolada em derivedStateOf (só recompõe quando o índice muda).
 */
@Composable
fun rememberCurrentLyricLineIndex(
    lyricLines: List<LyricLine>,
    positionState: State<Long>,
    lyricsSyncOffset: Long
): State<Int> = remember(lyricLines, positionState, lyricsSyncOffset) {
    derivedStateOf {
        if (lyricLines.isEmpty()) return@derivedStateOf -1
        val effectivePos = positionState.value + lyricsSyncOffset
        lyricLines.indexOfLast { effectivePos >= it.startTimeMs }
    }
}

/**
 * Autoscroll focal contínuo: posiciona a linha ativa a [focalFraction] da altura da viewport.
 * - Mola criticamente amortecida em malha fechada (frame a frame) com preservação de velocidade.
 * - Mesmo quando a linha atual está fora de visualização (targetItem == null), puxa a lista
 *   suavemente sem saltos bruscos (scrollToItem), convergindo direto para [focalY] sem overshoot.
 * - Pausa enquanto o usuário arrasta e retoma automaticamente com transição suave após 2s.
 */
@Composable
fun LyricsAutoScrollEffect(
    listState: LazyListState,
    currentLineIndex: Int,
    lineCount: Int,
    enabled: Boolean,
    focalFraction: Float = 0.34f
) {
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var isUserInteracting by remember { mutableStateOf(false) }
    var resumeTrigger by remember { mutableIntStateOf(0) }
    var scrollVelocity by remember { mutableFloatStateOf(0f) }
    var lastObservedIndex by remember { mutableIntStateOf(currentLineIndex) }

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            isUserInteracting = true
            scrollVelocity = 0f
        } else if (isUserInteracting) {
            delay(2000L)
            isUserInteracting = false
            resumeTrigger++
        }
    }

    // Se o usuário clicou em uma linha ou fez seek na faixa, retoma o autoscroll suave imediatamente
    LaunchedEffect(currentLineIndex) {
        val jump = abs(currentLineIndex - lastObservedIndex)
        lastObservedIndex = currentLineIndex
        if (!isUserDragging && isUserInteracting && jump > 1) {
            isUserInteracting = false
            resumeTrigger++
        }
    }

    LaunchedEffect(currentLineIndex, lineCount, enabled, resumeTrigger) {
        if (!enabled || isUserInteracting || lineCount <= 0) return@LaunchedEffect
        val targetIndex = currentLineIndex.coerceIn(0, lineCount - 1)

        if (listState.layoutInfo.viewportSize.height <= 0) {
            withFrameNanos { }
        }

        listState.scroll {
            var velocity = scrollVelocity
            var lastFrameNanos = 0L
            val omega = 8.5f // frequência natural (rad/s) da mola criticamente amortecida

            while (!isUserDragging) {
                val dt = withFrameNanos { now ->
                    val deltaSec = if (lastFrameNanos == 0L) {
                        1f / 60f
                    } else {
                        ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 240f, 0.05f)
                    }
                    lastFrameNanos = now
                    deltaSec
                }

                val info = listState.layoutInfo
                val viewportHeight = info.viewportSize.height
                if (viewportHeight <= 0) break
                val visibleItems = info.visibleItemsInfo
                if (visibleItems.isEmpty()) break

                val focalY = (viewportHeight * focalFraction).toInt() + info.viewportStartOffset
                val targetItem = visibleItems.firstOrNull { it.index == targetIndex }

                val remaining = if (targetItem != null) {
                    (targetItem.offset - focalY).toFloat()
                } else {
                    val avgItemSize = if (visibleItems.size >= 2) {
                        val first = visibleItems.first()
                        val last = visibleItems.last()
                        val indexSpan = (last.index - first.index).coerceAtLeast(1)
                        ((last.offset - first.offset).toFloat() / indexSpan).coerceAtLeast(48f)
                    } else {
                        visibleItems.first().size.toFloat().coerceAtLeast(48f)
                    }
                    val refItem = if (targetIndex < visibleItems.first().index) {
                        visibleItems.first()
                    } else {
                        visibleItems.last()
                    }
                    (refItem.offset - focalY) + (targetIndex - refItem.index) * avgItemSize
                }

                if (targetItem != null && abs(remaining) < 0.75f && abs(velocity) < 12f) {
                    if (abs(remaining) > 0.1f) {
                        scrollBy(remaining)
                    }
                    scrollVelocity = 0f
                    break
                }

                // Solução analítica exata da mola criticamente amortecida:
                // r(dt) = (r0 + (omega * r0 - v0) * dt) * exp(-omega * dt)
                val expTerm = exp(-omega * dt)
                val rawVelocity = (velocity + omega * (omega * remaining - velocity) * dt) * expTerm

                // Limita a velocidade máxima em distâncias longas respeitando a fronteira
                // de amortecimento crítico (|v| <= omega * |r|) para nunca causar overshoot.
                val maxCruiseSpeed = viewportHeight * 4.2f
                val maxAllowedSpeed = min(maxCruiseSpeed, abs(omega * remaining))
                velocity = if (abs(rawVelocity) > maxAllowedSpeed && rawVelocity * remaining > 0f) {
                    sign(rawVelocity) * maxAllowedSpeed
                } else {
                    rawVelocity
                }
                scrollVelocity = velocity

                val nextRemaining = (remaining + (omega * remaining - velocity) * dt) * expTerm
                val maxStep = viewportHeight * 0.35f
                val step = (remaining - nextRemaining).coerceIn(-maxStep, maxStep)
                val consumed = scrollBy(step)

                // Se atingiu o limite físico da lista (topo ou fim), encerra suavemente
                if (abs(step) > 0.5f && abs(consumed) < 0.05f) {
                    scrollVelocity = 0f
                    break
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Modo tela cheia (imersivo): oculta status bar e barra de navegação enquanto [hide] for true.
 * As barras podem ser exibidas temporariamente com um gesto de deslizar a partir da borda.
 */
@Composable
fun HideSystemBarsEffect(hide: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(hide) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hide && controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (hide) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * Letras sincronizadas renderizadas dentro do quadrado da arte do álbum (tablets / paisagem).
 * Fundo: a própria capa desfocada + véu escuro para contraste; bordas com fade.
 */
@Composable
fun EmbeddedLyricsPanel(
    playbackStateFlow: StateFlow<PlaybackState>,
    playbackPositionFlow: StateFlow<Long>?,
    lyricsSyncOffset: Long,
    lyricsTextStyle: TextStyle,
    coverArtUri: Uri,
    onCoverArtLoaded: (ImageBitmap?) -> Unit,
    onSeekTo: (Long) -> Unit,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onFullscreenClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by playbackStateFlow.collectAsState()
    val lyrics = playbackState.lyrics
    val isLoading = playbackState.isLoadingLyrics

    val fallbackPositionFlow = remember(playbackStateFlow) { playbackStateFlow.map { it.position } }
    val positionState = playbackPositionFlow?.collectAsState()
        ?: fallbackPositionFlow.collectAsState(initial = 0L)

    val lyricLines = remember(lyrics) { lyrics?.resolvedLines ?: emptyList() }
    val currentLineIndex by rememberCurrentLyricLineIndex(lyricLines, positionState, lyricsSyncOffset)

    val motionScheme = remember { MotionScheme.expressive() }
    val spatialSpec = remember { motionScheme.defaultSpatialSpec<Float>() }
    val fastSpatialSpec = remember { motionScheme.fastSpatialSpec<Float>() }

    val contentColor by animateColorAsState(
        targetValue = if (isDarkTheme) Color.White else Color(0xFF141414),
        animationSpec = tween(durationMillis = 300),
        label = "embedded-lyrics-content-color"
    )
    val veilTopColor by animateColorAsState(
        targetValue = if (isDarkTheme) Color.Black.copy(alpha = .55f) else Color.White.copy(alpha = .72f),
        animationSpec = tween(durationMillis = 300),
        label = "embedded-lyrics-veil-top"
    )
    val veilBottomColor by animateColorAsState(
        targetValue = if (isDarkTheme) Color.Black.copy(alpha = .72f) else Color.White.copy(alpha = .85f),
        animationSpec = tween(durationMillis = 300),
        label = "embedded-lyrics-veil-bottom"
    )
    val panelStyle = lyricsTextStyle.copy(
        fontSize = lyricsTextStyle.fontSize * 0.85f,
        lineHeight = lyricsTextStyle.lineHeight * 0.85f
    )

    Box(
        modifier = modifier
            .background(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverArtUri)
                .build(),
            onSuccess = {
                onCoverArtLoaded(it.result.image.toBitmap().asImageBitmap())
            },
            onError = {
                onCoverArtLoaded(it.result.image?.toBitmap()?.asImageBitmap())
            },
            contentScale = ContentScale.Crop,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(48.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(veilTopColor, veilBottomColor)
                    )
                )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // Fade nas bordas superior/inferior
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.12f to Color.Black,
                            0.85f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            val focalFraction = 0.34f
            when {
                lyricLines.isNotEmpty() -> {
                    val listState = rememberLazyListState()
                    LyricsAutoScrollEffect(
                        listState = listState,
                        currentLineIndex = currentLineIndex,
                        lineCount = lyricLines.size,
                        enabled = true,
                        focalFraction = focalFraction
                    )
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 28.dp,
                            end = 28.dp,
                            top = maxHeight * focalFraction,
                            bottom = maxHeight * 0.6f
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
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
                                style = panelStyle,
                                accentColor = contentColor,
                                contentColor = contentColor,
                                isImmersive = true,
                                m3eSpatialSpec = spatialSpec,
                                m3eFastSpatialSpec = fastSpatialSpec,
                                onClick = { onSeekTo(line.startTimeMs) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                lyrics?.plain != null -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 64.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(lyrics.plain) { _, line ->
                            Text(
                                text = line,
                                style = panelStyle,
                                color = contentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                isLoading -> CircularProgressIndicator(
                    color = contentColor,
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> Text(
                    text = context.resources.getString(R.string.cant_find_lyrics),
                    color = contentColor,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val buttonContainerColor by animateColorAsState(
                targetValue = if (isDarkTheme) {
                    Color.Black.copy(alpha = .35f)
                } else {
                    Color.White.copy(alpha = .55f)
                },
                animationSpec = tween(durationMillis = 350),
                label = "embedded-lyrics-button-container"
            )
            val buttonColors = IconButtonDefaults.iconButtonColors(
                containerColor = buttonContainerColor,
                contentColor = contentColor
            )
            IconButton(onClick = onFullscreenClick, colors = buttonColors) {
                Icon(
                    imageVector = Icons.Rounded.Fullscreen,
                    contentDescription = context.resources.getString(R.string.lyrics_fullscreen)
                )
            }
            IconButton(onClick = onCloseClick, colors = buttonColors) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = context.resources.getString(R.string.hide_lyrics)
                )
            }
        }
    }
}