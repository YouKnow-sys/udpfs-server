package com.udpfs.app.core

import com.udpfs.udpfsbridge.Config
import com.udpfs.udpfsbridge.Udpfsbridge

data class TrafficCounters(
    val bytesTx: Long,
    val bytesRx: Long,
    val avgTxThroughput: Double,
    val avgRxThroughput: Double,
    val totalOps: Long,
    val errors: Long,
    val reads: Long,
    val writes: Long,
    val packetsTx: Long,
    val packetsRx: Long,
    val retransmits: Long,
    val nackCount: Long,
    val outOfOrder: Long,
    val peerNackCount: Long,
    val resetCount: Long,
)

private val EMPTY_COUNTERS =
    TrafficCounters(
        bytesTx = 0,
        bytesRx = 0,
        avgTxThroughput = 0.0,
        avgRxThroughput = 0.0,
        totalOps = 0,
        errors = 0,
        reads = 0,
        writes = 0,
        packetsTx = 0,
        packetsRx = 0,
        retransmits = 0,
        nackCount = 0,
        outOfOrder = 0,
        peerNackCount = 0,
        resetCount = 0,
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
    val counters: TrafficCounters = EMPTY_COUNTERS,
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
    val level: String,
    val message: String,
)

class BridgeController {
    private val controller = Udpfsbridge.newServer()

    fun start(config: ServerConfig) = controller.start(config.toBridgeConfig())

    fun stop() = controller.stop()

    fun stats(): StatsSnapshot {
        val s = controller.stats()
        val peers =
            buildList {
                repeat(s.peerCount.toInt()) { i ->
                    controller.peer(i.toLong())?.let { add(it.toSnapshot()) }
                }
            }
        return s.toSnapshot(peers)
    }

    fun mount(): MountSnapshot = controller.mountInfo().toSnapshot(controller.compressionFormats().toFormatList())

    fun setLogger(
        logger: (
            level: String,
            message: String,
        ) -> Unit,
    ) {
        controller.setLogger { level, message -> logger(level, message) }
    }
}

fun ServerConfig.toBridgeConfig(): Config {
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

private fun com.udpfs.udpfsbridge.Stats.toCounters() =
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

private fun com.udpfs.udpfsbridge.PeerStats.toCounters() =
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

internal fun com.udpfs.udpfsbridge.Stats.toSnapshot(peers: List<PeerSnapshot> = emptyList()) =
    StatsSnapshot(
        running = running,
        uptimeSeconds = uptimeSeconds,
        peerCount = peerCount.toInt(),
        counters = toCounters(),
        peers = peers,
    )

internal fun com.udpfs.udpfsbridge.PeerStats.toSnapshot() =
    PeerSnapshot(
        addr = addr.orEmpty(),
        lastSeenUnix = lastSeenUnix,
        counters = toCounters(),
    )

internal fun com.udpfs.udpfsbridge.MountInfo.toSnapshot(compressionFormats: List<String> = emptyList()) =
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
