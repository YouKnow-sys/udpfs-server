package com.udpfs.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun `bytes formats small values without a unit`() {
        assertEquals("0 B", Formatters.bytes(0))
        assertEquals("1 B", Formatters.bytes(1))
        assertEquals("1023 B", Formatters.bytes(1023))
    }

    @Test
    fun `bytes keeps the sign of negative values`() {
        assertEquals("-1 B", Formatters.bytes(-1))
        assertEquals("-1.5 KiB", Formatters.bytes(-1536))
    }

    @Test
    fun `bytes scales through binary units`() {
        assertEquals("1.0 KiB", Formatters.bytes(1024))
        assertEquals("1.5 KiB", Formatters.bytes(1536))
        assertEquals("1.0 MiB", Formatters.bytes(1024L * 1024))
        assertEquals("1.0 GiB", Formatters.bytes(1024L * 1024 * 1024))
        assertEquals("1.0 TiB", Formatters.bytes(1024L * 1024 * 1024 * 1024))
        assertEquals("1.0 PiB", Formatters.bytes(1024L * 1024 * 1024 * 1024 * 1024))
        assertEquals("8192.0 PiB", Formatters.bytes(Long.MAX_VALUE))
    }

    @Test
    fun `rate floors sub-unit throughput and appends per second`() {
        assertEquals("0 B/s", Formatters.rate(0.0))
        assertEquals("0 B/s", Formatters.rate(0.9))
        assertEquals("1 B/s", Formatters.rate(1.0))
        assertEquals("1023 B/s", Formatters.rate(1023.9))
        assertEquals("10.0 KiB/s", Formatters.rate(10.0 * 1024))
    }

    @Test
    fun `duration drops leading zero components`() {
        assertEquals("0s", Formatters.duration(0))
        assertEquals("59s", Formatters.duration(59))
        assertEquals("1m 00s", Formatters.duration(60))
        assertEquals("59m 59s", Formatters.duration(3599))
        assertEquals("1h 00m 00s", Formatters.duration(3600))
        assertEquals("1h 01m 01s", Formatters.duration(3661))
    }

    @Test
    fun `duration renders past 24 hours as plain hours`() {
        assertEquals("24h 00m 00s", Formatters.duration(24 * 3600))
        assertEquals("25h 01m 01s", Formatters.duration(25 * 3600 + 61))
    }

    @Test
    fun `ago boundaries are exact against an injected clock`() {
        val nowMillis = 1_700_000_000_000L
        val nowSeconds = nowMillis / 1000

        assertEquals("now", Formatters.ago(nowSeconds, nowMillis))
        assertEquals("now", Formatters.ago(nowSeconds - 4, nowMillis))
        assertEquals("now", Formatters.ago(nowSeconds + 60, nowMillis))

        assertEquals("5s ago", Formatters.ago(nowSeconds - 5, nowMillis))
        assertEquals("59s ago", Formatters.ago(nowSeconds - 59, nowMillis))
        assertEquals("1m ago", Formatters.ago(nowSeconds - 60, nowMillis))
        assertEquals("59m ago", Formatters.ago(nowSeconds - 3599, nowMillis))
        assertEquals("1h ago", Formatters.ago(nowSeconds - 3600, nowMillis))
        assertEquals("2h ago", Formatters.ago(nowSeconds - 2 * 3600, nowMillis))
    }
}
