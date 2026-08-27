package com.udpfs.app.core

import com.udpfs.udpfsbridge.Udpfsbridge

interface BridgeController {
    fun start(config: ServerConfig)

    fun stop()

    fun stats(): StatsSnapshot

    fun mount(): MountSnapshot

    fun setLogger(
        logger: (
            level: String,
            message: String,
        ) -> Unit,
    )
}

class UdpfsBridgeController : BridgeController {
    private val controller = Udpfsbridge.newServer()

    override fun start(config: ServerConfig) = controller.start(config.toBridgeConfig())

    override fun stop() = controller.stop()

    override fun stats(): StatsSnapshot {
        val s = controller.stats()
        val peers =
            buildList {
                repeat(s.peerCount.toInt()) { i ->
                    controller.peer(i.toLong())?.let { add(it.toSnapshot()) }
                }
            }
        return s.toSnapshot(peers)
    }

    override fun mount(): MountSnapshot = controller.mountInfo().toSnapshot(controller.compressionFormats().toFormatList())

    override fun setLogger(
        logger: (
            level: String,
            message: String,
        ) -> Unit,
    ) {
        controller.setLogger { level, message -> logger(level, message) }
    }
}

private fun String.toFormatList(): List<String> = split(',').filter { it.isNotBlank() }
