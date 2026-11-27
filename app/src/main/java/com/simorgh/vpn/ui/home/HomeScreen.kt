package com.simorgh.vpn.ui.home

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simorgh.vpn.R
import com.simorgh.vpn.ServiceLocator
import com.simorgh.vpn.core.VpnBus
import com.simorgh.vpn.core.VpnStatus
import com.simorgh.vpn.data.SettingsStore
import com.simorgh.vpn.ui.components.LatencyText
import com.simorgh.vpn.ui.components.PhoenixConnectButton
import com.simorgh.vpn.ui.components.ProtocolChip
import com.simorgh.vpn.ui.components.StatBox
import com.simorgh.vpn.util.Fmt
import com.simorgh.vpn.vpn.SimorghVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpenProfiles: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vpnState by VpnBus.state.collectAsState()
    val settings by ServiceLocator.settings.collectAsState()
    val profiles by ServiceLocator.profileRepo.profiles.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val activeProfile = remember(vpnState.profileId, settings.activeProfileId, profiles) {
        profiles.firstOrNull { it.id == vpnState.profileId }
            ?: profiles.firstOrNull { it.id == settings.activeProfileId }
            ?: profiles.firstOrNull()
    }

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            activeProfile?.let { SimorghVpnService.start(context, it.id) }
        } else {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.vpn_permission_needed)) }
        }
    }

    fun connect() {
        val p = activeProfile
        if (p == null) {
            onOpenProfiles()
            return
        }
        scope.launch { SettingsStore.setActiveProfile(context, p.id) }
        val prep = VpnService.prepare(context)
        if (prep != null) {
            vpnPermission.launch(prep)
        } else {
            SimorghVpnService.start(context, p.id)
        }
    }

    // ticking clock for the duration display
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vpnState.status) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // v0.2: auto-connect on app open (only when VPN permission is already granted)
    LaunchedEffect(Unit) {
        if (ServiceLocator.settings.value.autoConnect &&
            VpnBus.state.value.status == VpnStatus.DISCONNECTED
        ) {
            delay(1200) // give profiles a moment to load
            val st = ServiceLocator.settings.value
            val profiles = ServiceLocator.profileRepo.profiles.value
            val pid = st.activeProfileId.ifBlank { profiles.firstOrNull()?.id }
            if (pid != null &&
                VpnBus.state.value.status == VpnStatus.DISCONNECTED &&
                VpnService.prepare(context) == null
            ) {
                SimorghVpnService.start(context, pid)
            }
        }
    }
    val durSec =
        if (vpnState.status == VpnStatus.CONNECTED && vpnState.sinceMs > 0) (now - vpnState.sinceMs) / 1000
        else 0L

    val buttonLabel = when (vpnState.status) {
        VpnStatus.CONNECTED -> stringResource(R.string.disconnect)
        VpnStatus.CONNECTING -> stringResource(R.string.connecting)
        VpnStatus.STOPPING -> stringResource(R.string.disconnecting)
        VpnStatus.BLOCKED -> stringResource(R.string.connect)
        else -> stringResource(R.string.connect)
    }
    val statusText = when (vpnState.status) {
        VpnStatus.CONNECTED -> stringResource(R.string.status_connected)
        VpnStatus.CONNECTING -> stringResource(R.string.connecting)
        VpnStatus.STOPPING -> stringResource(R.string.disconnecting)
        VpnStatus.ERROR -> stringResource(R.string.status_error)
        VpnStatus.BLOCKED -> stringResource(R.string.status_blocked)
        else -> stringResource(R.string.status_disconnected)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(84.dp)
            )
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))
            PhoenixConnectButton(
                status = vpnState.status,
                label = buttonLabel,
                onClick = {
                    if (vpnState.status == VpnStatus.CONNECTED ||
                        vpnState.status == VpnStatus.CONNECTING ||
                        vpnState.status == VpnStatus.BLOCKED
                    ) {
                        if (vpnState.status == VpnStatus.BLOCKED) {
                            connect()
                        } else {
                            SimorghVpnService.stop(context)
                        }
                    } else {
                        connect()
                    }
                }
            )

            Spacer(Modifier.height(10.dp))
            Text(
                statusText,
                style = MaterialTheme.typography.titleSmall,
                color = if (vpnState.status == VpnStatus.ERROR || vpnState.status == VpnStatus.BLOCKED)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if ((vpnState.status == VpnStatus.ERROR || vpnState.status == VpnStatus.BLOCKED) &&
                vpnState.error.isNotBlank()
            ) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        vpnState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            if (activeProfile == null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.no_server_title),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.no_server_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onOpenProfiles) {
                            Text(stringResource(R.string.add_server_now))
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenProfiles),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.current_server),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    activeProfile.name,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    "${activeProfile.host}:${activeProfile.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LatencyText(activeProfile.lastLatencyMs)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProtocolChip(activeProfile.protocol)
                            ProtocolChip(activeProfile.security)
                            ProtocolChip(activeProfile.network)
                        }
                    }
                }
            }

            if (vpnState.status == VpnStatus.CONNECTED) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        label = stringResource(R.string.download),
                        value = Fmt.speed(vpnState.downSpeedBps),
                        sub = Fmt.bytes(vpnState.downTotal),
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        label = stringResource(R.string.upload),
                        value = Fmt.speed(vpnState.upSpeedBps),
                        sub = Fmt.bytes(vpnState.upTotal),
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        label = stringResource(R.string.duration),
                        value = Fmt.duration(durSec),
                        sub = "",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
