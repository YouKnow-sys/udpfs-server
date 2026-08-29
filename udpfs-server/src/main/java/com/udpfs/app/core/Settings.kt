package com.udpfs.app.core

import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.udpfs.app.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ServerConfig(
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

enum class ConfigIssueReason(
    @StringRes val resId: Int,
) {
    MissingStorage(R.string.config_issue_missing_storage),
    PortRange(R.string.config_issue_port_range),
    SectorSize(R.string.config_issue_sector_size),
    PeerTimeoutRange(R.string.config_issue_peer_timeout_range),
    CompressionCache(R.string.config_issue_compression_cache),
}

fun ServerConfig.validate(): List<ConfigIssueReason> =
    buildList {
        if (fsRoot.isBlank() && blockDevice.isBlank()) add(ConfigIssueReason.MissingStorage)
        if (port !in 1..65535) add(ConfigIssueReason.PortRange)
        if (sectorSize !in ServerConfig.SECTOR_SIZES) add(ConfigIssueReason.SectorSize)
        if (peerTimeoutMinutes !in 1..1440) add(ConfigIssueReason.PeerTimeoutRange)
        if (enableCompression && compressionCacheSize < 1) add(ConfigIssueReason.CompressionCache)
    }

private object Keys {
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

internal fun Preferences.toServerConfig() =
    ServerConfig(
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

class Settings(
    private val dataStore: DataStore<Preferences>,
) {
    val config: Flow<ServerConfig> = dataStore.data.map { it.toServerConfig() }

    suspend fun set(config: ServerConfig) {
        dataStore.edit { config.writeTo(it) }
    }
}

internal fun ServerConfig.writeTo(p: MutablePreferences) {
    p[Keys.fsRoot] = fsRoot
    p[Keys.blockDevice] = blockDevice
    p[Keys.bindIP] = bindIP
    p[Keys.port] = port
    p[Keys.sectorSize] = sectorSize
    p[Keys.readOnly] = readOnly
    p[Keys.enableCompression] = enableCompression
    p[Keys.compressionCacheSize] = compressionCacheSize
    p[Keys.peerTimeoutMinutes] = peerTimeoutMinutes
    p[Keys.showStats] = showStats
    p[Keys.autoStart] = autoStart
}
