package com.udpfs.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveBridgePathsTest {
    @Test
    fun `folder mode shares fsRoot and blanks the block device`() {
        val (fsRoot, blockDevice) = ServerConfig(fsRoot = "/share", blockDevice = "/leftover.iso").effectiveBridgePaths()

        assertEquals("/share", fsRoot)
        assertEquals("", blockDevice)
    }

    @Test
    fun `disk image mode blanks fsRoot and shares the image`() {
        val (fsRoot, blockDevice) =
            ServerConfig(storageMode = StorageMode.DiskImage, fsRoot = "/leftover", blockDevice = "/game.iso")
                .effectiveBridgePaths()

        assertEquals("", fsRoot)
        assertEquals("/game.iso", blockDevice)
    }
}
