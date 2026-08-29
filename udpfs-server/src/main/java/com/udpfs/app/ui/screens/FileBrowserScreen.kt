package com.udpfs.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.udpfs.app.R
import com.udpfs.app.core.Permissions
import com.udpfs.app.core.formatByteValue
import com.udpfs.app.ui.BrowseMode
import com.udpfs.app.ui.components.FOCUS_STIFFNESS
import com.udpfs.app.ui.components.SupportingText
import com.udpfs.app.ui.components.focusedClickable
import com.udpfs.app.ui.format.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val DEFAULT_START = "/storage/emulated/0"

private val BLOCK_EXTENSIONS = listOf(".iso", ".bin", ".img", ".zso", ".cso", ".vhd")

private val VOLUME_PARENTS = setOf("/", "/storage", "/storage/emulated")

private data class Entry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: String?,
)

private data class Listing(
    val entries: List<Entry>,
    val accessible: Boolean,
)

private val LISTING_UNREADABLE = Listing(entries = emptyList(), accessible = false)

private fun volumeRootPath(
    externalFilesDirPath: String,
    packageName: String,
): String? {
    val suffix = "/Android/data/$packageName/files"
    return externalFilesDirPath.takeIf { it.endsWith(suffix) }?.removeSuffix(suffix)
}

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

    var dirPath by rememberSaveable { mutableStateOf(startPath.ifBlank { DEFAULT_START }) }
    val atVolumes = dirPath in VOLUME_PARENTS

    var resumeKey by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeKey++ }

    var storageGranted by remember(context, resumeKey) { mutableStateOf(Permissions.hasStorageAccess(context)) }
    val requestStoragePerms =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            storageGranted = Permissions.hasStorageAccess(context)
        }

    val volumes by produceState(initialValue = emptyList<File>(), context, resumeKey) {
        value =
            withContext(Dispatchers.IO) {
                buildList {
                    File(DEFAULT_START).takeIf { it.exists() }?.let { add(it) }
                    context.getExternalFilesDirs(null).forEach { dirFile ->
                        val root =
                            dirFile
                                ?.let { volumeRootPath(it.absolutePath, context.packageName) }
                                ?.let(::File)
                                ?.takeIf { it.exists() && it.absolutePath != DEFAULT_START }
                        if (root != null) add(root)
                    }
                }.distinct()
            }
    }

    BackHandler {
        if (atVolumes) {
            onDismiss()
        } else {
            File(dirPath).parentFile?.let { dirPath = it.absolutePath } ?: onDismiss()
        }
    }

    val listing by produceState<Listing?>(initialValue = null, dirPath, mode, volumes, resumeKey) {
        val corrected =
            withContext(Dispatchers.IO) {
                if (atVolumes) {
                    File(dirPath)
                } else {
                    val f = File(dirPath)
                    when {
                        f.isDirectory -> f
                        f.isFile -> f.parentFile ?: File(DEFAULT_START)
                        else -> File(DEFAULT_START)
                    }
                }
            }
        if (corrected.absolutePath != dirPath) {
            dirPath = corrected.absolutePath
            return@produceState
        }
        value =
            withContext(Dispatchers.IO) {
                if (atVolumes) {
                    Listing(
                        entries =
                            volumes.map { v ->
                                Entry(
                                    path = v.absolutePath,
                                    name = if (v.absolutePath == DEFAULT_START) internalLabel else v.name,
                                    isDirectory = true,
                                    size = null,
                                )
                            },
                        accessible = true,
                    )
                } else {
                    listEntries(corrected, mode) { formatBytes(context, formatByteValue(it)) }
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
                            if (atVolumes) {
                                stringResource(R.string.browser_storage)
                            } else {
                                File(dirPath).name.ifBlank { "/" }
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
                        }
                    },
                )
                if (!atVolumes) {
                    Breadcrumb(path = dirPath) { dirPath = it }
                }
            }
        },
        bottomBar = {
            if (mode == BrowseMode.Directory && !atVolumes) {
                val canSelect = listing?.accessible == true
                var focused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    if (focused) 1.02f else 1f,
                    spring(Spring.DampingRatioNoBouncy, FOCUS_STIFFNESS),
                    label = "selectScale",
                )
                val container by animateColorAsState(
                    if (focused) {
                        lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, 0.4f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    label = "selectContainer",
                )
                Button(
                    onClick = { onPick(dirPath) },
                    enabled = canSelect,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 20.dp)
                            .onFocusChanged { focused = it.hasFocus }
                            .scale(scale),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = container,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    Text(stringResource(R.string.browser_select_folder, File(dirPath).name.ifBlank { dirPath }))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!storageGranted) {
                AccessBanner(onGrant = { Permissions.requestStorageAccess(context, requestStoragePerms) })
            }
            val result = listing
            when {
                result == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                !result.accessible -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                if (storageGranted) R.string.browser_restricted else R.string.browser_no_access,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                result.entries.isEmpty() && mode == BrowseMode.File -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.browser_no_files),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(result.entries, key = { it.path }) { entry ->
                            BrowserRow(
                                icon =
                                    painterResource(
                                        when {
                                            atVolumes -> R.drawable.ic_storage
                                            entry.isDirectory -> R.drawable.ic_folder
                                            else -> R.drawable.ic_insert_drive_file
                                        },
                                    ),
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
    }
}

@Composable
private fun AccessBanner(onGrant: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SupportingText(
            stringResource(R.string.browser_limited_access),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onGrant) { Text(stringResource(R.string.storage_grant)) }
    }
}

private fun listEntries(
    dir: File,
    mode: BrowseMode,
    formatSize: (Long) -> String,
): Listing {
    val files = dir.listFiles() ?: return LISTING_UNREADABLE
    val entries =
        files
            .asSequence()
            .filter { f ->
                f.isDirectory || (mode == BrowseMode.File && BLOCK_EXTENSIONS.any { f.name.lowercase().endsWith(it) })
            }.map { f ->
                val isDir = f.isDirectory
                Entry(
                    path = f.absolutePath,
                    name = f.name,
                    isDirectory = isDir,
                    size = if (isDir) null else formatSize(f.length()),
                )
            }.sortedWith(compareByDescending<Entry> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    return Listing(entries = entries, accessible = true)
}

@Composable
private fun Breadcrumb(
    path: String,
    onNavigate: (String) -> Unit,
) {
    val crumbs =
        remember(path) {
            buildList {
                var f: File? = File(path)
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
        crumbs.forEachIndexed { index, (name, crumbPath) ->
            if (index > 0) {
                Text(
                    "›",
                    Modifier
                        .clearAndSetSemantics { }
                        .padding(horizontal = 6.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                name,
                modifier =
                    Modifier
                        .clickable(enabled = index < crumbs.lastIndex) { onNavigate(crumbPath) }
                        .padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (index == crumbs.lastIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BrowserRow(
    icon: Painter,
    name: String,
    detail: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusedClickable(MaterialTheme.shapes.medium, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = icon,
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
