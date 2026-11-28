package com.simorgh.vpn.ui.profiles

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.simorgh.vpn.ImportBus
import com.simorgh.vpn.R
import com.simorgh.vpn.ServiceLocator
import com.simorgh.vpn.core.CoreBootstrap
import com.simorgh.vpn.core.VpnBus
import com.simorgh.vpn.core.VpnStatus
import com.simorgh.vpn.core.XrayConfigBuilder
import com.simorgh.vpn.core.XrayRunner
import com.simorgh.vpn.data.BuiltInSources
import com.simorgh.vpn.data.BuiltinConfigManager
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
    var showSourcePicker by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }

    // v0.2: QR import / smart clipboard / latency sorting
    var addPrefill by rememberSaveable { mutableStateOf("") }
    var subPrefill by rememberSaveable { mutableStateOf("") }
    var sortByLatency by rememberSaveable { mutableStateOf(false) }
    var clipboardCandidate by remember { mutableStateOf<String?>(null) }
    var ignoredClipboard by rememberSaveable { mutableStateOf("") }

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

    /**
     * v0.2: shared import path for QR results and clipboard banners —
     * link sets import directly, http(s) URLs prefill the subscription
     * dialog, anything else lands in the add-server dialog for review.
     */
    fun importExternalText(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val isLinkSet =
            Regex("(vless|vmess|trojan|ss)://", RegexOption.IGNORE_CASE).containsMatchIn(text)
        when {
            isLinkSet -> scope.launch {
                val n = ServiceLocator.profileRepo.addFromLinks(text)
                snackbar.showSnackbar(
                    context.getString(if (n > 0) R.string.imported_n else R.string.import_failed, n)
                )
            }
            text.startsWith("http://", true) || text.startsWith("https://", true) -> {
                subPrefill = text
                showSubDialog = true
            }
            else -> {
                addPrefill = text
                showAddDialog = true
            }
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { importExternalText(it) }
    }

    fun launchQr() {
        qrLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(context.getString(R.string.scan_qr))
                setBeepEnabled(false)
                setOrientationLocked(true)
            }
        )
    }

    // v0.2: smart clipboard banner (Derafsh pattern) — offer to import a VPN
    // link found in the clipboard when the screen opens; dismissed/ignored
    // content is remembered so it doesn't nag again until it changes.
    LaunchedEffect(Unit) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return@LaunchedEffect
            if (clip.itemCount == 0) return@LaunchedEffect
            val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
            val looksLikeConfig =
                Regex("(vless|vmess|trojan|ss)://", RegexOption.IGNORE_CASE).containsMatchIn(text)
            if (looksLikeConfig && text != ignoredClipboard) clipboardCandidate = text
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

    val displayProfiles = remember(profiles, sortByLatency) {
        if (sortByLatency) profiles.sortedWith(
            compareBy({ it.lastLatencyMs < 0 }, { if (it.lastLatencyMs < 0) 0L else it.lastLatencyMs })
        ) else profiles
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

            // v0.2: smart clipboard import banner
            clipboardCandidate?.let { clipText ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.ContentPaste,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    stringResource(R.string.clipboard_found_title),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    stringResource(R.string.clipboard_found_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                ignoredClipboard = clipText
                                clipboardCandidate = null
                                importExternalText(clipText)
                            }) { Text(stringResource(R.string.clipboard_import)) }
                            TextButton(onClick = {
                                ignoredClipboard = clipText
                                clipboardCandidate = null
                            }) { Text(stringResource(R.string.ignore)) }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showSubDialog = true }) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.subscription))
                }
                OutlinedButton(onClick = { launchQr() }) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_qr))
                }
            }

            if (profiles.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    // v0.2: sort by latency toggle
                    OutlinedButton(onClick = { sortByLatency = !sortByLatency }) {
                        Icon(Icons.Outlined.Sort, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(
                                if (sortByLatency) R.string.sort_latency else R.string.sort_default
                            )
                        )
                    }
                }
            }

            if (subscriptions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                subscriptions.forEach { sub ->
                    if (sub.id == BuiltInSources.BUILTIN_SUB_ID) {
                        BuiltinSubscriptionRow(
                            sub = sub,
                            serverCount = profiles.count { it.subscriptionId == sub.id },
                            activeSourceName = BuiltinConfigManager.sourceName(
                                context, settings.builtinSourceIndex
                            ),
                            switching = switching,
                            onRefresh = {
                                scope.launch {
                                    runCatching { ServiceLocator.subscriptionRepo.refresh(sub) }
                                        .onSuccess { snackbar.showSnackbar(context.getString(R.string.sub_updated_n, it)) }
                                        .onFailure { snackbar.showSnackbar(context.getString(R.string.builtin_refresh_failed)) }
                                }
                            },
                            onSwitchSource = { showSourcePicker = true }
                        )
                    } else {
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
            }

            Spacer(Modifier.height(8.dp))

            if (profiles.isEmpty()) {
                EmptyHint(stringResource(R.string.no_servers_yet))
            } else {
                displayProfiles.forEach { p ->
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
        var text by remember { mutableStateOf(addPrefill) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false; addPrefill = "" },
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
                            addPrefill = ""
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
                TextButton(onClick = { showAddDialog = false; addPrefill = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSubDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf(subPrefill) }
        AlertDialog(
            onDismissRequest = { showSubDialog = false; subPrefill = "" },
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
                                val sub = ServiceLocator.profileRepo.addSubscription(name, url)
                                showSubDialog = false
                                subPrefill = ""
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
                TextButton(onClick = { showSubDialog = false; subPrefill = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // -------------------------------------------------- built-in source picker

    if (showSourcePicker) {
        AlertDialog(
            onDismissRequest = { if (!switching) showSourcePicker = false },
            title = { Text(stringResource(R.string.pick_source)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.pick_source_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        repeat(BuiltInSources.count()) { i ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !switching && i != settings.builtinSourceIndex) {
                                        switching = true
                                        scope.launch {
                                            runCatching {
                                                BuiltinConfigManager.switchSource(context, i)
                                            }.onSuccess { n ->
                                                snackbar.showSnackbar(
                                                    context.getString(R.string.source_switched_n, n)
                                                )
                                            }.onFailure {
                                                snackbar.showSnackbar(
                                                    context.getString(R.string.switch_failed)
                                                )
                                            }
                                            switching = false
                                            showSourcePicker = false
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = i == settings.builtinSourceIndex,
                                    onClick = null,
                                    enabled = !switching
                                )
                                Text(BuiltinConfigManager.sourceName(context, i))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (switching) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { showSourcePicker = false }) {
                        Text(stringResource(R.string.cancel))
                    }
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

/**
 * The built-in free-configs subscription row (Derafsh pattern): neutral title,
 * active source label, no URL and no delete — plus a source-switch action.
 */
@Composable
private fun BuiltinSubscriptionRow(
    sub: Subscription,
    serverCount: Int,
    activeSourceName: String,
    switching: Boolean,
    onRefresh: () -> Unit,
    onSwitchSource: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.builtin_sub_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "$activeSourceName · ${stringResource(R.string.servers_count, serverCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.builtin_sub_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (switching) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.refresh)
                )
            }
            IconButton(onClick = onSwitchSource) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = stringResource(R.string.change_source)
                )
            }
        }
    }
}

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
