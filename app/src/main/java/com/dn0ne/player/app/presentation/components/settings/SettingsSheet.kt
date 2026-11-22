package com.dn0ne.player.app.presentation.components.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dn0ne.player.R
import com.dn0ne.player.app.presentation.components.isTablet
import com.dn0ne.player.app.presentation.components.topbar.ColumnWithCollapsibleTopBar
import com.kmpalette.DominantColorState
import kotlinx.serialization.Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: SettingsSheetState,
    onFolderPick: (scan: Boolean) -> Unit,
    onPlaylistPick: () -> Unit,
    onScanFoldersClick: () -> Unit,
    onCloseClick: () -> Unit,
    dominantColorState: DominantColorState<ImageBitmap>,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isShown,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(
            initialOffsetY = { it / 10 }),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + slideOutVertically(
            targetOffsetY = { it / 10 }),
    ) {
        BackHandler {
            onCloseClick()
        }

        Box(
            modifier = modifier
                .background(color = MaterialTheme.colorScheme.surface)
                .clickable(
                    enabled = false,
                    onClick = {}
                )
        ) {
            if (isTablet()) {
                val context = LocalContext.current
                var selectedRoute by remember {
                    mutableStateOf<SettingsRoutes>(SettingsRoutes.Playback)
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 280.dp, max = 360.dp)
                            .weight(0.36f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = onCloseClick) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBackIosNew,
                                    contentDescription = context.resources.getString(R.string.close_settings_sheet)
                                )
                            }

                            Text(
                                text = context.resources.getString(R.string.settings),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val settings = listOf(
                            SettingsItem(
                                title = context.resources.getString(R.string.playback),
                                supportingText = context.resources.getString(R.string.playback_supporting_text),
                                icon = Icons.Rounded.MusicNote,
                                isSelected = selectedRoute == SettingsRoutes.Playback,
                                onClick = { selectedRoute = SettingsRoutes.Playback }
                            ),
                            SettingsItem(
                                title = context.resources.getString(R.string.music_scan),
                                supportingText = context.resources.getString(R.string.music_scan_supporting_text),
                                icon = Icons.Rounded.Radar,
                                isSelected = selectedRoute == SettingsRoutes.MusicScan,
                                onClick = { selectedRoute = SettingsRoutes.MusicScan }
                            ),
                            SettingsItem(
                                title = context.resources.getString(R.string.tabs),
                                supportingText = context.resources.getString(R.string.tabs_supporting_text),
                                icon = Icons.Rounded.TableChart,
                                isSelected = selectedRoute == SettingsRoutes.Tabs,
                                onClick = { selectedRoute = SettingsRoutes.Tabs }
                            ),
                            SettingsItem(
                                title = context.resources.getString(R.string.playlists),
                                supportingText = context.resources.getString(R.string.playlists_supporting_text),
                                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                isSelected = selectedRoute == SettingsRoutes.Playlists,
                                onClick = { selectedRoute = SettingsRoutes.Playlists }
                            ),
                            SettingsItem(
                                title = context.resources.getString(R.string.theme),
                                supportingText = context.resources.getString(R.string.theme_supporting_text),
                                icon = Icons.Rounded.ColorLens,
                                isSelected = selectedRoute == SettingsRoutes.Theme,
                                onClick = { selectedRoute = SettingsRoutes.Theme }
                            ),
                            SettingsItem(
                                title = context.resources.getString(R.string.lyrics),
                                supportingText = context.resources.getString(R.string.lyrics_supporting_text),
                                icon = Icons.Rounded.Lyrics,
                                isSelected = selectedRoute == SettingsRoutes.Lyrics,
                                onClick = { selectedRoute = SettingsRoutes.Lyrics }
                            )
                        )

                        SettingsGroup(items = settings)

                        SettingsGroup(
                            items = listOf(
                                SettingsItem(
                                    title = context.resources.getString(R.string.about_app),
                                    supportingText = context.resources.getString(R.string.about_explain),
                                    icon = Icons.Rounded.Info,
                                    isSelected = selectedRoute == SettingsRoutes.About,
                                    onClick = { selectedRoute = SettingsRoutes.About }
                                )
                            )
                        )
                    }

                    VerticalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    Box(
                        modifier = Modifier
                            .weight(0.64f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        AnimatedContent(
                            targetState = selectedRoute,
                            transitionSpec = {
                                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                            },
                            label = "tablet-settings-detail",
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxSize()
                        ) { route ->
                            when (route) {
                                SettingsRoutes.Playback -> PlaybackSettings(
                                    settings = state.settings,
                                    equalizerController = state.equalizerController,
                                    onBackClick = onCloseClick,
                                    modifier = Modifier.fillMaxSize()
                                )

                                SettingsRoutes.MusicScan -> MusicScanSettings(
                                    settings = state.settings,
                                    musicScanner = state.musicScanner,
                                    foldersWithAudio = state.foldersWithAudio,
                                    onFolderPick = onFolderPick,
                                    onScanFoldersClick = onScanFoldersClick,
                                    onBackClick = onCloseClick,
                                    modifier = Modifier.fillMaxSize()
                                )

                                SettingsRoutes.Tabs -> TabsSettings(
                                    settings = state.settings,
                                    onBackClick = onCloseClick,
                                    modifier = Modifier.fillMaxSize()
                                )

                                SettingsRoutes.Playlists -> PlaylistsSettings(
                                    settings = state.settings,
                                    onPlaylistPick = onPlaylistPick,
                                    onBackClick = onCloseClick,
                                    modifier = Modifier.fillMaxSize()
                                )

                                SettingsRoutes.Theme -> ThemeSettings(
                                    settings = state.settings,
                                    onBackClick = onCloseClick,
                                    dominantColorState = dominantColorState,
                                    modifier = Modifier.fillMaxSize()
                                )

                                SettingsRoutes.Lyrics -> LyricsSettings(
                                    settings = state.settings,
                                    onBackClick = onCloseClick,
                                    modifier = Modifier.fillMaxSize()
                                )

                                SettingsRoutes.About -> AboutPage(
                                    onBackClick = onCloseClick,
                                    modifier = Modifier.fillMaxSize()
                                )

                                SettingsRoutes.Main -> Unit
                            }
                        }
                    }
                }
            } else {
                val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = SettingsRoutes.Main,
                enterTransition = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                            slideInHorizontally(initialOffsetX = { it / 5 })
                },
                exitTransition = {
                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                            slideOutHorizontally(targetOffsetX = { -it / 5 })
                },
                popEnterTransition = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                            slideInHorizontally(initialOffsetX = { -it / 5 })
                },
                popExitTransition = {
                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                            slideOutHorizontally(targetOffsetX = { it / 5 })
                }
            ) {
                composable<SettingsRoutes.Main> {
                    val context = LocalContext.current
                    var collapseFraction by remember {
                        mutableFloatStateOf(0f)
                    }

                    ColumnWithCollapsibleTopBar(
                        topBarContent = {
                            IconButton(
                                onClick = onCloseClick,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBackIosNew,
                                    contentDescription = context.resources.getString(R.string.close_settings_sheet)
                                )
                            }

                            Text(
                                text = context.resources.getString(R.string.settings),
                                fontSize = lerp(
                                    MaterialTheme.typography.titleLarge.fontSize,
                                    MaterialTheme.typography.displaySmall.fontSize,
                                    collapseFraction
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 16.dp)
                            )
                        },
                        collapseFraction = {
                            collapseFraction = it
                        },
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        contentHorizontalAlignment = Alignment.CenterHorizontally,
                        contentVerticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                    ) {
                        val settings = remember {
                            listOf(
                                SettingsItem(
                                    title = context.resources.getString(R.string.playback),
                                    supportingText = context.resources.getString(R.string.playback_supporting_text),
                                    icon = Icons.Rounded.MusicNote,
                                    onClick = {
                                        navController.navigate(SettingsRoutes.Playback)
                                    }
                                ),
                                SettingsItem(
                                    title = context.resources.getString(R.string.music_scan),
                                    supportingText = context.resources.getString(R.string.music_scan_supporting_text),
                                    icon = Icons.Rounded.Radar,
                                    onClick = {
                                        navController.navigate(SettingsRoutes.MusicScan)
                                    }
                                ),
                                SettingsItem(
                                    title = context.resources.getString(R.string.tabs),
                                    supportingText = context.resources.getString(R.string.tabs_supporting_text),
                                    icon = Icons.Rounded.TableChart,
                                    onClick = {
                                        navController.navigate(SettingsRoutes.Tabs)
                                    }
                                ),
                                SettingsItem(
                                    title = context.resources.getString(R.string.playlists),
                                    supportingText = context.resources.getString(R.string.playlists_supporting_text),
                                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    onClick = {
                                        navController.navigate(SettingsRoutes.Playlists)
                                    }
                                ),
                                SettingsItem(
                                    title = context.resources.getString(R.string.theme),
                                    supportingText = context.resources.getString(R.string.theme_supporting_text),
                                    icon = Icons.Rounded.ColorLens,
                                    onClick = {
                                        navController.navigate(SettingsRoutes.Theme)
                                    }
                                ),
                                SettingsItem(
                                    title = context.resources.getString(R.string.lyrics),
                                    supportingText = context.resources.getString(R.string.lyrics_supporting_text),
                                    icon = Icons.Rounded.Lyrics,
                                    onClick = {
                                        navController.navigate(SettingsRoutes.Lyrics)
                                    }
                                )
                            )
                        }

                        SettingsGroup(
                            items = settings
                        )

                        SettingsGroup(
                            items = listOf(
                                SettingsItem(
                                    title = context.resources.getString(R.string.about_app),
                                    supportingText = context.resources.getString(R.string.about_explain),
                                    icon = Icons.Rounded.Info,
                                    onClick = {
                                        navController.navigate(SettingsRoutes.About)
                                    }
                                )
                            )
                        )
                    }
                }

                composable<SettingsRoutes.Playback> {
                    PlaybackSettings(
                        settings = state.settings,
                        equalizerController = state.equalizerController,
                        onBackClick = {
                            navController.navigateUp()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<SettingsRoutes.MusicScan> {
                    MusicScanSettings(
                        settings = state.settings,
                        musicScanner = state.musicScanner,
                        foldersWithAudio = state.foldersWithAudio,
                        onFolderPick = onFolderPick,
                        onScanFoldersClick = onScanFoldersClick,
                        onBackClick = {
                            navController.navigateUp()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<SettingsRoutes.Tabs> {
                    TabsSettings(
                        settings = state.settings,
                        onBackClick = {
                            navController.navigateUp()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<SettingsRoutes.Playlists> {
                    PlaylistsSettings(
                        settings = state.settings,
                        onPlaylistPick = onPlaylistPick,
                        onBackClick = {
                            navController.navigateUp()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<SettingsRoutes.Theme> {
                    ThemeSettings(
                        settings = state.settings,
                        onBackClick = {
                            navController.navigateUp()
                        },
                        dominantColorState = dominantColorState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<SettingsRoutes.Lyrics> {
                    LyricsSettings(
                        settings = state.settings,
                        onBackClick = {
                            navController.navigateUp()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                composable<SettingsRoutes.About> {
                    AboutPage(
                        onBackClick = {
                            navController.navigateUp()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            }
        }
    }
}

@Serializable
sealed interface SettingsRoutes {
    @Serializable
    data object Main : SettingsRoutes

    @Serializable
    data object Playback : SettingsRoutes

    @Serializable
    data object MusicScan : SettingsRoutes

    @Serializable
    data object Tabs : SettingsRoutes

    @Serializable
    data object Playlists : SettingsRoutes

    @Serializable
    data object Theme : SettingsRoutes

    @Serializable
    data object Lyrics : SettingsRoutes

    @Serializable
    data object About : SettingsRoutes
}