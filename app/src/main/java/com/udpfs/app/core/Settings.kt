package com.udpfs.app.core

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import com.udpfs.app.R
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.annotation.StringRes
import android.content.Context

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
) {
    companion object {
        val SECTOR_SIZES = listOf(512, 2048, 4096)
    }
}

enum class ConfigIssueReason(@StringRes val resId: Int) {
    MissingFolder(R.string.config_issue_missing_folder),
    MissingBlockDevice(R.string.config_issue_missing_block_device),
    PortRange(R.string.config_issue_port_range),
    SectorSize(R.string.config_issue_sector_size),
    PeerTimeoutRange(R.string.config_issue_peer_timeout_range),
    CompressionCache(R.string.config_issue_compression_cache),
}

data class ConfigIssue(val reason: ConfigIssueReason)

fun ServerConfig.validate(): List<ConfigIssue> = buildList {
    when (storageMode) {
        StorageMode.Folder -> if (fsRoot.isBlank()) add(ConfigIssue(ConfigIssueReason.MissingFolder))
        StorageMode.DiskImage -> if (blockDevice.isBlank()) add(ConfigIssue(ConfigIssueReason.MissingBlockDevice))
    }
    if (port !in 1..65535) add(ConfigIssue(ConfigIssueReason.PortRange))
    if (sectorSize !in ServerConfig.SECTOR_SIZES) add(ConfigIssue(ConfigIssueReason.SectorSize))
    if (peerTimeoutMinutes !in 1..1440) add(ConfigIssue(ConfigIssueReason.PeerTimeoutRange))
    if (enableCompression && compressionCacheSize < 1) add(ConfigIssue(ConfigIssueReason.CompressionCache))
}

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

private val DEFAULTS = ServerConfig()

private fun Preferences.toServerConfig() = ServerConfig(
    storageMode = this[Keys.storageMode].toStorageMode(),
    fsRoot = this[Keys.fsRoot] ?: DEFAULTS.fsRoot,
    blockDevice = this[Keys.blockDevice] ?: DEFAULTS.blockDevice,
    bindIP = this[Keys.bindIP] ?: DEFAULTS.bindIP,
    port = this[Keys.port] ?: DEFAULTS.port,
    sectorSize = this[Keys.sectorSize] ?: DEFAULTS.sectorSize,
    readOnly = this[Keys.readOnly] ?: DEFAULTS.readOnly,
    enableCompression = this[Keys.enableCompression] ?: DEFAULTS.enableCompression,
    compressionCacheSize = this[Keys.compressionCacheSize] ?: DEFAULTS.compressionCacheSize,
    peerTimeoutMinutes = this[Keys.peerTimeoutMinutes] ?: DEFAULTS.peerTimeoutMinutes,
    showStats = this[Keys.showStats] ?: DEFAULTS.showStats,
    autoStart = this[Keys.autoStart] ?: DEFAULTS.autoStart,
)

class Settings(private val context: Context) {

    val config: Flow<ServerConfig> = context.dataStore.data.map { it.toServerConfig() }

    suspend fun update(transform: (ServerConfig) -> ServerConfig) {
        context.dataStore.edit { p ->
            val c = transform(p.toServerConfig())
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
