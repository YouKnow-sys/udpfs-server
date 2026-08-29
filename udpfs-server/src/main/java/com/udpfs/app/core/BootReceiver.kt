package com.udpfs.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.udpfs.app.UdpfsApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in BOOT_ACTIONS) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = (context.applicationContext as UdpfsApplication).serverRepository
                val config = repo.awaitConfig()
                if (config.autoStart && config.validate().isEmpty()) {
                    ServerService.start(context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("BootReceiver", "autostart skipped", e)
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
