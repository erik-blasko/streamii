package com.erikblasko.tvstreamapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erikblasko.tvstreamapp.R

@Composable
fun AppDrawerContent(
    currentRoute: String?,
    channelCount: Int,
    stationCount: Int,
    favoriteCount: Int,
    onNavigate: (String) -> Unit,
    onRecheck: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        DrawerItem(Icons.Default.LiveTv, stringResource(R.string.nav_channels), currentRoute == Routes.CHANNELS,
            badge = channelCount.takeIf { it > 0 }?.toString()) { onNavigate(Routes.CHANNELS) }
        DrawerItem(Icons.Default.Radio, stringResource(R.string.nav_radio), currentRoute == Routes.RADIO,
            badge = stationCount.takeIf { it > 0 }?.toString()) { onNavigate(Routes.RADIO) }
        DrawerItem(Icons.Default.Star, stringResource(R.string.nav_favorites), currentRoute == Routes.FAVORITES,
            badge = favoriteCount.takeIf { it > 0 }?.toString()) { onNavigate(Routes.FAVORITES) }
        DrawerItem(Icons.Default.History, stringResource(R.string.nav_recent), currentRoute == Routes.RECENT) {
            onNavigate(Routes.RECENT)
        }
        DrawerItem(Icons.Default.Settings, stringResource(R.string.nav_settings), currentRoute == Routes.SETTINGS) {
            onNavigate(Routes.SETTINGS)
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
        )

        DrawerItem(Icons.Default.Refresh, stringResource(R.string.drawer_recheck), selected = false, onClick = onRecheck)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        badge = badge?.let { { Text(it, style = MaterialTheme.typography.labelMedium) } },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}
