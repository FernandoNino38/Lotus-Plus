package com.dn0ne.player.app.presentation.components.playback.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring

class MotionScheme private constructor() {
    companion object {
        fun expressive(): MotionScheme = MotionScheme()
    }

    fun <T> defaultSpatialSpec(): AnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 260.0f)

    fun <T> fastSpatialSpec(): AnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 480.0f)
}