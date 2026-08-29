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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerService : Service() {
    private lateinit var repo: ServerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastStartId = 0
    private var observing = false
    private val notifications: NotificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private var lastPeers = 0

    override fun onCreate() {
        repo = (application as UdpfsApplication).serverRepository
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
                    buildNotification(initial),
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
            repo.status.collectLatest { st ->
                when (st) {
                    is ServerStatus.Idle -> {
                        stopSelfQuietly()
                    }

                    is ServerStatus.Running -> {
                        var first = true
                        while (true) {
                            val peers =
                                try {
                                    withContext(Dispatchers.IO) { repo.peerCount() }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    lastPeers
                                }
                            if (first || peers != lastPeers) {
                                lastPeers = peers
                                notifications.notify(NOTIFICATION_ID, buildNotification(st, peers))
                            }
                            first = false
                            delay(PEER_POLL_MS)
                        }
                    }

                    else -> {
                        lastPeers = 0
                        notifications.notify(NOTIFICATION_ID, buildNotification(st))
                    }
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
        peers: Int = 0,
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
                    if (peers > 0) {
                        resources.getQuantityString(R.plurals.notif_peers_connected, peers, peers)
                    } else {
                        getString(R.string.notif_running)
                    }
                }

                is ServerStatus.Starting -> {
                    getString(R.string.notif_starting)
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

        private const val PEER_POLL_MS = 2_000L

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
