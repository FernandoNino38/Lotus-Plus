package com.dn0ne.player.app.presentation.components.playback.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.dn0ne.player.app.domain.lyrics.LyricWord
import com.dn0ne.player.app.domain.lyrics.LyricWordState

/**
 * Física da Palavra Ativa (LyricWordSpan):
 * - Salto de escala para 1.08x animado por fastSpatialSpec quando CURRENT
 * - Transição suave de cor e brilho para o tom de destaque (accentColor)
 * - Ao passar (PASSED), desce para escala 1.0x mantendo opacidade de 0.95x para evidenciar que já foi executada
 */
@Composable
fun LyricWordSpan(
    word: LyricWord,
    state: LyricWordState,
    style: TextStyle,
    accentColor: Color,
    baseColor: Color,
    fastSpatialSpec: AnimationSpec<Float>,
    modifier: Modifier = Modifier
) {
    val targetScale = when (state) {
        LyricWordState.CURRENT -> 1.08f
        else -> 1.0f
    }

    val targetAlpha = when (state) {
        LyricWordState.INACTIVE -> 0.45f
        LyricWordState.UPCOMING -> 0.50f
        LyricWordState.CURRENT -> 1.0f
        LyricWordState.PASSED -> 0.95f
    }

    val targetColor = when (state) {
        LyricWordState.CURRENT -> baseColor
        LyricWordState.PASSED -> baseColor.copy(alpha = 0.90f)
        LyricWordState.UPCOMING -> baseColor.copy(alpha = 0.45f)
        LyricWordState.INACTIVE -> baseColor.copy(alpha = 0.30f)
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = fastSpatialSpec,
        label = "word-scale"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = fastSpatialSpec,
        label = "word-alpha"
    )

    val animatedGlowAlpha by animateFloatAsState(
        targetValue = if (state == LyricWordState.CURRENT) 0.50f else 0f,
        animationSpec = fastSpatialSpec,
        label = "word-glow"
    )

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "word-color"
    )

    val effectiveStyle = if (animatedGlowAlpha > 0.01f) {
        style.copy(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = baseColor.copy(alpha = animatedGlowAlpha),
                blurRadius = 14f * (animatedGlowAlpha / 0.50f)
            )
        )
    } else {
        style
    }

    Text(
        text = word.text,
        style = effectiveStyle,
        color = animatedColor,
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedAlpha
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
    )
}