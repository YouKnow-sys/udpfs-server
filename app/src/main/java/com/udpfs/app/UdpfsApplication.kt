package com.udpfs.app

import android.app.Application
import com.udpfs.app.core.ServerRepository
import com.udpfs.app.core.Settings
import com.udpfs.app.core.UdpfsBridgeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class UdpfsApplication : Application() {
    val settings: Settings by lazy { Settings(this) }

    val serverRepository: ServerRepository by lazy {
        val appCtx = applicationContext
        ServerRepository(
            controller = UdpfsBridgeController(),
            configSource = settings.config,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            packageName = appCtx.packageName,
            issueText = { reason -> appCtx.getString(reason.resId) },
        )
    }
}
