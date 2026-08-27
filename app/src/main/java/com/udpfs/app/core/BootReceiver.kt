package com.udpfs.app.core

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, ACTION_QUICKBOOT)) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServerRepository.init(context)
                val config = ServerRepository.awaitConfig()
                if (config.autoStart && config.validate().isEmpty()) {
                    ServerService.start(context)
                }
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}
