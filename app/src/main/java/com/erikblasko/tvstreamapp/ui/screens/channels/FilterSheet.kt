package com.erikblasko.tvstreamapp.ui.screens.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erikblasko.tvstreamapp.R
import com.erikblasko.tvstreamapp.ui.components.appLocale
import com.erikblasko.tvstreamapp.ui.components.categoryName
import com.erikblasko.tvstreamapp.ui.components.countryName
import com.erikblasko.tvstreamapp.util.countryFlag
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/** How many of the largest countries / categories are shown before "More". */
private const val COLLAPSED_COUNT = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    uiState: ChannelsUiState,
    section: ChannelsViewModel.Section,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.filters),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = section::clearFilters,
                    enabled = uiState.activeFilterCount > 0
                ) {
                    Text(stringResource(R.string.filters_clear_all))
                }
            }

            if (uiState.countries.size > 1) {
                val locale = appLocale()
                val names = uiState.countries.associate { (code, _) -> code to countryName(code) }
                ChipSection(
                    title = stringResource(R.string.filter_country),
                    // The phone's own country first, then the countries with most channels
                    keys = uiState.countries.map { it.first }
                        .sortedByDescending { it == Locale.getDefault().country },
                    selected = uiState.selectedCountries,
                    label = { code -> listOf(countryFlag(code), names.getValue(code)).filter { it.isNotEmpty() }.joinToString(" ") },
                    sortKey = { code -> names.getValue(code) },
                    collator = remember(locale) { Collator.getInstance(locale) },
                    onToggle = section::toggleCountry
                )
            }

            if (uiState.categories.size > 1) {
                val locale = appLocale()
                val names = uiState.categories.associate { (key, _) -> key to categoryName(key) }
                ChipSection(
                    title = stringResource(R.string.filter_category),
                    keys = uiState.categories.map { it.first },
                    selected = uiState.selectedCategories,
                    label = { key -> names.getValue(key) },
                    sortKey = { key -> names.getValue(key) },
                    collator = remember(locale) { Collator.getInstance(locale) },
                    onToggle = section::toggleCategory
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(stringResource(R.string.filter_availability))
                val options = listOf(
                    StatusFilter.ALL to R.string.filter_status_all,
                    StatusFilter.ONLINE to R.string.filter_status_online,
                    StatusFilter.OFFLINE to R.string.filter_status_offline
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (status, labelRes) ->
                        SegmentedButton(
                            selected = uiState.selectedStatus == status,
                            onClick = { section.onStatusFilterChanged(status) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size)
                        ) {
                            Text(stringResource(labelRes))
                        }
                    }
                }
            }

            Button(
                onClick = close,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = pluralStringResource(
                        SectionTexts.of(section.kind).showCount,
                        uiState.channels.size,
                        uiState.channels.size
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Chips for the largest groups (plus anything selected); "More" shows all of them
 * in alphabetical order.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(
    title: String,
    keys: List<String>,
    selected: Set<String>,
    label: (String) -> String,
    sortKey: (String) -> String,
    collator: Collator,
    onToggle: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visible = if (expanded || keys.size <= COLLAPSED_COUNT) {
        keys.sortedWith(compareBy(collator) { sortKey(it) })
    } else {
        val top = keys.take(COLLAPSED_COUNT)
        top + selected.filter { it !in top }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            visible.forEach { key ->
                FilterChip(
                    selected = key in selected,
                    onClick = { onToggle(key) },
                    label = { Text(label(key)) }
                )
            }
            if (keys.size > COLLAPSED_COUNT) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) stringResource(R.string.filter_less)
                        else stringResource(R.string.filter_more, keys.size - visible.size)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
