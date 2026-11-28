package com.simorgh.vpn.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.simorgh.vpn.R
import com.simorgh.vpn.core.mae.MaeLearner
import com.simorgh.vpn.core.mae.MaeRegistry
import com.simorgh.vpn.core.mae.MaeRoute
import com.simorgh.vpn.core.mae.MaeServiceDef
import com.simorgh.vpn.core.mae.MaeServiceState
import com.simorgh.vpn.core.mae.MaeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v0.4 MAE: per-service adaptive routing screen (mlmvpn pattern).
 *
 * Shows each well-known service, what the learner measured (direct-ok or
 * blocked) and what the routing table will do with it. A tap cycles the user
 * override: خودکار → تونل → مستقیم. Verdicts feed the Xray routing rules at
 * the next connect.
 */
@Composable
fun MaeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var states by remember { mutableStateOf<Map<String, MaeServiceState>>(emptyMap()) }
    var learning by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            states = withContext(Dispatchers.IO) { MaeStore.load(context).services }
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun relearn() {
        if (learning) return
        learning = true
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { MaeLearner.learn(context, onlyStale = false) }
            }
            reload()
            learning = false
        }
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
                // -------------------------------------------------- header
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
                            stringResource(R.string.mae_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            stringResource(R.string.mae_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(enabled = !learning, onClick = { relearn() }) {
                        if (learning) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.mae_retest))
                        }
                    }
                }

                Text(
                    stringResource(R.string.mae_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // -------------------------------------------------- services
                LazyColumn(Modifier.weight(1f)) {
                    items(MaeRegistry.SERVICES, key = { it.id }) { def ->
                        ServiceRow(
                            def = def,
                            state = states[def.id],
                            onClick = {
                                val cur = states[def.id]?.override ?: MaeRoute.AUTO
                                val next = when (cur) {
                                    MaeRoute.AUTO -> MaeRoute.PROXY
                                    MaeRoute.PROXY -> MaeRoute.DIRECT
                                    MaeRoute.DIRECT -> MaeRoute.AUTO
                                }
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        MaeStore.setOverride(context, def.id, next)
                                    }
                                    reload()
                                }
                            }
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.mae_apply_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceRow(def: MaeServiceDef, state: MaeServiceState?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(def.faName, style = MaterialTheme.typography.bodyLarge)
            Text(
                def.domains.take(3).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(state)
    }
}

@Composable
private fun StatusChip(state: MaeServiceState?) {
    val (labelRes, color) = when {
        state == null -> R.string.mae_unknown to MaterialTheme.colorScheme.surfaceVariant
        state.override == MaeRoute.PROXY -> R.string.mae_pinned_proxy to MaterialTheme.colorScheme.tertiaryContainer
        state.override == MaeRoute.DIRECT -> R.string.mae_pinned_direct to MaterialTheme.colorScheme.secondaryContainer
        state.directOk == true -> R.string.mae_direct_ok to MaterialTheme.colorScheme.secondaryContainer
        state.directOk == false -> R.string.mae_blocked -> MaterialTheme.colorScheme.errorContainer
        else -> R.string.mae_unknown to MaterialTheme.colorScheme.surfaceVariant
    }
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
