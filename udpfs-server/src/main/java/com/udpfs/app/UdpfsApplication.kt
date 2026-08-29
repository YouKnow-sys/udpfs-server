package com.udpfs.app

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.udpfs.app.core.BridgeController
import com.udpfs.app.core.ServerRepository
import com.udpfs.app.core.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class UdpfsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        serverRepository
    }

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = { File(filesDir, "datastore/settings.preferences_pb") },
        )
    }

    val settings: Settings by lazy { Settings(dataStore) }

    val serverRepository: ServerRepository by lazy {
        val appCtx = applicationContext
        ServerRepository(
            controller = BridgeController(),
            configSource = settings.config,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            issueText = { reason -> appCtx.getString(reason.resId) },
            messageText = { message -> appCtx.getString(message.resId) },
            persistConfig = { settings.set(it) },
        )
    }
}
