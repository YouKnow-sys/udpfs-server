package com.udpfs.app.core

import com.udpfs.udpfsbridge.Config

data class PeerSnapshot(
    val addr: String,
    val lastSeenUnix: Long,
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

data class StatsSnapshot(
    val running: Boolean = false,
    val uptimeSeconds: Long = 0,
    val peerCount: Int = 0,
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

data class LogLine(val seq: Long, val timeMillis: Long, val level: String, val message: String)

fun ServerConfig.toBridgeConfig(): Config {
    val c = Config()
    c.setFSRoot(if (storageMode == StorageMode.Folder) fsRoot else "")
    c.setBlockDevicePath(if (storageMode == StorageMode.DiskImage) blockDevice else "")
    c.setBindIP(bindIP)
    c.setPort(port.toLong())
    c.setSectorSize(sectorSize.toLong())
    c.setReadOnly(readOnly)
    c.setEnableCompression(enableCompression)
    c.setCompressionCacheSize(compressionCacheSize.toLong())
    c.setPeerTimeoutMinutes(peerTimeoutMinutes.toLong())
    return c
}

internal fun com.udpfs.udpfsbridge.Stats.toSnapshot(peers: List<PeerSnapshot> = emptyList()) = StatsSnapshot(
    running = running,
    uptimeSeconds = uptimeSeconds,
    peerCount = peerCount.toInt(),
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
    peers = peers,
)

internal fun com.udpfs.udpfsbridge.PeerStats.toSnapshot() = PeerSnapshot(
    addr = addr.orEmpty(),
    lastSeenUnix = lastSeenUnix,
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

internal fun com.udpfs.udpfsbridge.MountInfo.toSnapshot(compressionFormats: List<String> = emptyList()) = MountSnapshot(
    fsRoot = getFSRoot().orEmpty(),
    blockDevice = blockDevice.orEmpty(),
    sectorSize = sectorSize.toInt(),
    totalSectors = totalSectors,
    totalBytes = totalBytes,
    readOnly = readOnly,
    compressionFormats = compressionFormats,
)
