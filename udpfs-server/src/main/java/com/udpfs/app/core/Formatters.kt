package com.udpfs.app.core

import kotlin.math.abs

sealed interface ByteValue {
    data class Plain(
        val bytes: Long,
    ) : ByteValue

    data class Scaled(
        val amount: Double,
        val unit: String,
    ) : ByteValue
}

private val BYTE_UNITS = arrayOf("KiB", "MiB", "GiB", "TiB")

fun formatByteValue(v: Long): ByteValue {
    if (abs(v) < 1024) return ByteValue.Plain(v)
    var value = v.toDouble()
    for (unit in BYTE_UNITS) {
        value /= 1024
        if (abs(value) < 1024) return ByteValue.Scaled(value, unit)
    }
    return ByteValue.Scaled(value / 1024, "PiB")
}

fun formatRateValue(bytesPerSecond: Double): ByteValue =
    if (bytesPerSecond < 1.0) ByteValue.Plain(0) else formatByteValue(bytesPerSecond.toLong())

data class DurationParts(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)

fun formatDurationParts(totalSeconds: Long): DurationParts =
    DurationParts(
        hours = (totalSeconds / 3600).toInt(),
        minutes = (totalSeconds % 3600 / 60).toInt(),
        seconds = (totalSeconds % 60).toInt(),
    )

sealed interface Ago {
    data object Now : Ago

    data class Seconds(
        val value: Long,
    ) : Ago

    data class Minutes(
        val value: Long,
    ) : Ago

    data class Hours(
        val value: Long,
    ) : Ago
}

fun formatAgoValue(
    unixSeconds: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Ago {
    val delta = nowMillis / 1000 - unixSeconds
    return when {
        delta < 5 -> Ago.Now
        delta < 60 -> Ago.Seconds(delta)
        delta < 3600 -> Ago.Minutes(delta / 60)
        else -> Ago.Hours(delta / 3600)
    }
}
