package com.dn0ne.player.app.presentation.components.tablet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dn0ne.player.R
import com.dn0ne.player.app.presentation.components.topbar.Tab

@Composable
fun TabletNavigationRail(
    tabs: List<Tab>,
    selectedTab: Tab,
    isInSubRoute: Boolean,
    isSearchActive: Boolean,
    replaceSearchWithFilter: Boolean,
    onSearchClick: () -> Unit,
    onTabSelect: (Tab) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isExpanded = screenWidthDp >= 900

    Column(
        modifier = modifier
            .width(if (isExpanded) 212.dp else 88.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Start
                )
            )
            .padding(horizontal = if (isExpanded) 12.dp else 8.dp, vertical = 16.dp),
        horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
    ) {
        // App Logo & Name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isExpanded) 8.dp else 0.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = context.resources.getString(R.string.app_name),
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            scaleX = 1.6f
                            scaleY = 1.6f
                        }
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = context.resources.getString(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Button
        val searchIcon = if (replaceSearchWithFilter && selectedTab == Tab.Tracks) {
            Icons.Rounded.FilterList
        } else {
            Icons.Rounded.Search
        }
        val searchLabel = if (replaceSearchWithFilter && selectedTab == Tab.Tracks) {
            context.resources.getString(R.string.filter)
        } else {
            context.resources.getString(R.string.search)
        }

        TabletNavItem(
            icon = searchIcon,
            label = searchLabel,
            selected = isSearchActive,
            isExpanded = isExpanded,
            onClick = onSearchClick
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Navigation Tabs (Tracks, Playlists, Albums, Artists, Genres, Folders)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
        ) {
            tabs.forEach { tab ->
                TabletNavItem(
                    icon = tab.icon(),
                    label = context.resources.getString(tab.titleResId),
                    selected = selectedTab == tab && !isInSubRoute,
                    isExpanded = isExpanded,
                    onClick = { onTabSelect(tab) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Settings Button
        TabletNavItem(
            icon = Icons.Rounded.Settings,
            label = context.resources.getString(R.string.settings),
            selected = false,
            isExpanded = isExpanded,
            onClick = onSettingsClick
        )
    }
}

@Composable
private fun TabletNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    if (isExpanded) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(ShapeDefaults.Large)
                .background(containerColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(ShapeDefaults.Medium)
                .background(containerColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun Tab.icon(): ImageVector {
    return when (this) {
        Tab.Tracks -> Icons.Rounded.MusicNote
        Tab.Playlists -> Icons.AutoMirrored.Rounded.QueueMusic
        Tab.Albums -> Icons.Rounded.Album
        Tab.Artists -> Icons.Rounded.Person
        Tab.Genres -> Icons.Rounded.Category
        Tab.Folders -> Icons.Rounded.Folder
    }
}
