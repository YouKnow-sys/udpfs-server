package com.udpfs.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udpfs.app.AppViewModel
import com.udpfs.app.R
import com.udpfs.app.core.LogLevel
import com.udpfs.app.core.LogLine
import com.udpfs.app.core.PeerSnapshot
import com.udpfs.app.core.StatsSnapshot
import com.udpfs.app.core.formatAgoValue
import com.udpfs.app.core.formatByteValue
import com.udpfs.app.core.formatDurationParts
import com.udpfs.app.ui.components.EmptyState
import com.udpfs.app.ui.components.InfoRow
import com.udpfs.app.ui.components.StatRows
import com.udpfs.app.ui.components.SupportingText
import com.udpfs.app.ui.components.focusedClickable
import com.udpfs.app.ui.components.tvDpadScroll
import com.udpfs.app.ui.format.formatAgo
import com.udpfs.app.ui.format.formatBytes
import com.udpfs.app.ui.format.formatDuration
import com.udpfs.app.ui.format.formatRate
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    vm: AppViewModel,
    isTv: Boolean = false,
) {
    var logExpanded by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    val sidePadding = if (isTv) 48.dp else 20.dp
    val listState = rememberLazyListState()

    val lastPeer = remember { FocusRequester() }
    val logsHeader = remember { FocusRequester() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().tvDpadScroll(listState, enabled = isTv),
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeaderCard(vm.stats) }

        if (isTv) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    TotalsCard(vm.stats, Modifier.weight(1f))
                    PeersCard(vm.stats, Modifier.weight(1f), lastPeer = lastPeer, belowPeers = logsHeader)
                }
            }
        } else {
            item { TotalsCard(vm.stats) }
            item { PeersCard(vm.stats, lastPeer = lastPeer, belowPeers = logsHeader) }
        }

        item {
            LogsCard(
                vm.logs,
                timeFormat,
                anchor = logsHeader,
                up = lastPeer,
                onClear = vm::clearLogs,
                onExpand = { logExpanded = true },
            )
        }
    }

    if (logExpanded) {
        LogDialog(
            logs = vm.logs,
            timeFormat = timeFormat,
            onClear = vm::clearLogs,
            onClose = { logExpanded = false },
        )
    }
}

@Composable
private fun HeaderCard(stats: StateFlow<StatsSnapshot>) {
    val stats by stats.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.stats_uptime, formatDuration(formatDurationParts(stats.uptimeSeconds))),
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
                    stringResource(R.string.stats_tx_bytes, formatBytes(formatByteValue(stats.counters.bytesTx))),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.stats_rx_bytes, formatBytes(formatByteValue(stats.counters.bytesRx))),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.stats_tx_rate, formatRate(stats.counters.avgTxThroughput)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.stats_rx_rate, formatRate(stats.counters.avgRxThroughput)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(
    stats: StateFlow<StatsSnapshot>,
    modifier: Modifier = Modifier,
) {
    val stats by stats.collectAsStateWithLifecycle()
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
    stats: StateFlow<StatsSnapshot>,
    modifier: Modifier = Modifier,
    lastPeer: FocusRequester,
    belowPeers: FocusRequester,
) {
    val stats by stats.collectAsStateWithLifecycle()
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.stats_section_peers), style = MaterialTheme.typography.titleSmall)
            if (stats.peers.isEmpty()) {
                SupportingText(stringResource(R.string.stats_no_peers))
            } else {
                stats.peers.forEachIndexed { index, peer ->
                    PeerCard(
                        peer,
                        tail = if (index == stats.peers.lastIndex) lastPeer else null,
                        down = if (index == stats.peers.lastIndex) belowPeers else null,
                        first = index == 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerCard(
    peer: PeerSnapshot,
    tail: FocusRequester?,
    down: FocusRequester?,
    first: Boolean,
) {
    var expanded by remember(peer.addr) { mutableStateOf(false) }
    val requester = remember(peer.addr, tail) { tail ?: FocusRequester() }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusRequester(requester)
                    .focusProperties {
                        if (first) up = FocusRequester.Cancel
                        if (down != null) this.down = down
                    }.focusedClickable(MaterialTheme.shapes.small) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(peer.addr, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                    SupportingText(stringResource(R.string.stats_last_seen, formatAgo(formatAgoValue(peer.lastSeenUnix))))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.stats_tx_bytes, formatRate(peer.counters.avgTxThroughput)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.stats_rx_bytes, formatRate(peer.counters.avgRxThroughput)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Icon(
                    painterResource(
                        if (expanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down,
                    ),
                    contentDescription = null,
                )
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow(
                        stringResource(R.string.stats_bytes),
                        stringResource(
                            R.string.stats_bytes_pair,
                            formatBytes(formatByteValue(peer.counters.bytesTx)),
                            formatBytes(formatByteValue(peer.counters.bytesRx)),
                        ),
                    )
                    StatRows(peer.counters)
                }
            }
        }
    }
}

@Composable
private fun LogsCard(
    logs: StateFlow<List<LogLine>>,
    timeFormat: SimpleDateFormat,
    anchor: FocusRequester,
    up: FocusRequester,
    onClear: () -> Unit,
    onExpand: () -> Unit,
) {
    val logs by logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    StickToBottom(listState, logs.firstOrNull()?.seq)
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusProperties {
                        this.up = up
                        down = FocusRequester.Cancel
                    }.padding(start = 16.dp, top = 8.dp, end = 10.dp, bottom = 2.dp),
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
                            .size(48.dp)
                            .focusRequester(anchor)
                            .focusedClickable(MaterialTheme.shapes.small) { onClear() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.stats_logs_clear),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .focusedClickable(MaterialTheme.shapes.small) { onExpand() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_open_in_full),
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
                val recentLogs = remember(logs) { logs.take(100) }
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                    reverseLayout = true,
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
    logs: StateFlow<List<LogLine>>,
    timeFormat: SimpleDateFormat,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val logs by logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    StickToBottom(listState, logs.firstOrNull()?.seq)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.tvDpadScroll(listState, reversed = true),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.stats_section_logs)) },
                    actions = {
                        IconButton(onClick = onClear) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.stats_logs_clear))
                        }
                        IconButton(onClick = onClose) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
                        }
                    },
                )
            },
        ) { padding ->
            if (logs.isEmpty()) {
                EmptyState(stringResource(R.string.stats_no_logs), Modifier.padding(padding))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    reverseLayout = true,
                ) {
                    items(logs, key = { it.seq }) { line ->
                        LogLineRow(line, timeFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun StickToBottom(
    listState: LazyListState,
    newestKey: Long?,
) {
    LaunchedEffect(newestKey) {
        if (newestKey != null && !listState.isScrollInProgress && listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
    }
}

@Composable
private fun LogLineRow(
    line: LogLine,
    timeFormat: SimpleDateFormat,
) {
    val color =
        when (line.level) {
            LogLevel.ERROR -> MaterialTheme.colorScheme.error
            LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    val text =
        remember(line, timeFormat) {
            "${timeFormat.format(Date(line.timeMillis))}  ${line.message}"
        }

    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color,
    )
}
