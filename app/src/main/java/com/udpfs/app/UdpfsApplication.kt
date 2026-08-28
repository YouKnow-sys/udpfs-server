package com.udpfs.app

import android.app.Application
import com.udpfs.app.core.BridgeController
import com.udpfs.app.core.ServerRepository
import com.udpfs.app.core.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class UdpfsApplication : Application() {
    val settings: Settings by lazy { Settings(this) }

    val serverRepository: ServerRepository by lazy {
        val appCtx = applicationContext
        ServerRepository(
            controller = BridgeController(),
            configSource = settings.config,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            packageName = appCtx.packageName,
            issueText = { reason -> appCtx.getString(reason.resId) },
        )
    }
}
