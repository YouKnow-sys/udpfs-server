@file:Suppress("FunctionName")

package com.udpfs.app.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udpfs.app.AppViewModel
import com.udpfs.app.R
import com.udpfs.app.UdpfsApplication
import com.udpfs.app.core.Formatters
import com.udpfs.app.core.MountSnapshot
import com.udpfs.app.core.Permissions
import com.udpfs.app.core.ServerConfig
import com.udpfs.app.core.ServerService
import com.udpfs.app.core.ServerStatus
import com.udpfs.app.core.StorageMode
import com.udpfs.app.ui.components.InfoRow
import com.udpfs.app.ui.components.SupportingText
import com.udpfs.app.ui.components.focusRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ServerScreen(
    vm: AppViewModel,
    isTv: Boolean = false,
) {
    val context = LocalContext.current
    val repo = (context.applicationContext as UdpfsApplication).serverRepository
    val status by vm.status.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val mount by vm.mount.collectAsStateWithLifecycle()

    var storageGranted by remember { mutableStateOf(Permissions.hasStorageAccess(context)) }
    var ip by remember { mutableStateOf("") }
    val running = status is ServerStatus.Running
    LaunchedEffect(running) {
        ip = withContext(Dispatchers.IO) { repo.localIP() }
    }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        storageGranted = Permissions.hasStorageAccess(context)
        scope.launch { ip = withContext(Dispatchers.IO) { repo.localIP() } }
    }

    val requestStoragePerms =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            storageGranted = Permissions.hasStorageAccess(context)
        }
    val requestNotifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            ServerService.start(context)
        }

    val toggle: () -> Unit = {
        when (status) {
            ServerStatus.Idle -> {
                when {
                    !storageGranted -> {
                        requestStorage(context, requestStoragePerms)
                    }

                    Permissions.needsNotificationPermission(context) -> {
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    else -> {
                        ServerService.start(context)
                    }
                }
            }

            ServerStatus.Running -> {
                ServerService.stop(context)
            }

            else -> {}
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (isTv) 48.dp else 24.dp,
                    vertical = if (isTv) 32.dp else 16.dp,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 16.dp),
    ) {
        if (!storageGranted) {
            StorageGate(onRequest = { requestStorage(context, requestStoragePerms) })
        }

        if (isTv) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(56.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    PowerButton(
                        running = running,
                        busy = status is ServerStatus.Starting || status is ServerStatus.Stopping,
                        size = 252.dp,
                        onToggle = toggle,
                    )
                    StatusText(status, vm)
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (running) ConnectCard(ip = ip, port = config.port, startAligned = true)
                    MountCard(mount, config)
                }
            }
        } else {
            PowerButton(
                running = running,
                busy = status is ServerStatus.Starting || status is ServerStatus.Stopping,
                size = 216.dp,
                onToggle = toggle,
            )
            StatusText(status, vm)
            if (running) ConnectCard(ip = ip, port = config.port, startAligned = false)
            MountCard(mount, config)
        }
    }
}

private fun requestStorage(
    context: Context,
    launcher: ActivityResultLauncher<Array<String>>,
) {
    val legacy = Permissions.legacyStoragePermissions()
    if (legacy != null) launcher.launch(legacy) else context.startActivity(Permissions.storageSettingsIntent(context))
}

@Composable
private fun StatusText(
    status: ServerStatus,
    vm: AppViewModel,
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    Text(
        text = statusLabel(status, stats.uptimeSeconds),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun statusLabel(
    status: ServerStatus,
    uptimeSeconds: Long,
): String =
    when (status) {
        ServerStatus.Idle -> {
            stringResource(R.string.status_idle)
        }

        ServerStatus.Starting -> {
            stringResource(R.string.status_starting)
        }

        ServerStatus.Running -> {
            if (uptimeSeconds > 0) {
                stringResource(R.string.status_running, Formatters.duration(uptimeSeconds))
            } else {
                stringResource(R.string.status_running_plain)
            }
        }

        ServerStatus.Stopping -> {
            stringResource(R.string.status_stopping)
        }
    }

@Composable
private fun PowerButton(
    running: Boolean,
    busy: Boolean,
    size: Dp,
    onToggle: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val container = if (running) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (running) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val ring = if (running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Box(
        modifier =
            Modifier
                .size(size)
                .shadow(14.dp, CircleShape, ambientColor = ring, spotColor = ring)
                .focusRing(CircleShape)
                .clip(CircleShape)
                .background(container)
                .border(5.dp, ring, CircleShape)
                .clickable(enabled = !busy) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(if (size > 230.dp) 84.dp else 72.dp),
                tint = content,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (running) stringResource(R.string.action_stop) else stringResource(R.string.action_start),
                style = MaterialTheme.typography.titleLarge,
                color = content,
            )
        }
    }
}

@Composable
private fun ConnectCard(
    ip: String,
    port: Int,
    startAligned: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(if (startAligned) 24.dp else 20.dp),
            horizontalAlignment = if (startAligned) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.connect_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$ip:$port",
                style = if (startAligned) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
            )
            SupportingText(stringResource(R.string.connect_hint))
        }
    }
}

@Composable
private fun StorageGate(onRequest: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.storage_title), style = MaterialTheme.typography.titleSmall)
                SupportingText(stringResource(R.string.storage_hint))
            }
            Button(onClick = onRequest) { Text(stringResource(R.string.storage_grant)) }
        }
    }
}

@Composable
private fun MountCard(
    mount: MountSnapshot,
    config: ServerConfig,
) {
    val folderMode = config.storageMode == StorageMode.Folder
    val root = mount.fsRoot.ifEmpty { if (folderMode) config.fsRoot else "" }
    val image = mount.blockDevice.ifEmpty { if (!folderMode) config.blockDevice else "" }
    if (root.isBlank() && image.isBlank()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.mount_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (root.isNotBlank()) InfoRow(stringResource(R.string.mount_root), root)
            if (image.isNotBlank()) {
                InfoRow(stringResource(R.string.mount_block_device), image)
                if (mount.totalBytes > 0) {
                    InfoRow(stringResource(R.string.mount_size), Formatters.bytes(mount.totalBytes))
                }
            }
            InfoRow(
                stringResource(R.string.mount_mode),
                if (mount.readOnly || config.readOnly) stringResource(R.string.mount_ro) else stringResource(R.string.mount_rw),
            )
            if (mount.compressionFormats.isNotEmpty()) {
                InfoRow(stringResource(R.string.mount_compression), mount.compressionFormats.joinToString(", "))
            }
        }
    }
}
