package com.udpfs.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udpfs.app.AppViewModel
import com.udpfs.app.R
import com.udpfs.app.core.Formatters
import com.udpfs.app.core.MountSnapshot
import com.udpfs.app.core.Permissions
import com.udpfs.app.core.ServerConfig
import com.udpfs.app.core.ServerService
import com.udpfs.app.core.ServerStatus
import com.udpfs.app.ui.components.FOCUS_STIFFNESS
import com.udpfs.app.ui.components.InfoRow
import com.udpfs.app.ui.components.SupportingText
import com.udpfs.app.ui.format.formatBytes
import com.udpfs.app.ui.format.formatDuration
import kotlinx.coroutines.launch

@Composable
fun ServerScreen(
    vm: AppViewModel,
    isTv: Boolean = false,
) {
    val context = LocalContext.current
    val status by vm.status.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val mount by vm.mount.collectAsStateWithLifecycle()

    var storageGranted by remember { mutableStateOf(Permissions.hasStorageAccess(context)) }
    var ip by remember { mutableStateOf("") }
    val running = status is ServerStatus.Running
    val busy = status is ServerStatus.Starting || status is ServerStatus.Stopping
    LaunchedEffect(running) { ip = vm.localIP() }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        storageGranted = Permissions.hasStorageAccess(context)
        scope.launch { ip = vm.localIP() }
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
                        Permissions.requestStorageAccess(context, requestStoragePerms)
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

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isTv) 48.dp else 24.dp,
                    vertical = if (isTv) 32.dp else 16.dp,
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement =
                if (isTv) {
                    Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
                } else {
                    Arrangement.spacedBy(16.dp)
                },
        ) {
            if (!storageGranted) {
                StorageGate(onRequest = { Permissions.requestStorageAccess(context, requestStoragePerms) })
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
                            busy = busy,
                            diameter = PowerDiameterTv,
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
                    busy = busy,
                    diameter = PowerDiameterMobile,
                    onToggle = toggle,
                )
                StatusText(status, vm)
                if (running) ConnectCard(ip = ip, port = config.port, startAligned = false)
                MountCard(mount, config)
            }
        }
    }
}

@Composable
private fun StatusText(
    status: ServerStatus,
    vm: AppViewModel,
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    Text(
        text =
            when (status) {
                ServerStatus.Idle -> {
                    stringResource(R.string.status_idle)
                }

                ServerStatus.Starting -> {
                    stringResource(R.string.status_starting)
                }

                ServerStatus.Running -> {
                    if (stats.uptimeSeconds > 0) {
                        stringResource(R.string.status_running, formatDuration(Formatters.duration(stats.uptimeSeconds)))
                    } else {
                        stringResource(R.string.status_running_plain)
                    }
                }

                ServerStatus.Stopping -> {
                    stringResource(R.string.status_stopping)
                }
            },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val PowerDiameterTv = 252.dp
private val PowerDiameterMobile = 216.dp
private const val FOCUSED_POWER_SCALE = 1.03f
private val PowerIconLarge = 84.dp
private val PowerIconSmall = 72.dp

@Composable
private fun PowerButton(
    running: Boolean,
    busy: Boolean,
    diameter: Dp,
    onToggle: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val container = if (running) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (running) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val ring = if (running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    var focused by remember { mutableStateOf(false) }
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 6.dp,
        animationSpec = spring(Spring.DampingRatioNoBouncy, FOCUS_STIFFNESS),
        label = "powerRing",
    )

    Box(
        modifier =
            Modifier
                .size(diameter)
                .graphicsLayer {
                    if (focused) {
                        scaleX = FOCUSED_POWER_SCALE
                        scaleY = FOCUSED_POWER_SCALE
                    }
                }.clip(CircleShape)
                .background(container)
                .drawBehind {
                    val w = ringWidth.toPx()
                    drawCircle(
                        color = ring,
                        radius = size.minDimension / 2 - w / 2,
                        center = center,
                        style = Stroke(w),
                    )
                }.onFocusChanged { focused = it.isFocused }
                .clickable(role = Role.Button) {
                    if (!busy) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painterResource(R.drawable.ic_power),
                contentDescription = null,
                modifier = Modifier.size(if (diameter > PowerDiameterMobile) PowerIconLarge else PowerIconSmall),
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
                painterResource(R.drawable.ic_folder_off),
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
    val root = mount.fsRoot.ifEmpty { config.fsRoot }
    val image = mount.blockDevice.ifEmpty { config.blockDevice }
    val notSet = stringResource(R.string.config_not_set)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.mount_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoRow(stringResource(R.string.mount_root), root.ifBlank { notSet })
            InfoRow(stringResource(R.string.mount_block_device), image.ifBlank { notSet })
            if (mount.totalBytes > 0) {
                InfoRow(stringResource(R.string.mount_size), formatBytes(Formatters.bytes(mount.totalBytes)))
            }
            InfoRow(
                stringResource(R.string.mount_mode),
                if (mount.readOnly || config.readOnly) stringResource(R.string.mount_ro) else stringResource(R.string.mount_rw),
            )
            InfoRow(
                stringResource(R.string.mount_compression),
                mount.compressionFormats.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: stringResource(if (config.enableCompression) R.string.option_on else R.string.option_off),
            )
        }
    }
}
