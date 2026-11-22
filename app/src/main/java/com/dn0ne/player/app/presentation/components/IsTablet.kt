package com.dn0ne.player.app.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun isTablet(): Boolean {
    return LocalConfiguration.current.smallestScreenWidthDp >= 600
}
