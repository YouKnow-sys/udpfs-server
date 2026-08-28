package com.udpfs.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udpfs.app.core.ServerConfig
import com.udpfs.app.core.ServerRepository
import com.udpfs.app.core.Settings
import com.udpfs.app.core.WRITE_PROBE_TIMEOUT_MS
import com.udpfs.app.core.WriteAccess
import com.udpfs.app.core.probeWriteAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

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

    fun pickFsRoot(path: String) = pickStoragePath(path, ServerConfig::fsRoot) { c, p -> c.copy(fsRoot = p) }

    fun pickBlockDevice(path: String) = pickStoragePath(path, ServerConfig::blockDevice) { c, p -> c.copy(blockDevice = p) }

    private fun pickStoragePath(
        path: String,
        current: (ServerConfig) -> String,
        select: (ServerConfig, String) -> ServerConfig,
    ) {
        if (path.isBlank()) {
            viewModelScope.launch { settings.update { c -> select(c, "") } }
            return
        }
        viewModelScope.launch {
            val access =
                withTimeoutOrNull(WRITE_PROBE_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { probeWriteAccess(File(path)) }
                }
            settings.update { c ->
                if (current(c) == path) {
                    select(c, path).copy(readOnly = c.readOnly || access == WriteAccess.READ_ONLY)
                } else {
                    c
                }
            }
        }
    }
}
