@file:Suppress("FunctionName")

package com.udpfs.app.ui.screens

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udpfs.app.AppViewModel
import com.udpfs.app.R
import com.udpfs.app.core.Formatters
import com.udpfs.app.core.LogLine
import com.udpfs.app.core.PeerSnapshot
import com.udpfs.app.ui.components.EmptyState
import com.udpfs.app.ui.components.InfoRow
import com.udpfs.app.ui.components.StatRows
import com.udpfs.app.ui.components.SupportingText
import com.udpfs.app.ui.components.focusedClickable
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(
    vm: AppViewModel,
    isTv: Boolean = false,
) {
    var logExpanded by remember { mutableStateOf(false) }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()) }

    val sidePadding = if (isTv) 48.dp else 20.dp
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val tvScroll =
        remember(isTv) {
            if (!isTv) {
                Modifier
            } else {
                Modifier.onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val viewport = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset).toFloat()
                    val step = viewport * 0.4f
                    when (event.key) {
                        Key.DirectionDown -> {
                            scope.launch { listState.animateScrollBy(step) }
                            true
                        }

                        Key.DirectionUp -> {
                            scope.launch { listState.animateScrollBy(-step) }
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }
            }
        }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().then(tvScroll),
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeaderCard(vm) }

        if (isTv) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    TotalsCard(vm, Modifier.weight(1f))
                    PeersCard(vm, Modifier.weight(1f))
                }
            }
        } else {
            item { TotalsCard(vm) }
            item { PeersCard(vm) }
        }

        item { LogsCard(vm, timeFormat, onExpand = { logExpanded = true }) }
    }

    if (logExpanded) {
        LogDialog(vm = vm, timeFormat = timeFormat, onClose = { logExpanded = false })
    }
}

@Composable
private fun HeaderCard(vm: AppViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.stats_uptime, Formatters.duration(stats.uptimeSeconds)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    pluralStringResource(R.plurals.stats_peers, stats.peerCount, stats.peerCount.toString()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "↑ ${Formatters.bytes(stats.counters.bytesTx)}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "↓ ${Formatters.bytes(stats.counters.bytesRx)}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.stats_tx_rate, Formatters.rate(stats.counters.avgTxThroughput)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.stats_rx_rate, Formatters.rate(stats.counters.avgRxThroughput)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.stats_section_totals),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            StatRows(stats.counters)
        }
    }
}

@Composable
private fun PeersCard(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.stats_section_peers), style = MaterialTheme.typography.titleSmall)
            if (stats.peers.isEmpty()) {
                SupportingText(stringResource(R.string.stats_no_peers))
            } else {
                stats.peers.forEach { peer -> PeerCard(peer) }
            }
        }
    }
}

@Composable
private fun PeerCard(peer: PeerSnapshot) {
    var expanded by remember(peer.addr) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusedClickable(MaterialTheme.shapes.medium) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(peer.addr, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                    SupportingText(stringResource(R.string.stats_last_seen, Formatters.ago(peer.lastSeenUnix)))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "↑ ${Formatters.rate(peer.counters.avgTxThroughput)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "↓ ${Formatters.rate(peer.counters.avgRxThroughput)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow(
                        stringResource(R.string.stats_bytes),
                        "${Formatters.bytes(peer.counters.bytesTx)} / ${Formatters.bytes(peer.counters.bytesRx)}",
                    )
                    StatRows(peer.counters)
                }
            }
        }
    }
}

@Composable
private fun LogsCard(
    vm: AppViewModel,
    timeFormat: DateTimeFormatter,
    onExpand: () -> Unit,
) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.stats_section_logs),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .focusedClickable(MaterialTheme.shapes.small) { onExpand() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.OpenInFull,
                        contentDescription = stringResource(R.string.stats_logs_expand),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (logs.isEmpty()) {
                SupportingText(
                    stringResource(R.string.stats_no_logs),
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                )
            } else {
                val recentLogs = remember(logs) { logs.takeLast(100).asReversed() }
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                ) {
                    items(recentLogs, key = { it.seq }) { line ->
                        LogLineRow(line, timeFormat)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDialog(
    vm: AppViewModel,
    timeFormat: DateTimeFormatter,
    onClose: () -> Unit,
) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.stats_section_logs)) },
                    actions = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                )
            },
        ) { padding ->
            if (logs.isEmpty()) {
                EmptyState(stringResource(R.string.stats_no_logs), Modifier.padding(padding))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(logs.asReversed(), key = { it.seq }) { line ->
                        LogLineRow(line, timeFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLineRow(
    line: LogLine,
    timeFormat: DateTimeFormatter,
) {
    val color =
        when (line.level.uppercase()) {
            "ERROR" -> MaterialTheme.colorScheme.error
            "WARN" -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        "${timeFormat.format(Instant.ofEpochMilli(line.timeMillis))}  ${line.message}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color,
    )
}
