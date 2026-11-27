package com.simorgh.vpn.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.simorgh.vpn.R
import com.simorgh.vpn.ServiceLocator
import com.simorgh.vpn.data.AppList
import com.simorgh.vpn.data.InstalledApp
import com.simorgh.vpn.data.PerAppMode
import com.simorgh.vpn.data.SettingsStore
import kotlinx.coroutines.launch

/**
 * Per-app proxy (split tunneling) picker — Derafsh/V2rayNG PerAppProxy pattern.
 * Mode + selected packages are persisted immediately.
 */
@Composable
fun PerAppProxyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settings.collectAsState()

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = AppList.load(context)
        loading = false
    }

    val selected = settings.perAppPackages

    fun toggle(pkg: String) {
        val ns = if (pkg in selected) selected - pkg else selected + pkg
        scope.launch { SettingsStore.setPerAppPackages(context, ns) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                // -------------------------------------------------------- header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.per_app_proxy),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (selected.isEmpty()) stringResource(R.string.per_app_none)
                            else stringResource(R.string.per_app_selected_n, selected.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selected.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch { SettingsStore.setPerAppPackages(context, emptySet()) }
                            }
                        ) {
                            Icon(Icons.Outlined.ClearAll, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.clear_selection))
                        }
                    }
                }

                // -------------------------------------------------------- modes
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ModeRow(
                        title = stringResource(R.string.per_app_all),
                        desc = "",
                        selected = settings.perAppMode == PerAppMode.OFF
                    ) { scope.launch { SettingsStore.setPerAppMode(context, PerAppMode.OFF) } }
                    ModeRow(
                        title = stringResource(R.string.per_app_allow),
                        desc = stringResource(R.string.per_app_mode_allow),
                        selected = settings.perAppMode == PerAppMode.ALLOW
                    ) { scope.launch { SettingsStore.setPerAppMode(context, PerAppMode.ALLOW) } }
                    ModeRow(
                        title = stringResource(R.string.per_app_deny),
                        desc = stringResource(R.string.per_app_mode_deny),
                        selected = settings.perAppMode == PerAppMode.DENY
                    ) { scope.launch { SettingsStore.setPerAppMode(context, PerAppMode.DENY) } }
                    Text(
                        stringResource(R.string.per_app_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // -------------------------------------------------------- search
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.per_app_search)) },
                    singleLine = true
                )

                // -------------------------------------------------------- list
                if (loading) {
                    Text(
                        stringResource(R.string.loading_apps),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val filtered = remember(apps, query) {
                        if (query.isBlank()) apps
                        else apps.filter {
                            it.label.contains(query, true) || it.packageName.contains(query, true)
                        }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                checked = app.packageName in selected,
                                enabled = settings.perAppMode != PerAppMode.OFF,
                                onToggle = { toggle(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeRow(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (desc.isNotBlank()) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.icon, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

@Composable
private fun AppIcon(icon: Drawable?, modifier: Modifier = Modifier) {
    val bmp = remember(icon) { icon?.toBitmap(72, 72)?.asImageBitmap() }
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(
            Icons.Outlined.Android,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
