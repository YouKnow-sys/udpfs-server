package com.udpfs.app.core

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMappingTest {
    @Test
    fun `round-trips a non-default config`() {
        val config =
            ServerConfig(
                storageMode = StorageMode.DiskImage,
                fsRoot = "/old-root",
                blockDevice = "/storage/1A2B/game.iso",
                bindIP = "192.168.1.2",
                port = 1234,
                sectorSize = 4096,
                readOnly = true,
                enableCompression = true,
                compressionCacheSize = 64,
                peerTimeoutMinutes = 30,
                showStats = false,
                autoStart = true,
            )
        val prefs = mutablePreferencesOf()
        config.writeTo(prefs)

        assertEquals(config, prefs.toServerConfig())
    }

    @Test
    fun `empty preferences fall back to the constructor defaults`() {
        assertEquals(ServerConfig(), mutablePreferencesOf().toServerConfig())
    }

    @Test
    fun `unknown keys do not leak into the config`() {
        assertEquals(
            ServerConfig(),
            mutablePreferencesOf(stringPreferencesKey("something_else") to "x").toServerConfig(),
        )
    }

    @Test
    fun `an unknown storage mode falls back to folder`() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("storage_mode") to "Garbage")

        assertEquals(StorageMode.Folder, prefs.toServerConfig().storageMode)
    }

    @Test
    fun `disk image mode persists and reads back`() {
        val prefs = mutablePreferencesOf()
        ServerConfig(storageMode = StorageMode.DiskImage, blockDevice = "/a.iso").writeTo(prefs)

        val loaded = prefs.toServerConfig()
        assertEquals(StorageMode.DiskImage, loaded.storageMode)
        assertEquals("/a.iso", loaded.blockDevice)
    }
}
