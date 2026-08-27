package com.udpfs.app.ui

import com.udpfs.app.ui.screens.StatsScreen
import com.udpfs.app.ui.screens.ServerScreen
import com.udpfs.app.ui.screens.FileBrowserScreen
import com.udpfs.app.ui.screens.DEFAULT_START
import com.udpfs.app.ui.screens.ConfigScreen
import com.udpfs.app.core.forcesReadOnly
import com.udpfs.app.core.Settings
import com.udpfs.app.core.ServerRepository
import com.udpfs.app.R
import com.udpfs.app.AppViewModelFactory
import com.udpfs.app.AppViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import android.os.Build
import android.content.res.Configuration

enum class Screen { Server, Config, Stats }

enum class BrowseMode { Directory, File }

enum class BrowseTarget { FsRoot, BlockDevice }

@Composable
fun UdpfsApp() {
    val context = LocalContext.current
    val vm: AppViewModel = viewModel(factory = AppViewModelFactory(Settings(context.applicationContext)))
    val config by vm.config.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(Screen.Server) }
    var browseTarget by rememberSaveable { mutableStateOf<BrowseTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        ServerRepository.errors.collect { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(config.showStats) {
        if (!config.showStats && screen == Screen.Stats) screen = Screen.Server
    }

    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION

    val destinations = remember(config.showStats) {
        buildList {
            add(Screen.Server to Icons.Filled.PowerSettingsNew)
            add(Screen.Config to Icons.Filled.Tune)
            if (config.showStats) add(Screen.Stats to Icons.Filled.Insights)
        }
    }

    val browsing = browseTarget != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!isTv && !browsing) {
                NavigationBar {
                    destinations.forEach { (dest, icon) ->
                        NavigationBarItem(
                            selected = screen == dest,
                            onClick = { screen = dest },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(screenLabel(dest)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val target = browseTarget
        if (target != null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                FileBrowserScreen(
                    mode = if (target == BrowseTarget.FsRoot) BrowseMode.Directory else BrowseMode.File,
                    startPath = when (target) {
                        BrowseTarget.FsRoot -> config.fsRoot.ifBlank { DEFAULT_START }
                        BrowseTarget.BlockDevice -> config.blockDevice.ifBlank { DEFAULT_START }
                    },
                    onPick = { path ->
                        val forcedRo = forcesReadOnly(Build.VERSION.SDK_INT, path, context.packageName)
                        vm.updateConfig {
                            when (target) {
                                BrowseTarget.FsRoot -> it.copy(fsRoot = path)
                                BrowseTarget.BlockDevice -> it.copy(blockDevice = path)
                            }.let { picked -> if (forcedRo) picked.copy(readOnly = true) else picked }
                        }
                        browseTarget = null
                    },
                    onDismiss = { browseTarget = null },
                )
            }
            return@Scaffold
        }

        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isTv) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail {
                        Column(
                            Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        ) {
                            destinations.forEach { (dest, icon) ->
                                NavigationRailItem(
                                    selected = screen == dest,
                                    onClick = { screen = dest },
                                    icon = { Icon(icon, contentDescription = null) },
                                    label = { Text(screenLabel(dest)) },
                                )
                            }
                        }
                    }
                    AppContent(screen, vm, isTv, onBrowse = { browseTarget = it })
                }
            } else {
                AppContent(screen, vm, isTv, onBrowse = { browseTarget = it })
            }
        }
    }
}

@Composable
private fun AppContent(
    screen: Screen,
    vm: AppViewModel,
    isTv: Boolean,
    onBrowse: (BrowseTarget) -> Unit,
) {
    when (screen) {
        Screen.Server -> ServerScreen(vm, isTv)
        Screen.Config -> ConfigScreen(vm = vm, onBrowse = onBrowse, isTv = isTv)
        Screen.Stats -> StatsScreen(vm, isTv)
    }
}

@Composable
private fun screenLabel(screen: Screen): String = when (screen) {
    Screen.Server -> stringResource(R.string.nav_server)
    Screen.Config -> stringResource(R.string.nav_config)
    Screen.Stats -> stringResource(R.string.nav_stats)
}
