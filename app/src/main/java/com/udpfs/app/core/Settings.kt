package com.udpfs.app.core

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import android.content.Context

// TODO: move to proto datastore eventually
private val Context.dataStore by preferencesDataStore(name = "settings")

enum class StorageMode { Folder, DiskImage }

data class ServerConfig(
    val storageMode: StorageMode = StorageMode.Folder,
    val fsRoot: String = "",
    val blockDevice: String = "",
    val bindIP: String = "",
    val port: Int = 62966,
    val sectorSize: Int = 512,
    val readOnly: Boolean = false,
    val enableCompression: Boolean = false,
    val compressionCacheSize: Int = 32,
    val peerTimeoutMinutes: Int = 60,
    val showStats: Boolean = true,
    val autoStart: Boolean = false,
)

data class ConfigIssue(val message: String)

fun ServerConfig.validate(): List<ConfigIssue> = buildList {
    when (storageMode) {
        StorageMode.Folder -> if (fsRoot.isBlank()) add(ConfigIssue("Select a folder to share"))
        StorageMode.DiskImage -> if (blockDevice.isBlank()) add(ConfigIssue("Select a disk image to share"))
    }
    if (port !in 1..65535) add(ConfigIssue("Port must be between 1 and 65535"))
    if (sectorSize !in setOf(512, 2048, 4096)) add(ConfigIssue("Sector size must be 512, 2048 or 4096"))
    if (peerTimeoutMinutes !in 1..1440) add(ConfigIssue("Peer timeout must be between 1 and 1440 minutes"))
    if (enableCompression && compressionCacheSize < 1) add(ConfigIssue("Compression cache must be at least 1 block"))
}

class Settings(private val context: Context) {

    private object Keys {
        val storageMode = stringPreferencesKey("storage_mode")
        val fsRoot = stringPreferencesKey("fs_root")
        val blockDevice = stringPreferencesKey("block_device")
        val bindIP = stringPreferencesKey("bind_ip")
        val port = intPreferencesKey("port")
        val sectorSize = intPreferencesKey("sector_size")
        val readOnly = booleanPreferencesKey("read_only")
        val enableCompression = booleanPreferencesKey("enable_compression")
        val compressionCacheSize = intPreferencesKey("compression_cache_size")
        val peerTimeoutMinutes = intPreferencesKey("peer_timeout_minutes")
        val showStats = booleanPreferencesKey("show_stats")
        val autoStart = booleanPreferencesKey("auto_start")
    }

    val config: Flow<ServerConfig> = context.dataStore.data.map { p ->
        ServerConfig(
            storageMode = p[Keys.storageMode].toStorageMode(),
            fsRoot = p[Keys.fsRoot] ?: "",
            blockDevice = p[Keys.blockDevice] ?: "",
            bindIP = p[Keys.bindIP] ?: "",
            port = p[Keys.port] ?: 62966,
            sectorSize = p[Keys.sectorSize] ?: 512,
            readOnly = p[Keys.readOnly] ?: false,
            enableCompression = p[Keys.enableCompression] ?: false,
            compressionCacheSize = p[Keys.compressionCacheSize] ?: 32,
            peerTimeoutMinutes = p[Keys.peerTimeoutMinutes] ?: 60,
            showStats = p[Keys.showStats] ?: true,
            autoStart = p[Keys.autoStart] ?: false,
        )
    }

    suspend fun update(transform: (ServerConfig) -> ServerConfig) {
        context.dataStore.edit { p ->
            val c = transform(
                ServerConfig(
                    storageMode = p[Keys.storageMode].toStorageMode(),
                    fsRoot = p[Keys.fsRoot] ?: "",
                    blockDevice = p[Keys.blockDevice] ?: "",
                    bindIP = p[Keys.bindIP] ?: "",
                    port = p[Keys.port] ?: 62966,
                    sectorSize = p[Keys.sectorSize] ?: 512,
                    readOnly = p[Keys.readOnly] ?: false,
                    enableCompression = p[Keys.enableCompression] ?: false,
                    compressionCacheSize = p[Keys.compressionCacheSize] ?: 32,
                    peerTimeoutMinutes = p[Keys.peerTimeoutMinutes] ?: 60,
                    showStats = p[Keys.showStats] ?: true,
                    autoStart = p[Keys.autoStart] ?: false,
                )
            )
            p[Keys.storageMode] = c.storageMode.name
            p[Keys.fsRoot] = c.fsRoot
            p[Keys.blockDevice] = c.blockDevice
            p[Keys.bindIP] = c.bindIP
            p[Keys.port] = c.port
            p[Keys.sectorSize] = c.sectorSize
            p[Keys.readOnly] = c.readOnly
            p[Keys.enableCompression] = c.enableCompression
            p[Keys.compressionCacheSize] = c.compressionCacheSize
            p[Keys.peerTimeoutMinutes] = c.peerTimeoutMinutes
            p[Keys.showStats] = c.showStats
            p[Keys.autoStart] = c.autoStart
        }
    }
}

private fun String?.toStorageMode(): StorageMode =
    if (this == StorageMode.DiskImage.name) StorageMode.DiskImage else StorageMode.Folder
