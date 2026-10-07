package com.erikblasko.tvstreamapp.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erikblasko.tvstreamapp.R
import com.erikblasko.tvstreamapp.ui.components.ChoiceDialog
import com.erikblasko.tvstreamapp.ui.components.MenuTopBar
import com.erikblasko.tvstreamapp.ui.components.SettingsDivider
import com.erikblasko.tvstreamapp.ui.components.SettingsInfoRow
import com.erikblasko.tvstreamapp.ui.components.SettingsLinkRow
import com.erikblasko.tvstreamapp.ui.components.SettingsSection
import com.erikblasko.tvstreamapp.ui.components.SettingsSwitchRow
import com.erikblasko.tvstreamapp.util.AppLocale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onLanguageChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val enabledIds by viewModel.enabledPlaylistIds.collectAsState()
    val customUrls by viewModel.customPlaylistUrls.collectAsState()
    val checkStreams by viewModel.checkStreamsOnStart.collectAsState()
    val hideOffline by viewModel.hideOffline.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val language = remember { AppLocale.get(context) }

    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }

    val languageOptions = listOf(
        AppLocale.SYSTEM to stringResource(R.string.language_system),
        "sk" to stringResource(R.string.language_sk),
        "en" to stringResource(R.string.language_en)
    )
    val themeOptions = listOf(
        "system" to stringResource(R.string.theme_system),
        "light" to stringResource(R.string.theme_light),
        "dark" to stringResource(R.string.theme_dark)
    )
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    val enabledCount = enabledIds.size + customUrls.size

    Column(modifier = modifier.fillMaxSize()) {
        MenuTopBar(title = stringResource(R.string.nav_settings), onOpenDrawer = onOpenDrawer)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsSection(stringResource(R.string.settings_section_appearance)) {
                SettingsLinkRow(
                    label = stringResource(R.string.settings_language),
                    hint = stringResource(R.string.settings_language_hint),
                    value = languageOptions.first { it.first == language }.second,
                    onClick = { showLanguageDialog = true }
                )
                SettingsDivider()
                SettingsLinkRow(
                    label = stringResource(R.string.settings_theme),
                    value = themeOptions.firstOrNull { it.first == themeMode }?.second,
                    onClick = { showThemeDialog = true }
                )
            }

            SettingsSection(stringResource(R.string.settings_section_channels)) {
                SettingsLinkRow(
                    label = stringResource(R.string.settings_playlists),
                    hint = pluralStringResource(R.plurals.settings_playlists_summary, enabledCount, enabledCount),
                    value = null,
                    onClick = onOpenPlaylists
                )
                SettingsDivider()
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_check_streams),
                    hint = stringResource(R.string.settings_check_streams_hint),
                    checked = checkStreams,
                    onCheckedChange = viewModel::setCheckStreamsOnStart
                )
                SettingsDivider()
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_hide_offline),
                    hint = stringResource(R.string.settings_hide_offline_hint),
                    checked = hideOffline,
                    onCheckedChange = viewModel::setHideOffline
                )
            }

            SettingsSection(stringResource(R.string.settings_section_about)) {
                SettingsInfoRow(
                    label = stringResource(R.string.app_name),
                    hint = stringResource(R.string.settings_version, version)
                )
            }
        }
    }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = languageOptions,
            selected = language,
            onSelect = {
                showLanguageDialog = false
                if (it != language) {
                    AppLocale.set(context, it)
                    onLanguageChanged()
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = themeOptions,
            selected = themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}
