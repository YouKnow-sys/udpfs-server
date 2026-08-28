@file:Suppress("FunctionName")

package com.udpfs.app.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udpfs.app.AppViewModel
import com.udpfs.app.R
import com.udpfs.app.core.ServerConfig
import com.udpfs.app.core.ServerStatus
import com.udpfs.app.core.WRITE_PROBE_TIMEOUT_MS
import com.udpfs.app.core.WriteAccess
import com.udpfs.app.core.probeWriteAccess
import com.udpfs.app.ui.BrowseTarget
import com.udpfs.app.ui.components.SettingRow
import com.udpfs.app.ui.components.SupportingText
import com.udpfs.app.ui.components.focusRing
import com.udpfs.app.ui.components.focusedClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private enum class EditTarget {
    Port,
    BindIP,
}

@Composable
fun ConfigScreen(
    vm: AppViewModel,
    onBrowse: (BrowseTarget) -> Unit,
    isTv: Boolean = false,
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val enabled = status is ServerStatus.Idle

    var resumeKey by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeKey++ }

    val fsRootAccess by writeAccessState(config.fsRoot, resumeKey)
    val blockDeviceAccess by writeAccessState(config.blockDevice, resumeKey)

    val forcedReadOnly =
        fsRootAccess == WriteAccess.READ_ONLY || blockDeviceAccess == WriteAccess.READ_ONLY

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
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
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
                    StorageSection(vm, config, enabled, onBrowse)
                    ServerSection(
                        vm,
                        config,
                        enabled,
                        onEditPort = { editing = EditTarget.Port },
                        onEditBindIP = { editing = EditTarget.BindIP },
                    )
                }
                Column(Modifier.weight(1f)) {
                    FeaturesSection(vm, config, featuresEnabled, forcedReadOnly)
                }
            }
        } else {
            StorageSection(vm, config, enabled, onBrowse)
            ServerSection(
                vm,
                config,
                enabled,
                onEditPort = { editing = EditTarget.Port },
                onEditBindIP = { editing = EditTarget.BindIP },
            )
            FeaturesSection(vm, config, featuresEnabled, forcedReadOnly)
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
                            vm.updateConfig { it.copy(port = v.coerceIn(1, 65535)) }
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
                        vm.updateConfig { it.copy(bindIP = value.trim()) }
                        editing = null
                    },
                )
            }
        }
    }
}

@Composable
private fun writeAccessState(
    path: String,
    resumeKey: Int,
): State<WriteAccess?> =
    produceState<WriteAccess?>(initialValue = null, path, resumeKey) {
        value = null
        if (path.isNotBlank()) {
            value =
                withTimeoutOrNull(WRITE_PROBE_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { probeWriteAccess(File(path)) }
                } ?: WriteAccess.READ_ONLY
        }
    }

@Composable
private fun StorageSection(
    vm: AppViewModel,
    config: ServerConfig,
    enabled: Boolean,
    onBrowse: (BrowseTarget) -> Unit,
) {
    Section(stringResource(R.string.config_section_storage)) {
        PathRow(
            icon = Icons.Filled.Folder,
            title = stringResource(R.string.config_fs_root),
            value = config.fsRoot.ifBlank { stringResource(R.string.config_not_set) },
            enabled = enabled,
            onClick = { onBrowse(BrowseTarget.FsRoot) },
            clearLabel = stringResource(R.string.action_clear_fs_root),
            onClear =
                if (config.fsRoot.isBlank()) {
                    null
                } else {
                    { vm.updateConfig { it.copy(fsRoot = "") } }
                },
        )
        PathRow(
            icon = Icons.Filled.Storage,
            title = stringResource(R.string.config_block_device),
            value = config.blockDevice.ifBlank { stringResource(R.string.config_not_set) },
            enabled = enabled,
            onClick = { onBrowse(BrowseTarget.BlockDevice) },
            clearLabel = stringResource(R.string.action_clear_block_device),
            onClear =
                if (config.blockDevice.isBlank()) {
                    null
                } else {
                    { vm.updateConfig { it.copy(blockDevice = "") } }
                },
        )
        SupportingText(stringResource(R.string.config_share_hint))
    }
}

@Composable
private fun ServerSection(
    vm: AppViewModel,
    config: ServerConfig,
    enabled: Boolean,
    onEditPort: () -> Unit,
    onEditBindIP: () -> Unit,
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
            onChange = { v -> vm.updateConfig { it.copy(port = v) } },
        )
        SettingRow(stringResource(R.string.config_sector_size)) {
            SegmentedRow(
                options = ServerConfig.SECTOR_SIZES,
                selected = config.sectorSize,
                enabled = enabled,
                optionLabel = { it.toString() },
                onSelect = { size -> vm.updateConfig { it.copy(sectorSize = size) } },
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
            onChange = { v -> vm.updateConfig { it.copy(peerTimeoutMinutes = v) } },
        )
        PathRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.config_bind_ip),
            value = config.bindIP.ifBlank { stringResource(R.string.config_bind_auto) },
            enabled = enabled,
            onClick = onEditBindIP,
        )
    }
}

@Composable
private fun FeaturesSection(
    vm: AppViewModel,
    config: ServerConfig,
    enabled: Boolean,
    forcedReadOnly: Boolean,
) {
    Section(stringResource(R.string.config_section_features)) {
        SwitchRow(
            title = stringResource(R.string.config_read_only),
            checked = config.readOnly || forcedReadOnly,
            enabled = enabled && !forcedReadOnly,
            onChange = { v -> vm.updateConfig { it.copy(readOnly = v) } },
        )
        if (forcedReadOnly) {
            SupportingText(stringResource(R.string.config_readonly_forced))
        }
        SwitchRow(
            title = stringResource(R.string.config_compression),
            checked = config.enableCompression,
            enabled = enabled,
            onChange = { v -> vm.updateConfig { it.copy(enableCompression = v) } },
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
                onChange = { v -> vm.updateConfig { it.copy(compressionCacheSize = v) } },
            )
        }
        SwitchRow(
            title = stringResource(R.string.config_show_stats),
            checked = config.showStats,
            enabled = true,
            onChange = { v -> vm.updateConfig { it.copy(showStats = v) } },
        )
        SwitchRow(
            title = stringResource(R.string.config_auto_start),
            checked = config.autoStart,
            enabled = enabled,
            onChange = { v -> vm.updateConfig { it.copy(autoStart = v) } },
        )
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun PathRow(
    icon: ImageVector,
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    clearLabel: String = "",
    onClear: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusedClickable(MaterialTheme.shapes.medium, enabled, onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClear != null) {
            IconButton(
                onClick = onClear,
                enabled = enabled,
                modifier = Modifier.focusRing(),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = clearLabel)
            }
        }
        if (enabled) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    step: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    suffix: String = "",
    onValueClick: (() -> Unit)? = null,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = enabled && value > min) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.action_decrease, title))
        }
        Text(
            "$value$suffix",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .width(92.dp)
                    .then(
                        if (onValueClick != null && enabled) {
                            Modifier
                                .focusedClickable(MaterialTheme.shapes.small) { onValueClick() }
                                .padding(vertical = 4.dp)
                        } else {
                            Modifier
                        },
                    ),
        )
        IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = enabled && value < max) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_increase, title))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            ).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            interactionSource = interactionSource,
        )
    }
}

@Composable
private fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    enabled: Boolean,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(optionLabel(option)) },
            )
        }
    }
}

@Composable
private fun InputDialog(
    title: String,
    initial: String,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Uri,
    transform: (String) -> String = { it },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = transform(it) },
                singleLine = true,
                placeholder = placeholder?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
