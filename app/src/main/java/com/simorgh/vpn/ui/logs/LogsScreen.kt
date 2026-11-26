package com.simorgh.vpn.ui.logs

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.simorgh.vpn.R
import com.simorgh.vpn.ui.components.EmptyHint
import com.simorgh.vpn.util.LogBuffer

@Composable
fun LogsScreen() {
    val context = LocalContext.current
    val logs by LogBuffer.lines.collectAsState()
    val listState = rememberLazyListState()
    var autoScroll by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(logs.size, autoScroll) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.logs_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.auto_scroll),
                style = MaterialTheme.typography.labelMedium
            )
            Switch(checked = autoScroll, onCheckedChange = { autoScroll = it })
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { LogBuffer.clear() }) {
                Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.clear))
            }
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, LogBuffer.dump())
                }
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
            }) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share_logs))
            }
        }

        if (logs.isEmpty()) {
            EmptyHint(stringResource(R.string.empty_logs))
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(logs) { _, line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = when {
                            line.contains(" E/") -> MaterialTheme.colorScheme.error
                            line.contains(" W/") -> MaterialTheme.colorScheme.tertiary
                            line.contains(" I/") -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
