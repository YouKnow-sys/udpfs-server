package com.udpfs.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRingBufferTest {
    @Test
    fun `keeps only the last capacity lines`() {
        val buffer = LogRingBuffer(capacity = 3)
        repeat(8) { i -> buffer.append("INFO", "line $i", timeMillis = i.toLong()) }

        assertEquals(listOf("line 5", "line 6", "line 7"), buffer.snapshot().map { it.message })
    }

    @Test
    fun `sequence numbers stay monotonic across eviction`() {
        val buffer = LogRingBuffer(capacity = 2)
        repeat(5) { buffer.append("INFO", "m") }

        assertEquals(listOf(4L, 5L), buffer.snapshot().map { it.seq })
    }

    @Test
    fun `snapshot of an empty buffer is empty`() {
        assertTrue(LogRingBuffer(capacity = 3).snapshot().isEmpty())
    }

    @Test
    fun `preserves level and timestamp`() {
        val buffer = LogRingBuffer(capacity = 3)
        buffer.append("WARN", "careful", timeMillis = 42L)

        val line = buffer.snapshot().single()
        assertEquals("WARN", line.level)
        assertEquals(42L, line.timeMillis)
    }
}
