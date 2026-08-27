package com.udpfs.app.ui.screens

import com.udpfs.app.ui.components.focusHighlight
import com.udpfs.app.ui.BrowseTarget
import com.udpfs.app.core.forcesReadOnly
import com.udpfs.app.core.activeStoragePath
import com.udpfs.app.core.StorageMode
import com.udpfs.app.core.ServerStatus
import com.udpfs.app.core.ServerConfig
import com.udpfs.app.R
import com.udpfs.app.AppViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import android.os.Build

@Composable
fun ConfigScreen(vm: AppViewModel, onBrowse: (BrowseTarget) -> Unit, isTv: Boolean = false) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val enabled = status is ServerStatus.Idle

    val forcedReadOnly = forcesReadOnly(Build.VERSION.SDK_INT, config.activeStoragePath, context.packageName)

    var editing by remember { mutableStateOf<Int?>(null) }

    val storageSection: @Composable () -> Unit = {
        Section(stringResource(R.string.config_section_storage)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.config_share), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.storageMode == StorageMode.Folder,
                        enabled = enabled,
                        onClick = { vm.updateConfig { it.copy(storageMode = StorageMode.Folder) } },
                        label = { Text(stringResource(R.string.config_mode_folder)) },
                    )
                    FilterChip(
                        selected = config.storageMode == StorageMode.DiskImage,
                        enabled = enabled,
                        onClick = { vm.updateConfig { it.copy(storageMode = StorageMode.DiskImage) } },
                        label = { Text(stringResource(R.string.config_mode_image)) },
                    )
                }
            }
            if (config.storageMode == StorageMode.Folder) {
                PathRow(
                    icon = Icons.Filled.Folder,
                    title = stringResource(R.string.config_fs_root),
                    value = config.fsRoot.ifBlank { stringResource(R.string.config_not_set) },
                    enabled = enabled,
                    onClick = { onBrowse(BrowseTarget.FsRoot) },
                )
            } else {
                PathRow(
                    icon = Icons.Filled.Storage,
                    title = stringResource(R.string.config_block_device),
                    value = config.blockDevice.ifBlank { stringResource(R.string.config_not_set) },
                    enabled = enabled,
                    onClick = { onBrowse(BrowseTarget.BlockDevice) },
                )
            }
        }
    }

    val serverSection: @Composable () -> Unit = {
        Section(stringResource(R.string.config_section_server)) {
            StepperRow(
                title = stringResource(R.string.config_port),
                value = config.port,
                step = 1,
                min = 1,
                max = 65535,
                enabled = enabled,
                onValueClick = { editing = EDIT_PORT },
                onChange = { v -> vm.updateConfig { it.copy(port = v) } },
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.config_sector_size), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServerConfig.SECTOR_SIZES.forEach { size ->
                        FilterChip(
                            selected = config.sectorSize == size,
                            enabled = enabled,
                            onClick = { vm.updateConfig { it.copy(sectorSize = size) } },
                            label = { Text(size.toString()) },
                        )
                    }
                }
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
                onClick = { editing = EDIT_BIND_IP },
            )
        }
    }

    val featuresSection: @Composable () -> Unit = {
        Section(stringResource(R.string.config_section_features)) {
            SwitchRow(
                title = stringResource(R.string.config_read_only),
                checked = config.readOnly || forcedReadOnly,
                enabled = enabled && !forcedReadOnly,
                onChange = { v -> vm.updateConfig { it.copy(readOnly = v) } },
            )
            if (forcedReadOnly) {
                Text(
                    stringResource(R.string.config_readonly_forced),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

    Column(
        modifier = Modifier
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
                Text(
                    stringResource(R.string.config_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isTv) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    storageSection()
                    serverSection()
                }
                Column(Modifier.weight(1f)) {
                    featuresSection()
                }
            }
        } else {
            storageSection()
            serverSection()
            featuresSection()
        }
    }

    editing?.let { edit ->
        when (edit) {
            EDIT_PORT -> NumberDialog(
                title = stringResource(R.string.config_port),
                initial = config.port.toString(),
                onDismiss = { editing = null },
                onConfirm = { value ->
                    vm.updateConfig { it.copy(port = value.coerceIn(1, 65535)) }
                    editing = null
                },
            )
            EDIT_BIND_IP -> TextDialog(
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

private const val EDIT_PORT = 1
private const val EDIT_BIND_IP = 2

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
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
) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusHighlight(MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
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
            Icon(Icons.Filled.Remove, contentDescription = null)
        }
        Text(
            "$value$suffix",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .width(92.dp)
                .then(
                    if (onValueClick != null && enabled) {
                        Modifier
                            .focusHighlight(MaterialTheme.shapes.small)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onValueClick)
                            .padding(vertical = 4.dp)
                    } else {
                        Modifier
                    }
                ),
        )
        IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = enabled && value < max) {
            Icon(Icons.Filled.Add, contentDescription = null)
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
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    placeholder: String,
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
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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

@Composable
private fun NumberDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(5) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { text.toIntOrNull()?.let(onConfirm) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
