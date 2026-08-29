package com.udpfs.app

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udpfs.app.core.ServerConfig
import com.udpfs.app.core.ServerRepository
import com.udpfs.app.core.WriteAccess

class AppViewModel(
    private val repo: ServerRepository,
) : ViewModel() {
    val status = repo.status
    val config = repo.config
    val stats = repo.stats
    val mount = repo.mount
    val logs = repo.logs
    val errors = repo.errors

    fun updateConfig(transform: (ServerConfig) -> ServerConfig) = repo.updateConfig(transform)

    fun clearLogs() = repo.clearLogs()

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as UdpfsApplication
                    AppViewModel(app.serverRepository)
                }
            }
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
    ) = repo.updateConfig { c -> select(c, path) }
}
