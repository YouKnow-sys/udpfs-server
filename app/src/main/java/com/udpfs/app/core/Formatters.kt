package com.udpfs.app.core

import kotlin.math.abs
import java.util.Locale

object Formatters {

    private val BYTE_UNITS = arrayOf("KiB", "MiB", "GiB", "TiB")

    fun bytes(v: Long): String {
        if (abs(v) < 1024) return "$v B"
        var value = v.toDouble()
        for (unit in BYTE_UNITS) {
            value /= 1024
            if (abs(value) < 1024) return String.format(Locale.US, "%.1f %s", value, unit)
        }
        return String.format(Locale.US, "%.1f PiB", value / 1024)
    }

    fun rate(bytesPerSecond: Double): String =
        if (bytesPerSecond < 1.0) "0 B/s" else bytes(bytesPerSecond.toLong()) + "/s"

    fun duration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = totalSeconds % 3600 / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> "%dh %02dm %02ds".format(h, m, s)
            m > 0 -> "%dm %02ds".format(m, s)
            else -> "${s}s"
        }
    }

    fun ago(unixSeconds: Long): String {
        val delta = System.currentTimeMillis() / 1000 - unixSeconds
        return when {
            delta < 5 -> "now"
            delta < 60 -> "${delta}s ago"
            delta < 3600 -> "${delta / 60}m ago"
            else -> "${delta / 3600}h ago"
        }
    }
}
