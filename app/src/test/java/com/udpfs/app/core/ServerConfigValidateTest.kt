package com.udpfs.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigValidateTest {
    private val valid = ServerConfig(fsRoot = "/storage/emulated/0/share")

    @Test
    fun `a complete folder config has no issues`() {
        assertTrue(valid.validate().isEmpty())
    }

    @Test
    fun `a complete disk image config has no issues`() {
        val config =
            ServerConfig(
                storageMode = StorageMode.DiskImage,
                blockDevice = "/storage/emulated/0/game.iso",
            )
        assertTrue(config.validate().isEmpty())
    }

    @Test
    fun `blank storage paths are reported per mode`() {
        assertEquals(
            listOf(ConfigIssueReason.MissingFolder),
            ServerConfig().validate().map { it.reason },
        )
        assertEquals(
            listOf(ConfigIssueReason.MissingBlockDevice),
            ServerConfig(storageMode = StorageMode.DiskImage).validate().map { it.reason },
        )
    }

    @Test
    fun `port must be inside the valid range`() {
        assertEquals(
            listOf(ConfigIssueReason.PortRange),
            valid.copy(port = 0).validate().map { it.reason },
        )
        assertEquals(
            listOf(ConfigIssueReason.PortRange),
            valid.copy(port = 65536).validate().map { it.reason },
        )
        assertTrue(valid.copy(port = 1).validate().isEmpty())
        assertTrue(valid.copy(port = 65535).validate().isEmpty())
    }

    @Test
    fun `sector size must be one of the supported sizes`() {
        assertEquals(
            listOf(ConfigIssueReason.SectorSize),
            valid.copy(sectorSize = 1024).validate().map { it.reason },
        )
        ServerConfig.SECTOR_SIZES.forEach { size ->
            assertTrue("sector size $size should be valid", valid.copy(sectorSize = size).validate().isEmpty())
        }
    }

    @Test
    fun `peer timeout must be between one minute and one day`() {
        assertEquals(
            listOf(ConfigIssueReason.PeerTimeoutRange),
            valid.copy(peerTimeoutMinutes = 0).validate().map { it.reason },
        )
        assertEquals(
            listOf(ConfigIssueReason.PeerTimeoutRange),
            valid.copy(peerTimeoutMinutes = 1441).validate().map { it.reason },
        )
        assertTrue(valid.copy(peerTimeoutMinutes = 1).validate().isEmpty())
        assertTrue(valid.copy(peerTimeoutMinutes = 1440).validate().isEmpty())
    }

    @Test
    fun `compression requires a positive cache size`() {
        assertEquals(
            listOf(ConfigIssueReason.CompressionCache),
            valid.copy(enableCompression = true, compressionCacheSize = 0).validate().map { it.reason },
        )
        assertTrue(valid.copy(enableCompression = true, compressionCacheSize = 1).validate().isEmpty())
        assertTrue(valid.copy(compressionCacheSize = 0).validate().isEmpty())
    }

    @Test
    fun `multiple problems are all reported`() {
        val issues = ServerConfig(port = 0, sectorSize = 999).validate().map { it.reason }
        assertEquals(
            listOf(
                ConfigIssueReason.MissingFolder,
                ConfigIssueReason.PortRange,
                ConfigIssueReason.SectorSize,
            ),
            issues,
        )
    }
}
