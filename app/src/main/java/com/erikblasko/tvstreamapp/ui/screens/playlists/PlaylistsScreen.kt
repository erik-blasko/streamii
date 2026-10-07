package com.erikblasko.tvstreamapp.ui.screens.playlists

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.erikblasko.tvstreamapp.R
import com.erikblasko.tvstreamapp.data.model.Playlist
import com.erikblasko.tvstreamapp.data.model.defaultPlaylists
import com.erikblasko.tvstreamapp.ui.components.BackTopBar
import com.erikblasko.tvstreamapp.ui.screens.settings.SettingsViewModel

@Composable
fun PlaylistsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPlaylistsChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledIds by viewModel.enabledPlaylistIds.collectAsState()
    val customUrls by viewModel.customPlaylistUrls.collectAsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    // Reload channels once when leaving the screen, not on every toggle
    val initial = remember { enabledIds to customUrls }
    val current by rememberUpdatedState(enabledIds to customUrls)
    val onChanged by rememberUpdatedState(onPlaylistsChanged)
    DisposableEffect(Unit) {
        onDispose { if (current != initial) onChanged() }
    }

    val groups = remember { defaultPlaylists.groupBy { it.provider } }

    Column(modifier = modifier.fillMaxSize()) {
        BackTopBar(title = stringResource(R.string.settings_playlists), onBack = onBack) {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.playlists_add))
            }
        }
        Text(
            text = stringResource(R.string.playlists_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            groups.forEach { (provider, playlists) ->
                ProviderCard(
                    provider = provider,
                    playlists = playlists,
                    enabledIds = enabledIds,
                    onTogglePlaylist = viewModel::togglePlaylist,
                    onToggleGroup = { enabled -> viewModel.setPlaylistsEnabled(playlists.map { it.id }, enabled) }
                )
            }
            CustomPlaylistsCard(
                urls = customUrls,
                onAdd = { showAddDialog = true },
                onRemove = viewModel::removeCustomUrl
            )
        }
    }

    if (showAddDialog) {
        AddPlaylistDialog(
            onAdd = { url ->
                viewModel.addCustomUrl(url)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: String,
    playlists: List<Playlist>,
    enabledIds: Set<String>,
    onTogglePlaylist: (String) -> Unit,
    onToggleGroup: (Boolean) -> Unit
) {
    val title = if (provider == "FAST") stringResource(R.string.playlists_fast) else provider
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (playlists.size == 1) {
            val playlist = playlists.first()
            SwitchHeader(
                title = title,
                summary = null,
                checked = playlist.id in enabledIds,
                onCheckedChange = { onTogglePlaylist(playlist.id) }
            )
            return@Column
        }

        var expanded by rememberSaveable(provider) { mutableStateOf(false) }
        val enabledCount = playlists.count { it.id in enabledIds }
        val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable { expanded = !expanded }
                .padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.playlists_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(8.dp)
                    .rotate(rotation)
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(R.string.playlists_group_summary, enabledCount, playlists.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // On when the whole group is on; switching it on enables the rest of the group
            Switch(checked = enabledCount == playlists.size, onCheckedChange = onToggleGroup)
        }
        if (expanded) {
            playlists.forEach { playlist ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .toggleable(
                            value = playlist.id in enabledIds,
                            role = Role.Checkbox,
                            onValueChange = { onTogglePlaylist(playlist.id) }
                        )
                        .padding(start = 56.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playlist.nameRes?.let { stringResource(it) } ?: playlist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(checked = playlist.id in enabledIds, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
private fun SwitchHeader(title: String, summary: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CustomPlaylistsCard(urls: Set<String>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onAdd)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.playlists_custom),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (urls.isEmpty()) {
                    Text(
                        stringResource(R.string.playlists_custom_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.playlists_add),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        urls.forEach { url ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onRemove(url) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.playlists_remove),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPlaylistDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val trimmed = url.trim()
    val valid = trimmed.startsWith("http://") || trimmed.startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlists_add)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.playlists_add_hint)) },
                placeholder = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(trimmed) }, enabled = valid) {
                Text(stringResource(R.string.playlists_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
