package com.simorgh.vpn.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.simorgh.vpn.BuildConfig
import com.simorgh.vpn.R
import com.simorgh.vpn.ServiceLocator
import com.simorgh.vpn.core.CoreBootstrap
import com.simorgh.vpn.core.anticensor.TlsFragmentProfile
import com.simorgh.vpn.data.BackupManager
import com.simorgh.vpn.data.RoutingMode
import com.simorgh.vpn.data.SettingsStore
import com.simorgh.vpn.data.TunnelMode
import com.simorgh.vpn.data.UpdateChecker
import com.simorgh.vpn.ui.components.SectionCard
import com.simorgh.vpn.util.ApkSharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings by ServiceLocator.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showPerApp by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var updateFailed by remember { mutableStateOf(false) }

    // -------------------------------------------------- backup (SAF) launchers

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val os = context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("cannot open output stream")
                os.use { it.write(BackupManager.export().toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            snackbar.showSnackbar(
                context.getString(if (ok) R.string.backup_saved else R.string.backup_failed)
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                } ?: error("cannot read file")
                BackupManager.restore(context, text)
            }.onSuccess { r ->
                snackbar.showSnackbar(
                    context.getString(R.string.restore_done, r.profilesAdded, r.subscriptionsAdded)
                )
            }.onFailure {
                snackbar.showSnackbar(context.getString(R.string.restore_failed))
            }
        }
    }

    fun checkUpdate() {
        checkingUpdate = true
        updateFailed = false
        updateInfo = null
        scope.launch {
            runCatching { UpdateChecker.check(BuildConfig.VERSION_NAME) }
                .onSuccess { updateInfo = it }
                .onFailure { updateFailed = true }
            checkingUpdate = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            SectionCard(stringResource(R.string.routing)) {
                RadioRow(
                    title = stringResource(R.string.routing_ir),
                    desc = stringResource(R.string.routing_ir_desc),
                    selected = settings.routingMode == RoutingMode.IR_BYPASS
                ) { scope.launch { SettingsStore.setRoutingMode(context, RoutingMode.IR_BYPASS) } }
                RadioRow(
                    title = stringResource(R.string.routing_global),
                    desc = stringResource(R.string.routing_global_desc),
                    selected = settings.routingMode == RoutingMode.GLOBAL
                ) { scope.launch { SettingsStore.setRoutingMode(context, RoutingMode.GLOBAL) } }
            }

            SectionCard(stringResource(R.string.tunnel_mode)) {
                RadioRow(
                    title = stringResource(R.string.tunnel_hev),
                    desc = stringResource(R.string.tunnel_hev_desc),
                    selected = settings.tunnelMode == TunnelMode.HEV
                ) { scope.launch { SettingsStore.setTunnelMode(context, TunnelMode.HEV) } }
                RadioRow(
                    title = stringResource(R.string.tunnel_xray),
                    desc = stringResource(R.string.tunnel_xray_desc),
                    selected = settings.tunnelMode == TunnelMode.XRAY_TUN
                ) { scope.launch { SettingsStore.setTunnelMode(context, TunnelMode.XRAY_TUN) } }
                RadioRow(
                    title = stringResource(R.string.tunnel_proxy),
                    desc = stringResource(R.string.tunnel_proxy_desc),
                    selected = settings.tunnelMode == TunnelMode.PROXY_ONLY
                ) { scope.launch { SettingsStore.setTunnelMode(context, TunnelMode.PROXY_ONLY) } }
            }

            // v0.2: behavior & safety (kill-switch, auto-connect, per-app proxy)
            SectionCard(stringResource(R.string.section_behavior)) {
                SwitchRow(
                    title = stringResource(R.string.kill_switch),
                    desc = stringResource(R.string.kill_switch_desc),
                    checked = settings.killSwitch
                ) { scope.launch { SettingsStore.setKillSwitch(context, it) } }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SwitchRow(
                    title = stringResource(R.string.auto_connect),
                    desc = stringResource(R.string.auto_connect_desc),
                    checked = settings.autoConnect
                ) { scope.launch { SettingsStore.setAutoConnect(context, it) } }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SwitchRow(
                    title = stringResource(R.string.auto_start_boot),
                    desc = stringResource(R.string.auto_start_boot_desc),
                    checked = settings.autoStartBoot
                ) { scope.launch { SettingsStore.setAutoStartBoot(context, it) } }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                PerAppRow(onClick = { showPerApp = true })
            }

            // v0.3: anti-censorship (fragmentation, uTLS rotation, fallback chain)
            SectionCard(stringResource(R.string.section_anticensor)) {
                SwitchRow(
                    title = stringResource(R.string.tls_fragment),
                    desc = stringResource(R.string.tls_fragment_desc),
                    checked = settings.tlsFragment
                ) { scope.launch { SettingsStore.setTlsFragment(context, it) } }
                if (settings.tlsFragment) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(
                        stringResource(R.string.tls_fragment_profile),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    RadioRow(
                        title = stringResource(R.string.tls_fragment_light),
                        desc = stringResource(R.string.tls_fragment_light_desc),
                        selected = settings.tlsFragmentProfile == TlsFragmentProfile.IRAN_LIGHT.name
                    ) {
                        scope.launch {
                            SettingsStore.setTlsFragmentProfile(
                                context, TlsFragmentProfile.IRAN_LIGHT.name
                            )
                        }
                    }
                    RadioRow(
                        title = stringResource(R.string.tls_fragment_medium),
                        desc = stringResource(R.string.tls_fragment_medium_desc),
                        selected = settings.tlsFragmentProfile == TlsFragmentProfile.IRAN_MEDIUM.name
                    ) {
                        scope.launch {
                            SettingsStore.setTlsFragmentProfile(
                                context, TlsFragmentProfile.IRAN_MEDIUM.name
                            )
                        }
                    }
                    RadioRow(
                        title = stringResource(R.string.tls_fragment_aggressive),
                        desc = stringResource(R.string.tls_fragment_aggressive_desc),
                        selected = settings.tlsFragmentProfile == TlsFragmentProfile.IRAN_AGGRESSIVE.name
                    ) {
                        scope.launch {
                            SettingsStore.setTlsFragmentProfile(
                                context, TlsFragmentProfile.IRAN_AGGRESSIVE.name
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SwitchRow(
                    title = stringResource(R.string.utls_rotation),
                    desc = stringResource(R.string.utls_rotation_desc),
                    checked = settings.utlsRotation
                ) { scope.launch { SettingsStore.setUtlsRotation(context, it) } }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SwitchRow(
                    title = stringResource(R.string.fallback_chain),
                    desc = stringResource(R.string.fallback_chain_desc),
                    checked = settings.fallbackChain
                ) { scope.launch { SettingsStore.setFallbackChain(context, it) } }
            }

            // v0.2: JSON backup/restore via SAF
            SectionCard(stringResource(R.string.backup)) {
                Text(
                    stringResource(R.string.backup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("simorgh-backup.json") },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.export_backup)) }
                    OutlinedButton(
                        onClick = {
                            restoreLauncher.launch(
                                arrayOf("application/json", "application/octet-stream", "text/plain")
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.restore_backup)) }
                }
            }

            SectionCard(stringResource(R.string.advanced)) {
                SwitchRow(
                    title = stringResource(R.string.ipv6),
                    checked = settings.ipv6
                ) { scope.launch { SettingsStore.setIpv6(context, it) } }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SwitchRow(
                    title = stringResource(R.string.allow_insecure),
                    desc = stringResource(R.string.allow_insecure_desc),
                    checked = settings.allowInsecure
                ) { scope.launch { SettingsStore.setAllowInsecure(context, it) } }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                TextFieldRow(
                    label = stringResource(R.string.socks_port),
                    value = settings.socksPort.toString(),
                    numeric = true
                ) { v -> v.toIntOrNull()?.let { scope.launch { SettingsStore.setSocksPort(context, it) } } }
                TextFieldRow(
                    label = stringResource(R.string.mtu),
                    value = settings.mtu.toString(),
                    numeric = true
                ) { v -> v.toIntOrNull()?.let { scope.launch { SettingsStore.setMtu(context, it) } } }
                TextFieldRow(
                    label = stringResource(R.string.remote_dns),
                    value = settings.remoteDns,
                    numeric = false
                ) { scope.launch { SettingsStore.setRemoteDns(context, it) } }
                TextFieldRow(
                    label = stringResource(R.string.domestic_dns),
                    value = settings.domesticDns,
                    numeric = false
                ) { scope.launch { SettingsStore.setDomesticDns(context, it) } }
            }

            SectionCard(stringResource(R.string.about)) {
                RowLabelValue(
                    stringResource(R.string.version),
                    BuildConfig.VERSION_NAME
                )
                RowLabelValue(
                    stringResource(R.string.core_version),
                    if (CoreBootstrap.isInitialized) CoreBootstrap.version() else "…"
                )

                // v0.2: GitHub release update check
                Spacer(Modifier.height(8.dp))
                OutlinedButton(enabled = !checkingUpdate, onClick = { checkUpdate() }) {
                    if (checkingUpdate) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.check_update))
                }
                when {
                    checkingUpdate -> Text(
                        stringResource(R.string.update_checking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    updateFailed -> Text(
                        stringResource(R.string.update_check_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> updateInfo?.let { info ->
                        if (info.hasUpdate) {
                            Text(
                                stringResource(R.string.update_available_fmt, info.latestTag),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(info.apkUrl ?: info.releaseUrl)
                                        )
                                    )
                                }
                            }) { Text(stringResource(R.string.download_update)) }
                        } else {
                            Text(
                                stringResource(R.string.up_to_date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // v0.2.1: offline APK share (openrung pattern)
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val ok = ApkSharer.share(
                            context, context.getString(R.string.share_apk)
                        )
                        if (!ok) snackbar.showSnackbar(context.getString(R.string.share_apk_failed))
                    }
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.share_apk))
                }
                Text(
                    stringResource(R.string.share_apk_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    stringResource(R.string.licenses_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Soroushchq/SimorghVPN"))
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.open_github))
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // v0.2: per-app proxy picker (fullscreen dialog)
    if (showPerApp) {
        PerAppProxyDialog(onDismiss = { showPerApp = false })
    }
}

// ---------------------------------------------------------------------- rows

@Composable
private fun PerAppRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.per_app_proxy),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                stringResource(R.string.per_app_row_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RadioRow(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, desc: String = "", onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (desc.isNotBlank()) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TextFieldRow(
    label: String,
    value: String,
    numeric: Boolean,
    onCommit: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            val cleaned = newText.trim()
            if (cleaned.isNotBlank() && cleaned != value) {
                if (!numeric || cleaned.toIntOrNull() != null) onCommit(cleaned)
            }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun RowLabelValue(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
