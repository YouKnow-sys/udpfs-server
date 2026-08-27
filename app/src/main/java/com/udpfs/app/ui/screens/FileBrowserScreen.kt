package com.udpfs.app.ui.screens

import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.udpfs.app.ui.components.focusHighlight
import com.udpfs.app.ui.BrowseMode
import com.udpfs.app.core.Formatters
import com.udpfs.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler

const val DEFAULT_START = "/storage/emulated/0"

private val BLOCK_EXTENSIONS = listOf(".iso", ".bin", ".img", ".zso", ".cso", ".vhd")

private val VOLUME_PARENTS = setOf("/", "/storage", "/storage/emulated")
private const val PRIMARY_ROOT = "/storage/emulated/0"

private data class Entry(val path: String, val name: String, val isDirectory: Boolean, val size: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    mode: BrowseMode,
    startPath: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val internalLabel = stringResource(R.string.browser_internal_storage)

    val initial = File(startPath).let { f ->
        when {
            f.isDirectory -> f
            f.isFile -> f.parentFile ?: File(DEFAULT_START)
            else -> File(DEFAULT_START)
        }
    }
    var dirPath by rememberSaveable { mutableStateOf(initial.absolutePath) }
    val dir = File(dirPath)
    val atVolumes = dirPath in VOLUME_PARENTS

    val volumes = remember(context) {
        buildList {
            File(PRIMARY_ROOT).takeIf { it.exists() }?.let { add(it) }
            context.getExternalFilesDirs(null).forEach { dirFile ->
                if (dirFile == null) return@forEach
                val segments = dirFile.absolutePath.split('/').filter { it.isNotBlank() }.take(3)
                if (segments.size == 3 && segments[0] == "storage") {
                    File("/" + segments.joinToString("/"))
                        .takeIf { it.exists() && it.absolutePath != PRIMARY_ROOT }
                        ?.let { add(it) }
                }
            }
        }.distinct()
    }

    BackHandler {
        when {
            atVolumes -> onDismiss()
            else -> dir.parentFile?.let { dirPath = it.absolutePath } ?: onDismiss()
        }
    }

    val entries by produceState<List<Entry>?>(initialValue = null, dirPath, mode, volumes) {
        value = withContext(Dispatchers.IO) {
            if (atVolumes) {
                volumes.map { v ->
                    Entry(
                        path = v.absolutePath,
                        name = if (v.absolutePath == PRIMARY_ROOT) internalLabel else v.name,
                        isDirectory = true,
                        size = null,
                    )
                }
            } else {
                dir.listFiles()
                    ?.map { f ->
                        Entry(
                            path = f.absolutePath,
                            name = f.name,
                            isDirectory = f.isDirectory,
                            size = if (f.isFile) Formatters.bytes(f.length()) else null,
                        )
                    }
                    ?.filter { e ->
                        e.isDirectory || (mode == BrowseMode.File && BLOCK_EXTENSIONS.any { e.name.lowercase().endsWith(it) })
                    }
                    ?.sortedWith(compareByDescending<Entry> { it.isDirectory }.thenBy { it.name.lowercase() })
                    ?: emptyList()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            if (atVolumes) stringResource(R.string.browser_storage)
                            else dir.name.ifBlank { "/" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                )
                if (atVolumes) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            stringResource(R.string.browser_storage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Breadcrumb(dir = dir) { dirPath = it }
                }
            }
        },
        bottomBar = {
            if (mode == BrowseMode.Directory && !atVolumes) {
                Button(
                    onClick = { onPick(dir.absolutePath) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.browser_select_folder, dir.name.ifBlank { dir.absolutePath }))
                }
            }
        },
    ) { padding ->
        val list = entries
        when {
            list == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            list.isEmpty() && mode == BrowseMode.File ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.browser_no_files),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                if (mode == BrowseMode.File) {
                    item {
                        BrowserRow(
                            icon = Icons.Filled.RadioButtonUnchecked,
                            name = stringResource(R.string.browser_none),
                            detail = stringResource(R.string.config_no_block_device),
                        ) { onPick("") }
                    }
                }
                items(list, key = { it.path }) { entry ->
                    val isVolume = atVolumes
                    BrowserRow(
                        icon = when {
                            isVolume -> Icons.Filled.Storage
                            entry.isDirectory -> Icons.Filled.Folder
                            else -> Icons.Outlined.InsertDriveFile
                        },
                        name = entry.name,
                        detail = entry.size,
                    ) {
                        if (entry.isDirectory) dirPath = entry.path else onPick(entry.path)
                    }
                }
            }
        }
    }
}

@Composable
private fun Breadcrumb(dir: File, onNavigate: (String) -> Unit) {
    val crumbs = remember(dir.absolutePath) {
        buildList {
            var f: File? = dir
            while (f != null) {
                add(0, f.name.ifBlank { "/" } to f.absolutePath)
                f = f.parentFile
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, (name, path) ->
            if (index > 0) {
                Text(
                    "›",
                    Modifier.padding(horizontal = 6.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                name,
                modifier = Modifier
                    .clickable(enabled = index < crumbs.lastIndex) { onNavigate(path) }
                    .padding(vertical = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (index == crumbs.lastIndex) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.secondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BrowserRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    detail: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusHighlight(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
