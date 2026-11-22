package com.dn0ne.player.app.presentation.components.playback.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

data class MorphCubicSegment(
    val startX: Float,
    val startY: Float,
    val cp1X: Float,
    val cp1Y: Float,
    val cp2X: Float,
    val cp2Y: Float,
    val endX: Float,
    val endY: Float
)

fun createCirclePathNodes(centerX: Float, centerY: Float, radius: Float): List<MorphCubicSegment> {
    val c = radius * 0.5522848f
    return listOf(
        MorphCubicSegment(centerX, centerY - radius, centerX + c, centerY - radius, centerX + radius, centerY - c, centerX + radius, centerY),
        MorphCubicSegment(centerX + radius, centerY, centerX + radius, centerY + c, centerX + c, centerY + radius, centerX, centerY + radius),
        MorphCubicSegment(centerX, centerY + radius, centerX - c, centerY + radius, centerX - radius, centerY + c, centerX - radius, centerY),
        MorphCubicSegment(centerX - radius, centerY, centerX - radius, centerY - c, centerX - c, centerY - radius, centerX, centerY - radius)
    )
}

fun createVectorNotePathNodes(centerX: Float, centerY: Float, radius: Float): List<MorphCubicSegment> {
    val headCenterX = centerX - (0.22f * radius)
    val headCenterY = centerY + (radius * 0.38f)
    val stemX = centerX + (0.3f * radius)
    val topY = centerY - (0.78f * radius)
    return listOf(
        MorphCubicSegment(headCenterX - (radius * 0.38f), headCenterY, headCenterX - (radius * 0.38f), headCenterY + (radius * 0.38f), headCenterX + (0.28f * radius), headCenterY + (radius * 0.38f), stemX, headCenterY),
        MorphCubicSegment(stemX, headCenterY, stemX + (0.04f * radius), centerY - (0.1f * radius), stemX, centerY - (0.5f * radius), stemX, topY),
        MorphCubicSegment(stemX, topY, stemX + (radius * 0.58f), topY + (radius * 0.18f), stemX + (radius * 0.48f), topY + (0.58f * radius), stemX + (radius * 0.18f), topY + (0.48f * radius)),
        MorphCubicSegment(stemX + (0.18f * radius), topY + (0.48f * radius), stemX - (0.02f * radius), centerY - (0.05f * radius), headCenterX + (0.12f * radius), headCenterY - (0.32f * radius), headCenterX - (0.38f * radius), headCenterY)
    )
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float = ((stop - start) * fraction) + start

fun lerpPath(
    circleNodes: List<MorphCubicSegment>,
    noteNodes: List<MorphCubicSegment>,
    t: Float,
    outPath: Path
) {
    outPath.reset()
    if (circleNodes.isEmpty() || circleNodes.size != noteNodes.size) return
    val clampedT = t.coerceIn(0f, 1f)
    val firstC = circleNodes[0]
    val firstN = noteNodes[0]
    outPath.moveTo(
        lerpFloat(firstC.startX, firstN.startX, clampedT),
        lerpFloat(firstC.startY, firstN.startY, clampedT)
    )
    for (i in circleNodes.indices) {
        val c = circleNodes[i]
        val n = noteNodes[i]
        outPath.cubicTo(
            lerpFloat(c.cp1X, n.cp1X, clampedT),
            lerpFloat(c.cp1Y, n.cp1Y, clampedT),
            lerpFloat(c.cp2X, n.cp2X, clampedT),
            lerpFloat(c.cp2Y, n.cp2Y, clampedT),
            lerpFloat(c.endX, n.endX, clampedT),
            lerpFloat(c.endY, n.endY, clampedT)
        )
    }
    outPath.close()
}

@Composable
fun BubblesLine(
    modifier: Modifier = Modifier,
    accentColor: Color = LocalContentColor.current,
    containerHeight: Dp = 48.dp
) {
    val density = LocalDensity.current
    val floatAmplitudePx = with(density) { 8.dp.toPx() }
    val infiniteTransition = rememberInfiniteTransition(label = "bubbles-line-rhythm")
    val cycleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "infinite-cycle-progress"
    )

    Box(
        modifier = modifier
            .height(containerHeight)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.wrapContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val phases = listOf(0f, 0.333f, 0.666f)
            phases.forEach { phaseOffset ->
                MorphingBubbleParticle(
                    cycleProgress = cycleProgress,
                    phaseOffset = phaseOffset,
                    floatAmplitudePx = floatAmplitudePx,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun MorphingBubbleParticle(
    cycleProgress: Float,
    phaseOffset: Float,
    floatAmplitudePx: Float,
    color: Color,
    modifier: Modifier = Modifier,
    particleSize: Dp = 22.dp
) {
    val localPhase = (cycleProgress + phaseOffset) % 1f
    val verticalTranslationY = (sin(localPhase * 2.0 * PI) * floatAmplitudePx).toFloat()
    val morphProgress = sin(localPhase * PI).toFloat().coerceIn(0f, 1f)
    val scale = (0.4f * morphProgress) + 1f
    val path = remember { Path() }

    Box(
        modifier = modifier
            .size(particleSize)
            .graphicsLayer {
                translationY = verticalTranslationY
                scaleX = scale
                scaleY = scale
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.minDimension * 0.45f
            lerpPath(
                createCirclePathNodes(cx, cy, radius),
                createVectorNotePathNodes(cx, cy, radius),
                morphProgress,
                path
            )
            val brush = Brush.radialGradient(
                0.0f to color.copy(alpha = 0.95f),
                0.7f to color.copy(alpha = (morphProgress * 0.25f) + 0.7f),
                1.0f to color.copy(alpha = morphProgress * 0.25f),
                center = Offset(cx, cy),
                radius = radius * 1.5f
            )
            drawPath(path = path, brush = brush, style = Fill)
        }
    }
}
