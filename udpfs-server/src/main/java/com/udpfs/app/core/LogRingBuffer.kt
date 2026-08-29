package com.udpfs.app.core

internal class LogRingBuffer(
    private val capacity: Int,
) {
    private val buffer = ArrayDeque<LogLine>()
    private var seq = 0L

    @Synchronized
    fun append(
        level: LogLevel,
        message: String,
        timeMillis: Long = System.currentTimeMillis(),
    ) {
        buffer.addLast(LogLine(++seq, timeMillis, level, message))
        if (buffer.size > capacity) buffer.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<LogLine> = buffer.toList()

    @Synchronized
    fun clear() = buffer.clear()
}
