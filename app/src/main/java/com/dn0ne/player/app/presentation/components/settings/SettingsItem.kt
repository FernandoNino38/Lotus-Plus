package com.dn0ne.player.app.presentation.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed

data class SettingsItem(
    val title: String,
    val supportingText: String,
    val icon: ImageVector,
    val isSelected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun SettingsItem(
    item: SettingsItem,
    modifier: Modifier = Modifier
) {
    val containerColor = if (item.isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val titleColor = if (item.isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = if (item.isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .background(color = containerColor)
            .clickable {
                item.onClick()
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            tint = titleColor,
            contentDescription = null
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor
            )

            Text(
                text = item.supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = supportingColor
            )
        }
    }
}


@Composable
fun SettingsGroup(
    items: List<SettingsItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.fastForEachIndexed { index, item ->
            SettingsItem(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        when {
                            items.lastIndex == 0 -> {
                                RoundedCornerShape(28.dp)
                            }

                            index == 0 -> {
                                RoundedCornerShape(
                                    topStart = 28.dp,
                                    topEnd = 28.dp,
                                    bottomStart = 4.dp,
                                    bottomEnd = 4.dp
                                )
                            }

                            index == items.lastIndex -> {
                                RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp,
                                    bottomStart = 28.dp,
                                    bottomEnd = 28.dp
                                )
                            }

                            else -> {
                                RoundedCornerShape(4.dp)
                            }
                        }
                    )
            )
        }
    }
}
