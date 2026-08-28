package com.udpfs.app

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udpfs.app.core.ServerConfig
import com.udpfs.app.core.ServerRepository
import com.udpfs.app.core.Settings
import com.udpfs.app.core.WriteAccess
import kotlinx.coroutines.launch

class AppViewModel(
    repo: ServerRepository,
    private val settings: Settings,
) : ViewModel() {
    val status = repo.status
    val config = repo.config
    val stats = repo.stats
    val mount = repo.mount
    val logs = repo.logs
    val errors = repo.errors

    fun updateConfig(transform: (ServerConfig) -> ServerConfig) {
        viewModelScope.launch { settings.update(transform) }
    }

    private val writeAccessCache = mutableStateMapOf<String, WriteAccess>()

    fun cachedWriteAccess(path: String): WriteAccess? =
        if (path.isBlank()) {
            null
        } else {
            writeAccessCache[path]
        }

    fun storeWriteAccess(
        path: String,
        access: WriteAccess,
    ) {
        if (path.isNotBlank()) writeAccessCache[path] = access
    }

    fun pickFsRoot(path: String) = pickStoragePath(path) { c, p -> c.copy(fsRoot = p) }

    fun pickBlockDevice(path: String) = pickStoragePath(path) { c, p -> c.copy(blockDevice = p) }

    private fun pickStoragePath(
        path: String,
        select: (ServerConfig, String) -> ServerConfig,
    ) {
        viewModelScope.launch { settings.update { c -> select(c, path) } }
    }
}
