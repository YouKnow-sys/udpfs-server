package com.udpfs.app.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udpfs.app.AppViewModel
import com.udpfs.app.R
import com.udpfs.app.core.ServerConfig
import com.udpfs.app.core.ServerStatus
import com.udpfs.app.core.WriteAccess
import com.udpfs.app.core.probeWriteAccessCapped
import com.udpfs.app.ui.BrowseTarget
import com.udpfs.app.ui.components.InputDialog
import com.udpfs.app.ui.components.PathRow
import com.udpfs.app.ui.components.Section
import com.udpfs.app.ui.components.SegmentedRow
import com.udpfs.app.ui.components.SettingRow
import com.udpfs.app.ui.components.StepperRow
import com.udpfs.app.ui.components.SupportingText
import com.udpfs.app.ui.components.SwitchRow
import java.io.File

private enum class EditTarget {
    Port,
    BindIP,
}

private typealias ConfigUpdate = ((ServerConfig) -> ServerConfig) -> Unit

@Composable
fun ConfigScreen(
    vm: AppViewModel,
    onBrowse: (BrowseTarget) -> Unit,
    isTv: Boolean = false,
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val enabled = status is ServerStatus.Idle
    val update = vm::updateConfig

    var resumeKey by remember { mutableIntStateOf(0) }
    var awaitedPause by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { awaitedPause = true }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaitedPause) {
            awaitedPause = false
            resumeKey++
        }
    }

    val fsRootAccess by writeAccessState(vm, config.fsRoot, resumeKey)
    val blockDeviceAccess by writeAccessState(vm, config.blockDevice, resumeKey)

    val forcedReadOnly =
        fsRootAccess == WriteAccess.ReadOnly || blockDeviceAccess == WriteAccess.ReadOnly
    val unreachable =
        fsRootAccess == WriteAccess.Inaccessible || blockDeviceAccess == WriteAccess.Inaccessible

    val pendingProbe =
        (config.fsRoot.isNotBlank() && fsRootAccess == null) ||
            (config.blockDevice.isNotBlank() && blockDeviceAccess == null)
    val featuresEnabled = enabled && !pendingProbe

    var editing by remember { mutableStateOf<EditTarget?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (isTv) 48.dp else 20.dp,
                    vertical = if (isTv) 24.dp else 12.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!enabled) {
            Row(
                Modifier.padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                SupportingText(stringResource(R.string.config_locked))
            }
        }

        if (isTv) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    StorageSection(config, enabled, onBrowse, unreachable, update)
                    ServerSection(
                        config,
                        enabled,
                        onEditPort = { editing = EditTarget.Port },
                        onEditBindIP = { editing = EditTarget.BindIP },
                        update = update,
                    )
                }
                Column(Modifier.weight(1f)) {
                    FeaturesSection(config, featuresEnabled, forcedReadOnly, update)
                }
            }
        } else {
            StorageSection(config, enabled, onBrowse, unreachable, update)
            ServerSection(
                config,
                enabled,
                onEditPort = { editing = EditTarget.Port },
                onEditBindIP = { editing = EditTarget.BindIP },
                update = update,
            )
            FeaturesSection(config, featuresEnabled, forcedReadOnly, update)
        }
    }

    editing?.let { edit ->
        when (edit) {
            EditTarget.Port -> {
                InputDialog(
                    title = stringResource(R.string.config_port),
                    initial = config.port.toString(),
                    keyboardType = KeyboardType.Number,
                    transform = { it.filter(Char::isDigit).take(5) },
                    onDismiss = { editing = null },
                    onConfirm = { value ->
                        value.toIntOrNull()?.let { v ->
                            update { it.copy(port = v.coerceIn(1, 65535)) }
                            editing = null
                        }
                    },
                )
            }

            EditTarget.BindIP -> {
                InputDialog(
                    title = stringResource(R.string.config_bind_ip),
                    initial = config.bindIP,
                    placeholder = stringResource(R.string.config_bind_auto),
                    onDismiss = { editing = null },
                    onConfirm = { value ->
                        update { it.copy(bindIP = value.trim()) }
                        editing = null
                    },
                )
            }
        }
    }
}

@Composable
private fun writeAccessState(
    vm: AppViewModel,
    path: String,
    resumeKey: Int,
): State<WriteAccess?> =
    produceState<WriteAccess?>(initialValue = vm.cachedWriteAccess(path), path, resumeKey) {
        val cached = vm.cachedWriteAccess(path)
        value = cached
        if (path.isNotBlank() && (cached == null || resumeKey > 0)) {
            val access = probeWriteAccessCapped(File(path))
            vm.storeWriteAccess(path, access)
            value = access
        }
    }

@Composable
private fun StorageSection(
    config: ServerConfig,
    enabled: Boolean,
    onBrowse: (BrowseTarget) -> Unit,
    unreachable: Boolean,
    update: ConfigUpdate,
) {
    Section(stringResource(R.string.config_section_storage)) {
        PathRow(
            icon = painterResource(R.drawable.ic_folder),
            title = stringResource(R.string.config_fs_root),
            value = config.fsRoot.ifBlank { stringResource(R.string.config_not_set) },
            enabled = enabled,
            onClick = { onBrowse(BrowseTarget.FsRoot) },
            clearLabel = stringResource(R.string.action_clear_fs_root),
            onClear = { update { it.copy(fsRoot = "") } }.takeIf { config.fsRoot.isNotBlank() },
        )
        PathRow(
            icon = painterResource(R.drawable.ic_storage),
            title = stringResource(R.string.config_block_device),
            value = config.blockDevice.ifBlank { stringResource(R.string.config_not_set) },
            enabled = enabled,
            onClick = { onBrowse(BrowseTarget.BlockDevice) },
            clearLabel = stringResource(R.string.action_clear_block_device),
            onClear = { update { it.copy(blockDevice = "") } }.takeIf { config.blockDevice.isNotBlank() },
        )
        SupportingText(stringResource(R.string.config_share_hint))
        if (unreachable) {
            SupportingText(stringResource(R.string.config_location_unreachable))
        }
    }
}

@Composable
private fun ServerSection(
    config: ServerConfig,
    enabled: Boolean,
    onEditPort: () -> Unit,
    onEditBindIP: () -> Unit,
    update: ConfigUpdate,
) {
    Section(stringResource(R.string.config_section_server)) {
        StepperRow(
            title = stringResource(R.string.config_port),
            value = config.port,
            step = 1,
            min = 1,
            max = 65535,
            enabled = enabled,
            onValueClick = onEditPort,
            onChange = { v -> update { it.copy(port = v) } },
        )
        SettingRow(stringResource(R.string.config_sector_size)) {
            SegmentedRow(
                options = ServerConfig.SECTOR_SIZES,
                selected = config.sectorSize,
                enabled = enabled,
                optionLabel = { it.toString() },
                onSelect = { size -> update { it.copy(sectorSize = size) } },
            )
        }
        StepperRow(
            title = stringResource(R.string.config_peer_timeout),
            value = config.peerTimeoutMinutes,
            step = 5,
            min = 1,
            max = 1440,
            enabled = enabled,
            suffix = stringResource(R.string.config_unit_minutes),
            onChange = { v -> update { it.copy(peerTimeoutMinutes = v) } },
        )
        PathRow(
            icon = painterResource(R.drawable.ic_info),
            title = stringResource(R.string.config_bind_ip),
            value = config.bindIP.ifBlank { stringResource(R.string.config_bind_auto) },
            enabled = enabled,
            onClick = onEditBindIP,
        )
    }
}

@Composable
private fun FeaturesSection(
    config: ServerConfig,
    enabled: Boolean,
    forcedReadOnly: Boolean,
    update: ConfigUpdate,
) {
    Section(stringResource(R.string.config_section_features)) {
        SwitchRow(
            title = stringResource(R.string.config_read_only),
            checked = config.readOnly || forcedReadOnly,
            enabled = enabled && !forcedReadOnly,
            onChange = { v -> update { it.copy(readOnly = v) } },
        )
        if (forcedReadOnly) {
            SupportingText(stringResource(R.string.config_readonly_forced))
        }
        SwitchRow(
            title = stringResource(R.string.config_compression),
            checked = config.enableCompression,
            enabled = enabled,
            onChange = { v -> update { it.copy(enableCompression = v) } },
        )
        if (config.enableCompression) {
            StepperRow(
                title = stringResource(R.string.config_cache),
                value = config.compressionCacheSize,
                step = 1,
                min = 1,
                max = 4096,
                enabled = enabled,
                suffix = stringResource(R.string.config_unit_blocks),
                onChange = { v -> update { it.copy(compressionCacheSize = v) } },
            )
        }
        SwitchRow(
            title = stringResource(R.string.config_show_stats),
            checked = config.showStats,
            enabled = true,
            onChange = { v -> update { it.copy(showStats = v) } },
        )
        val autostartBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        SwitchRow(
            title = stringResource(R.string.config_auto_start),
            checked = config.autoStart,
            enabled = enabled && !autostartBlocked,
            onChange = { v -> update { it.copy(autoStart = v) } },
        )
        if (autostartBlocked) {
            SupportingText(stringResource(R.string.config_auto_start_unavailable))
        }
    }
}
