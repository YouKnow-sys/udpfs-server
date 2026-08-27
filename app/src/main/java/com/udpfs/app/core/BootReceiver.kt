package com.udpfs.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in BOOT_ACTIONS) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServerRepository.init(context)
                val config = ServerRepository.awaitConfig()
                if (config.autoStart && config.validate().isEmpty()) {
                    ServerService.start(context)
                }
            } catch (e: Exception) {
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val BOOT_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, ACTION_QUICKBOOT)
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}
