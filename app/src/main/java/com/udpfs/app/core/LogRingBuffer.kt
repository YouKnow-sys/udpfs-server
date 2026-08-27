package com.udpfs.app.core

import java.util.concurrent.atomic.AtomicLong

internal class LogRingBuffer(
    private val capacity: Int,
) {
    private val buffer = ArrayDeque<LogLine>()
    private val seq = AtomicLong()

    fun append(
        level: String,
        message: String,
        timeMillis: Long = System.currentTimeMillis(),
    ) {
        synchronized(buffer) {
            buffer.addLast(LogLine(seq.incrementAndGet(), timeMillis, level, message))
            while (buffer.size > capacity) buffer.removeFirst()
        }
    }

    fun snapshot(): List<LogLine> = synchronized(buffer) { buffer.toList() }
}
