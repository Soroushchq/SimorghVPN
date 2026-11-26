package com.simorgh.vpn.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.simorgh.vpn.data.RoutingMode
import com.simorgh.vpn.data.SettingsStore
import com.simorgh.vpn.data.TunnelMode
import com.simorgh.vpn.ui.components.SectionCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings by ServiceLocator.settings.collectAsState()
    val scope = rememberCoroutineScope()

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
            Spacer(Modifier.height(8.dp))
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
}

// ---------------------------------------------------------------------- rows

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
