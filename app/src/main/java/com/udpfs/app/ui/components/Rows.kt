package com.udpfs.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udpfs.app.R
import com.udpfs.app.core.TrafficCounters

@Composable
fun InfoRow(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun StatRows(counters: TrafficCounters) {
    InfoRow(stringResource(R.string.stats_operations), counters.totalOps.toString())
    InfoRow(stringResource(R.string.stats_errors), counters.errors.toString())
    InfoRow(stringResource(R.string.stats_reads), counters.reads.toString())
    InfoRow(stringResource(R.string.stats_writes), counters.writes.toString())
    InfoRow(stringResource(R.string.stats_packets_tx), counters.packetsTx.toString())
    InfoRow(stringResource(R.string.stats_packets_rx), counters.packetsRx.toString())
    InfoRow(stringResource(R.string.stats_retransmits), counters.retransmits.toString())
    InfoRow(stringResource(R.string.stats_nacks), counters.nackCount.toString())
    InfoRow(stringResource(R.string.stats_out_of_order), counters.outOfOrder.toString())
    InfoRow(stringResource(R.string.stats_peer_resets), counters.resetCount.toString())
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SupportingText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
