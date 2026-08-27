package com.udpfs.app.core

import org.junit.Test
import org.junit.Assert.assertEquals

class FormattersTest {

    @Test
    fun `bytes formats small values without a unit`() {
        assertEquals("0 B", Formatters.bytes(0))
        assertEquals("1 B", Formatters.bytes(1))
        assertEquals("1023 B", Formatters.bytes(1023))
    }

    @Test
    fun `bytes scales through binary units`() {
        assertEquals("1.0 KiB", Formatters.bytes(1024))
        assertEquals("1.5 KiB", Formatters.bytes(1536))
        assertEquals("1.0 MiB", Formatters.bytes(1024L * 1024))
        assertEquals("1.0 GiB", Formatters.bytes(1024L * 1024 * 1024))
        assertEquals("1.0 TiB", Formatters.bytes(1024L * 1024 * 1024 * 1024))
        assertEquals("1.0 PiB", Formatters.bytes(1024L * 1024 * 1024 * 1024 * 1024))
    }

    @Test
    fun `rate floors sub-unit throughput and appends per second`() {
        assertEquals("0 B/s", Formatters.rate(0.0))
        assertEquals("0 B/s", Formatters.rate(0.9))
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
    fun `ago describes recency relative to now`() {
        val now = System.currentTimeMillis() / 1000
        assertEquals("now", Formatters.ago(now))
        assertEquals("now", Formatters.ago(now - 1))
        assertEquals("30s ago", Formatters.ago(now - 30))
        assertEquals("5m ago", Formatters.ago(now - 5 * 60))
        assertEquals("2h ago", Formatters.ago(now - 2 * 3600))
    }
}
