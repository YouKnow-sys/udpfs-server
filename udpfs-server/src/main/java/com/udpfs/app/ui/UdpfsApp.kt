package com.udpfs.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udpfs.app.AppViewModel
import com.udpfs.app.R
import com.udpfs.app.ui.screens.AboutScreen
import com.udpfs.app.ui.screens.ConfigScreen
import com.udpfs.app.ui.screens.DEFAULT_START
import com.udpfs.app.ui.screens.FileBrowserScreen
import com.udpfs.app.ui.screens.ServerScreen
import com.udpfs.app.ui.screens.StatsScreen
import kotlinx.coroutines.flow.map

enum class Screen { Server, Config, Stats }

private val Screen.labelRes: Int get() =
    when (this) {
        Screen.Server -> R.string.nav_server
        Screen.Config -> R.string.nav_config
        Screen.Stats -> R.string.nav_stats
    }

enum class BrowseMode { Directory, File }

enum class BrowseTarget { FsRoot, BlockDevice }

@Composable
fun UdpfsApp() {
    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)

    val showStats by vm.config.map { it.showStats }.collectAsStateWithLifecycle(initialValue = true)

    var screen by rememberSaveable { mutableStateOf(Screen.Server) }
    var browseTarget by rememberSaveable { mutableStateOf<BrowseTarget?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        for (message in vm.errors) snackbar.showSnackbar(message)
    }

    LaunchedEffect(showStats) {
        if (!showStats && screen == Screen.Stats) screen = Screen.Server
    }

    val isTv =
        (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION

    val destinations =
        remember(showStats) {
            buildList {
                add(Screen.Server to R.drawable.ic_power)
                add(Screen.Config to R.drawable.ic_tune)
                if (showStats) add(Screen.Stats to R.drawable.ic_insights)
            }
        }

    val overlay = browseTarget != null || showAbout

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!isTv && !overlay) {
                NavigationBar {
                    destinations.forEach { (dest, icon) ->
                        NavigationBarItem(
                            selected = screen == dest,
                            onClick = { screen = dest },
                            icon = { Icon(painterResource(icon), contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val target = browseTarget
        if (target != null) {
            val config = vm.config.value
            Box(Modifier.fillMaxSize().padding(padding)) {
                FileBrowserScreen(
                    mode = if (target == BrowseTarget.FsRoot) BrowseMode.Directory else BrowseMode.File,
                    startPath =
                        when (target) {
                            BrowseTarget.FsRoot -> config.fsRoot.ifBlank { DEFAULT_START }
                            BrowseTarget.BlockDevice -> config.blockDevice.ifBlank { DEFAULT_START }
                        },
                    onPick = { path ->
                        when (target) {
                            BrowseTarget.FsRoot -> vm.pickFsRoot(path)
                            BrowseTarget.BlockDevice -> vm.pickBlockDevice(path)
                        }
                        browseTarget = null
                    },
                    onDismiss = { browseTarget = null },
                )
            }
            return@Scaffold
        }

        if (showAbout) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                AboutScreen(onDismiss = { showAbout = false })
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
                                    icon = { Icon(painterResource(icon), contentDescription = null) },
                                    label = { Text(stringResource(dest.labelRes)) },
                                )
                            }
                        }
                    }
                    AppContent(screen, vm, isTv, onBrowse = { browseTarget = it }, onAbout = { showAbout = true })
                }
            } else {
                AppContent(screen, vm, isTv, onBrowse = { browseTarget = it }, onAbout = { showAbout = true })
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
    onAbout: () -> Unit,
) {
    val server = remember(isTv) { movableContentOf { ServerScreen(vm, onAbout, isTv) } }
    val config = remember(isTv) { movableContentOf { ConfigScreen(vm = vm, onBrowse = onBrowse, isTv = isTv) } }
    val stats = remember(isTv) { movableContentOf { StatsScreen(vm, isTv) } }
    when (screen) {
        Screen.Server -> server()
        Screen.Config -> config()
        Screen.Stats -> stats()
    }
}
