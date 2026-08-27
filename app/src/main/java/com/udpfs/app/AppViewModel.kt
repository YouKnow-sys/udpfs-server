package com.udpfs.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.udpfs.app.core.ServerConfig
import com.udpfs.app.core.ServerRepository
import com.udpfs.app.core.Settings
import kotlinx.coroutines.launch

class AppViewModel(
    private val settings: Settings,
) : ViewModel() {
    val status = ServerRepository.status
    val config = ServerRepository.config
    val stats = ServerRepository.stats
    val mount = ServerRepository.mount
    val logs = ServerRepository.logs

    fun updateConfig(transform: (ServerConfig) -> ServerConfig) {
        val current = config.value
        if (transform(current) == current) return
        viewModelScope.launch { settings.update(transform) }
    }
}

class AppViewModelFactory(
    private val settings: Settings,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(settings) as T
}
