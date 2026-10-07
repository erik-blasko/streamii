package com.erikblasko.tvstreamapp.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erikblasko.tvstreamapp.R

/** Top-level screen header: menu button and title. */
@Composable
fun MenuTopBar(title: String, onOpenDrawer: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopBarRow {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu_open))
        }
        TopBarTitle(title, Modifier.weight(1f))
        actions()
    }
}

/** Sub-screen header: back button and title. */
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopBarRow {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        TopBarTitle(title, Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun TopBarRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun TopBarTitle(title: String, modifier: Modifier) {
    Spacer(Modifier.width(4.dp))
    Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}
