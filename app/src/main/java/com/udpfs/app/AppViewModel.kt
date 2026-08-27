package com.udpfs.app

import kotlinx.coroutines.launch
import com.udpfs.app.core.Settings
import com.udpfs.app.core.ServerRepository
import com.udpfs.app.core.ServerConfig
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel

class AppViewModel(private val settings: Settings) : ViewModel() {

    val status = ServerRepository.status
    val config = ServerRepository.config
    val stats = ServerRepository.stats
    val mount = ServerRepository.mount
    val logs = ServerRepository.logs

    fun updateConfig(transform: (ServerConfig) -> ServerConfig) {
        viewModelScope.launch { settings.update(transform) }
    }
}

class AppViewModelFactory(private val settings: Settings) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(settings) as T
}
