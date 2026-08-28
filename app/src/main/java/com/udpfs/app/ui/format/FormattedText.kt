package com.udpfs.app.ui.format

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.udpfs.app.R
import com.udpfs.app.core.Formatters
import com.udpfs.app.core.Formatters.Ago
import com.udpfs.app.core.Formatters.ByteValue
import com.udpfs.app.core.Formatters.DurationParts

@Composable
fun formatBytes(value: ByteValue): String =
    when (value) {
        is ByteValue.Plain -> stringResource(R.string.bytes_plain, value.bytes)
        is ByteValue.Scaled -> stringResource(R.string.bytes_scaled, value.amount, value.unit)
    }

fun formatBytes(
    context: Context,
    value: ByteValue,
): String =
    when (value) {
        is ByteValue.Plain -> context.getString(R.string.bytes_plain, value.bytes)
        is ByteValue.Scaled -> context.getString(R.string.bytes_scaled, value.amount, value.unit)
    }

@Composable
fun formatRate(bytesPerSecond: Double): String {
    val rate = Formatters.rate(bytesPerSecond)
    return if (rate == ByteValue.Plain(0)) {
        stringResource(R.string.rate_zero)
    } else {
        stringResource(R.string.rate_format, formatBytes(rate))
    }
}

@Composable
fun formatDuration(parts: DurationParts): String =
    when {
        parts.hours > 0 -> stringResource(R.string.duration_hms, parts.hours, parts.minutes, parts.seconds)
        parts.minutes > 0 -> stringResource(R.string.duration_ms, parts.minutes, parts.seconds)
        else -> stringResource(R.string.duration_s, parts.seconds)
    }

@Composable
fun formatAgo(ago: Ago): String =
    when (ago) {
        Ago.Now -> stringResource(R.string.ago_now)
        is Ago.Seconds -> stringResource(R.string.ago_seconds, ago.value)
        is Ago.Minutes -> stringResource(R.string.ago_minutes, ago.value)
        is Ago.Hours -> stringResource(R.string.ago_hours, ago.value)
    }
