package com.udpfs.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.udpfs.app.MainActivity
import com.udpfs.app.R
import com.udpfs.app.UdpfsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerService : Service() {
    private lateinit var repo: ServerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastStartId = 0
    private var observing = false
    private lateinit var notifications: NotificationManager

    private var lastIP = ""

    override fun onCreate() {
        repo = (application as UdpfsApplication).serverRepository
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        lastStartId = startId
        val status = repo.status.value
        return when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                val initial =
                    if (status is ServerStatus.Idle || status is ServerStatus.Starting) {
                        ServerStatus.Starting
                    } else {
                        status
                    }
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(initial, lastIP),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                if (status is ServerStatus.Idle) repo.start()
                observeStatus()
                START_STICKY
            }

            ACTION_STOP -> {
                if (status is ServerStatus.Idle) stopSelfQuietly() else repo.stop()
                START_NOT_STICKY
            }

            else -> {
                START_NOT_STICKY
            }
        }
    }

    private fun observeStatus() {
        if (observing) return
        observing = true
        scope.launch {
            repo.status.collect { st ->
                if (st is ServerStatus.Idle) {
                    stopSelfQuietly()
                } else {
                    val ip = withContext(Dispatchers.IO) { repo.localIP() }
                    if (ip.isNotEmpty()) lastIP = ip
                    notifications.notify(NOTIFICATION_ID, buildNotification(st, ip))
                }
            }
        }
    }

    private fun stopSelfQuietly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(
        status: ServerStatus,
        ip: String,
    ): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, ServerService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val text =
            when (status) {
                is ServerStatus.Running -> {
                    getString(
                        R.string.notif_running,
                        "$ip:${repo.activeConfig().port}",
                    )
                }

                is ServerStatus.Starting -> {
                    getString(R.string.notif_starting)
                }

                is ServerStatus.Stopping -> {
                    getString(R.string.notif_stopping)
                }

                is ServerStatus.Idle -> {
                    getString(R.string.notif_idle)
                }
            }

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.notif_stop), stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "server"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.udpfs.app.action.START"
        const val ACTION_STOP = "com.udpfs.app.action.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ServerService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ServerService::class.java).setAction(ACTION_STOP))
        }
    }
}
