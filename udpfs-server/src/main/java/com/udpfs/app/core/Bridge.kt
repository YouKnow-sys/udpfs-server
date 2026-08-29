package com.udpfs.app.core

import com.udpfs.udpfsdbridge.Config
import com.udpfs.udpfsdbridge.ServerController
import com.udpfs.udpfsdbridge.Udpfsdbridge

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ;

    companion object {
        fun of(value: String): LogLevel =
            when (value.uppercase()) {
                "ERROR" -> ERROR
                "WARN", "WARNING" -> WARN
                "INFO" -> INFO
                else -> DEBUG
            }
    }
}

data class TrafficCounters(
    val bytesTx: Long = 0,
    val bytesRx: Long = 0,
    val avgTxThroughput: Double = 0.0,
    val avgRxThroughput: Double = 0.0,
    val totalOps: Long = 0,
    val errors: Long = 0,
    val reads: Long = 0,
    val writes: Long = 0,
    val packetsTx: Long = 0,
    val packetsRx: Long = 0,
    val retransmits: Long = 0,
    val nackCount: Long = 0,
    val outOfOrder: Long = 0,
    val peerNackCount: Long = 0,
    val resetCount: Long = 0,
)

data class PeerSnapshot(
    val addr: String,
    val lastSeenUnix: Long,
    val counters: TrafficCounters,
)

data class StatsSnapshot(
    val running: Boolean = false,
    val uptimeSeconds: Long = 0,
    val peerCount: Int = 0,
    val counters: TrafficCounters = TrafficCounters(),
    val peers: List<PeerSnapshot> = emptyList(),
)

data class MountSnapshot(
    val fsRoot: String = "",
    val blockDevice: String = "",
    val sectorSize: Int = 0,
    val totalSectors: Long = 0,
    val totalBytes: Long = 0,
    val readOnly: Boolean = false,
    val compressionFormats: List<String> = emptyList(),
)

data class LogLine(
    val seq: Long,
    val timeMillis: Long,
    val level: LogLevel,
    val message: String,
)

class BridgeController {
    @Volatile
    private var logger: ((level: LogLevel, message: String) -> Unit)? = null

    private val delegate: ServerController by lazy {
        Udpfsdbridge.newServer().also { server ->
            server.setLogger { level, message -> logger?.invoke(LogLevel.of(level), message) }
        }
    }

    fun start(config: ServerConfig) = delegate.start(config.toBridgeConfig())

    fun stop() = delegate.stop()

    fun stats(): StatsSnapshot {
        val s = delegate.stats()
        val peers =
            buildList {
                repeat(s.peerCount.toInt()) { i ->
                    delegate.peer(i.toLong())?.let { add(it.toSnapshot()) }
                }
            }
        return s.toSnapshot(peers)
    }

    fun isRunning(): Boolean = delegate.stats().running

    fun mount(): MountSnapshot = delegate.mountInfo().toSnapshot(delegate.compressionFormats().toFormatList())

    fun setLogger(logger: (level: LogLevel, message: String) -> Unit) {
        this.logger = logger
    }
}

private fun ServerConfig.toBridgeConfig(): Config {
    val c = Config()
    c.fsRoot = fsRoot
    c.blockDevicePath = blockDevice
    c.bindIP = bindIP
    c.port = port.toLong()
    c.sectorSize = sectorSize.toLong()
    c.readOnly = readOnly
    c.enableCompression = enableCompression
    c.compressionCacheSize = compressionCacheSize.toLong()
    c.peerTimeoutMinutes = peerTimeoutMinutes.toLong()
    return c
}

private fun com.udpfs.udpfsdbridge.Stats.toCounters() =
    TrafficCounters(
        bytesTx = bytesTx,
        bytesRx = bytesRx,
        avgTxThroughput = avgTxThroughput,
        avgRxThroughput = avgRxThroughput,
        totalOps = totalOps,
        errors = errors,
        reads = reads,
        writes = writes,
        packetsTx = packetsTx,
        packetsRx = packetsRx,
        retransmits = retransmits,
        nackCount = nackCount,
        outOfOrder = outOfOrder,
        peerNackCount = peerNackCount,
        resetCount = resetCount,
    )

private fun com.udpfs.udpfsdbridge.PeerStats.toCounters() =
    TrafficCounters(
        bytesTx = bytesTx,
        bytesRx = bytesRx,
        avgTxThroughput = avgTxThroughput,
        avgRxThroughput = avgRxThroughput,
        totalOps = totalOps,
        errors = errors,
        reads = reads,
        writes = writes,
        packetsTx = packetsTx,
        packetsRx = packetsRx,
        retransmits = retransmits,
        nackCount = nackCount,
        outOfOrder = outOfOrder,
        peerNackCount = peerNackCount,
        resetCount = resetCount,
    )

private fun com.udpfs.udpfsdbridge.Stats.toSnapshot(peers: List<PeerSnapshot> = emptyList()) =
    StatsSnapshot(
        running = running,
        uptimeSeconds = uptimeSeconds,
        peerCount = peerCount.toInt(),
        counters = toCounters(),
        peers = peers,
    )

private fun com.udpfs.udpfsdbridge.PeerStats.toSnapshot() =
    PeerSnapshot(
        addr = addr.orEmpty(),
        lastSeenUnix = lastSeenUnix,
        counters = toCounters(),
    )

private fun com.udpfs.udpfsdbridge.MountInfo.toSnapshot(compressionFormats: List<String> = emptyList()) =
    MountSnapshot(
        fsRoot = fsRoot.orEmpty(),
        blockDevice = blockDevice.orEmpty(),
        sectorSize = sectorSize.toInt(),
        totalSectors = totalSectors,
        totalBytes = totalBytes,
        readOnly = readOnly,
        compressionFormats = compressionFormats,
    )

private fun String.toFormatList(): List<String> = split(',').filter { it.isNotBlank() }
