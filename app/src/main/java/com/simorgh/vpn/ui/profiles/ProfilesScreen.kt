package com.simorgh.vpn.ui.profiles

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simorgh.vpn.ImportBus
import com.simorgh.vpn.R
import com.simorgh.vpn.ServiceLocator
import com.simorgh.vpn.core.CoreBootstrap
import com.simorgh.vpn.core.VpnBus
import com.simorgh.vpn.core.VpnStatus
import com.simorgh.vpn.core.XrayConfigBuilder
import com.simorgh.vpn.core.XrayRunner
import com.simorgh.vpn.data.Profile
import com.simorgh.vpn.data.SettingsStore
import com.simorgh.vpn.data.ShareLinkParser
import com.simorgh.vpn.data.Subscription
import com.simorgh.vpn.ui.components.EmptyHint
import com.simorgh.vpn.ui.components.LatencyText
import com.simorgh.vpn.ui.components.SelectionDot
import com.simorgh.vpn.vpn.SimorghVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfilesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by ServiceLocator.profileRepo.profiles.collectAsState()
    val subscriptions by ServiceLocator.profileRepo.subscriptions.collectAsState()
    val settings by ServiceLocator.settings.collectAsState()
    val vpnState by VpnBus.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var showSubDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }
    var testingAll by remember { mutableStateOf(false) }

    val connected = vpnState.status == VpnStatus.CONNECTED || vpnState.status == VpnStatus.CONNECTING

    fun testOne(p: Profile) {
        scope.launch(Dispatchers.IO) {
            val ms = runCatching {
                CoreBootstrap.ensureInit(context)
                val cfg = XrayConfigBuilder.buildTestConfig(p, ServiceLocator.settings.value)
                XrayRunner.measureDelay(cfg)
            }.getOrDefault(-1L)
            ServiceLocator.profileRepo.updateLatency(p.id, ms)
        }
    }

    // consume deep-link imports (vless:// etc. opened from browser/IM)
    LaunchedEffect(Unit) {
        while (true) {
            val links = ImportBus.pollAll()
            if (links.isNotEmpty()) {
                val n = ServiceLocator.profileRepo.addFromLinks(links.joinToString("\n"))
                snackbar.showSnackbar(
                    context.getString(if (n > 0) R.string.imported_n else R.string.import_failed, n)
                )
            }
            delay(400)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_server)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(R.string.nav_profiles),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showSubDialog = true }) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.subscription))
                }
                if (profiles.isNotEmpty()) {
                    OutlinedButton(
                        enabled = !connected && !testingAll,
                        onClick = {
                            testingAll = true
                            scope.launch(Dispatchers.IO) {
                                profiles.forEach { p ->
                                    runCatching {
                                        CoreBootstrap.ensureInit(context)
                                        val cfg =
                                            XrayConfigBuilder.buildTestConfig(p, ServiceLocator.settings.value)
                                        val ms = XrayRunner.measureDelay(cfg)
                                        ServiceLocator.profileRepo.updateLatency(p.id, ms)
                                    }
                                    delay(250)
                                }
                                testingAll = false
                            }
                        }
                    ) {
                        if (testingAll) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Bolt, contentDescription = null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.test_all))
                    }
                }
            }

            if (subscriptions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                subscriptions.forEach { sub ->
                    SubscriptionRow(
                        sub = sub,
                        onRefresh = {
                            scope.launch {
                                runCatching {
                                    val n = ServiceLocator.subscriptionRepo.refresh(sub)
                                    snackbar.showSnackbar(context.getString(R.string.sub_updated_n, n))
                                }.onFailure {
                                    snackbar.showSnackbar(context.getString(R.string.sub_failed))
                                }
                            }
                        },
                        onDelete = {
                            scope.launch { ServiceLocator.profileRepo.removeSubscription(sub.id) }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (profiles.isEmpty()) {
                EmptyHint(stringResource(R.string.no_servers_yet))
            } else {
                profiles.forEach { p ->
                    val selected = p.id == settings.activeProfileId || p.id == vpnState.profileId
                    ProfileRow(
                        profile = p,
                        selected = selected,
                        enabled = !connected,
                        onSelect = {
                            scope.launch {
                                SettingsStore.setActiveProfile(context, p.id)
                                if (vpnState.status == VpnStatus.CONNECTED) {
                                    SimorghVpnService.stop(context)
                                    delay(700)
                                    SimorghVpnService.start(context, p.id)
                                }
                            }
                        },
                        onTest = { testOne(p) },
                        onDelete = { deleteTarget = p },
                        onShare = { shareLink(context, ShareLinkParser.toLink(p)) }
                    )
                }
            }

            Spacer(Modifier.height(96.dp))
        }
    }

    // ------------------------------------------------------------------ dialogs

    if (showAddDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_server)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.add_link_hint)) },
                    minLines = 3,
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val n = ServiceLocator.profileRepo.addFromLinks(text)
                            showAddDialog = false
                            snackbar.showSnackbar(
                                context.getString(
                                    if (n > 0) R.string.imported_n else R.string.import_failed,
                                    n
                                )
                            )
                        }
                    }
                ) { Text(stringResource(R.string.import_links)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSubDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSubDialog = false },
            title = { Text(stringResource(R.string.subscription)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.sub_name_hint)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.sub_url_hint)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (url.isNotBlank()) {
                            scope.launch {
                                val sub = ServiceLocator.subscriptionRepo.add(name, url)
                                showSubDialog = false
                                runCatching {
                                    val n = ServiceLocator.subscriptionRepo.refresh(sub)
                                    snackbar.showSnackbar(context.getString(R.string.sub_updated_n, n))
                                }.onFailure {
                                    snackbar.showSnackbar(context.getString(R.string.sub_failed))
                                }
                            }
                        }
                    }
                ) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { showSubDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_server_q)) },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            ServiceLocator.profileRepo.delete(target.id)
                            deleteTarget = null
                            snackbar.showSnackbar(context.getString(R.string.server_deleted))
                        }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ---------------------------------------------------------------------- rows

@Composable
private fun SubscriptionRow(sub: Subscription, onRefresh: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(sub.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    sub.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.refresh)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionDot(selected = selected)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${protocolLabel(profile.protocol)} · ${profile.host}:${profile.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LatencyText(profile.lastLatencyMs)
            IconButton(onClick = onTest, enabled = enabled) {
                Icon(Icons.Outlined.Bolt, contentDescription = stringResource(R.string.test))
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun protocolLabel(protocol: String) = when (protocol) {
    "vless" -> "VLESS"
    "vmess" -> "VMESS"
    "trojan" -> "TROJAN"
    "shadowsocks" -> "SS"
    else -> protocol
}

private fun shareLink(context: android.content.Context, link: String) {
    if (link.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }
    context.startActivity(Intent.createChooser(send, null))
}
