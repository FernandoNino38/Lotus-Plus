package com.dn0ne.player.app.presentation.components.tablet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dn0ne.player.app.presentation.components.topbar.Tab
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

@Composable
fun TabletMainContent(
    selectedTab: Tab,
    defaultTab: Tab,
    onBackToDefaultTab: () -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
    header: @Composable (Tab) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    contentHorizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(
        16.dp,
        alignment = androidx.compose.ui.Alignment.CenterHorizontally
    ),
    contentVerticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    enableScrollbar: Boolean = true,
    gridCells: (Tab) -> GridCells = { GridCells.Fixed(1) },
    modifier: Modifier = Modifier,
    tabContent: LazyGridScope.(Tab) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom
                )
            )
    ) {
        header(selectedTab)

        AnimatedContent(
            targetState = selectedTab,
            label = "tablet-column-tab-animation",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { tab ->
            if (selectedTab != defaultTab) {
                BackHandler {
                    onBackToDefaultTab()
                }
            }

            LazyVerticalGridScrollbar(
                state = gridState,
                settings = ScrollbarSettings(
                    enabled = enableScrollbar,
                    thumbUnselectedColor = MaterialTheme.colorScheme.surfaceContainer,
                    thumbSelectedColor = MaterialTheme.colorScheme.primaryContainer,
                )
            ) {
                LazyVerticalGrid(
                    columns = gridCells(tab),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    horizontalArrangement = contentHorizontalArrangement,
                    verticalArrangement = contentVerticalArrangement
                ) {
                    tabContent(tab)

                    item(
                        span = {
                            GridItemSpan(this.maxLineSpan)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                        )
                    }
                }
            }
        }
    }
}
