package com.dn0ne.player.app.presentation.components.playback.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dn0ne.player.app.domain.lyrics.LyricLine
import com.dn0ne.player.app.domain.lyrics.LyricWordState
import com.dn0ne.player.app.domain.lyrics.clusterSyncedWords
import kotlin.math.abs

/**
 * Linha de letra com:
 * 1. Efeito óptico "Fisheye" (Foco por distância relativa)
 * 2. Parallax não-linear na fase de desenho (Zero Recomposição na GPU)
 * 3. Animação granular palavra por palavra (Word-by-Word Karaoke) com clusterização
 * 4. Interlúdios instrumentais vetoriais via [BubblesLine]
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricLineRow(
    index: Int,
    currentLineIndex: Int,
    line: LyricLine,
    listState: LazyListState,
    positionState: State<Long>,
    lyricsSyncOffset: Long,
    style: TextStyle,
    accentColor: Color,
    contentColor: Color,
    isImmersive: Boolean,
    m3eSpatialSpec: AnimationSpec<Float>,
    m3eFastSpatialSpec: AnimationSpec<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 3. Efeito Óptico "Fisheye" (Foco por Distância Relativa)
    val distanceFromCurrent = if (currentLineIndex < 0) 3 else abs(currentLineIndex - index)
    val isCurrentLine = distanceFromCurrent == 0

    val targetScale = when (distanceFromCurrent) {
        0 -> if (isImmersive) 1.05f else 1.07f
        1 -> 0.96f
        2 -> 0.92f
        else -> 0.89f
    }

    val targetAlpha = when (distanceFromCurrent) {
        0 -> 1.0f
        1 -> 0.50f
        2 -> 0.34f
        else -> 0.22f
    }

    // Espaçamento vertical constante: animar o padding altera a altura dos itens durante o
    // autoscroll, invalidando o alvo do scroll e causando um reposicionamento brusco no final.
    // O foco "fisheye" é mantido apenas na fase de desenho (scale/alpha via graphicsLayer).
    val linePadding = 14.dp

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = m3eSpatialSpec,
        label = "fisheye-scale"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = m3eSpatialSpec,
        label = "fisheye-alpha"
    )

    val animatedGlowAlpha by animateFloatAsState(
        targetValue = if (isCurrentLine) 0.45f else 0f,
        animationSpec = m3eSpatialSpec,
        label = "fisheye-glow"
    )

    val animatedTextColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = tween(durationMillis = 250),
        label = "line-text-color"
    )

    val effectiveLineStyle = if (animatedGlowAlpha > 0.01f) {
        style.copy(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = contentColor.copy(alpha = animatedGlowAlpha),
                blurRadius = 14f * (animatedGlowAlpha / 0.45f)
            )
        )
    } else {
        style
    }

    val transformOrigin = when (style.textAlign) {
        TextAlign.Center -> TransformOrigin(0.5f, 0.5f)
        TextAlign.End, TextAlign.Right -> TransformOrigin(1f, 0.5f)
        else -> TransformOrigin(0f, 0.5f)
    }

    val flowRowArrangement = when (style.textAlign) {
        TextAlign.Center -> Arrangement.Center
        TextAlign.End, TextAlign.Right -> Arrangement.End
        else -> Arrangement.Start
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = linePadding)
            // 4. Parallax Não-Linear na Fase de Desenho (Zero Recomposição)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedAlpha
                this.transformOrigin = transformOrigin

                val currentLayoutInfo = listState.layoutInfo
                val visibleItems = currentLayoutInfo.visibleItemsInfo
                val halfSpan =
                    ((currentLayoutInfo.viewportEndOffset - currentLayoutInfo.viewportStartOffset) / 2f)
                if (halfSpan > 0f && visibleItems.isNotEmpty()) {
                    val viewportCenter =
                        currentLayoutInfo.viewportStartOffset + halfSpan
                    val lineItemInfo = visibleItems.find { it.index == index }
                    val distanceRatio = if (lineItemInfo != null) {
                        val itemCenter = lineItemInfo.offset + (lineItemInfo.size / 2f)
                        ((itemCenter - viewportCenter) / halfSpan).coerceIn(-1f, 1f)
                    } else if (index < visibleItems.first().index) {
                        -1f
                    } else {
                        1f
                    }
                    val maxTranslation = 18f
                    // Curva cúbica não-linear contínua sem salto ao entrar na viewport
                    translationY = distanceRatio * distanceRatio * distanceRatio * maxTranslation
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = when (style.textAlign) {
            TextAlign.Center -> Alignment.Center
            TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        if (line.isInstrumental) {
            // 6. Interlúdio Instrumental Procedural (BubblesLine)
            BubblesLine(
                accentColor = contentColor,
                modifier = Modifier.align(
                    when (style.textAlign) {
                        TextAlign.Center -> Alignment.Center
                        TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
                        else -> Alignment.CenterStart
                    }
                )
            )
        } else if (line.words.isNotEmpty()) {
            // 5. Animação Granular Palavra por Palavra com Clusterização Gramatical
            val clusters = remember(line.words) {
                clusterSyncedWords(line.words)
            }

            // Resolução de alta eficiência (derivedStateOf) para palavras
            val effectivePosition by remember(positionState, lyricsSyncOffset) {
                derivedStateOf { positionState.value + lyricsSyncOffset }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = flowRowArrangement,
                verticalArrangement = Arrangement.Center
            ) {
                clusters.forEach { cluster ->
                    Row(
                        modifier = Modifier.wrapContentSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        cluster.words.forEach { word ->
                            val wordState = when {
                                !isCurrentLine -> {
                                    if (currentLineIndex >= 0 && index < currentLineIndex) {
                                        LyricWordState.PASSED
                                    } else {
                                        LyricWordState.INACTIVE
                                    }
                                }
                                effectivePosition < word.startTimeMs -> LyricWordState.UPCOMING
                                effectivePosition in word.startTimeMs..word.endTimeMs -> LyricWordState.CURRENT
                                else -> LyricWordState.PASSED
                            }

                            LyricWordSpan(
                                word = word,
                                state = wordState,
                                style = style,
                                accentColor = accentColor,
                                baseColor = contentColor,
                                fastSpatialSpec = m3eFastSpatialSpec
                            )
                        }

                        if (cluster.trailingSpace) {
                            Text(
                                text = " ",
                                style = style,
                                color = Color.Transparent
                            )
                        }
                    }
                }
            }
        } else {
            // Linha sincronizada padrão (sem tags de palavras)
            Text(
                text = line.text,
                style = effectiveLineStyle,
                color = animatedTextColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}