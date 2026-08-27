package com.udpfs.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udpfs.app.core.ServerConfig
import com.udpfs.app.core.ServerRepository
import com.udpfs.app.core.Settings
import kotlinx.coroutines.launch

class AppViewModel(
    private val repo: ServerRepository,
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
}
