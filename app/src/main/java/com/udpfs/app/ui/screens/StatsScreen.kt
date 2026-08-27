package com.udpfs.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.udpfs.app.core.StatsSnapshot
import com.udpfs.app.ui.components.InfoRow
import com.udpfs.app.ui.components.focusHighlight
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(
    vm: AppViewModel,
    isTv: Boolean = false,
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()) }
    var logExpanded by remember { mutableStateOf(false) }

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
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.stats_uptime, Formatters.duration(stats.uptimeSeconds)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.stats_peers, stats.peerCount.toString()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "↑ ${Formatters.bytes(stats.bytesTx)}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "↓ ${Formatters.bytes(stats.bytesRx)}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.stats_tx_rate, Formatters.rate(stats.avgTxThroughput)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.stats_rx_rate, Formatters.rate(stats.avgRxThroughput)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (isTv) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    TotalsCard(stats, Modifier.weight(1f))
                    PeersCard(stats.peers, Modifier.weight(1f))
                }
            }
        } else {
            item { TotalsCard(stats) }
            item { PeersCard(stats.peers) }
        }

        item {
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
                                    .focusHighlight(MaterialTheme.shapes.small)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { logExpanded = true },
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
                        Text(
                            stringResource(R.string.stats_no_logs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }

    if (logExpanded) {
        LogDialog(logs = logs, timeFormat = timeFormat, onClose = { logExpanded = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDialog(
    logs: List<LogLine>,
    timeFormat: DateTimeFormatter,
    onClose: () -> Unit,
) {
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
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.stats_no_logs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

@Composable
private fun TotalsCard(
    stats: StatsSnapshot,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.stats_section_totals),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            InfoRow(stringResource(R.string.stats_operations), stats.totalOps.toString())
            InfoRow(stringResource(R.string.stats_errors), stats.errors.toString())
            InfoRow(stringResource(R.string.stats_reads), stats.reads.toString())
            InfoRow(stringResource(R.string.stats_writes), stats.writes.toString())
            InfoRow(stringResource(R.string.stats_packets_tx), stats.packetsTx.toString())
            InfoRow(stringResource(R.string.stats_packets_rx), stats.packetsRx.toString())
            InfoRow(stringResource(R.string.stats_retransmits), stats.retransmits.toString())
            InfoRow(stringResource(R.string.stats_nacks), stats.nackCount.toString())
            InfoRow(stringResource(R.string.stats_out_of_order), stats.outOfOrder.toString())
            InfoRow(stringResource(R.string.stats_peer_resets), stats.resetCount.toString())
        }
    }
}

@Composable
private fun PeersCard(
    peers: List<PeerSnapshot>,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.stats_section_peers), style = MaterialTheme.typography.titleSmall)
            if (peers.isEmpty()) {
                Text(
                    stringResource(R.string.stats_no_peers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                peers.forEach { peer -> PeerCard(peer) }
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
                    .focusHighlight(MaterialTheme.shapes.medium)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(peer.addr, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                    Text(
                        stringResource(R.string.stats_last_seen, Formatters.ago(peer.lastSeenUnix)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "↑ ${Formatters.rate(peer.avgTxThroughput)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "↓ ${Formatters.rate(peer.avgRxThroughput)}",
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
                    InfoRow(stringResource(R.string.stats_bytes), "${Formatters.bytes(peer.bytesTx)} / ${Formatters.bytes(peer.bytesRx)}")
                    InfoRow(stringResource(R.string.stats_operations), peer.totalOps.toString())
                    InfoRow(stringResource(R.string.stats_errors), peer.errors.toString())
                    InfoRow(stringResource(R.string.stats_reads), peer.reads.toString())
                    InfoRow(stringResource(R.string.stats_writes), peer.writes.toString())
                    InfoRow(stringResource(R.string.stats_packets_tx), peer.packetsTx.toString())
                    InfoRow(stringResource(R.string.stats_packets_rx), peer.packetsRx.toString())
                    InfoRow(stringResource(R.string.stats_retransmits), peer.retransmits.toString())
                    InfoRow(stringResource(R.string.stats_nacks), peer.nackCount.toString())
                    InfoRow(stringResource(R.string.stats_out_of_order), peer.outOfOrder.toString())
                    InfoRow(stringResource(R.string.stats_peer_resets), peer.resetCount.toString())
                }
            }
        }
    }
}
